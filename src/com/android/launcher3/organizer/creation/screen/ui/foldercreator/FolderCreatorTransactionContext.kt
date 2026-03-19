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

package com.android.launcher3.organizer.creation.screen.ui.foldercreator

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.IntSet
import java.util.ArrayList

/**
 * A specialized [TransactionContext] for creating and placing multiple folders on the workspace.
 */
class FolderCreatorTransactionContext(
    private val delegate: TransactionContext,
    private val spaceFinder: WorkspaceItemSpaceFinder,
    private val folders: List<FolderInfo>,
) : TransactionContext by delegate {

    private val placedItems = ArrayList<ItemInfo>()

    /**
     * Adds multiple folders to the workspace, automatically finding available space for each to
     * avoid collisions.
     */
    fun addFolders() {
        for (folder in folders) {
            addFolder(folder)
        }
    }

    /** Adds a single folder to the workspace, finding available space for it. */
    private fun addFolder(folder: FolderInfo) {
        val coords = spaceFinder.findSpaceForItem(placedItems, folder.spanX, folder.spanY, IntSet())
        folder.container = Favorites.CONTAINER_DESKTOP
        folder.screenId = coords.screenId
        folder.cellX = coords.cellX
        folder.cellY = coords.cellY

        addItemToDatabase(folder)
        placedItems.add(folder)

        for (item in folder.getContents()) {
            item.container = folder.id
            addItemToDatabase(item)
        }
    }
}
