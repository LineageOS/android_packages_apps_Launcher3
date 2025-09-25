/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.homescreenfiles

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATA
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.database.getStringOrNull
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.FileChange
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** MediaStore-based implementation of [HomeScreenFilesProvider]. */
class HomeScreenFilesMediaStoreProvider(
    private val context: Context,
    private val executorService: ExecutorService,
    lifecycle: DaggerSingletonTracker,
) : HomeScreenFilesProvider {
    override val fileChanges = MutableListenableStream<FileChange>()

    // Future that completes when the external storage directory mounts.
    private val externalStorageDirectoryMountedFuture = CompletableFuture<Void>()

    // Tracks URI movements originating from calls to [#moveToHomeScreen()]. This allows us to:
    // (1) Disallow overlapping attempts to move a given URI, and
    // (2) Reconcile media store URIs with URI aliases from other content provider authorities.
    private val inProgressMoveToHomeScreenUriAliases = ConcurrentHashMap<Uri, Uri>()

    init {
        if (isExternalStorageDirectoryMounted()) {
            externalStorageDirectoryMountedFuture.complete(null)
        }

        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                    if (selfChange || uri == null) {
                        return
                    }

                    // NOTE: The media provider dispatches an event during both mount and unmount.
                    with(externalStorageDirectoryMountedFuture) {
                        if (!isDone && isExternalStorageDirectoryMounted()) {
                            complete(null)
                        }
                    }

                    if (uri.hasIdSegment()) {
                        fileChanges.dispatchValue(
                            FileChange(
                                uri,
                                flags,
                                query(uri),
                                inProgressMoveToHomeScreenUriAliases.remove(uri),
                            )
                        )
                    }
                }
            }

        val uri = MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.registerContentObserver(uri, true, observer)
        lifecycle.addCloseable { context.contentResolver.unregisterContentObserver(observer) }
    }

    override fun canMoveToHomeScreen(uriList: List<Uri>?): Boolean =
        uriList?.run { isNotEmpty() && all { uri -> canMoveToHomeScreen(uri) } } == true

    /** NOTE: Currently only URIs which can be resolved by the media store are supported. */
    private fun canMoveToHomeScreen(uri: Uri): Boolean =
        isExternalStorageProviderUri(uri) || isMediaStoreUri(uri)

    override fun moveToHomeScreen(uriList: List<Uri>): List<CompletableFuture<Boolean>> =
        uriList.map { uri: Uri -> supplyAsync({ moveToHomeScreen(uri) }, executorService) }

    @WorkerThread
    private fun moveToHomeScreen(uri: Uri): Boolean {
        var attemptMove = false
        var mediaUri: Uri? = null
        var success = false
        try {
            // NOTE: Overlapping move attempts for a given URI are disallowed. Also note that the
            // selection criteria below prevents moving a URI to a path it already occupies; the
            // media provider itself has additional protections to prevent recursive moves.
            mediaUri = if (isMediaStoreUri(uri)) uri else MediaStore.getMediaUri(context, uri)!!
            attemptMove = inProgressMoveToHomeScreenUriAliases.putIfAbsent(mediaUri, uri) == null
            success =
                attemptMove &&
                    (context.contentResolver.update(
                        /*uri=*/ mediaUri,
                        /*contentValues=*/ ContentValues().apply {
                            put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH)
                        },
                        /*where=*/ "$RELATIVE_PATH != ?",
                        /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                    ) == 1)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to move URI to '$HOME_SCREEN_FOLDER_RELATIVE_PATH'", e)
        } finally {
            if (attemptMove && !success) {
                inProgressMoveToHomeScreenUriAliases.remove(mediaUri)
            }
        }
        return success
    }

    /** Returns all file items presented in [HOME_SCREEN_FOLDER_RELATIVE_PATH]. */
    override fun query(): Lazy<Map<Uri, HomeScreenFile>> {
        val query: Callable<Map<Uri, HomeScreenFile>> = Callable {
            val result = mutableMapOf<Uri, HomeScreenFile>()
            try {
                context.contentResolver
                    .query(
                        MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                        arrayOf(_ID).plus(QUERY_DEFAULT_PROJECTION),
                        QUERY_DEFAULT_SELECTION,
                        QUERY_DEFAULT_SELECTION_ARGS,
                        null,
                        null,
                    )
                    ?.use {
                        val idColumnIndex = it.getColumnIndex(_ID)
                        val displayNameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
                        val mimeTypeColumnIndex = it.getColumnIndex(MIME_TYPE)
                        val dataColumnIndex = it.getColumnIndex(DATA)
                        val user = Process.myUserHandle()

                        while (it.moveToNext()) {
                            val id = it.getLong(idColumnIndex)
                            val uri = MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY, id)
                            result[uri] =
                                HomeScreenFile(
                                    uri = uri,
                                    displayName = it.getString(displayNameColumnIndex),
                                    mimeType = it.getStringOrNull(mimeTypeColumnIndex),
                                    isDirectory = File(it.getString(dataColumnIndex)).isDirectory,
                                    user = user,
                                )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query '$HOME_SCREEN_FOLDER_RELATIVE_PATH'", e)
            }
            return@Callable result
        }

        // TODO(b/444563784): Implement more robust solution that doesn't block loader task thread.
        // NOTE: The external storage directory may not have been mounted when [#query()] is called
        // since it is called early in the both the user session and application lifecycles. Giving
        // the directory an opportunity to mount is a temporary solution which has the potential to
        // block the loader task thread, though the clock starts when the loader task is created,
        // not when it is run. This will be replaced with a more robust solution that does not block
        // the loader task prior to feature launch.
        val future =
            externalStorageDirectoryMountedFuture
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .handleAsync(
                    { _, throwable ->
                        if (throwable != null) {
                            Log.e(TAG, "External storage directory not mounted", throwable)
                            emptyMap()
                        } else {
                            query.call()
                        }
                    },
                    executorService,
                )

        return lazy { future.get() }
    }

    /** Queries a single file from MediaStore by its URI. */
    private fun query(uri: Uri): Future<HomeScreenFile?> {
        if (!isMediaStoreUri(uri)) {
            return CompletableFuture.completedFuture(null)
        }
        val query: Callable<HomeScreenFile?> = Callable {
            context.contentResolver
                .query(
                    uri,
                    QUERY_DEFAULT_PROJECTION,
                    QUERY_DEFAULT_SELECTION,
                    QUERY_DEFAULT_SELECTION_ARGS,
                    null,
                    null,
                )
                ?.use {
                    if (it.count == 1) {
                        it.moveToFirst()
                        val displayNameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
                        val mimeTypeColumnIndex = it.getColumnIndex(MIME_TYPE)
                        val dataColumnIndex = it.getColumnIndex(DATA)
                        HomeScreenFile(
                            uri = uri,
                            displayName = it.getString(displayNameColumnIndex),
                            mimeType = it.getStringOrNull(mimeTypeColumnIndex),
                            isDirectory = File(it.getString(dataColumnIndex)).isDirectory,
                            user = Process.myUserHandle(),
                        )
                    } else {
                        null
                    }
                }
        }
        return executorService.submit(query)
    }

    companion object {
        private val QUERY_DEFAULT_PROJECTION = arrayOf(DISPLAY_NAME, MIME_TYPE, DATA)
        private const val QUERY_DEFAULT_SELECTION = "$RELATIVE_PATH = ?"
        private val QUERY_DEFAULT_SELECTION_ARGS = arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH)
        private const val TAG = "HomeScreenFilesMediaStoreProvider"

        private fun isExternalStorageDirectoryMounted() =
            Environment.getExternalStorageState(Environment.getExternalStorageDirectory()) ==
                Environment.MEDIA_MOUNTED

        private fun isExternalStorageProviderUri(uri: Uri?) =
            uri?.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY

        private fun isMediaStoreUri(uri: Uri) =
            uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY

        private fun Uri.hasIdSegment(): Boolean =
            kotlin.runCatching { ContentUris.parseId(this) != -1L }.getOrDefault(false)
    }
}
