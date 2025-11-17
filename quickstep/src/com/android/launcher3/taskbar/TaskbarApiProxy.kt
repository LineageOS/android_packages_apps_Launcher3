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

import android.content.res.Resources
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag
import com.android.launcher3.util.Executors.TASKBAR_UI_THREAD
import com.android.quickstep.NavHandle
import javax.annotation.concurrent.ThreadSafe

/**
 * Expose API of [TaskbarActivityContext] to TaskbarUnstashInputConsumer and BubbleBarInputConsumer
 * to ensure touches for taskbar are handled on taskbar UI thread.
 */
@ThreadSafe
class TaskbarApiProxy(private val delegate: TaskbarActivityContext) {

    val taskbarUiState: TaskbarUiState = delegate.taskbarUiState

    private val transitionCallback = if (isTransient()) delegate.translationCallbacks else null

    // TODO(b/404636836): Avoid exposing NavHandle although we use it to build
    //  NavHandleLongPressInputConsumer
    fun navHandle(): NavHandle = delegate.navHandle

    fun isBubbleBarSwipeGesture() =
        delegate.bubbleControllers?.bubbleBarSwipeController?.orElse(null)?.isSwipeGesture()
            ?: false

    fun isInStashedLauncherState() = delegate.isInStashedLauncherState

    fun isBubbleBarEnabled() = delegate.isBubbleBarEnabled

    fun isPhoneMode() = delegate.isPhoneMode

    fun isInApp() = delegate.isInApp

    fun hasBubbleControllers() = delegate.bubbleControllers != null

    fun isBubbleStashedHandleViewControllerPresent() =
        delegate.bubbleControllers?.bubbleStashedHandleViewController?.isPresent ?: false

    fun openTaskbarAllApps() {
        TASKBAR_UI_THREAD.execute { delegate.openTaskbarAllApps() }
    }

    fun updateStashControllerLauncherStateFlag(isVisible: Boolean) {
        TASKBAR_UI_THREAD.execute { delegate.updateStashControllerLauncherStateFlag(isVisible) }
    }

    fun startBubbleBarSwipeController() {
        TASKBAR_UI_THREAD.execute {
            delegate.bubbleControllers?.bubbleBarSwipeController?.orElse(null)?.start()
        }
    }

    fun finishBubbleBarSwipeController() {
        TASKBAR_UI_THREAD.execute {
            delegate.bubbleControllers?.bubbleBarSwipeController?.orElse(null)?.finish()
        }
    }

    fun showBubbleBar(expandBubbles: Boolean, bubbleBarGesture: Boolean) {
        TASKBAR_UI_THREAD.execute {
            delegate.bubbleControllers
                ?.bubbleStashController
                ?.showBubbleBar(expandBubbles, bubbleBarGesture)
        }
    }

    fun swipeBubbleBarTo(dY: Float, taskToRun: Runnable) {
        TASKBAR_UI_THREAD.execute {
            delegate.bubbleControllers?.bubbleBarSwipeController?.orElse(null)?.let {
                it.swipeTo(dY)
                taskToRun.run()
            }
        }
    }

    fun isTransient(): Boolean = delegate.taskbarFeatureEvaluator.isTransient

    fun shouldAllowTaskbarToAutoStash(): Boolean = delegate.shouldAllowTaskbarToAutoStash()

    /** Called only once during a gesture. Safe to post Runnable to TASKBAR_UI_THREAD. */
    fun playTaskbarBackgroundAlphaAnimation() {
        TASKBAR_UI_THREAD.execute { delegate.playTaskbarBackgroundAlphaAnimation() }
    }

    /**
     * Called on ACTION_DOWN, ACTION_UP and ACTION_CANCEL. Safe to post Runnable to
     * TASKBAR_UI_THREAD.
     */
    fun setAutohideSuspendFlag(@AutohideSuspendFlag flag: Int, newValue: Boolean) {
        TASKBAR_UI_THREAD.execute { delegate.setAutohideSuspendFlag(flag, newValue) }
    }

    fun startTaskbarUnstashHint(isHovered: Boolean) {
        TASKBAR_UI_THREAD.execute { delegate.startTaskbarUnstashHint(isHovered) }
    }

    /** Called once when ACTION_MOVE reach certain threshold. */
    fun onSwipeToUnstashTaskbar(delayTaskbarBackground: Boolean) {
        TASKBAR_UI_THREAD.execute { delegate.onSwipeToUnstashTaskbar(delayTaskbarBackground) }
    }

    /** Called on ACTION_DOWN. */
    fun onTransitionActionDown() {
        if (transitionCallback == null) return
        TASKBAR_UI_THREAD.execute { transitionCallback.onActionDown() }
    }

    /** Called on every ACTION_MOVE. */
    fun onTransitionActionMove(dy: Float) {
        if (transitionCallback == null) return
        TASKBAR_UI_THREAD.execute { transitionCallback.onActionMove(dy) }
    }

    /** Called on ACTION_UP and ACTION_CANCEL */
    fun onTransitionActionEnd() {
        if (transitionCallback == null) return
        TASKBAR_UI_THREAD.execute { transitionCallback.onActionEnd() }
    }

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Should be removed once we turned on [refactorTaskbarUiState()] flag")
    fun isTaskbarAllAppsOpen() = delegate.isTaskbarAllAppsOpen

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Should be removed once we turned on [refactorTaskbarUiState()] flag")
    fun isTaskbarStashed() = delegate.isTaskbarStashed

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Should be removed once we turned on [refactorTaskbarUiState()] flag")
    fun getResources(): Resources = delegate.resources

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Will be removed after launching refactorTaskbarUiState")
    val isBubbleBarExpanded: Boolean =
        delegate.bubbleControllers?.bubbleBarViewController?.isExpanded ?: false

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Will be removed after launching refactorTaskbarUiState")
    val isBubbleBarVisible: Boolean =
        delegate.bubbleControllers?.bubbleStashController?.isBubbleBarVisible() ?: false

    // TODO(b/404636836): Remove after launching refactorTaskbarUiState()
    @Deprecated("Will be removed after launching refactorTaskbarUiState")
    val isBubbleBarStashed: Boolean =
        delegate.bubbleControllers?.bubbleStashController?.isStashed ?: false
}
