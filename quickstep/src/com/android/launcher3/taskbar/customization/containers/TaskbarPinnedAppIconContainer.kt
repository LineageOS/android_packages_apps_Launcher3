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

package com.android.launcher3.taskbar.customization.containers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity.CENTER_VERTICAL
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.contains
import androidx.core.view.get
import androidx.core.view.isEmpty
import androidx.core.view.setPadding
import com.android.app.tracing.traceSection
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities.dpToPx
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.celllayout.CellInfo
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.folder.PreviewBackground
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.ItemInfoWrapper
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarDropTargetGhostView
import com.android.launcher3.taskbar.TaskbarOverflowView
import com.android.launcher3.taskbar.TaskbarPopupController
import com.android.launcher3.taskbar.TaskbarViewCallbacks
import com.android.launcher3.taskbar.customization.TaskbarContainer
import com.android.launcher3.taskbar.customization.enums.OverflowIconPosition
import com.android.launcher3.taskbar.customization.listeners.TaskbarIconsContainerHoverListener
import com.android.launcher3.taskbar.customization.listeners.TaskbarIconsContainerOverflowClickListeners
import com.android.launcher3.taskbar.customization.overflow.TaskbarIconsContainerOverflowViewHelper
import com.android.launcher3.taskbar.customization.overflow.TaskbarOverflowIconWrapper
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerLayoutParams
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerUtil
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerUtil.DEFAULT_BOUNCE_SCALE
import com.android.launcher3.taskbar.customization.viewfactory.TaskbarPinnedAppsIconsViewFactory
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.PredictedAppIcon
import kotlin.math.min
import androidx.core.graphics.withTranslation

