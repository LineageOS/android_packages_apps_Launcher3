/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.launcher3.taskbar

import android.app.ActivityManager.RunningTaskInfo
import android.window.RemoteTransition
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.RecentsActivity
import com.android.quickstep.fallback.RecentsState
import com.android.quickstep.split.SplitSelectStateController
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FallbackTaskbarUIControllerTest : TaskbarBaseTestCase() {

    lateinit var fallbackTaskbarUIController: FallbackTaskbarUIController<RecentsActivity>
    lateinit var stateListener: StateManager.StateListener<RecentsState>

    private val recentsActivity: RecentsActivity = mock()
    private val stateManager: StateManager<RecentsState, RecentsActivity> = mock()
    private val splitSelectStateController: SplitSelectStateController = mock()
    private val remoteTransition: RemoteTransition = mock()

    @Before
    override fun setup() {
        super.setup()
        whenever(recentsActivity.stateManager).thenReturn(stateManager)
        fallbackTaskbarUIController = FallbackTaskbarUIController(recentsActivity)
        whenever(recentsActivity.getSplitSelectStateController())
            .thenReturn(splitSelectStateController)

        // Capture registered state listener to send events to in our tests
        val captor = argumentCaptor<StateManager.StateListener<RecentsState>>()
        fallbackTaskbarUIController.init(taskbarControllers)
        verify(stateManager).addStateListener(captor.capture())
        stateListener = captor.lastValue
    }

    @Test
    fun stateTransitionComplete_stateDefault() {
        stateListener.onStateTransitionComplete(RecentsState.DEFAULT)
        // verify dragging disabled
        verify(taskbarDragController, times(1)).setDisallowGlobalDrag(true)
        verify(taskbarAllAppsController, times(1)).setDisallowGlobalDrag(true)
        // verify long click enabled
        verify(taskbarDragController, times(1)).setDisallowLongClick(false)
        verify(taskbarAllAppsController, times(1)).setDisallowLongClick(false)
        // verify split selection enabled
        verify(taskbarPopupController, times(1)).setAllowInitialSplitSelection(true)
    }

    @Test
    fun stateTransitionComplete_stateSplitSelect() {
        stateListener.onStateTransitionComplete(RecentsState.OVERVIEW_SPLIT_SELECT)
        // verify dragging disabled
        verify(taskbarDragController, times(1)).setDisallowGlobalDrag(false)
        verify(taskbarAllAppsController, times(1)).setDisallowGlobalDrag(false)
        // verify long click enabled
        verify(taskbarDragController, times(1)).setDisallowLongClick(true)
        verify(taskbarAllAppsController, times(1)).setDisallowLongClick(true)
        // verify split selection enabled
        verify(taskbarPopupController, times(1)).setAllowInitialSplitSelection(false)
    }

    @Test
    fun launchSplitTasks_delegatesToSplitSelectStateController() {
        val splitTask = createSplitTask(1, 2)

        fallbackTaskbarUIController.launchSplitTasks(splitTask, remoteTransition)

        verify(splitSelectStateController)
            .launchExistingSplitPair(
                anyOrNull(), // launchingTaskView
                eq(1), // taskId1
                eq(2), // taskId2
                eq(SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT),
                any(), // callback
                eq(false), // freezeTaskList
                eq(SNAP_TO_2_50_50), // snapPosition
                eq(remoteTransition),
            )
    }

    @Test
    fun launchSplitTasks_noSplitSelectController_doesNotLaunch() {
        whenever(recentsActivity.getSplitSelectStateController()).thenReturn(null)
        val splitTask = createSplitTask(1, 2)

        fallbackTaskbarUIController.launchSplitTasks(splitTask, remoteTransition)

        verify(splitSelectStateController, never())
            .launchExistingSplitPair(
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            )
    }

    private fun createSplitTask(taskId1: Int, taskId2: Int): SplitTask {
        val taskInfo1 = RunningTaskInfo().apply { taskId = taskId1 }
        val taskInfo2 = RunningTaskInfo().apply { taskId = taskId2 }
        // Use the from(TaskInfo) method
        val task1 = Task.from(taskInfo1)
        val task2 = Task.from(taskInfo2)
        return SplitTask(task1, task2, null)
    }
}
