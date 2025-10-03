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
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
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
import com.android.launcher3.R
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
    private val fileFactory: (path: String) -> File,
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

    override fun canCreateNewFolder(): Boolean =
        externalStorageDirectoryMountedFuture.isDone &&
            !externalStorageDirectoryMountedFuture.isCancelled &&
            !externalStorageDirectoryMountedFuture.isCompletedExceptionally

    override fun createNewFolder(): CompletableFuture<Boolean> {
        return supplyAsync(
            {
                if (!canCreateNewFolder()) {
                    Log.e(TAG, "Unable to create folder due to unmet preconditions")
                    return@supplyAsync false
                }

                try {
                    // NOTE: The media provider will create a disambiguated folder name if needed so
                    // as to ensure uniqueness (e.g. "New folder" -> "New folder (1)").
                    val uri =
                        context.contentResolver.insert(
                            MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                            ContentValues().apply {
                                put(
                                    DISPLAY_NAME,
                                    context.getString(R.string.default_new_folder_name),
                                )
                                put(MIME_TYPE, MIME_TYPE_DIR)
                                put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH)
                            },
                        )

                    if (uri == null) {
                        Log.e(
                            TAG,
                            "Unable to create new folder due to failure to insert into media store",
                        )
                        return@supplyAsync false
                    }

                    context.contentResolver.query(uri, arrayOf(DATA), null, null).use { c ->
                        if (c == null || !c.moveToFirst()) {
                            Log.e(
                                TAG,
                                "Unable to create new folder due to failure to query media store",
                            )
                            return@supplyAsync false
                        }

                        // NOTE: Insertion into the media store doesn't guarantee the creation of
                        // the new folder on the file system because it is empty. To ensure new
                        // folder creation, we must do so explicitly using file system operations.
                        val folder = fileFactory.invoke(c.getString(c.getColumnIndexOrThrow(DATA)))
                        if (!folder.exists() && !folder.mkdirs()) {
                            Log.e(TAG, "Unable to create new folder due to 'File#mkdirs()' failure")
                            return@supplyAsync false
                        }

                        return@supplyAsync true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to create new folder due to exception.", e)
                    return@supplyAsync false
                }
            },
            executorService,
        )
    }

    override fun canMoveToHomeScreen(uriList: List<Uri>?): Boolean =
        uriList?.run { isNotEmpty() && all { uri -> canMoveToHomeScreen(uri) } } == true

    /** NOTE: Currently only URIs which can be resolved by the media store are supported. */
    private fun canMoveToHomeScreen(uri: Uri): Boolean =
        isExternalStorageProviderUri(uri) || isExternalPrimaryMediaStoreUri(uri)

    override fun moveToHomeScreen(uriList: List<Uri>): List<CompletableFuture<Boolean>> =
        uriList.map { uri: Uri -> supplyAsync({ moveToHomeScreen(uri) }, executorService) }

    @WorkerThread
    private fun moveToHomeScreen(uri: Uri): Boolean {
        val mediaUri = getExternalPrimaryMediaStoreUri(context, uri)
        if (mediaUri == null) {
            Log.e(
                TAG,
                "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to unsupported URI",
            )
            return false
        }

        // NOTE: Overlapping move attempts for a given URI are disallowed.
        if (inProgressMoveToHomeScreenUriAliases.putIfAbsent(mediaUri, uri) != null) {
            Log.e(
                TAG,
                "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to overlapping attempt",
            )
            return false
        }

        var success = false
        try {
            // NOTE: The selection criteria below prevents moving a URI to a path it already
            // occupies; the media provider has additional protections to prevent recursive moves.
            success =
                (context.contentResolver.update(
                    /*uri=*/ mediaUri,
                    /*contentValues=*/ ContentValues().apply {
                        put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH)
                    },
                    /*where=*/ "$RELATIVE_PATH != ?",
                    /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                ) == 1)
            if (!success) {
                Log.e(
                    TAG,
                    "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' possibly due to unmet " +
                        "selection criteria or the media provider itself enforcing additional " +
                        "unmet conditions",
                )
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to exception", e)
        } finally {
            if (!success) {
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
                            val mimeType = it.getStringOrNull(mimeTypeColumnIndex)
                            result[uri] =
                                HomeScreenFile(
                                    uri = uri,
                                    displayName = it.getString(displayNameColumnIndex),
                                    mimeType = mimeType,
                                    isDirectory =
                                        fileFactory.invoke(it.getString(dataColumnIndex)).let { f ->
                                            // Defer to [mimeType] when the file does not yet exist.
                                            (f.exists() && f.isDirectory) ||
                                                (mimeType == MIME_TYPE_DIR)
                                        },
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
        if (!isExternalPrimaryMediaStoreUri(uri)) {
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
                        val dataColumnIndex = it.getColumnIndex(DATA)
                        val mimeTypeColumnIndex = it.getColumnIndex(MIME_TYPE)
                        val mimeType = it.getStringOrNull(mimeTypeColumnIndex)
                        HomeScreenFile(
                            uri = uri,
                            displayName = it.getString(displayNameColumnIndex),
                            mimeType = mimeType,
                            isDirectory =
                                fileFactory.invoke(it.getString(dataColumnIndex)).let { f ->
                                    // Defer to [mimeType] when the file does not yet exist.
                                    (f.exists() && f.isDirectory) || (mimeType == MIME_TYPE_DIR)
                                },
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

        private fun getExternalPrimaryMediaStoreUri(context: Context, uri: Uri): Uri? {
            if (isExternalPrimaryMediaStoreUri(uri)) {
                return uri
            }
            try {
                val mediaUri = MediaStore.getMediaUri(context, uri)
                when {
                    mediaUri == null -> {
                        Log.e(
                            TAG,
                            "Unable to get external primary media store URI due to null media URI",
                        )
                        return null
                    }
                    isExternalPrimaryMediaStoreUri(mediaUri) -> {
                        return mediaUri
                    }
                    else -> {
                        Log.e(
                            TAG,
                            "Unable to get external primary media store URI due to unsupported " +
                                "media URI volume",
                        )
                        return null
                    }
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Unable to get external primary media store URI due to exception", e)
                return null
            }
        }

        private fun isExternalPrimaryMediaStoreUri(uri: Uri) =
            uri.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == MediaStore.AUTHORITY &&
                kotlin
                    .runCatching { MediaStore.getVolumeName(uri) == VOLUME_EXTERNAL_PRIMARY }
                    .getOrDefault(false)

        private fun isExternalStorageDirectoryMounted() =
            Environment.getExternalStorageState(Environment.getExternalStorageDirectory()) ==
                Environment.MEDIA_MOUNTED

        private fun isExternalStorageProviderUri(uri: Uri?) =
            uri?.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY

        private fun Uri.hasIdSegment(): Boolean =
            kotlin.runCatching { ContentUris.parseId(this) != -1L }.getOrDefault(false)
    }
}
