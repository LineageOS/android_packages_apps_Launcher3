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
import android.util.SparseArray
import android.view.View
import com.android.launcher3.DropTarget
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext

/**
 * Manages the [DropTarget] implementations that handle drag and drop events over the taskbarView
 * location
 */
class TaskbarViewDragDropController(
    activityContext: ActivityContext,
    pinnedAppsContainerDelegate: PinnedAppsContainerDelegate,
) {
    val pinningDropTarget = PinningDropTarget(activityContext, pinnedAppsContainerDelegate)
    val unpinDropTarget = UnpinDropTarget(activityContext, pinnedAppsContainerDelegate)

    fun setTaskbarInfoList(items: SparseArray<ItemInfo>?) {
        pinningDropTarget.hotseatInfosList = items
    }

    fun setApps(items: Array<AppInfo>) {
        pinningDropTarget.appInfosList = items
    }

    fun addDropTargets(dragController: DragController<*>) {
        dragController.addDropTarget(pinningDropTarget)
        dragController.addDropTarget(unpinDropTarget)
    }

    fun removeDropTargets(dragController: DragController<*>) {
        dragController.removeDropTarget(pinningDropTarget)
        dragController.removeDropTarget(unpinDropTarget)
    }

    class UnpinDropTarget(
        private val activityContext: ActivityContext,
        private val pinnedAppsContainerDelegate: PinnedAppsContainerDelegate,
    ) : DropTarget {

        override fun isDropEnabled(): Boolean {
            return true
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {}

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

    class PinningDropTarget(
        private val activityContext: ActivityContext,
        private val pinnedAppsContainerDelegate: PinnedAppsContainerDelegate,
    ) : DropTarget {

        var hotseatInfosList: SparseArray<ItemInfo>? = null
        var appInfosList: Array<AppInfo> = AppInfo.EMPTY_ARRAY

        override fun isDropEnabled(): Boolean {
            return true
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {}

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
