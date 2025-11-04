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
package com.android.launcher3.taskbar.customization

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity.CENTER_VERTICAL
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.view.contains
import androidx.core.view.isEmpty
import androidx.core.view.setPadding
import com.android.app.tracing.traceSection
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities.dpToPx
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.celllayout.CellInfo
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.ItemInfoWrapper
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarOverflowView
import com.android.launcher3.taskbar.TaskbarPopupController
import com.android.launcher3.taskbar.TaskbarViewCallbacks
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.util.ViewCache
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.PredictedAppIcon
import kotlin.math.min

/** This manages a group of icons within `TaskbarView`. */
class TaskbarIconsContainer
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes), Reorderable, TaskbarContainer {
    private val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)
    // Needs its own cache to avoid crashes from moving icons between containers. LayoutTransition
    // doesn't remove views immediately from this in order to perform the disappear animation.
    // If a cache is shared, when a different container may tries to take this view from the cache,
    // there will be a crash.
    private val viewCache = ViewCache()
    private var itemMarginLeftRight = 0
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = DEFAULT_BOUNCE_SCALE
    private val isRtl = isRtl(resources)

    override val taskbarIconViewSize =
        dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconTouchSize, activityContext)

    override val taskbarIconViewPadding =
        dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconPadding, activityContext)


    val taskbarPinnedOverflowView: TaskbarOverflowView =
        TaskbarOverflowView.inflateIcon(
            TaskbarOverflowView.OverflowType.PINNED,
            this,
            taskbarIconViewSize,
            taskbarIconViewPadding,
        )

    val isOverflowViewShowing: Boolean
        get() = taskbarPinnedOverflowView in this

    private lateinit var taskbarViewCallbacks: TaskbarViewCallbacks

    init {
        orientation = HORIZONTAL
        gravity = CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    fun updateIcons(itemInfos: Array<ItemInfo>) {
        traceSection("TaskbarIconsContainer#updateIcons") {
            updateIconsInternal(itemInfos)
        }
    }

    private fun updateIconsInternal(itemInfos: Array<ItemInfo>) {
        var numViewsAnimated = 0
        val numMaxIcons = activityContext.taskbarSpecsEvaluator.numShownHotseatIcons
        val hotseatLength = itemInfos.size
        val hasOverflow = hotseatLength > numMaxIcons && TaskbarPopupController.canPinAppsOverflow()

        var onTaskbarStartIdx = 0

        // The last index of the pinned items on the taskbar. This does not include the overflow
        // icon and the items inside the overflow icon if the pinned items overflow.
        var onTaskbarEndIdx = min(hotseatLength, numMaxIcons)

        var list = itemInfos.asList().subList(onTaskbarStartIdx, onTaskbarEndIdx)
        if (isRtl) list = list.reversed()
        forEachIcon(list) { index, itemInfo ->
            // Replace any Hotseat views with the appropriate type if it's not already that type.
            var isCollection = false
            val expectedLayoutResId: Int =
                if (itemInfo.isPredictedItem) {
                    R.layout.taskbar_predicted_app_icon
                } else if (itemInfo is CollectionInfo) {
                    isCollection = true
                    if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP)
                        R.layout.app_pair_icon
                    else R.layout.folder_icon
                } else {
                    R.layout.taskbar_app_icon
                }

            val current = index
            var hotseatView: View? = null
            while (current < childCount) {
                hotseatView = getChildAt(current)
                if (
                    (hotseatView?.sourceLayoutResId != expectedLayoutResId) ||
                        (isCollection && (hotseatView.tag !== itemInfo))
                ) {
                    removeAndRecycle(hotseatView)
                    hotseatView = null
                } else {
                    // View found
                    break
                }
            }
            if (hotseatView == null) {
                if (isCollection) {
                    val collectionInfo = itemInfo as CollectionInfo
                    when (itemInfo.itemType) {
                        LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                            hotseatView =
                                FolderIcon.inflateFolderAndIcon(
                                    expectedLayoutResId,
                                    activityContext,
                                    this,
                                    collectionInfo as FolderInfo,
                                )
                            (hotseatView as FolderIcon).folderName.setContainerTextVisibility(false)
                        }

                        LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP -> {
                            hotseatView =
                                AppPairIcon.inflateIcon(
                                    expectedLayoutResId,
                                    activityContext,
                                    this,
                                    collectionInfo as AppPairInfo,
                                    BubbleTextView.DISPLAY_TASKBAR,
                                )
                            (hotseatView as AppPairIcon)
                                .titleTextView
                                .setContainerTextVisibility(false)
                        }

                        else ->
                            throw IllegalStateException(
                                "Unexpected item type: " + itemInfo.itemType
                            )
                    }
                } else {
                    hotseatView = inflate(expectedLayoutResId)
                    (hotseatView as BubbleTextView).setContainerTextVisibility(false)
                }
                val lp = TaskbarIconContainerLayoutParams(taskbarIconViewSize, taskbarIconViewSize)
                if (index != 0) {
                    lp.marginStart = itemMarginLeftRight
                }
                if (index != hotseatLength - 1) {
                    lp.marginEnd = itemMarginLeftRight
                }

                hotseatView.setPadding(taskbarIconViewPadding)
                addView(hotseatView, lp)
            } else if (hotseatView is FolderIcon) {
                hotseatView.onItemsChanged(false)
                hotseatView.folder.reapplyItemInfo()
            }

            (hotseatView.layoutParams as TaskbarIconContainerLayoutParams).bindInfo =
                CellInfo(
                    hotseatView,
                    itemInfo.screenId,
                    itemInfo.container,
                    itemInfo.cellX,
                    itemInfo.cellY,
                    itemInfo.spanX,
                    itemInfo.spanY,
                )

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView is BubbleTextView && itemInfo is WorkspaceItemInfo) {
                if (hotseatView is PredictedAppIcon) {
                    if (
                        hotseatView.applyFromWorkspaceItemWithAnimation(itemInfo, numViewsAnimated)
                    ) {
                        numViewsAnimated++
                    }
                } else {
                    val lp = hotseatView.layoutParams as TaskbarIconContainerLayoutParams
                    if (index != 0) {
                        lp.marginStart = itemMarginLeftRight
                    }
                    if (index != hotseatLength - 1) {
                        lp.marginEnd = itemMarginLeftRight
                    }
                    hotseatView.layoutParams = lp
                    hotseatView.applyFromWorkspaceItem(itemInfo)
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView)
            setHoverListenerForIcon(hotseatView)
        }

        if (hasOverflow) {
            val itemsNotOverflown = numMaxIcons - 1
            onTaskbarStartIdx = if (isRtl) hotseatLength - itemsNotOverflown else 0
            onTaskbarEndIdx = if (isRtl) hotseatLength else itemsNotOverflown

            val overflownStartIndex = if (isRtl) 0 else onTaskbarEndIdx
            val overflownEndIndex = if (isRtl) onTaskbarStartIdx else hotseatLength
            val overflownItems = itemInfos.toList().subList(overflownStartIndex, overflownEndIndex)
            taskbarPinnedOverflowView.setItems(
                overflownItems.map { iteminfo: ItemInfo? ->
                    ItemInfoWrapper(iteminfo, activityContext)
                }
            )
            maybeAddPinOverflowView()
        } else if (isOverflowViewShowing) {
            removeView(taskbarPinnedOverflowView)
            taskbarPinnedOverflowView.clearItems()
        }

        while (childCount > hotseatLength) {
            removeAndRecycle(getChildAt(childCount - 1))
        }
    }

    /** Applies and traces [body] for each [icons] instance. */
    private inline fun forEachIcon(icons: List<ItemInfo>, body: (Int, ItemInfo) -> Unit) {
        for ((index, icon) in icons.withIndex()) {
            traceSection("TaskbarIconsContainer#forEachIcon.icon") { body(index, icon) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setUpCallbacks(callbacks: TaskbarViewCallbacks) {
        taskbarViewCallbacks = callbacks
    }

    private fun removeAndRecycle(view: View) {
        removeView(view)
        view.setOnClickListener(null)
        view.onLongClickListener = null
        if (view.tag !is CollectionInfo) {
            viewCache.recycleView(view.sourceLayoutResId, view)
        }
        view.tag = null
    }

    /** Sets OnClickListener and OnLongClickListener for the given view. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setClickAndLongClickListenersForIcon(icon: View?) {
        icon?.setOnClickListener(taskbarViewCallbacks.iconOnClickListener)
        icon?.onLongClickListener = taskbarViewCallbacks.iconOnLongClickListener
        // Add right-click support to btv icons.
        icon?.setOnTouchListener { v: View, event: MotionEvent ->
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

    private fun setHoverListenerForIcon(icon: View?) {
        icon?.setOnHoverListener(taskbarViewCallbacks.getIconOnHoverListener(icon))
    }

    private fun maybeAddPinOverflowView() {
        if (!TaskbarPopupController.canPinAppsOverflow() || isOverflowViewShowing) {
            return
        }
        // adding overflow view remove last hotseat item
        removeViewAt(childCount - 1)
        val lp = TaskbarIconContainerLayoutParams(taskbarIconViewSize, taskbarIconViewSize)
        lp.marginStart = itemMarginLeftRight
        taskbarPinnedOverflowView.setPadding(taskbarIconViewPadding)
        taskbarPinnedOverflowView.setOnClickListener(
            taskbarViewCallbacks.pinnedOverflowOnClickListener
        )
        taskbarPinnedOverflowView.onLongClickListener =
            taskbarViewCallbacks.pinnedOverflowOnLongClickListener
        setHoverListenerForIcon(taskbarPinnedOverflowView)
        addView(taskbarPinnedOverflowView, lp)
    }

    private fun inflate(@LayoutRes layoutResId: Int): View? {
        return viewCache.getView(layoutResId, activityContext, this)
    }

    class TaskbarIconContainerLayoutParams : LayoutParams {
        var bindInfo: CellInfo? = null

        constructor(width: Int, height: Int) : super(width, height)

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)

        constructor(p: ViewGroup.LayoutParams?) : super(p)
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
        get() =
            if (isEmpty()) 0
            else (childCount * taskbarIconViewSize) + ((childCount - 1) * 2 * itemMarginLeftRight)

    companion object {
        // effectively a no-op since we do not scale this container.
        private const val DEFAULT_BOUNCE_SCALE = 1f

        /** @return a new instance of [TaskbarIconsContainer]. */
        @JvmStatic
        fun create(
            context: Context,
            itemMarginLeftRight: Int,
        ): TaskbarIconsContainer {
            return TaskbarIconsContainer(context).apply {
                this.itemMarginLeftRight = itemMarginLeftRight
                // App icon views draw running state indicators outside of the icon view bounds, and
                // thus outside the icons container bounds - don't clip the children so running
                // state indicators remain visible.
                this.clipChildren = false
            }
        }
    }
}
