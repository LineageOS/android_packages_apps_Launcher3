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

package com.android.launcher3.popup

import android.content.Context
import android.net.Uri
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.SparseArray
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DropTargetHandler
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_QSB
import com.android.launcher3.R
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent.FullRefresh
import com.android.launcher3.model.data.WorkspaceData.ImmutableWorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.android.providers.media.flags.Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API
import java.io.File
import java.util.concurrent.CompletableFuture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for the [PopupDataRepositoryImpl] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupDataRepositoryImplUnitTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule = SetFlagsRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val homeScreenRepository = HomeScreenRepository()
    @Mock private lateinit var homeScreenFilesProvider: HomeScreenFilesProvider
    @Mock private lateinit var lifeCycle: DaggerSingletonTracker

    private lateinit var popupDataSource: PopupDataSource
    private lateinit var popupDataRepository: PopupDataRepository

    @Before
    fun setup() {
        // Late initialization of `PopupDataSource` is required because some of the created
        // `PopupData` use feature flags.
        popupDataSource = PopupDataSource(homeScreenFilesProvider)
        popupDataRepository =
            PopupDataRepositoryImpl(popupDataSource, context, homeScreenRepository, lifeCycle)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithInvalidItemInfoShouldReturnEmptyList() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_QSB
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithEmptyItemInfoShouldReturnEmptyList() {
        val itemInfo = ItemInfo()
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithFolderShouldReturnMapContainingFolderItem() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_FOLDER
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.size == 1)
        assert(popupDataMap[itemInfo.id] != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithFolderAndWidgetShouldReturnMapContainingFolderAndWidgetItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        widgetItemInfo.id = 2
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.size == 2)
        assert(popupDataMap[folderItemInfo.id] != null)
        assert(popupDataMap[widgetItemInfo.id] != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldBeNullIfWeDontHaveThatItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.id = 2
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(ItemInfo())

        assert(popupDataStream == null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldNotBeNullIfWeHaveThatItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.id = 2
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        assert(popupDataStream != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldStillWorkIfMapDoesNotHaveItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER

        // There should be no popup data since we didn't update the home screen repository.
        assert(popupDataRepository.getAllPopupData().isEmpty())

        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        // Now that we called getPopupDataByItemInfo we should have the folderItemInfo.
        assert(popupDataRepository.getAllPopupData().size == 1)

        // Verify the stream is correct.
        assert(popupDataStream != null)
        assert(popupDataStream?.size == 1)
        assert(popupDataStream?.contains(popupDataSource.removePopupData) == true)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun popupDataShouldHaveAllTheDataFilledIn() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        seedData(folderItemInfo)
        val popupData = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        assert(popupData?.size == 1)
        assert(popupData?.get(0)?.category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
        assert(popupData?.get(0)?.iconResId == R.drawable.ic_remove_no_shadow)
        assert(popupData?.get(0)?.labelResId == R.string.remove_system_shortcut_label)
        assert(popupData?.get(0)?.popupAction != null)
    }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenRenamingAndTrashingDisabled() {
        testPopupDataForFileSystemItems(supportsRenaming = false, supportsTrashing = false)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenRenamingAndTrashingEnabled() {
        testPopupDataForFileSystemItems(supportsRenaming = true, supportsTrashing = true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING)
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenRenamingEnabledAndTrashingDisabled() {
        testPopupDataForFileSystemItems(supportsRenaming = true, supportsTrashing = false)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING)
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenRenamingDisabledAndTrashingEnabled() {
        testPopupDataForFileSystemItems(supportsRenaming = false, supportsTrashing = true)
    }

    private fun testPopupDataForFileSystemItems(
        supportsRenaming: Boolean,
        supportsTrashing: Boolean,
    ) {
        testPopupDataForFileSystemItem(
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            ),
            supportsRenaming,
            supportsTrashing,
        )
        testPopupDataForFileSystemItem(
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/2"),
                displayName = "folder",
                mimeType = null,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            supportsRenaming,
            supportsTrashing,
        )
    }

    private fun testPopupDataForFileSystemItem(
        file: HomeScreenFile,
        supportsRenaming: Boolean,
        supportsTrashing: Boolean,
    ) {
        val activityContext = mock<ActivityContext>()
        val view = mock<View>()
        val item =
            WorkspaceItemInfo().apply {
                id = 1
                itemType = HomeScreenFilesUtils.buildItemType(file)
                intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
                title = file.displayName
            }
        val popupData = popupDataRepository.getPopupDataByItemInfo(item)
        var popupDataIndex = 0

        assert(popupData!!.size == if (supportsRenaming) 3 else 2)
        with(popupData[popupDataIndex++]) {
            assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
            assert(iconResId == R.drawable.ic_home_screen_files_context_menu_open_in_app)
            assert(labelResId == R.string.home_screen_files_context_menu_open_in_app_label)
            assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_OPEN_VIA_CONTEXT_MENU)

            popupAction.invoke(activityContext, item, view)
            verify(activityContext, times(1)).startActivitySafely(view, item.intent, item)
        }
        if (supportsRenaming) {
            with(popupData[popupDataIndex++]) {
                assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
                assert(iconResId == R.drawable.ic_home_screen_files_context_menu_rename)
                assert(labelResId == R.string.home_screen_files_context_menu_rename_label)
                assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_RENAME_VIA_CONTEXT_MENU)

                // TODO(b/450710219): Replace assertion once dialog is implemented.
                whenever(homeScreenFilesProvider.rename(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(true))
                popupAction.invoke(activityContext, item, view)
                verify(homeScreenFilesProvider)
                    .rename(
                        eq(file.uri),
                        argThat {
                            val extension = File(file.displayName).extension
                            val suffix = if (extension.isNotEmpty()) ".$extension" else ""
                            matches("\\d+$suffix".toRegex())
                        },
                    )
            }
        }
        with(popupData[popupDataIndex++]) {
            assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
            assert(iconResId == R.drawable.ic_home_screen_files_context_menu_move_to_trash)
            if (supportsTrashing) {
                assert(labelResId == R.string.home_screen_files_context_menu_move_to_trash_label)
            } else {
                assert(
                    labelResId == R.string.home_screen_files_context_menu_delete_permanently_label
                )
            }
            assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_DELETE_VIA_CONTEXT_MENU)

            val dropTargetHandler = mock<DropTargetHandler>()
            whenever(activityContext.dragLayer).thenReturn(mock<BaseDragLayer<*>>())
            whenever(activityContext.dropTargetHandler).thenReturn(dropTargetHandler)
            popupAction.invoke(activityContext, item, view)
            verify(dropTargetHandler, times(1)).prepareToUndoDelete(item)
            verify(dropTargetHandler, times(1)).onDeleteComplete(item, view)
        }
    }

    private fun seedData(vararg items: ItemInfo) {
        val data: SparseArray<ItemInfo> = SparseArray()
        items.forEachIndexed { i: Int, item: ItemInfo -> data[i] = item }
        homeScreenRepository.dispatchWorkspaceDataChange(
            ImmutableWorkspaceData(version = 0, modificationId = 0, items = data),
            FullRefresh(reason = "seedData"),
        )
        TestUtil.runOnExecutorSync(Executors.DATA_HELPER_EXECUTOR) {}
    }
}
