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

package com.android.launcher3

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.view.View
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags.FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS
import com.android.launcher3.Flags.enableFileSystemFoldersAsDropTargets
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SYSTEM_DRAG
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.SystemDragItemInfo
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.testutil.rule.ApplicationOverrideRule
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SandboxApplication
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@LargeTest
@RunWith(AndroidJUnit4::class)
class WorkspaceTest {

    @get:Rule val flags = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = ApplicationOverrideRule(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private val nextUniqueId = AtomicInteger(1)

    @Test
    @DisableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testAddToExistingFileSystemFolderWithFeatureDisabled() {
        testAddToExistingFileSystemFolder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testAddToExistingFileSystemFolderWithFeatureEnabled() {
        testAddToExistingFileSystemFolder()
    }

    private fun testAddToExistingFileSystemFolder() {
        launcherActivity.executeOnLauncher { launcher ->
            val displayName = "Folder"
            val dropOverView = createDropOverView(createFolder(displayName))
            val expected = enableFileSystemFoldersAsDropTargets()
            val provider = HomeScreenFilesProvider.INSTANCE[launcher].apply { convertToSpy() }
            val times = if (expected) times(1) else times(0)

            // Case: Dropping internal file system file on file system folder.
            var uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_FILE_SYSTEM_FILE, uri),
                    false,
                ),
            )
            verify(provider, times).moveToHomeScreen(listOf(uri), displayName)

            // Case: Dropping internal file system folder on file system folder.
            uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_FILE_SYSTEM_FOLDER, uri),
                    false,
                ),
            )
            verify(provider, times).moveToHomeScreen(listOf(uri), displayName)

            // Case: Dropping external file system file/folder on file system folder.
            uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_SYSTEM_DRAG, uri),
                    true,
                ),
            )
            verify(provider, times).moveToHomeScreen(listOf(uri), displayName)

            // Case: Dropping application on file system folder.
            uri = createUniqueMediaStoreUri()
            assertFalse(
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_APPLICATION, uri),
                    false,
                )
            )
            verifyNoMoreInteractions(provider)

            reset(provider)
        }
    }

    @Test
    @DisableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testWillAddToExistingFileSystemFolderWithFeatureDisabled() {
        testWillAddToExistingFileSystemFolder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testWillAddToExistingFileSystemFolderWithFeatureEnabled() {
        testWillAddToExistingFileSystemFolder()
    }

    private fun testWillAddToExistingFileSystemFolder() {
        launcherActivity.executeOnLauncher { launcher ->
            val dropOverView = createDropOverView(createFolder("Folder"))
            val expected = enableFileSystemFoldersAsDropTargets()

            // Case: Dropping internal file system file on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_FILE_SYSTEM_FILE),
                    dropOverView,
                ),
            )

            // Case: Dropping internal file system folder on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_FILE_SYSTEM_FOLDER),
                    dropOverView,
                ),
            )

            // Case: Dropping external file system file/folder on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_SYSTEM_DRAG),
                    dropOverView,
                ),
            )

            // Case: Dropping application on file system folder.
            assertFalse(
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_APPLICATION),
                    dropOverView,
                )
            )
        }
    }

    private fun createDragObject(itemType: Int, uri: Uri) =
        mock<DragObject>().apply {
            dragInfo = createWorkspaceItemInfo(itemType, uri)
            originalDragInfo = dragInfo
        }

    private fun createDropOverView(folder: HomeScreenFile) =
        mock<View>().apply {
            doReturn(
                    CellLayoutLayoutParams(
                        /*cellX=*/ 1,
                        /*cellY=*/ 1,
                        /*cellHSpan=*/ 1,
                        /*cellVSpan=*/ 1,
                    )
                )
                .whenever(this@apply)
                .layoutParams

            doReturn(
                    WorkspaceItemInfo().apply {
                        intent = HomeScreenFilesUtils.buildLaunchIntent(folder.uri, folder)
                        itemType = HomeScreenFilesUtils.buildItemType(folder)
                        title = folder.displayName
                    }
                )
                .whenever(this@apply)
                .tag
        }

    private fun createFolder(displayName: String) =
        HomeScreenFile(
            displayName = displayName,
            isDirectory = true,
            mimeType = MIME_TYPE_DIR,
            uri = createUniqueMediaStoreUri(),
            user = Process.myUserHandle(),
        )

    private fun createWorkspaceItemInfo(itemType: Int, uri: Uri? = null): WorkspaceItemInfo {
        return when (itemType) {
            ITEM_TYPE_SYSTEM_DRAG ->
                SystemDragItemInfo().apply { if (uri != null) uriList = listOf(uri) }
            else ->
                WorkspaceItemInfo().apply {
                    if (uri != null) intent = Intent().apply { data = uri }
                    this.itemType = itemType
                }
        }
    }

    private fun createUniqueMediaStoreUri(): Uri =
        "content://media/external_primary/file/${nextUniqueId.getAndIncrement()}".toUri()
}
