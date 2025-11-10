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

package com.android.launcher3.taskbar

import android.graphics.Rect
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.util.size
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemFactory
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.ItemInfoMatcher
import java.util.Collections

/**
 * Manages the [DropTarget] implementations that handle drag and drop events over the taskbarView
 * location
 */
class TaskbarViewDragDropController(
    val activityContext: TaskbarActivityContext,
    val pinnedAppsContainerDelegate: PinnedAppsContainerDelegate,
) {
    @VisibleForTesting val pinningDropTarget = PinningDropTarget()
    @VisibleForTesting val unpinDropTarget = UnpinDropTarget()
    private var modelCallbacks: TaskbarModelCallbacks? = null

    fun setUpCallbacks(callbacks: TaskbarModelCallbacks) {
        modelCallbacks = callbacks
    }

    fun addDropTargets(dragController: DragController) {
        dragController.addDropTarget(pinningDropTarget)
        dragController.addDropTarget(unpinDropTarget)
    }

    fun removeDropTargets(dragController: DragController) {
        dragController.removeDropTarget(pinningDropTarget)
        dragController.removeDropTarget(unpinDropTarget)
    }

    /**
     * Implementation of the [DropTarget] that handles drag and drop events over the recent apps
     * area.
     */
    inner class UnpinDropTarget() : DropTarget {

        override fun isDropEnabled(): Boolean {
            return true
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {
            val itemToUnpin = dragObject?.dragInfo ?: return

            activityContext.modelWriter.deleteItemFromDatabase(
                itemToUnpin,
                "Unpin by taskbar drag and drop",
            )
            modelCallbacks?.bindWorkspaceComponentsRemoved(
                ItemInfoMatcher.ofItems(Collections.singleton(itemToUnpin))
            )
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {}

        override fun onDragOver(dragObject: DropTarget.DragObject?) {}

        override fun onDragExit(dragObject: DropTarget.DragObject?) {}

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            return true
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            // TODO(b/447444838): For now, this makes recent apps section a drop target to unpin,
            // this should probably be updated to be a clear drop target for item removal
            // (pendng UX).
            pinnedAppsContainerDelegate.getHitRectForUnpinRelativeToDragLayer(outRect)
        }
    }

    /**
     * Implementation of the [DropTarget] that handles drag and drop events over the hotseat items
     * area.
     */
    inner class PinningDropTarget() : DropTarget {

        private val canPinMoreItems: Boolean
            get() {
                val hotseatItems = modelCallbacks?.hotseatItems ?: return false
                return hotseatItems.size < activityContext.taskbarSpecsEvaluator.maxPinnableCount
            }

        private fun extractItemInfoFromDragObject(dragObject: DropTarget.DragObject?): ItemInfo? {
            return when (val dragItemInfo = dragObject?.dragInfo) {
                is WorkspaceItemInfo -> dragItemInfo
                is WorkspaceItemFactory -> dragItemInfo.makeWorkspaceItem(activityContext)
                else -> null
            }
        }

        /** Returns the screenId where the dragObject is dropped at. */
        private fun findTargetScreenId(hotSeatItems: IntSparseArrayMap<ItemInfo>): Int {
            // TODO(b/447444838): Using the last hotSeat screenId now. Need to calculate
            // targetScreenId based on where the object was dropped and extract them into a util
            // function.
            return (hotSeatItems.lastOrNull()?.screenId ?: 0) + 1
        }

        override fun isDropEnabled(): Boolean {
            // TODO(b/447444838): For now, only accept drops when the number of pinned items has
            // not reached limit. This will probably be modified after dropping to hotseat overflow
            // folder UX finalized.
            return canPinMoreItems
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {
            val newInfo = extractItemInfoFromDragObject(dragObject) ?: return
            val hotseatItems = modelCallbacks?.hotseatItems ?: return
            val existingInfo =
                if (newInfo.id != ItemInfo.NO_ID && newInfo.container == CONTAINER_HOTSEAT) newInfo
                else null
            val targetScreenId = findTargetScreenId(hotseatItems)

            // When the dragObject is from pinned items area and is moving right, shift items left
            // to fill the gap left by the moved item.
            val itemsToShift = mutableListOf<ItemInfo>()
            var lastShiftedScreenId = targetScreenId - 1

            if (existingInfo != null) {
                for (i in 0..<hotseatItems.size) {
                    val item = hotseatItems.valueAt(hotseatItems.size - i - 1) ?: continue

                    if (item.screenId == lastShiftedScreenId && item != existingInfo) {
                        itemsToShift.add(item)
                        lastShiftedScreenId--
                    } else if (item.screenId != targetScreenId) {
                        break
                    }
                }
            }

            val writer = activityContext.modelWriter
            for (item in itemsToShift) {
                writer.addOrMoveItemInDatabase(
                    item,
                    CONTAINER_HOTSEAT,
                    item.screenId - 1,
                    item.screenId - 1,
                    0,
                )
            }
            modelCallbacks?.bindItemsUpdated(itemsToShift.toSet())

            val newItemScreenId = targetScreenId - if (existingInfo != null) 1 else 0
            writer.addOrMoveItemInDatabase(
                newInfo,
                CONTAINER_HOTSEAT,
                newItemScreenId,
                newItemScreenId,
                0,
            )
            modelCallbacks?.bindItemsUpdated(hashSetOf(newInfo))
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {}

        override fun onDragOver(dragObject: DropTarget.DragObject?) {}

        override fun onDragExit(dragObject: DropTarget.DragObject?) {}

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            // TODO(b/447444838): For now, only accept drops when the number of pinned items has
            // not reached limit. This will probably be modified after dropping to hotseat overflow
            // folder UX finalized.
            return canPinMoreItems
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            pinnedAppsContainerDelegate.getHitRectForPinRelativeToDragLayer(outRect)
        }
    }

    /**
     * A delegate for a container that manages pinned apps, used for drag-and-drop operations.
     *
     * This interface is implemented by a view, such as [TaskbarView], that can have apps pinned to
     * it. It provides the necessary boundaries (hit rectangles) to a drag controller, allowing the
     * controller to determine valid drop zones for pinning or unpinning an item.
     */
    interface PinnedAppsContainerDelegate {
        /**
         * Calculates the hit rectangle for the primary interactive area of the pinned icons,
         * relative to the DragLayer. This area includes the All Apps button and all visible icons.
         * Returns a Rect object to be populated with the calculated coordinates.
         */
        fun getHitRectForPinRelativeToDragLayer(outRect: Rect?)

        /**
         * Calculates the hit rectangle for the unpinned icons, relative to the DragLayer. This area
         * includes the unpinned icons area. Returns a Rect object to be populated with the
         * calculated coordinates.
         */
        fun getHitRectForUnpinRelativeToDragLayer(outRect: Rect?)
    }
}
