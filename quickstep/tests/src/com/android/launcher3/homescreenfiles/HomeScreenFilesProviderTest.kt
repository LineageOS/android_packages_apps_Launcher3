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
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY
import android.provider.DocumentsContract.EXTRA_URI
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.MockitoSession
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
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesProviderTest {

    @get:Rule val context = spy(SandboxApplication())

    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var contentProviderClient: ContentProviderClient
    @Mock private lateinit var externalStorageDir: File

    private lateinit var mockitoSession: MockitoSession
    private lateinit var provider: HomeScreenFilesProvider

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .initMocks(this@HomeScreenFilesProviderTest)
                .strictness(Strictness.LENIENT)
                .mockStatic(Environment::class.java)
                .startMocking()

        doReturn(contentResolver).whenever(context).contentResolver

        whenever(Environment.getExternalStorageDirectory()).thenReturn(externalStorageDir)
        whenever(Environment.getExternalStorageState(externalStorageDir))
            .thenReturn(Environment.MEDIA_MOUNTED)

        provider = createProvider()
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
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
    fun testQueryWhenExternalStorageDirectoryMountsAfterCall() {
        // Unmount external storage directory prior to [provider] init.
        whenever(Environment.getExternalStorageState(externalStorageDir))
            .thenReturn(Environment.MEDIA_UNMOUNTED)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(
            expectResults = true,
            afterQueryCallback = {
                // Mount external storage directory.
                whenever(Environment.getExternalStorageState(externalStorageDir))
                    .thenReturn(Environment.MEDIA_MOUNTED)

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
        whenever(Environment.getExternalStorageState(externalStorageDir))
            .thenReturn(Environment.MEDIA_UNMOUNTED)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#query()].
        testQuery(
            expectResults = true,
            beforeQueryCallback = {
                // Mount external storage directory.
                whenever(Environment.getExternalStorageState(externalStorageDir))
                    .thenReturn(Environment.MEDIA_MOUNTED)

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
        // Mount external storage directory prior to [provider] init.
        whenever(Environment.getExternalStorageState(externalStorageDir))
            .thenReturn(Environment.MEDIA_MOUNTED)

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
        whenever(Environment.getExternalStorageState(externalStorageDir))
            .thenReturn(Environment.MEDIA_UNMOUNTED)

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
        val expectedProjection =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA,
            )

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
                eq("${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"),
                argThat { x -> x.contentDeepEquals(arrayOf("Home screen/")) },
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
                val answer =
                    MatrixCursor(
                        arrayOf(
                            MediaStore.Files.FileColumns.DISPLAY_NAME,
                            MediaStore.Files.FileColumns.MIME_TYPE,
                            MediaStore.Files.FileColumns.DATA,
                        )
                    )
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
            context.appComponent.daggerSingletonTracker,
        )

    private fun createTestUri(id: String) = "content://test/path/$id".toUri()

    companion object {
        private const val GET_MEDIA_URI_CALL = "get_media_uri"
    }
}
