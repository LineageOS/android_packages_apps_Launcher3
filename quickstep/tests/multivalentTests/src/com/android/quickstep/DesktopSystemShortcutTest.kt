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

package com.android.quickstep

import android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.R
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.enableRefactorTaskContentView
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.views.IconAppChipView
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY
import com.android.window.flags.Flags.FLAG_ENABLE_DREAM_ACTIVITY_WINDOWING_EXCLUSION
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.google.common.truth.Truth.assertThat
import java.util.ArrayList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [DesktopSystemShortcut] */
@RunWith(AndroidJUnit4::class)
class DesktopSystemShortcutTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    private val launcher: RecentsViewContainer = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val statsLogger: StatsLogManager.StatsLogger = mock()
    private val recentsView: LauncherRecentsView = mock()
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper = mock()
    private val overlayFactory: TaskOverlayFactory = mock()
    private val desktopState = FakeDesktopState()
    private val factory: TaskShortcutFactory =
        DesktopSystemShortcut.createFactory(
            abstractFloatingViewHelper,
            desktopStateFactory = { desktopState },
        )
    private val context: Context = spy(InstrumentationRegistry.getInstrumentation().targetContext)
    private val taskView: TaskView = createTaskViewMock()

    @Before
    fun setUp() {
        desktopState.canEnterDesktopMode = true
        whenever(overlayFactory.createOverlay(any())).thenReturn(mock<TaskOverlay<*>>())
        doReturn(DEFAULT_DISPLAY).whenever(context).displayId
        whenever(launcher.asContext()).thenReturn(context)
    }

    @Test
    fun createDesktopTaskShortcutFactory_desktopModeDisabled() {
        desktopState.canEnterDesktopMode = false

        val taskContainer = createTaskContainer(createTask())

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun createDesktopTaskShortcutFactory_noDisplayActivity() {
        val baseComponent = ComponentName("", /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                context.userId,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ true,
                /* isActivityStackTransparent */ false,
                /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun createDesktopTaskShortcutFactory_transparentTask() {
        val baseComponent = ComponentName("", /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                context.userId,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ true,
                /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun createDesktopTaskShortcutFactory_systemUiTask() {
        val sysUiPackageName: String = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(sysUiPackageName, /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                context.userId,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
                /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    @SkipOnDeviceless
    fun createDesktopTaskShortcutFactory_defaultHomeTask() {
        val homeActivity = context.packageManager.getHomeActivities(ArrayList())
        val homeActivities = ComponentName(homeActivity?.packageName.toString(), /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                homeActivities,
                context.userId,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                homeActivities,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
                /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey).apply { isDockable = true })
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        FLAG_ENABLE_DREAM_ACTIVITY_WINDOWING_EXCLUSION,
    )
    fun createDesktopTaskShortcutFactory_dreamActivity() {
        val baseComponent = ComponentName("", /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                context.userId,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
                /* topActivityType */ ACTIVITY_TYPE_DREAM,
                /* isTopActivityTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun createDesktopTaskShortcutFactory_undockable() {
        val unDockableTask = createTask().apply { isDockable = false }
        val taskContainer = createTaskContainer(unDockableTask)

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun desktopSystemShortcutClickedWithoutDesktopModeOnDisplay() {
        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))

        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(launcher.statsLogManager).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(taskView.context).thenReturn(context)
        whenever(recentsView.moveTaskToDesktop(any(), any(), any())).thenAnswer {
            val successCallback = it.getArgument<Runnable>(2)
            successCallback.run()
        }
        val taskViewItemInfo = mock<TaskViewItemInfo>()
        doReturn(taskViewItemInfo).whenever(taskContainer).itemInfo

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun desktopSystemShortcutClickedWithDesktopModeOnDisplay() {
        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))

        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(launcher.statsLogManager).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(taskView.context).thenReturn(context)
        whenever(recentsView.moveTaskToDesktop(any(), any(), any())).thenAnswer {
            val successCallback = it.getArgument<Runnable>(2)
            successCallback.run()
        }
        val taskViewItemInfo = mock<TaskViewItemInfo>()
        doReturn(taskViewItemInfo).whenever(taskContainer).itemInfo

        val singleShortcut = factory.getShortcuts(launcher, taskContainer)!!.single()
        assertThat(singleShortcut).isInstanceOf(DesktopSystemShortcut::class.java)

        singleShortcut.onClick(taskView)

        val allTypesExceptRebindSafe =
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        verify(abstractFloatingViewHelper).closeOpenViews(launcher, true, allTypesExceptRebindSafe)
        verify(recentsView)
            .moveTaskToDesktop(
                eq(taskContainer),
                eq(DesktopModeTransitionSource.OVERVIEW_TASK_MENU),
                any(),
            )
        verify(statsLogger).withItemInfo(taskViewItemInfo)
        verify(statsLogger).log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
    }

    private fun createTask(displayId: Int = DEFAULT_DISPLAY) =
        Task(
                TaskKey(
                    /* id */ 1,
                    /* windowingMode */ 0,
                    Intent(),
                    ComponentName("", ""),
                    context.userId,
                    /* lastActiveTime */ 2000,
                    displayId,
                    ComponentName("", ""),
                    /* numActivities */ 1,
                    /* isTopActivityNoDisplay */ false,
                    /* isActivityStackTransparent */ false,
                    /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                    /* isTopActivityTransparent */ false,
                )
            )
            .apply { isDockable = true }

    private fun createTaskContainer(task: Task) =
        TaskContainer(
            taskView,
            task,
            when {
                enableRefactorTaskContentView() -> mock<TaskContentView>()
                else -> mock<TaskThumbnailView>()
            },
            mock<TaskThumbnailView>(),
            mock<IconAppChipView>(),
            mock<TransformingTouchDelegate>(),
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            overlayFactory,
        )

    private fun createTaskViewMock(): TaskView {
        val taskView: TaskView = mock()
        whenever(taskView.type).thenReturn(TaskViewType.SINGLE)
        whenever(taskView.context).thenReturn(context)
        return taskView
    }

    private companion object {
        const val SECONDARY_DISPLAY = 13
    }
}
