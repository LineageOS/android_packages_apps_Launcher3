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
import android.os.Looper
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.util.size
import com.android.launcher3.Alarm
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.OnAlarmListener
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
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
    private val activityContext: TaskbarActivityContext,
    private val taskbarPinDelegate: PinnedAppsContainerDelegate,
) {
    companion object {
        private const val OPEN_OVERFLOW_DELAY_MS = 800L
        private const val CLOSE_OVERFLOW_DELAY_MS = OPEN_OVERFLOW_DELAY_MS
    }

    @VisibleForTesting val taskbarPinningDropTarget = PinningDropTarget(taskbarPinDelegate, false)
    @VisibleForTesting val unpinDropTarget = UnpinDropTarget()
    @VisibleForTesting var targetPinIndex = -1
    @VisibleForTesting var overflowPinningDropTarget: PinningDropTarget? = null
    private var modelCallbacks: TaskbarModelCallbacks? = null
    @VisibleForTesting val tooltipController = TaskbarDragViewTooltip(activityContext)
    @VisibleForTesting val overflowContainerAlarm = Alarm(Looper.getMainLooper())

    private enum class AlarmState {
        RUNNING_OPEN,
        RUNNING_CLOSE,
        IDLE,
    }

    private var overflowAlarmState = AlarmState.IDLE
    private val openOnAlarmListener = OnAlarmListener {
        overflowAlarmState = AlarmState.IDLE
        activityContext.controllers.taskbarViewController.openOverflowContainer()
    }
    private val closeOnAlarmListener = OnAlarmListener {
        overflowAlarmState = AlarmState.IDLE
        activityContext.controllers.taskbarViewController.closeOverflowContainer()
    }

    fun setUpCallbacks(callbacks: TaskbarModelCallbacks) {
        modelCallbacks = callbacks
    }

    fun addDropTargets(dragController: DragController) {
        dragController.addDropTarget(taskbarPinningDropTarget)
        dragController.addDropTarget(unpinDropTarget)
    }

    fun removeDropTargets(dragController: DragController) {
        dragController.removeDropTarget(taskbarPinningDropTarget)
        dragController.removeDropTarget(unpinDropTarget)
    }

    fun onTaskbarItemViewDragStart(itemView: View) {
        taskbarPinDelegate.updateItemViewVisibilityForDragState(itemView, /*isDragged */ true)

        // TODO("Handle overflow icon drag start")
    }

    fun onTaskbarItemViewDragEnd(itemView: View) {
        taskbarPinDelegate.updateItemViewVisibilityForDragState(itemView, /*isDragged */ false)

        // TODO("Handle overflow icon drag end")
    }

    fun addOverflowDropTarget(
        dragController: DragController,
        delegate: PinnedAppsContainerDelegate,
    ) {
        overflowPinningDropTarget = PinningDropTarget(delegate, true)
        dragController.addDropTarget(overflowPinningDropTarget)
    }

    fun removeOverflowDropTarget(dragController: DragController) {
        dragController.removeDropTarget(overflowPinningDropTarget)
        overflowPinningDropTarget = null
    }

    private fun startOpenOverflowAlarm() {
        if (overflowAlarmState == AlarmState.RUNNING_OPEN) return

        startOverflowAlarm(overflowContainerAlarm, openOnAlarmListener, OPEN_OVERFLOW_DELAY_MS)
        overflowAlarmState = AlarmState.RUNNING_OPEN
    }

    private fun startCloseOverflowAlarm() {
        if (overflowAlarmState == AlarmState.RUNNING_CLOSE) return

        startOverflowAlarm(overflowContainerAlarm, closeOnAlarmListener, CLOSE_OVERFLOW_DELAY_MS)
        overflowAlarmState = AlarmState.RUNNING_CLOSE
    }

    private fun startOverflowAlarm(alarm: Alarm, callback: OnAlarmListener, delay: Long) {
        cancelOverflowAlarm()
        alarm.setOnAlarmListener(callback)
        alarm.setAlarm(delay)
    }

    private fun cancelOverflowAlarm() {
        if (!overflowContainerAlarm.alarmPending()) return

        overflowContainerAlarm.cancelAlarm()
        overflowAlarmState = AlarmState.IDLE
    }

    /**
     * Returns [targetScreenId] where the dragObject is dropped at, and [shouldShiftLeft] which is
     * true if the dragged item's space can be made by shifting items before the dropped index to
     * the left in the hotseat.
     */
    private fun getDropTargetState(
        hotSeatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
    ): Pair<Int, Boolean> {
        var currentPinIndex = 0
        var lastScreenId = -1
        var shouldShiftLeft = false

        for (i in 0 until hotSeatItems.size) {
            val item = hotSeatItems.valueAt(i) ?: continue
            if (item.isSameItem(draggedInfo)) {
                // The dragged item is already at the target position, nothing need to change.
                if (currentPinIndex == targetPinIndex) {
                    return Pair(item.screenId, true)
                }
                continue
            }

            if (item.screenId - lastScreenId > 1) {
                shouldShiftLeft = true
            }

            lastScreenId = item.screenId

            if (currentPinIndex == targetPinIndex) {
                return Pair(
                    if (shouldShiftLeft) lastScreenId - 1 else lastScreenId,
                    shouldShiftLeft,
                )
            }

            ++currentPinIndex
        }

        return Pair(if (shouldShiftLeft) lastScreenId else lastScreenId + 1, shouldShiftLeft)
    }

    /** Returns the list of items that need to shift left after reordering. */
    private fun getItemsToShiftLeft(
        hotseatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
        targetScreenId: Int,
    ): List<ItemInfo> {
        val itemsToShift = mutableListOf<ItemInfo>()
        var nextScreenIdToShift = targetScreenId
        for (i in hotseatItems.size - 1 downTo 0) {
            val item = hotseatItems.valueAt(i) ?: continue
            if (item.screenId > targetScreenId) {
                continue
            }
            if (!item.isSameItem(draggedInfo) && item.screenId == nextScreenIdToShift) {
                --nextScreenIdToShift
                itemsToShift.add(item)
            } else {
                break
            }
        }

        return itemsToShift
    }

    /** Returns the list of items that need to shift right after reordering. */
    private fun getItemsToShiftRight(
        hotseatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
        targetScreenId: Int,
    ): List<ItemInfo> {
        val itemsToShift = mutableListOf<ItemInfo>()
        var nextScreenIdToShift = targetScreenId
        for (i in 0..<hotseatItems.size) {
            val item = hotseatItems.valueAt(i) ?: continue

            if (item.screenId < targetScreenId) {
                continue
            }

            if (!item.isSameItem(draggedInfo) && item.screenId == nextScreenIdToShift) {
                itemsToShift.add(item)
                ++nextScreenIdToShift
            } else {
                break
            }
        }
        return itemsToShift
    }

    private fun addOrMoveItemInDatabase(
        hotseatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
        hotseatItemsContainDraggedInfo: Boolean,
    ) {
        val (targetScreenId, shouldShiftLeft) = getDropTargetState(hotseatItems, draggedInfo)
        if (hotseatItemsContainDraggedInfo && draggedInfo.screenId == targetScreenId) return

        val itemsToShift =
            if (shouldShiftLeft) getItemsToShiftLeft(hotseatItems, draggedInfo, targetScreenId)
            else getItemsToShiftRight(hotseatItems, draggedInfo, targetScreenId)

        val writer = activityContext.modelWriter
        for (item in itemsToShift) {
            val newPosition = item.screenId + if (shouldShiftLeft) -1 else 1
            writer.addOrMoveItemInDatabase(item, CONTAINER_HOTSEAT, newPosition, newPosition, 0)
        }
        modelCallbacks?.bindItemsUpdated(itemsToShift.toSet())

        writer.addOrMoveItemInDatabase(
            draggedInfo,
            CONTAINER_HOTSEAT,
            targetScreenId,
            targetScreenId,
            0,
        )
        modelCallbacks?.bindItemsUpdated(hashSetOf(draggedInfo))
    }

    /** Returns the [ItemInfo] from the dragged object. */
    private fun extractItemInfoFromDragObject(dragObject: DropTarget.DragObject?): ItemInfo? {
        return when (val dragItemInfo = dragObject?.dragInfo) {
            is WorkspaceItemInfo -> dragItemInfo
            is WorkspaceItemFactory -> dragItemInfo.makeWorkspaceItem(activityContext)
            else -> null
        }
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
            tooltipController.hide()
            val itemToUnpin = dragObject?.dragInfo ?: return

            activityContext.modelWriter.deleteItemFromDatabase(
                itemToUnpin,
                "Unpin by taskbar drag and drop",
            )
            modelCallbacks?.bindWorkspaceComponentsRemoved(
                ItemInfoMatcher.ofItems(Collections.singleton(itemToUnpin))
            )
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            val draggedInfo = extractItemInfoFromDragObject(dragObject) ?: return
            if (draggedInfo.id != ItemInfo.NO_ID && draggedInfo.container == CONTAINER_HOTSEAT) {
                if (tooltipController.isActive()) {
                    tooltipController.hide()
                }
                tooltipController.show(calculateTooltipTargetPosition(dragObject))
            }
        }

        override fun onDragOver(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            tooltipController.updatePosition(calculateTooltipTargetPosition(dragObject))
        }

        override fun onDragExit(dragObject: DropTarget.DragObject?) {
            tooltipController.hide()
        }

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            return true
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            if (overflowPinningDropTarget == null) {
                taskbarPinDelegate.getHitRectForPinRelativeToDragLayer(outRect)
                outRect?.offset(0, -activityContext.deviceProfile.taskbarProfile.height)
            }
        }

        /** Calculates the tooltip target position based on visual center coordinates. */
        private fun calculateTooltipTargetPosition(dragObject: DropTarget.DragObject): FloatArray {
            val targetLocation = FloatArray(2)
            dragObject.getVisualCenter(targetLocation)
            val yOffset =
                activityContext.resources.getDimensionPixelSize(R.dimen.taskbar_tooltip_y_offset)
            targetLocation[1] -= (dragObject.dragView.measuredHeight / 2f) + yOffset

            return targetLocation
        }
    }

    /**
     * Implementation of the [DropTarget] that handles drag and drop events over the hotseat items
     * area.
     */
    inner class PinningDropTarget(
        private val delegate: PinnedAppsContainerDelegate,
        private val isOverflowDropTarget: Boolean,
    ) : DropTarget {
        private var draggedInfo: ItemInfo? = null
        private val dragObjectVisualCenter = FloatArray(2)

        private val canPinMoreItems: Boolean
            get() {
                val hotseatItems = modelCallbacks?.hotseatItems ?: return false
                return hotseatItems.size < activityContext.taskbarSpecsEvaluator.maxPinnableCount
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
            var newInfo = extractItemInfoFromDragObject(dragObject) ?: return
            val hotseatItems = modelCallbacks?.hotseatItems ?: return

            var hotseatItemsContainDraggedInfo = false
            // Check if the dragged item already exists in the model.
            // If it does, use the one from the Model's instance, to avoid failing the ModelWriter
            // itemInfo check.
            if (newInfo.id != ItemInfo.NO_ID && newInfo.container == CONTAINER_HOTSEAT) {
                for (i in 0 until hotseatItems.size) {
                    val item = hotseatItems.valueAt(i) ?: continue
                    if (item.id != ItemInfo.NO_ID && item.id == newInfo.id) {
                        newInfo = item
                        hotseatItemsContainDraggedInfo = true
                        break
                    }
                }
            }

            addOrMoveItemInDatabase(hotseatItems, newInfo, hotseatItemsContainDraggedInfo)
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {
            if (isOverflowDropTarget) {
                cancelOverflowAlarm()
            }

            dragObject ?: return
            draggedInfo = extractItemInfoFromDragObject(dragObject)
            dragObject.getVisualCenter(dragObjectVisualCenter)

            delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
        }

        override fun onDragOver(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            dragObject.getVisualCenter(dragObjectVisualCenter)

            if (isOverflowDropTarget) {
                delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
            } else if (delegate.isPointOnOverflowIcon(dragObjectVisualCenter)) {
                startOpenOverflowAlarm()
                delegate.releaseDropSlot()
            } else {
                startCloseOverflowAlarm()
                delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
            }
        }

        override fun onDragExit(dragObject: DropTarget.DragObject?) {
            startCloseOverflowAlarm()

            targetPinIndex = taskbarPinDelegate.getPinIndex()
            delegate.releaseDropSlot()
        }

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            // TODO(b/447444838): For now, only accept drops when the number of pinned items has
            // not reached limit. This will probably be modified after dropping to hotseat overflow
            // folder UX finalized.
            return targetPinIndex >= 0 && canPinMoreItems
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            delegate.getHitRectForPinRelativeToDragLayer(outRect)
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

        /** Returns true if the given point is on the pinned overflow icon. */
        fun isPointOnOverflowIcon(point: FloatArray): Boolean

        /** Reserves the location with a placeholder indicating where the icon to be dropped. */
        fun reserveDropSlotForDragLocation(onScreenLocationX: Int)

        /** Clears the reserved drop slot. */
        fun releaseDropSlot()

        /**
         * Returns the index in the taskbar where the dragged item would be pinned if dropped at the
         * current location.
         */
        fun getPinIndex(): Int

        /** Updates the visibility of the dragged Taskbar item view based on its drag state. */
        fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean)
    }
}
