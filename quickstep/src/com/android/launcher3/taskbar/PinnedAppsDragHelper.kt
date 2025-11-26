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

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.taskbar.TaskbarViewDragDropController.PinnedAppsContainerDelegate

/** A helper class to handle drag and drop logic for pinned apps container. */
abstract class PinnedAppsDragHelper(
    private val context: Context,
    private val container: ViewGroup,
    protected val iconSize: Int,
) : PinnedAppsContainerDelegate {

    protected var dropSpotIndex = -1
    private var dropTargetGhostView: View? = null
    private var indexOfChildHiddenForDrag = -1

    /**
     * Calculates the index where the "ghost" view (placeholder for the drop target) should be
     * inserted based on the drag's X coordinate.
     *
     * @param onScreenLocationX The raw X coordinate of the drag event on the screen.
     * @return The calculated index within the pinned apps list where the ghost view should appear,
     *   or -1 if it cannot be calculated or the default implementation is used.
     */
    open fun calculateGhostViewIndex(onScreenLocationX: Int): Int = -1

    /**
     * Calculates the actual index where the drop ghost view should be inserted into the container's
     * child list.
     *
     * @param dropIndex The logical index among the *visible* items where the drop should occur.
     * @param hiddenChildIndex The actual index in the container of the child currently hidden for
     *   the drag operation (or -1 if none).
     * @return The actual index to pass to [ViewGroup.addView].
     */
    open fun calculateDropIndexInContainer(dropIndex: Int, hiddenChildIndex: Int): Int {
        if (hiddenChildIndex in 0 until dropIndex) {
            return dropIndex + 1
        }
        return dropIndex
    }

    open fun createGhostViewLayoutParams(iconSize: Int): ViewGroup.LayoutParams =
        TaskbarView.TaskbarLayoutParams(0, 0)

    open fun onDragStateChanged() {}

    open fun hasHiddenChild(): Boolean = indexOfChildHiddenForDrag >= 0

    override fun getPinIndex(): Int = dropSpotIndex

    override fun releaseDropSlot() {
        dropSpotIndex = -1
        dropTargetGhostView?.let {
            container.removeView(it)
            dropTargetGhostView = null
            onDragStateChanged()
        }
    }

    override fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean) {
        val index = container.indexOfChild(itemView)
        if (index == -1) {
            indexOfChildHiddenForDrag = -1
            return
        }
        indexOfChildHiddenForDrag = if (isDragged) index else -1
        itemView.visibility = if (isDragged) View.GONE else View.VISIBLE
    }

    override fun reserveDropSlotForDragLocation(onScreenLocationX: Int) {
        val newDropIndex = calculateGhostViewIndex(onScreenLocationX)
        if (dropTargetGhostView != null && dropSpotIndex == newDropIndex) return

        releaseDropSlot()
        dropSpotIndex = newDropIndex

        if (dropTargetGhostView == null) {
            dropTargetGhostView = TaskbarDropTargetGhostView(context, iconSize)
        }

        val lp = createGhostViewLayoutParams(iconSize)
        val insertIndex = calculateDropIndexInContainer(newDropIndex, indexOfChildHiddenForDrag)

        container.addView(requireNotNull(dropTargetGhostView), insertIndex, lp)
        onDragStateChanged()
    }
}
