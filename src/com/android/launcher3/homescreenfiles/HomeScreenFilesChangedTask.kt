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

import android.content.ContentResolver.NOTIFY_INSERT
import android.content.ContentResolver.NOTIFY_UPDATE
import android.net.Uri
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.Utilities.qsbOnFirstScreen
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntSet
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Handles changes in file items shown on the home screen. */
class HomeScreenFilesChangedTask
@AssistedInject
constructor(
    @Assisted private val fileChange: HomeScreenFilesProvider.FileChange,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    private val workspaceItemSpaceFinder: WorkspaceItemSpaceFinder,
) : LauncherModel.ModelUpdateTask {
    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val isInsert = fileChange.flags and NOTIFY_INSERT == NOTIFY_INSERT
        val isUpdate = fileChange.flags and NOTIFY_UPDATE == NOTIFY_UPDATE
        val file = kotlin.runCatching { fileChange.file.get() }.getOrNull()

        if (isInsert && file != null) {
            processInsert(fileChange.uri, file, taskController)
        } else if (isUpdate && file != null) {
            processUpdate(fileChange.uri, fileChange.uriAlias, file, taskController, dataModel)
        } else {
            processDelete(fileChange.uri, fileChange.uriAlias, taskController)
        }
    }

    private fun processInsert(uri: Uri, file: HomeScreenFile, taskController: ModelTaskController) {
        iconCache.addIconToDBAndMemCache(
            file,
            HomeScreenFilesCachingLogic,
            iconCache.getSerialNumberForUser(file.user),
        )
        val item =
            WorkspaceItemInfo().apply {
                itemType = HomeScreenFilesUtils.buildItemType(file)
                intent = HomeScreenFilesUtils.buildLaunchIntent(uri, file)
                iconCache.getTitleAndIcon(this, DESKTOP_ICON_FLAG)
            }
        val coords =
            workspaceItemSpaceFinder.findSpaceForItem(
                ArrayList<ItemInfo>().apply {
                    if (qsbOnFirstScreen()) {
                        // Reserve layout space for the search container. Note that this is not
                        // required when [Flags.FLAG_INJECTABLE_MODEL_ITEMS] is enabled as injected
                        // items will already be accounted for in the [BgDataModel].
                        add(
                            WorkspaceItemInfo().apply {
                                cellX = 0
                                cellY = 0
                                container = CONTAINER_DESKTOP
                                screenId = WorkspaceLayoutManager.FIRST_SCREEN_ID
                                spanX = idp.numSearchContainerColumns
                                spanY = 1
                            }
                        )
                    }
                },
                item.spanX,
                item.spanY,
                IntSet(),
            )
        taskController
            .getModelWriter()
            .addItemToDatabase(item, CONTAINER_DESKTOP, coords.screenId, coords.cellX, coords.cellY)
        taskController.scheduleCallbackTask { cb -> cb.bindItemsAdded(listOf(item)) }
    }

    private fun processUpdate(
        uri: Uri,
        uriAlias: Uri?,
        file: HomeScreenFile,
        taskController: ModelTaskController,
        dataModel: BgDataModel,
    ) {
        val updatedItems =
            dataModel.updateAndCollectWorkspaceItemInfos(
                file.user,
                {
                    val data = it.intent?.data
                    if (data == uri || (uriAlias != null && data == uriAlias)) {
                        it.intent = HomeScreenFilesUtils.buildLaunchIntent(uri, file)
                        it.itemType = HomeScreenFilesUtils.buildItemType(file)
                        iconCache.addIconToDBAndMemCache(
                            file,
                            HomeScreenFilesCachingLogic,
                            iconCache.getSerialNumberForUser(file.user),
                        )
                        iconCache.getTitleAndIcon(it, it.matchingLookupFlag)
                        true
                    } else {
                        false
                    }
                },
            )
        if (updatedItems.isNotEmpty()) {
            taskController.getModelWriter().run { updatedItems.forEach(::updateItemInDatabase) }
            taskController.bindUpdatedWorkspaceItems(updatedItems)
        } else {
            processInsert(uri, file, taskController)
        }
    }

    private fun processDelete(uri: Uri, uriAlias: Uri?, taskController: ModelTaskController) {
        taskController.deleteAndBindComponentsRemoved(
            {
                val data = it?.intent?.data
                data == uri || (uriAlias != null && data == uriAlias)
            },
            "The file system item no longer exists",
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(fileChange: HomeScreenFilesProvider.FileChange): HomeScreenFilesChangedTask
    }
}
