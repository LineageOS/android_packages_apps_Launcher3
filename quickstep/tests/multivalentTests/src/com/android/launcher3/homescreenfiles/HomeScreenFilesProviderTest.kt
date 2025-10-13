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

import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY
import android.provider.DocumentsContract.EXTRA_URI
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATA
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.IS_TRASHED
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.testutil.rule.LazyInitRule.Companion.lazyRule
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesProviderTest {

    @get:Rule val contextSpy = lazyRule { spy(SandboxApplication()) }
    @get:Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val context: SandboxApplication by contextSpy
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var contentProviderClient: ContentProviderClient
    @Mock private lateinit var fileFactory: (path: String) -> File
    @Mock private lateinit var environmentWrapper: EnvironmentWrapper

    private lateinit var provider: HomeScreenFilesProvider

    @Before
    fun setUp() {
        doReturn(contentResolver).whenever(context).contentResolver
        whenever(fileFactory.invoke(any())).thenAnswer { i -> File(i.getArgument<String>(0)) }
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

        provider = createProvider()
    }

    @Test
    fun testCanCreateNewFolderWhenExternalStorageProviderIsMounted() {
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        assertTrue(provider.canCreateNewFolder())
    }

    @Test
    fun testCanCreateNewFolderWhenExternalStorageProviderIsUnmounted() {
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        assertFalse(provider.canCreateNewFolder())
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMounted() {
        testCreateNewFolder(externalStorageDirectoryIsMounted = true, expectSuccess = true)
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButFolderCreationFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            folderCreationSucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButMediaStoreInsertionFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            mediaStoreInsertionSucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButMediaStoreQueryFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            mediaStoreQuerySucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsUnmounted() {
        testCreateNewFolder(externalStorageDirectoryIsMounted = false, expectSuccess = false)
    }

    private fun testCreateNewFolder(
        externalStorageDirectoryIsMounted: Boolean = true,
        mediaStoreInsertionSucceeds: Boolean = true,
        mediaStoreQuerySucceeds: Boolean = true,
        folderCreationSucceeds: Boolean = true,
        expectSuccess: Boolean = true,
    ) {
        val defaultNewFolderName = context.getString(R.string.default_new_folder_name)
        val data = "$HOME_SCREEN_FOLDER_RELATIVE_PATH/$defaultNewFolderName"
        val folder = mock<File>()
        val uri = mock<Uri>()

        // Mock external storage directory state.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted())
            .thenReturn(externalStorageDirectoryIsMounted)

        // Mock media store insertion success/failure.
        whenever(
                contentResolver.insert(
                    eq(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)),
                    argThat {
                        get(DISPLAY_NAME) == defaultNewFolderName &&
                            get(MIME_TYPE) == DocumentsContract.Document.MIME_TYPE_DIR &&
                            get(RELATIVE_PATH) == HOME_SCREEN_FOLDER_RELATIVE_PATH
                    },
                )
            )
            .thenReturn(if (mediaStoreInsertionSucceeds) uri else null)

        // Mock media store query success/failure.
        whenever(contentResolver.query(uri, arrayOf(DATA), null, null)).thenAnswer {
            if (mediaStoreQuerySucceeds) MatrixCursor(arrayOf(DATA)).apply { addRow(arrayOf(data)) }
            else null
        }

        // Mock folder creation success/failure.
        whenever(folder.exists()).thenReturn(false)
        whenever(folder.mkdirs()).thenReturn(folderCreationSucceeds)
        whenever(fileFactory.invoke(data)).thenReturn(folder)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#createNewFolder()].
        assertEquals(expectSuccess, provider.createNewFolder().get())
    }

    @Test
    fun testCanMoveToHomeScreen() {
        val espUri = createExternalStorageProviderUri("externalRelativePath", "externalDisplayName")
        val mediaStoreUri = createExternalPrimaryMediaStoreUri(1L)
        val testUri = createTestUri("testId")

        assertFalse(provider.canMoveToHomeScreen(null))
        assertFalse(provider.canMoveToHomeScreen(emptyList()))
        assertTrue(provider.canMoveToHomeScreen(listOf(espUri)))
        assertTrue(provider.canMoveToHomeScreen(listOf(espUri, mediaStoreUri)))
        assertFalse(provider.canMoveToHomeScreen(listOf(espUri, mediaStoreUri, testUri)))
    }

    @Test
    fun testMoveToHomeScreen() {
        val espUri = createExternalStorageProviderUri("externalRelativePath", "externalDisplayName")
        val mediaStoreUri = createExternalPrimaryMediaStoreUri(1L)
        val mediaStoreUriResolvedFromEsp = createExternalPrimaryMediaStoreUri(2L)
        val testUri = createTestUri("testId")

        // Mock attempts to resolve media store URIs.
        whenever(contentProviderClient.call(eq(GET_MEDIA_URI_CALL), anyOrNull(), any()))
            .thenAnswer { invocation ->
                Bundle().apply {
                    putParcelable(
                        EXTRA_URI,
                        when (
                            invocation
                                .getArgument<Bundle>(2)
                                .getParcelable(EXTRA_URI, Uri::class.java)
                        ) {
                            espUri -> mediaStoreUriResolvedFromEsp
                            mediaStoreUri -> mediaStoreUri
                            mediaStoreUriResolvedFromEsp -> mediaStoreUriResolvedFromEsp
                            else -> null
                        },
                    )
                }
            }

        // Associate media store content provider client with content resolver.
        whenever(contentResolver.acquireContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        // Mock attempts to update media store.
        whenever(
                contentResolver.update(
                    /*uri=*/ anyOrNull(),
                    /*contentValues=*/ eq(
                        ContentValues().apply {
                            put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH)
                        }
                    ),
                    /*where=*/ eq("$RELATIVE_PATH != ?"),
                    /*selectionArgs=*/ eq(arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH)),
                )
            )
            .thenAnswer { invocation ->
                when (invocation.getArgument<Uri>(0)) {
                    mediaStoreUri,
                    mediaStoreUriResolvedFromEsp -> 1
                    else -> throw RuntimeException()
                }
            }

        // Attempt to move URIs to home screen.
        // NOTE: Overlapping move attempts for a given URI are disallowed.
        assertEquals(
            listOf(
                /*expectedEspUriResult=*/ true,
                /*expectedMediaStoreUriResult=*/ true,
                /*expectedMediaStoreUriResult=*/ false,
                /*expectedTestUriResult=*/ false,
            ),
            provider
                .moveToHomeScreen(listOf(espUri, mediaStoreUri, mediaStoreUri, testUri))
                .map(CompletableFuture<Boolean>::get),
        )
    }

    @Test
    fun testMoveToTrash() {
        val uri = Uri.parse("content://media/external_primary/file/1")
        provider.moveToTrash(uri)

        verify(contentResolver, times(1))
            .update(
                eq(uri),
                argThat { x -> x.containsKey(IS_TRASHED) && x.get(IS_TRASHED) == "1" },
                eq("$RELATIVE_PATH = ? AND $IS_TRASHED = ?"),
                eq(arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH, "0")),
            )
    }

    @Test
    fun testQueryWhenExternalStorageDirectoryMountsAfterCall() {
        // Unmount external storage directory prior to [provider] init.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(
            expectResults = true,
            afterQueryCallback = {
                // Mount external storage directory.
                whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

                // Notify external storage directory mounted.
                val observerCaptor = argumentCaptor<ContentObserver>()
                verify(contentResolver)
                    .registerContentObserver(
                        eq(Uri.parse("content://media/external_primary/file")),
                        eq(true),
                        observerCaptor.capture(),
                    )
                observerCaptor.firstValue.dispatchChange(
                    /*selfChange=*/ false,
                    Uri.parse("content://media/external_primary"),
                    ContentResolver.NOTIFY_SYNC_TO_NETWORK,
                )
            },
        )
    }

    @Test
    fun testQueryWhenExternalStorageDirectoryMountsBeforeCall() {
        // Unmount external storage directory prior to [provider] init.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(
            expectResults = true,
            beforeQueryCallback = {
                // Mount external storage directory.
                whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

                // Notify external storage directory mounted.
                val observerCaptor = argumentCaptor<ContentObserver>()
                verify(contentResolver)
                    .registerContentObserver(
                        eq(Uri.parse("content://media/external_primary/file")),
                        eq(true),
                        observerCaptor.capture(),
                    )
                observerCaptor.firstValue.dispatchChange(
                    /*selfChange=*/ false,
                    Uri.parse("content://media/external_primary"),
                    ContentResolver.NOTIFY_SYNC_TO_NETWORK,
                )
            },
        )
    }

    @Test
    fun testQueryWhenExternalStorageDirectoryMountsBeforeInit() {
        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(expectResults = true)
    }

    @Test
    fun testQueryWhenExternalStorageDirectoryMountTimesOutDuringCall() {
        // Unmount external storage directory prior to [provider] init.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(expectResults = false)
    }

    private fun testQuery(
        expectResults: Boolean,
        beforeQueryCallback: (() -> Unit)? = null,
        afterQueryCallback: (() -> Unit)? = null,
    ) {
        val expectedUri = Uri.parse("content://media/external_primary/file")
        val expectedProjection = arrayOf(_ID, DISPLAY_NAME, MIME_TYPE, DATA)

        whenever(
                contentResolver.query(
                    eq(expectedUri),
                    eq(expectedProjection),
                    any(),
                    any(),
                    isNull(),
                    isNull(),
                )
            )
            .thenAnswer {
                val answer = MatrixCursor(expectedProjection)
                answer.addRow(
                    arrayOf("1", "test.png", "image/png", "/storage/emulated/0/Desktop/test.png")
                )
                answer.addRow(
                    arrayOf("2", "subfolder", null, "/storage/emulated/0/Desktop/subfolder")
                )
                return@thenAnswer answer
            }

        beforeQueryCallback?.invoke()
        val query = provider.query()
        afterQueryCallback?.invoke()

        val result = query.value
        if (!expectResults) {
            assertTrue(result.isEmpty())
            return
        }

        assertThat(result.size).isEqualTo(2)

        val uri1 = Uri.parse("content://media/external_primary/file/1")
        assertThat(result.containsKey(uri1)).isTrue()
        assertThat(result[uri1]!!.uri).isEqualTo(uri1)
        assertThat(result[uri1]!!.displayName).isEqualTo("test.png")
        assertThat(result[uri1]!!.mimeType).isEqualTo("image/png")

        val uri2 = Uri.parse("content://media/external_primary/file/2")
        assertThat(result.containsKey(uri2)).isTrue()
        assertThat(result[uri2]!!.uri).isEqualTo(uri2)
        assertThat(result[uri2]!!.displayName).isEqualTo("subfolder")
        assertThat(result[uri2]!!.mimeType).isNull()

        verify(contentResolver, times(1))
            .query(
                eq(expectedUri),
                eq(expectedProjection),
                eq("$RELATIVE_PATH = ? AND $IS_TRASHED = ?"),
                eq(arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH, "0")),
                isNull(),
                isNull(),
            )
    }

    @Test
    fun testRegistersChangeCallback() {
        whenever(
                contentResolver.query(
                    eq(Uri.parse("content://media/external_primary/file/1")),
                    any(),
                    any(),
                    any(),
                    isNull(),
                    isNull(),
                )
            )
            .thenAnswer {
                val answer = MatrixCursor(arrayOf(DISPLAY_NAME, MIME_TYPE, DATA))
                answer.addRow(
                    arrayOf("NEW_test.png", "image/png", "/storage/emulated/0/Desktop/test.png")
                )
                return@thenAnswer answer
            }

        val callback = mock<(HomeScreenFilesProvider.FileChange) -> Unit>()
        val immediateExecutor = Executor { r -> r.run() }
        val unregisterChangeCallback =
            provider.fileChanges.forEach(immediateExecutor) { callback(it) }
        val underlyingContentObserverCaptor = argumentCaptor<ContentObserver>()
        verify(contentResolver, times(1))
            .registerContentObserver(
                eq(Uri.parse("content://media/external_primary/file")),
                eq(true),
                underlyingContentObserverCaptor.capture(),
            )

        underlyingContentObserverCaptor.firstValue.dispatchChange(
            false,
            Uri.parse("content://media/external_primary/file/1"),
            ContentResolver.NOTIFY_INSERT,
        )

        val fileChangeCaptor = argumentCaptor<HomeScreenFilesProvider.FileChange>()
        verify(callback, times(1))(fileChangeCaptor.capture())
        val fileChange = fileChangeCaptor.firstValue
        assertThat(fileChange.uri).isEqualTo(Uri.parse("content://media/external_primary/file/1"))
        assertThat(fileChange.flags).isEqualTo(ContentResolver.NOTIFY_INSERT)
        assertThat(fileChange.file.get()!!.uri)
            .isEqualTo(Uri.parse("content://media/external_primary/file/1"))
        assertThat(fileChange.file.get()!!.displayName).isEqualTo("NEW_test.png")
        assertThat(fileChange.file.get()!!.mimeType).isEqualTo("image/png")
        assertThat(fileChange.file.get()!!.isDirectory).isFalse()

        context.appComponent.daggerSingletonTracker.close()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        verify(contentResolver, times(1))
            .unregisterContentObserver(eq(underlyingContentObserverCaptor.firstValue))

        unregisterChangeCallback.close()
    }

    private fun createExternalPrimaryMediaStoreUri(id: Long) =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)

    private fun createExternalStorageProviderUri(relativePath: String, displayName: String) =
        DocumentsContract.buildDocumentUri(
            /*authority=*/ EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            /*documentId=*/ "primary:$relativePath%3F$displayName",
        )

    private fun createProvider() =
        HomeScreenFilesMediaStoreProvider(
            context,
            MoreExecutors.newDirectExecutorService(),
            fileFactory,
            environmentWrapper,
            context.appComponent.daggerSingletonTracker,
        )

    private fun createTestUri(id: String) = "content://test/path/$id".toUri()

    companion object {
        private const val GET_MEDIA_URI_CALL = "get_media_uri"
    }
}
