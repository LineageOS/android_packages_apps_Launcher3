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

package com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.repository.HomeScreenRepository

/**
 * A specialized [TransactionContext] for workspace organization operations.
 *
 * This class provides additional methods for moving and deleting screens, which are not part of the
 * base [TransactionContext] interface.
 *
 * @param delegate The base [TransactionContext] to delegate to.
 * @param homeScreenRepository The [HomeScreenRepository] to use for item lookup and filtering.
 */
class WorkspaceOrganizerTransactionContext(
    private val delegate: TransactionContext,
    private val homeScreenRepository: HomeScreenRepository,
) : TransactionContext by delegate {

    /**
     * Moves a screen to a new index.
     *
     * @param currentIndex The current index of the screen in the [orderedScreenIds] list.
     * @param targetIndex The new index for the screen.
     * @param orderedScreenIds The current list of screen IDs in their original order.
     */
    fun moveScreen(currentIndex: Int, targetIndex: Int, orderedScreenIds: List<Int>) {
        if (
            currentIndex == targetIndex ||
                currentIndex !in orderedScreenIds.indices ||
                targetIndex !in orderedScreenIds.indices
        )
            return

        val itemsInDesktop =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP
            }

        val updatedItems = mutableListOf<ItemInfo>()

        for (item in itemsInDesktop) {
            val itemScreenId = item.screenId
            val i = orderedScreenIds.indexOf(itemScreenId)
            if (i == -1) continue

            if (i == currentIndex) {
                item.screenId = orderedScreenIds[targetIndex]
                updatedItems.add(item)
            } else if (currentIndex < targetIndex && i in (currentIndex + 1)..targetIndex) {
                item.screenId = orderedScreenIds[i - 1]
                updatedItems.add(item)
            } else if (currentIndex > targetIndex && i in targetIndex until currentIndex) {
                item.screenId = orderedScreenIds[i + 1]
                updatedItems.add(item)
            }
        }

        if (updatedItems.isNotEmpty()) {
            this.moveItemsInDatabase(updatedItems, Favorites.CONTAINER_DESKTOP, -1)
        }
    }

    /**
     * Deletes a screen from the database.
     *
     * @param screenId The ID of the screen to delete.
     */
    fun deleteScreen(screenId: Int) {
        val itemsToDelete =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP && it.screenId == screenId
            }
        this.deleteItemsFromDatabase(itemsToDelete, "Screen deleted")

        val itemsToShift =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP && it.screenId > screenId
            }
        for (item in itemsToShift) {
            item.screenId -= 1
        }
        if (itemsToShift.isNotEmpty()) {
            this.moveItemsInDatabase(itemsToShift, Favorites.CONTAINER_DESKTOP, -1)
        }
    }
}
