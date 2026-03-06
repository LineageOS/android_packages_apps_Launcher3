/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.workspacefunctions

import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.appfunctions.workspace.AppInFolderSpec
import com.android.launcher3.appfunctions.workspace.HotseatItemSpec
import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceItemSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceScreenSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTypeTranslator
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import javax.inject.Inject

/** A translator that converts between [WorkspaceData] and [WorkspaceSpec]. */
class LauncherWorkspaceTypeTranslator @Inject constructor() :
    WorkspaceTypeTranslator<WorkspaceData> {

    override fun toSpec(workspace: WorkspaceData): WorkspaceSpec {
        val desktopItems = mutableListOf<ItemInfo>()
        val hotseatItems = mutableListOf<ItemInfo>()
        val folderContentsMap = mutableMapOf<Int, MutableList<ItemInfo>>()

        for (item in workspace) {
            when (item.container) {
                CONTAINER_DESKTOP -> desktopItems.add(item)
                CONTAINER_HOTSEAT -> hotseatItems.add(item)
                else -> {
                    if (item.container > 0) {
                        folderContentsMap.getOrPut(item.container) { mutableListOf() }.add(item)
                    }
                }
            }
        }

        val screenSpecs =
            desktopItems
                .groupBy { it.screenId }
                .toSortedMap()
                .map { (_, items) ->
                    WorkspaceScreenSpec(items.map { mapToWorkspaceItemSpec(it, folderContentsMap) })
                }

        val hotseatSpec =
            HotseatSpec(hotseatItems.map { mapToHotseatItemSpec(it, folderContentsMap) })

        return WorkspaceSpec(
            screens = screenSpecs,
            hotseat = hotseatSpec,
            rows = null, // TODO(b/457458301): Fetch from device profile
            columns = null,
        )
    }

    private fun mapToWorkspaceItemSpec(
        item: ItemInfo,
        folderContentsMap: Map<Int, List<ItemInfo>>
    ): WorkspaceItemSpec {
        return when (item) {
            is WorkspaceItemInfo ->
                WorkspaceItemSpec(
                    x = item.cellX,
                    y = item.cellY,
                    packageName = item.intent?.component?.packageName ?: item.intent?.`package`,
                    className = item.intent?.component?.className,
                    label = item.title?.toString(),
                    shortcutId = item.intent?.getStringExtra("shortcut_id"),
                )
            is LauncherAppWidgetInfo ->
                WorkspaceItemSpec(
                    x = item.cellX,
                    y = item.cellY,
                    spanX = item.spanX,
                    spanY = item.spanY,
                    packageName = item.providerName.packageName,
                    className = item.providerName.className,
                )
            is FolderInfo ->
                WorkspaceItemSpec(
                    x = item.cellX,
                    y = item.cellY,
                    title = item.title?.toString(),
                    items = folderContentsMap[item.id]?.map(::mapToAppInFolderSpec),
                )
            else -> throw IllegalArgumentException("Unsupported item type ${item::class.simpleName} on workspace")
        }
    }

    private fun mapToHotseatItemSpec(
        item: ItemInfo,
        folderContentsMap: Map<Int, List<ItemInfo>>
    ): HotseatItemSpec {
        return when (item) {
            is WorkspaceItemInfo ->
                HotseatItemSpec(
                    packageName = item.intent?.component?.packageName ?: item.intent?.`package`,
                    className = item.intent?.component?.className,
                    label = item.title?.toString(),
                )
            is FolderInfo ->
                HotseatItemSpec(
                    title = item.title?.toString(),
                    items = folderContentsMap[item.id]?.map(::mapToAppInFolderSpec),
                )
            else -> throw IllegalArgumentException("Unsupported item type on hotseat")
        }
    }

    private fun mapToAppInFolderSpec(item: ItemInfo): AppInFolderSpec {
        if (item !is WorkspaceItemInfo) {
            throw IllegalArgumentException("Only apps are supported in folders")
        }
        return AppInFolderSpec(
            packageName = item.intent?.component?.packageName ?: item.intent?.`package` ?: "",
            className = item.intent?.component?.className ?: "",
            label = item.title?.toString(),
        )
    }
}
