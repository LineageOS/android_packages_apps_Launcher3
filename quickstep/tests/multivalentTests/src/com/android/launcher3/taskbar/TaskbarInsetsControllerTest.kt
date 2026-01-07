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

import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.DEFAULT_TOUCH_REGION
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.FULLSCREEN_TASKBAR_WINDOW
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.ICONS_INVISIBLE
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarInsetsControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarInsetsController: TaskbarInsetsController
    @InjectController lateinit var taskbarStashController: TaskbarStashController

    private val taskbarContext by taskbarUnitTestRule::activityContext

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_taskbarWindowUntouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(ICONS_INVISIBLE)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_transientTaskbarUnstashed_taskbarWindowTouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, true)
            taskbarStashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(DEFAULT_TOUCH_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isFalse()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_transientTaskbarStashed_taskbarWindowUntouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, true)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(ICONS_INVISIBLE)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun windowFullscreen_entireTaskbarWindowTouchable() {
        runOnTaskbarUiThreadSync { taskbarContext.setTaskbarWindowFullscreen(true, 1) }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }

    @Test
    fun windowFullscreen_imeShowing_entireTaskbarWindowTouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.setTaskbarWindowFullscreen(true, 1)
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }
}
