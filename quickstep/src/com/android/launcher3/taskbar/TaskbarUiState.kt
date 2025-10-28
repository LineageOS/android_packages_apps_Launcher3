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
import android.view.MotionEvent
import androidx.annotation.Px
import com.android.launcher3.DeviceProfile
import com.android.launcher3.util.ImmutableRect
import com.android.launcher3.util.NavigationMode

/**
 * Data class that represents taskbar's UI states. This state is shared to launcher and recents.
 * Taskbar's UI thread is responsible to update below fields whenever any field is changed.
 *
 * Timings when each field is changed:
 * - [hasBubbles]: when BubbleBarView's child bubble view count is changed between 0 vs non-zero.
 *   Should be reset to false if we don't show bubble bar view and BubbleBarViewController is not
 *   even created.
 * - [shouldShowEduOnAppLaunch]: when DeviceProfile, TaskbarUIController or tooltip steps is changed
 * - [isDraggingItem]: when ether bubble or taskbar is dragging item. Note that this flag only
 *   represents drags originating from the main Taskbar window and does NOT represents drags
 *   originating from all apps using TaskbarOverlayContext.
 * - [isTaskbarStashed]: when [TaskbarStashController.mIsStashed] has changed
 * - [isTaskbarAllAppsOpen]: when [TaskbarAllAppsController.isOpen] has changed
 * - [isTaskbarOnHome]: when [TaskbarStashController.mState] is changed
 * - [showDesktopTaskbarForFreeformDisplay]: when [DisplayInfo] is changed
 * - [showLockedTaskbarOnHome]: when [DisplayInfo] is changed
 * - [isPrimaryDisplay]: when [TaskbarActivityContext] is constructed
 */
class TaskbarUiState {

    @Volatile var hasBubbles = false
    @Volatile var shouldShowEduOnAppLaunch = false
    @Volatile var isDraggingItem = false
    @Volatile var isTaskbarStashed = false
    @Volatile var isTaskbarAllAppsOpen = false
    @Volatile var isTaskbarOnHome = false
    @Volatile var showDesktopTaskbarForFreeformDisplay = false
    @Volatile var showLockedTaskbarOnHome = false
    @Volatile var isPrimaryDisplay = false

    @Volatile var bubbleBarViewVisible = true
    @Volatile var bubbleStashed = false
    @Volatile var bubbleBarExpanded = false
    @Volatile var unstashAreaSizePx: Int = 0
    @Volatile var actionCornerPaddingPx: Int = 0
    @Volatile var taskbarNavThreshold: Int = 0
    @Volatile var taskbarSlowVelocityYThreshold: Int = 0
    @Volatile var taskbarStashedScreenEdgeHoverDeadzoneHeightPx: Int = 0
    @Volatile var taskbarStashedBelowHoverDeadzoneHeightPx: Int = 0

    @Volatile private var _isBubbleDragging = false
    @Volatile private var _isTaskbarDragging = false
    @Volatile private var _stashState = 0L
    @Volatile private var _bubbleBarViewRect = ImmutableRect.EMPTY_RECT
    @Volatile private var _stashedBubbleBarHeightPx = Int.MAX_VALUE
    @Volatile private var _isStashedHandlerViewVisible = true
    @Volatile private var _stashedHandlerViewRect = ImmutableRect.EMPTY_RECT

    @Volatile private var _isTaskbarViewShown = false
    @Volatile private var _taskbarIconsActualBounds = ImmutableRect.EMPTY_RECT
    @Volatile private var _navbarFloatingRotationButtonsBounds = ImmutableRect.EMPTY_RECT

    @Volatile private var _deviceProfile = DeviceProfile.DEFAULT_DEVICE_PROFILE
    @Volatile private var _navigationMode = NavigationMode.THREE_BUTTONS
    @Volatile private var _isTransient = false

    fun setIsBubbleDragging(isBubbleDragging: Boolean) {
        _isBubbleDragging = isBubbleDragging
        isDraggingItem = _isBubbleDragging or _isTaskbarDragging
    }

    fun setIsTaskbarDragging(isTaskbarDragging: Boolean) {
        _isTaskbarDragging = isTaskbarDragging
        isDraggingItem = _isBubbleDragging or _isTaskbarDragging
    }

    fun setStashStateRef(state: Long) {
        _stashState = state
        isTaskbarOnHome =
            (_stashState and TaskbarStashController.FLAG_IN_OVERVIEW.toLong()) == 0L &&
                (_stashState and TaskbarStashController.FLAG_IN_APP.toLong()) == 0L
    }

    fun setIsTransient(isTransient: Boolean) {
        _isTransient = isTransient
    }

    fun setNavigationMode(navigationMode: NavigationMode) {
        _navigationMode = navigationMode
    }

    fun isThreeButtonNav() = _navigationMode == NavigationMode.THREE_BUTTONS

    fun isTransientTaskbar() =
        _isTransient && isPrimaryDisplay && !_deviceProfile.deviceProperties.isPhone

    fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean {
        return isEventOverBubbleBarView(ev) || isEventOverStashedHandler(ev)
    }

    private fun isEventOverBubbleBarView(e: MotionEvent) =
        if (!bubbleBarViewVisible) {
            false
        } else {
            _bubbleBarViewRect.contains(e.x, e.y)
        }

    private fun isEventOverStashedHandler(ev: MotionEvent): Boolean {
        if (!_isStashedHandlerViewVisible) {
            return false
        }
        val top = _deviceProfile.deviceProperties.heightPx - _stashedBubbleBarHeightPx
        val x = ev.rawX
        val y = ev.rawY
        return y >= top && x >= _stashedHandlerViewRect.left && x <= _stashedHandlerViewRect.right
    }

    fun setBubbleBarRect(rect: Rect) {
        _bubbleBarViewRect = ImmutableRect.from(rect)
    }

    fun setIsStashedHandlerViewVisible(isVisible: Boolean) {
        _isStashedHandlerViewVisible = isVisible
    }

    fun setStashedBubbleBarHeightPx(@Px height: Int) {
        _stashedBubbleBarHeightPx = height
    }

    fun setStashedHandlerViewRect(rect: Rect) {
        _stashedHandlerViewRect = ImmutableRect.from(rect)
    }

    fun isEventOverAnyTaskbarItem(ev: MotionEvent) = isEventOnTaskbarView(ev) || isEventOnNavbar(ev)

    private fun isEventOnTaskbarView(ev: MotionEvent) =
        _isTaskbarViewShown and _taskbarIconsActualBounds.contains(ev.rawX, ev.rawY)

    fun setTaskbarViewIsShown(isShown: Boolean) {
        _isTaskbarViewShown = isShown
    }

    fun setTaskbarIconsActualBounds(rect: Rect) {
        _taskbarIconsActualBounds = ImmutableRect.from(rect)
    }

    private fun isEventOnNavbar(ev: MotionEvent) =
        _navbarFloatingRotationButtonsBounds.contains(ev.x, ev.y)

    fun setNavbarFloatingRotationButtonsBounds(rect: Rect) {
        _navbarFloatingRotationButtonsBounds = ImmutableRect.from(rect)
    }

    fun setDeviceProfile(dp: DeviceProfile) {
        _deviceProfile = dp
    }

    fun getDeviceProfile(): DeviceProfile = _deviceProfile
}
