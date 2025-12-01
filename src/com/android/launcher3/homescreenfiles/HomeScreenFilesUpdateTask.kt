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

import android.content.Context
import android.net.Uri
import android.os.UserHandle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.Utilities.qsbOnFirstScreen
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_COUNT
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_FILE_SYSTEM_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntSet
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.CompletableFuture

/**
 * Represents an update to file items shown on the home screen.
 *
 * If this update is a delayed initialization of all file items, any files absent from the update
 * will be removed from the workspace. Additionally, any file items which have already been
 * initialized as indicated by the absence of the [FLAG_DISABLED_FILE_SYSTEM_NOT_READY] runtime
 * status flag are ignored.
 *
 * @param filesByUri The collection of file items that were updated.
 * @param isDelayedInit Whether this update is a delayed initialization of all file items.
 * @param user The user for which file items were updated.
 */
data class HomeScreenFilesUpdate(
    val filesByUri: CompletableFuture<Map<Uri, HomeScreenFile?>>,
    val user: UserHandle,
    val isDelayedInit: Boolean = false,
)

/**
 * A task which processes updates to file items shown on the home screen.
 *
 * @param iconCache The cache used to resolve file item icons.
 * @param idp The device profile used to conditionally reserve space for the search container.
 * @param update The update to file items to be processed.
 * @param workspaceItemSpaceFinder The finder used to place any newly created file items.
 */
class HomeScreenFilesUpdateTask
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    @Assisted private val update: HomeScreenFilesUpdate,
    private val workspaceItemSpaceFinder: WorkspaceItemSpaceFinder,
    private val statsLogManagerFactory: StatsLogManager.StatsLogManagerFactory,
) : LauncherModel.ModelUpdateTask {
    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val filesByUri = update.filesByUri.get()
        val addedItems = filesByUri.values.filterNotNull().distinct().toMutableList()
        val deletedItems = mutableSetOf<Int>()

        dataModel
            .updateAndCollectWorkspaceItemInfos(
                update.user,
                { itemInfo ->
                    // NOTE: Updates only affect file system items.
                    if (!itemInfo.isFileSystemItem()) {
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    val uri = itemInfo.intent?.data
                    val file = if (uri != null) filesByUri[uri] else null

                    if (file == null) {
                        // NOTE: File items which are absent from an update should only result in
                        // removal from the workspace if: (a) this is a delayed initialization of
                        // all file system items, or (b) we are processing a file item deletion.
                        if (update.isDelayedInit || filesByUri.containsKey(uri)) {
                            deletedItems.add(itemInfo.id)
                        }
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    addedItems.remove(file)

                    // NOTE: If a file item is not disabled due to file system readiness, it has
                    // already been initialized and can be ignored during delayed initialization.
                    // This avoids unnecessary rework and interruption of any scheduled animations.
                    if (update.isDelayedInit && !itemInfo.hasDisabledFileSystemNotReadyFlag()) {
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    // NOTE: The presence of a file item in an update implies that the file system
                    // is ready so the associated shortcut no longer needs to be disabled.
                    itemInfo.applyCommonProperties(file)
                    itemInfo.removeDisabledFileSystemNotReadyFlag()

                    return@updateAndCollectWorkspaceItemInfos true
                },
            )
            .also { updatedItems ->
                processUpdatedItems(updatedItems, taskController)
                processDeletedItems(deletedItems, taskController)
                processAddedItems(addedItems, taskController)
            }

        if (update.isDelayedInit) {
            filesByUri.values
                .count { it != null }
                .takeIf { it > 0 }
                ?.let { count ->
                    statsLogManagerFactory
                        .create(context)
                        .logger()
                        .withCardinality(count)
                        .log(LAUNCHER_HOME_SCREEN_FILES_COUNT)
                }
        }
    }

    private fun processAddedItems(
        addedItems: List<HomeScreenFile>,
        taskController: ModelTaskController,
    ) {
        if (addedItems.isEmpty()) {
            return
        }

        val knownItems =
            ArrayList<ItemInfo>().apply {
                // NOTE: If necessary, reserve layout space for the search container. This is not
                // required when [Flags.FLAG_INJECTABLE_MODEL_ITEMS] is enabled as injected items
                // will already be accounted for in the [BgDataModel].
                if (qsbOnFirstScreen()) {
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
            }

        addedItems
            .map { file ->
                WorkspaceItemInfo()
                    .apply { applyCommonProperties(file) }
                    .also { itemInfo ->
                        workspaceItemSpaceFinder
                            .findSpaceForItem(knownItems, itemInfo.spanX, itemInfo.spanY, IntSet())
                            .also { coords ->
                                itemInfo.screenId = coords.screenId
                                itemInfo.cellX = coords.cellX
                                itemInfo.cellY = coords.cellY
                                knownItems.add(itemInfo)
                            }
                    }
            }
            .toList()
            .run {
                taskController.getModelWriter().addItemsToDatabase(this)
                taskController.scheduleCallbackTask { it.bindItemsAdded(this) }
            }
    }

    private fun processDeletedItems(deletedItems: Set<Int>, taskController: ModelTaskController) {
        if (deletedItems.isNotEmpty()) {
            taskController.deleteAndBindComponentsRemoved(
                { deletedItems.contains(it?.id) },
                "The file system item no longer exists",
            )
        }
    }

    private fun processUpdatedItems(
        updatedItems: List<ItemInfo>,
        taskController: ModelTaskController,
    ) {
        if (updatedItems.isNotEmpty()) {
            taskController.getModelWriter().run { updatedItems.forEach(::updateItemInDatabase) }
            taskController.bindUpdatedWorkspaceItems(updatedItems)
        }
    }

    private fun WorkspaceItemInfo.applyCommonProperties(file: HomeScreenFile) {
        container = CONTAINER_DESKTOP
        intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
        itemType = HomeScreenFilesUtils.buildItemType(file)
        title = file.displayName
        user = file.user
        iconCache.addIconToDBAndMemCache(
            file,
            HomeScreenFilesCachingLogic,
            iconCache.getSerialNumberForUser(file.user),
        )
        iconCache.getTitleAndIcon(this, matchingLookupFlag)
    }

    private fun WorkspaceItemInfo.hasDisabledFileSystemNotReadyFlag(): Boolean =
        (runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY) != 0

    private fun WorkspaceItemInfo.removeDisabledFileSystemNotReadyFlag() {
        runtimeStatusFlags = runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY.inv()
    }

    @AssistedFactory
    interface Factory {
        fun create(update: HomeScreenFilesUpdate): HomeScreenFilesUpdateTask
    }
}
