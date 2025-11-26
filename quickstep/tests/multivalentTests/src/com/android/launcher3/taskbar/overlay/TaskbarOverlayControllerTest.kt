/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.overlay

import android.app.ActivityManager.RunningTaskInfo
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_OPTIONS_POPUP
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY
import com.android.launcher3.AbstractFloatingView.hasOpenView
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class TaskbarOverlayControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)
    @InjectController lateinit var overlayController: TaskbarOverlayController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    @Test
    fun testRequestWindow_twice_reusesWindow() {
        val (context1, context2) =
            getOnTaskbarUiThread {
                Pair(overlayController.requestWindow(), overlayController.requestWindow())
            }
        assertThat(context1).isSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_afterHidingExistingWindow_createsNewWindow() {
        val context1 = getOnTaskbarUiThread { overlayController.requestWindow() }
        runOnTaskbarUiThreadSync { overlayController.hideWindow() }

        val context2 = getOnTaskbarUiThread { overlayController.requestWindow() }
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_afterHidingOverlay_createsNewWindow() {
        val context1 = getOnTaskbarUiThread { overlayController.requestWindow() }
        runOnTaskbarUiThreadSync {
            TestOverlayView.show(context1)
            overlayController.hideWindow()
        }

        val context2 = getOnTaskbarUiThread { overlayController.requestWindow() }
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_addsProxyView() {
        runOnTaskbarUiThreadSync { TestOverlayView.show(overlayController.requestWindow()) }
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    fun testRequestWindow_closeProxyView_closesOverlay() {
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(overlayController.requestWindow())
        }
        runOnTaskbarUiThreadSync {
            AbstractFloatingView.closeOpenContainer(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)
        }
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testRequestWindow_attachesDragLayer() {
        val dragLayer = getOnTaskbarUiThread { overlayController.requestWindow().dragLayer }
        // Allow drag layer to attach before checking.
        runOnTaskbarUiThreadSync { assertThat(dragLayer.isAttachedToWindow).isTrue() }
    }

    @Test
    fun testHideWindow_closesOverlay() {
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(overlayController.requestWindow())
        }
        runOnTaskbarUiThreadSync { overlayController.hideWindow() }
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testHideWindow_detachesDragLayer() {
        val dragLayer = getOnTaskbarUiThread { overlayController.requestWindow().dragLayer }

        // Wait for drag layer to be attached to window before hiding.
        runOnTaskbarUiThreadSync {
            overlayController.hideWindow()
            assertThat(dragLayer.isAttachedToWindow).isFalse()
        }
    }

    @Test
    fun testTwoOverlays_closeOne_windowStaysOpen() {
        val (overlay1, overlay2) =
            getOnTaskbarUiThread {
                val context = overlayController.requestWindow()
                Pair(TestOverlayView.show(context), TestOverlayView.show(context))
            }

        runOnTaskbarUiThreadSync { overlay1.close(false) }
        assertThat(overlay2.isOpen).isTrue()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    fun testTwoOverlays_closeAll_closesWindow() {
        val (overlay1, overlay2) =
            getOnTaskbarUiThread {
                val context = overlayController.requestWindow()
                Pair(TestOverlayView.show(context), TestOverlayView.show(context))
            }

        runOnTaskbarUiThreadSync {
            overlay1.close(false)
            overlay2.close(false)
        }
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    fun testRecreateTaskbar_closesWindow() {
        runOnTaskbarUiThreadSync { TestOverlayView.show(overlayController.requestWindow()) }
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    fun testTaskMovedToFront_closesOverlay() {
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(overlayController.requestWindow())
        }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskMovedToFront(RunningTaskInfo())

        runOnMainSync {
            // Make sure TaskStackChangeListeners' Handler posts the callback before checking state.
            runOnTaskbarUiThreadSync { assertThat(overlay.isOpen).isFalse() }
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskMovedToFront_stashesBubbleBar() {
        val bubbleBarControllers = taskbarContext.controllers.bubbleControllers.get()
        val bubbleStashController = bubbleBarControllers.bubbleStashController
        runOnTaskbarUiThreadSync {
            bubbleBarControllers.bubbleBarViewController.setHiddenForBubbles(false)
            assertThat(bubbleStashController.isStashed).isFalse()
            TestOverlayView.show(overlayController.requestWindow())
        }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskMovedToFront(RunningTaskInfo())

        runOnMainSync {
            runOnTaskbarUiThreadSync {
                // Make sure TaskStackChangeListeners' Handler posts the callback before checking
                // state.
                assertThat(bubbleStashController.isStashed).isTrue()
            }
        }
    }

    @Test
    fun testTaskMovedToFront_requestedToShowBubble_doesNotStashBubbleBar() {
        val bubbleBarControllers = taskbarContext.controllers.bubbleControllers.get()
        val bubbleStashController = bubbleBarControllers.bubbleStashController
        runOnTaskbarUiThreadSync {
            bubbleBarControllers.bubbleBarViewController.setHiddenForBubbles(false)
        }
        assertThat(bubbleStashController.isStashed).isFalse()

        runOnTaskbarUiThreadSync { TestOverlayView.show(overlayController.requestWindow()) }
        BubbleActivityStarter.INSTANCE.get(taskbarContext)
            .showAppBubble(null, mock(), EntryPoint.TASKBAR_ICON_MENU)
        TaskStackChangeListeners.getInstance().listenerImpl.onTaskMovedToFront(RunningTaskInfo())

        // Make sure TaskStackChangeListeners' Handler posts the callback before checking state.
        runOnTaskbarUiThreadSync { assertThat(bubbleStashController.isStashed).isFalse() }
    }

    @Test
    fun testTaskStackChanged_allAppsClosed_overlayStaysOpen() {
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(overlayController.requestWindow())
        }
        runOnTaskbarUiThreadSync { taskbarContext.controllers.sharedState?.allAppsVisible = false }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()
        runOnTaskbarUiThreadSync { assertThat(overlay.isOpen).isTrue() }
    }

    @Test
    fun testTaskStackChanged_allAppsOpen_closesOverlay() {
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(overlayController.requestWindow())
        }
        runOnTaskbarUiThreadSync { taskbarContext.controllers.sharedState?.allAppsVisible = true }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()

        runOnMainSync { runOnTaskbarUiThreadSync { assertThat(overlay.isOpen).isFalse() } }
    }

    @Test
    fun testUpdateLauncherDeviceProfile_overlayNotRebindSafe_closesOverlay() {
        val context = getOnTaskbarUiThread { overlayController.requestWindow() }
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(context).apply { type = TYPE_OPTIONS_POPUP }
        }

        runOnTaskbarUiThreadSync {
            overlayController.updateLauncherDeviceProfile(
                overlayController.launcherDeviceProfile.toBuilder().setGestureMode(false).build()
            )
        }

        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testUpdateLauncherDeviceProfile_overlayRebindSafe_overlayStaysOpen() {
        val context = getOnTaskbarUiThread { overlayController.requestWindow() }
        val overlay = getOnTaskbarUiThread {
            TestOverlayView.show(context).apply { type = TYPE_TASKBAR_ALL_APPS }
        }

        runOnTaskbarUiThreadSync {
            overlayController.updateLauncherDeviceProfile(
                overlayController.launcherDeviceProfile.toBuilder().setGestureMode(false).build()
            )
        }

        assertThat(overlay.isOpen).isTrue()
    }

    private class TestOverlayView
    private constructor(private val overlayContext: TaskbarOverlayContext) :
        AbstractFloatingView(overlayContext, null) {

        var type = TYPE_OPTIONS_POPUP

        private fun show() {
            mIsOpen = true
            overlayContext.dragLayer.addView(this)
        }

        override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

        override fun handleClose(animate: Boolean) = overlayContext.dragLayer.removeView(this)

        override fun isOfType(type: Int): Boolean = (type and this.type) != 0

        companion object {
            /** Adds a generic View to the Overlay window for testing. */
            fun show(context: TaskbarOverlayContext): TestOverlayView {
                return TestOverlayView(context).apply { show() }
            }
        }
    }
}
