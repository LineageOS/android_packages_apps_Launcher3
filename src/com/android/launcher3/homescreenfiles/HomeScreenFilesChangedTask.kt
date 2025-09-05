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
import android.os.UserHandle
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
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
            processUpdate(
                fileChange.uri,
                fileChange.uriAlias,
                file,
                fileChange.user,
                taskController,
                dataModel,
            )
        } else {
            processDelete(fileChange.uri, fileChange.uriAlias, taskController)
        }
    }

    private fun processInsert(uri: Uri, file: HomeScreenFile, taskController: ModelTaskController) {
        val item =
            WorkspaceItemInfo().apply {
                title = file.displayName
                itemType = HomeScreenFilesUtils.buildItemType(file)
                intent = HomeScreenFilesUtils.buildLaunchIntent(uri, file)
                bitmap = iconCache.getDefaultIcon(user)
            }
        val coords =
            workspaceItemSpaceFinder.findSpaceForItem(ArrayList(), item.spanX, item.spanY, IntSet())
        taskController
            .getModelWriter()
            .addItemToDatabase(item, CONTAINER_DESKTOP, coords.screenId, coords.cellX, coords.cellY)
        taskController.scheduleCallbackTask { cb -> cb.bindItemsAdded(listOf(item)) }
    }

    private fun processUpdate(
        uri: Uri,
        uriAlias: Uri?,
        file: HomeScreenFile,
        user: UserHandle,
        taskController: ModelTaskController,
        dataModel: BgDataModel,
    ) {
        val updatedItems =
            dataModel.updateAndCollectWorkspaceItemInfos(
                user,
                {
                    val data = it.intent?.data
                    if (data == uri || (uriAlias != null && data == uriAlias)) {
                        it.intent = HomeScreenFilesUtils.buildLaunchIntent(uri, file)
                        it.itemType = HomeScreenFilesUtils.buildItemType(file)
                        it.title = file.displayName
                        true
                    } else {
                        false
                    }
                },
            )
        if (updatedItems.isNotEmpty()) {
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