/** Taskbar container which hosts its pinned apps. */
class TaskbarPinnedAppIconContainer(context: Context) :
    LinearLayout(context),
    Reorderable,
    TaskbarContainer,
    TaskbarIconsContainerHoverListener,
    TaskbarIconsContainerOverflowClickListeners,
    FolderIcon.FolderIconParent {

    private var itemMarginLeftRight = 0
    private val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = DEFAULT_BOUNCE_SCALE
    private val isRtl = isRtl(activityContext.resources)

    private var dropTargetGhostView: View? = null
    private var indexOfChildHiddenForDrag = -1
    private var dropSpotIndex = -1

    override val taskbarIconViewSize =
        dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconTouchSize, activityContext)

    override val taskbarIconViewPadding =
        dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconPadding, activityContext)

    override val overflowIconClickListener: OnClickListener
        get() = taskbarViewCallbacks.pinnedOverflowOnClickListener

    override val overflowIconLongClickListener: OnLongClickListener
        get() = taskbarViewCallbacks.pinnedOverflowOnLongClickListener

    private val itemViewFactory = TaskbarPinnedAppsIconsViewFactory(activityContext, this)

    private val taskbarIconsContainerOverflowHelper =
        TaskbarIconsContainerOverflowViewHelper.create(
            taskbarIconViewSize,
            taskbarIconViewPadding,
            OverflowIconPosition.END,
            TaskbarOverflowIconWrapper<ItemInfo> { item -> ItemInfoWrapper(item, activityContext) },
            parentView = this,
            TaskbarOverflowView.OverflowType.PINNED,
        )

    private val isOverflowEnabled = TaskbarPopupController.canPinAppsOverflow()

    val overflowView: TaskbarOverflowView =
        taskbarIconsContainerOverflowHelper.taskbarContainerOverflowView

    val isOverflowViewShowing: Boolean
        get() = overflowView in this

    private lateinit var taskbarViewCallbacks: TaskbarViewCallbacks

    private var leaveBehindFolderIcon: FolderIcon? = null

    private val folderLeaveBehindColor: Int =
        Themes.getAttrColor(activityContext, android.R.attr.textColorTertiary)

    init {
        orientation = HORIZONTAL
        gravity = CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    fun setUpCallbacks(taskbarViewCallbacks: TaskbarViewCallbacks) {
        this.taskbarViewCallbacks = taskbarViewCallbacks
    }

    fun updateIcons(itemInfos: List<ItemInfo>, forceUpdate: Boolean) {
        traceSection("TaskbarPinnedAppIconContainer#updateIcons") {
            updateIconsInternal(itemInfos, forceUpdate)
        }
    }

    private fun updateIconsInternal(itemInfos: List<ItemInfo>, forceUpdate: Boolean) {
        var numViewsAnimated = 0
        val itemCount = itemInfos.size

        // TODO: Migrate this logic to overflow logic when recent app container is ready.
        //  We currently cannot refactor test until recents container is ready.
        val numMaxIcons = activityContext.taskbarSpecsEvaluator.numShownHotseatIcons
        var itemList = itemInfos
        if (isRtl) itemList = itemList.reversed()

        val taskbarContainerIconsBySection =
            TaskbarIconContainerUtil.getOverflowAndNonOverflowLists(
                itemList,
                OverflowIconPosition.END,
                numMaxIcons,
            )

        forEachIcon(taskbarContainerIconsBySection.nonOverflownItems) { index, item ->
            val itemView = itemViewFactory.getView(item, index)
            itemView.setPadding(taskbarIconViewPadding)

            if (!forceUpdate && item.isSameItem(itemView.tag) && itemView in this) {
                // Might have been wrapped in TaskItemInfo by recents update.
                itemView.tag = item
                return@forEachIcon
            }

            if (itemView !in this) {
                addView(itemView, getLayoutParams(index, itemCount))
            } else {
                itemView.layoutParams = getLayoutParams(index, itemCount)
            }

            if (itemView is FolderIcon) {
                itemView.onItemsChanged(false)
                itemView.folder.reapplyItemInfo()
            }

            setCellBindingInfo(itemView, item)

            if (item is WorkspaceItemInfo) {
                when (itemView) {
                    is PredictedAppIcon -> {
                        if (itemView.applyFromWorkspaceItemWithAnimation(item, numViewsAnimated)) {
                            numViewsAnimated++
                        }
                    }

                    is BubbleTextView -> {
                        itemView.applyFromWorkspaceItem(item)
                    }
                }
            }

            setClickAndLongClickListenersForIcon(itemView)
            if (itemView.getTag(R.id.taskbar_icon_has_hover_listener) == true) {
                // Creating hover listener is expensive due to view inflation, so reuse if possible.
                return@forEachIcon
            }
            itemView.setOnHoverListener(taskbarViewCallbacks.getIconOnHoverListener(itemView))
            itemView.setTag(R.id.taskbar_icon_has_hover_listener, true)
        }
        // Recycle the remaining view if view count is more than items to show
        while (childCount > itemCount) {
            itemViewFactory.removeAndRecycle(this[childCount - 1])
        }

        if (isOverflowEnabled) {
            taskbarIconsContainerOverflowHelper.setUpOverflowView(
                taskbarContainerIconsBySection.overflownItems,
                itemMarginLeftRight,
            )
            taskbarIconsContainerOverflowHelper.setUpCallbacks(
                hoverListener = this,
                overflowClickListeners = this,
            )
        }
    }

    private fun getLayoutParams(index: Int, itemsCount: Int): TaskbarIconContainerLayoutParams {
        val lp = TaskbarIconContainerLayoutParams(taskbarIconViewSize, taskbarIconViewSize)
        if (index != 0) {
            lp.marginStart = itemMarginLeftRight
        }
        if (index != itemsCount - 1) {
            lp.marginEnd = itemMarginLeftRight
        }
        return lp
    }

    private fun setCellBindingInfo(itemView: View, itemInfo: ItemInfo) {
        (itemView.layoutParams as TaskbarIconContainerLayoutParams).bindInfo =
            CellInfo(
                itemView,
                itemInfo.screenId,
                itemInfo.container,
                itemInfo.cellX,
                itemInfo.cellY,
                itemInfo.spanX,
                itemInfo.spanY,
            )
    }

    /** Sets OnClickListener and OnLongClickListener for the given view. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setClickAndLongClickListenersForIcon(icon: View) {
        icon.setOnClickListener(taskbarViewCallbacks.iconOnClickListener)
        icon.onLongClickListener = taskbarViewCallbacks.iconOnLongClickListener
        // Add right-click support to btv icons.
        icon.setOnTouchListener { v, event ->
            if (
                event.isFromSource(InputDevice.SOURCE_MOUSE) &&
                    (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 &&
                    v is BubbleTextView
            ) {
                activityContext.showPopupMenuForIcon(v)
                true
            } else {
                false
            }
        }
    }

    /** Reserves a spot for a drop event by inserting a ghost view. */
    fun reserveDropSlot(index: Int) {
        if (dropTargetGhostView != null && dropSpotIndex == index) {
            return
        }
        // Remove old ghost and restore original item if position changed
        releaseDropSlot()
        dropSpotIndex = index

        // If no valid drop index, just return
        if (dropSpotIndex < 0) {
            return
        }

        if (isOverflowViewShowing) {
            dropSpotIndex = min(dropSpotIndex, indexOfChild(overflowView) - 1)
        }

        if (dropTargetGhostView == null) {
            dropTargetGhostView = TaskbarDropTargetGhostView(activityContext, taskbarIconViewSize)
        }
        val lp = TaskbarIconContainerLayoutParams(taskbarIconViewSize, taskbarIconViewSize)
        lp.marginStart = itemMarginLeftRight
        lp.marginEnd = itemMarginLeftRight
        var insertionIndex = dropSpotIndex

        if (indexOfChildHiddenForDrag != -1 && insertionIndex >= indexOfChildHiddenForDrag) {
            insertionIndex++
        }

        addView(dropTargetGhostView, min(insertionIndex, childCount), lp)
    }

    fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean) {
        val indexOfDraggedView = indexOfChild(itemView)
        if (indexOfDraggedView < 0) {
            indexOfChildHiddenForDrag = -1
            return
        }
        indexOfChildHiddenForDrag = if (isDragged) indexOfDraggedView else -1
        itemView.visibility = if (isDragged) GONE else VISIBLE
    }

    /** Removes the ghost view and restores the original item if it was hidden. */
    fun releaseDropSlot() {
        dropSpotIndex = -1
        dropTargetGhostView?.let { removeView(it) }
    }

    /** Applies and traces [body] for each [icons] instance. */
    private inline fun forEachIcon(icons: List<ItemInfo>, body: (Int, ItemInfo) -> Unit) {
        for ((index, icon) in icons.withIndex()) {
            traceSection("TaskbarPinnedAppIconContainer#forEachIcon.icon") { body(index, icon) }
        }
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): LayoutParams {
        return TaskbarIconContainerLayoutParams(lp)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return TaskbarIconContainerLayoutParams(context, attrs)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is TaskbarIconContainerLayoutParams
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    override fun setReorderBounceScale(scale: Float) {
        reorderBounceScale = scale
    }

    override fun getReorderBounceScale(): Float = reorderBounceScale

    override val spaceNeeded: Int
        get() {
            val visibleChildCount = getVisibleChildCount()
            if (visibleChildCount == 0) return 0
            return (visibleChildCount * taskbarIconViewSize) +
                    ((visibleChildCount - 1) * 2 * itemMarginLeftRight)
        }

    fun getVisibleChildCount(): Int {
        if (isEmpty()) return 0
        return childCount - if (indexOfChildHiddenForDrag >= 0) 1 else 0
    }
    override fun getHoverListener(icon: View): OnHoverListener =
        taskbarViewCallbacks.getIconOnHoverListener(icon)

    override fun drawFolderLeaveBehindForIcon(child: FolderIcon) {
        leaveBehindFolderIcon = child
        invalidate()
    }

    override fun clearFolderLeaveBehind(child: FolderIcon) {
        leaveBehindFolderIcon = null
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val folderIcon = leaveBehindFolderIcon
        if (folderIcon != null) {
            canvas.withTranslation(
                folderIcon.left + folderIcon.translationX,
                folderIcon.top.toFloat()
            ) {
                val previewBackground: PreviewBackground = folderIcon.folderBackground
                previewBackground.drawLeaveBehind(this, folderLeaveBehindColor)
            }
        }
    }

    companion object {
        /** Returns a new instance of [TaskbarPinnedAppIconContainer]. */
        @JvmStatic
        fun create(context: Context, itemMarginLeftRight: Int): TaskbarPinnedAppIconContainer {
            return TaskbarPinnedAppIconContainer(context).apply {
                this.itemMarginLeftRight = itemMarginLeftRight
                // App icon views draw running state indicators outside of the icon view bounds, and
                // thus outside the icons container bounds - don't clip the children so running
                // state indicators remain visible.
                this.clipChildren = false
            }
        }
    }
}
