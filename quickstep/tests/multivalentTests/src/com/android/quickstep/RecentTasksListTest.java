/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.Flags.FLAG_ENABLE_LATER_IS_LOCKED_CHECK;
import static com.android.launcher3.Flags.FLAG_HIDE_AUTOMATED_TASKS_IN_OVERVIEW;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.KeyguardManager;
import android.app.TaskInfo;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.automation.AutomationRepository;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.shared.split.SplitBounds;
import com.android.wm.shell.shared.split.SplitScreenConstants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecentTasksListTest {

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mContext;
    @Mock
    private SystemUiProxy mSystemUiProxy;
    @Mock
    private TopTaskTracker mTopTaskTracker;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private VirtualDeviceManager mVirtualDeviceManager;
    @Mock
    private AutomationRepository mAutomationRepository;

    // Class under test
    private RecentTasksList mRecentTasksList;

    @Before
    public void setup() {
        doReturn(mVirtualDeviceManager).when(mContext).getSystemService(VirtualDeviceManager.class);
        doReturn(mKeyguardManager).when(mContext).getSystemService(KeyguardManager.class);
        when(mVirtualDeviceManager.getDeviceIdForDisplayId(anyInt()))
                .thenReturn(Context.DEVICE_ID_DEFAULT);

        FakeDesktopState desktopState = new FakeDesktopState();
        desktopState.setCanEnterDesktopMode(true);

        mRecentTasksList = new RecentTasksList(mContext, MAIN_EXECUTOR,
                mSystemUiProxy, mTopTaskTracker, mock(DaggerSingletonTracker.class),
                mAutomationRepository, UI_HELPER_EXECUTOR, desktopState);
    }

    @Test
    public void onRecentTasksChanged_doesNotFetchTasks() throws Exception {
        mRecentTasksList.onRecentTasksChanged();
        verify(mSystemUiProxy, times(0))
                .getRecentTasks(anyInt(), anyInt());
    }

    @Test
    public void loadTasksInBackground_onlyKeys_noValidTaskDescription() throws Exception  {
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forSplitTasks(
                createRecentTaskInfo(/* taskId = */ 1), createRecentTaskInfo(/* taskId = */ 2),
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 1,
                        /* rightBottomTaskId = */ 2,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                true);

        assertEquals(1, taskList.size());
        taskList.get(0).getTasks().forEach(t -> assertNull(t.taskDescription.getLabel()));
    }

    @Test
    public void loadTasksInBackground_VdmDisplay() throws Exception  {
        int virtualDeviceDisplayId = 10;
        int nonVirtualDeviceDisplayId = 11;
        int virtualDeviceId = 42;
        when(mVirtualDeviceManager.getDeviceIdForDisplayId(virtualDeviceDisplayId))
                .thenReturn(virtualDeviceId);
        when(mVirtualDeviceManager.getDeviceIdForDisplayId(nonVirtualDeviceDisplayId))
                .thenReturn(Context.DEVICE_ID_DEFAULT);

        GroupedTaskInfo virtualDeviceDisplayTaskInfo = GroupedTaskInfo.forFullscreenTasks(
                createRecentTaskInfo(/* taskId= */ 1, /* displayId= */ virtualDeviceDisplayId));
        GroupedTaskInfo nonVirtualDeviceDisplayTaskInfo = GroupedTaskInfo.forFullscreenTasks(
                createRecentTaskInfo(/* taskId= */ 2, /* displayId= */ nonVirtualDeviceDisplayId));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt())).thenReturn(
                new ArrayList<>(List.of(virtualDeviceDisplayTaskInfo,
                        nonVirtualDeviceDisplayTaskInfo)));
        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertThat(taskList).hasSize(2);
        assertThat(taskList.get(0).taskViewType).isEqualTo(TaskViewType.SINGLE);
        assertThat(taskList.get(1).taskViewType).isEqualTo(TaskViewType.SINGLE);

        List<Task> virtualDeviceTasks = taskList.get(1).getTasks();
        assertThat(virtualDeviceTasks).hasSize(1);
        assertThat(virtualDeviceTasks.get(0).key.displayId).isEqualTo(DEFAULT_DISPLAY);

        List<Task> nonVirtualDeviceTasks = taskList.get(0).getTasks();
        assertThat(nonVirtualDeviceTasks).hasSize(1);
        assertThat(nonVirtualDeviceTasks.get(0).key.displayId).isEqualTo(nonVirtualDeviceDisplayId);

        // The displayIds are cached and there are no more calls to VDM.
        mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1, false);
        verify(mVirtualDeviceManager, times(1))
                .getDeviceIdForDisplayId(virtualDeviceDisplayId);
        verify(mVirtualDeviceManager, times(1))
                .getDeviceIdForDisplayId(nonVirtualDeviceDisplayId);
    }

    @Test
    public void loadTasksInBackground_GetRecentTasksException() throws Exception  {
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenThrow(new SystemUiProxy.GetRecentTasksException("task load failed"));

        RecentTasksList.TaskLoadResult taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE, -1, false);

        assertThat(taskList.getRequestId()).isEqualTo(-1);
        assertThat(taskList).isEmpty();
    }

    @Test
    public void loadTasksInBackground_freeformTask_multiDesksInMultiDisplays() throws Exception {
        List<TaskInfo> tasksInDefaultDesk1 = Arrays.asList(
                createRecentTaskInfo(/* taskId = */ 1, DEFAULT_DISPLAY),
                createRecentTaskInfo(/* taskId = */ 4, DEFAULT_DISPLAY));
        List<TaskInfo> tasksInDefaultDesk2 = Arrays.asList(
                createRecentTaskInfo(/* taskId = */ 2, DEFAULT_DISPLAY),
                createRecentTaskInfo(/* taskId = */ 3, DEFAULT_DISPLAY));
        List<TaskInfo> tasksInExtend = Arrays.asList(
                createRecentTaskInfo(/* taskId = */ 5, /* displayId = */ 1),
                createRecentTaskInfo(/* taskId = */ 6, /* displayId = */ 1));
        GroupedTaskInfo recentTaskInfosOfDesk1 = GroupedTaskInfo.forDeskTasks(/* deskId = */1,
                DEFAULT_DISPLAY, tasksInDefaultDesk1, /* minimizedTaskIds = */
                Collections.emptySet());
        GroupedTaskInfo recentTaskInfosOfDesk2 = GroupedTaskInfo.forDeskTasks(/* deskId = */2,
                DEFAULT_DISPLAY, tasksInDefaultDesk2, /* minimizedTaskIds = */
                Collections.emptySet());
        GroupedTaskInfo recentTaskInfosOfDesk3 = GroupedTaskInfo.forDeskTasks(/* deskId = */3,
                /* displayId = */ 1, tasksInExtend, /* minimizedTaskIds = */
                Collections.emptySet());
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt())).thenReturn(
                new ArrayList<>(Arrays.asList(recentTaskInfosOfDesk1, recentTaskInfosOfDesk2,
                        recentTaskInfosOfDesk3)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertThat(taskList).hasSize(3);
        assertThat(taskList.get(2).taskViewType).isEqualTo(TaskViewType.DESKTOP);
        List<Task> actualFreeformTasksInDesk1 = taskList.get(2).getTasks();
        assertThat(actualFreeformTasksInDesk1).hasSize(2);
        assertThat(actualFreeformTasksInDesk1.get(0).key.id).isEqualTo(1);
        assertThat(actualFreeformTasksInDesk1.get(0).isMinimized).isFalse();
        assertThat(actualFreeformTasksInDesk1.get(1).key.id).isEqualTo(4);
        assertThat(actualFreeformTasksInDesk1.get(1).isMinimized).isFalse();
        assertThat(((DesktopTask) taskList.get(2)).getDeskId()).isEqualTo(1);
        assertThat(taskList.get(2).getDisplayId()).isEqualTo(DEFAULT_DISPLAY);

        assertThat(taskList.get(1).taskViewType).isEqualTo(TaskViewType.DESKTOP);
        List<Task> actualFreeformTasksInDesk2 = taskList.get(1).getTasks();
        assertThat(actualFreeformTasksInDesk2).hasSize(2);
        assertThat(actualFreeformTasksInDesk2.get(0).key.id).isEqualTo(2);
        assertThat(actualFreeformTasksInDesk2.get(0).isMinimized).isFalse();
        assertThat(actualFreeformTasksInDesk2.get(1).key.id).isEqualTo(3);
        assertThat(actualFreeformTasksInDesk2.get(1).isMinimized).isFalse();
        assertThat(((DesktopTask) taskList.get(1)).getDeskId()).isEqualTo(2);
        assertThat(taskList.get(1).getDisplayId()).isEqualTo(DEFAULT_DISPLAY);

        assertThat(taskList.get(0).taskViewType).isEqualTo(TaskViewType.DESKTOP);
        List<Task> actualFreeformTasksInDesk3 = taskList.get(0).getTasks();
        assertThat(actualFreeformTasksInDesk3).hasSize(2);
        assertThat(actualFreeformTasksInDesk3.get(0).key.id).isEqualTo(5);
        assertThat(actualFreeformTasksInDesk3.get(0).isMinimized).isFalse();
        assertThat(actualFreeformTasksInDesk3.get(1).key.id).isEqualTo(6);
        assertThat(actualFreeformTasksInDesk3.get(1).isMinimized).isFalse();
        assertThat(((DesktopTask) taskList.get(0)).getDeskId()).isEqualTo(3);
        assertThat(taskList.get(0).getDisplayId()).isEqualTo(1);
    }

    @Test
    public void loadTasksInBackground_moreThanKeys_hasValidTaskDescription() throws Exception  {
        String taskDescription = "Wheeee!";
        RecentTaskInfo task1 = createRecentTaskInfo(/* taskId = */ 1);
        task1.taskDescription = new ActivityManager.TaskDescription(taskDescription);
        RecentTaskInfo task2 = createRecentTaskInfo(/* taskId = */ 2);
        task2.taskDescription = new ActivityManager.TaskDescription();
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forSplitTasks(task1, task2,
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 1,
                        /* rightBottomTaskId = */ 2,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertEquals(1, taskList.size());
        var tasks = taskList.get(0).getTasks();
        assertEquals(2, tasks.size());
        assertEquals(taskDescription, tasks.get(0).taskDescription.getLabel());
        assertNull(tasks.get(1).taskDescription.getLabel());
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LATER_IS_LOCKED_CHECK)
    public void loadTasksInBackground_moreThanKeys_doesNotCallIsDeviceLocked() throws Exception {
        GroupedTaskInfo recentTaskInfos =
                GroupedTaskInfo.forFullscreenTasks(createRecentTaskInfo(1, DEFAULT_DISPLAY));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1, false);

        verify(mKeyguardManager, times(0)).isDeviceLocked();
    }

    @Test
    public void loadTasksInBackground_freeformTask_onlyMinimizedTasks_createDesktopTask()
            throws Exception {
        List<TaskInfo> tasks = Arrays.asList(
                createRecentTaskInfo(1 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(4 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(5 /* taskId */, DEFAULT_DISPLAY));
        Set<Integer> minimizedTaskIds =
                Arrays.stream(new Integer[]{1, 4, 5}).collect(Collectors.toSet());
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forDeskTasks(
                0 /* deskId */, DEFAULT_DISPLAY, tasks, minimizedTaskIds);
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(1, taskList.size());
        assertEquals(TaskViewType.DESKTOP, taskList.get(0).taskViewType);
        List<Task> actualFreeformTasks = taskList.get(0).getTasks();
        assertEquals(3, actualFreeformTasks.size());
        assertEquals(1, actualFreeformTasks.get(0).key.id);
        assertTrue(actualFreeformTasks.get(0).isMinimized);
        assertEquals(4, actualFreeformTasks.get(1).key.id);
        assertTrue(actualFreeformTasks.get(1).isMinimized);
        assertEquals(5, actualFreeformTasks.get(2).key.id);
        assertTrue(actualFreeformTasks.get(2).isMinimized);
    }

    @Test
    @EnableFlags(FLAG_HIDE_AUTOMATED_TASKS_IN_OVERVIEW)
    public void loadTasksInBackground_desktopTask_filterOutAutomatedTasks() throws Exception {
        List<TaskInfo> tasksInDefaultDesk1 = Arrays.asList(
                createRecentTaskInfo(/* taskId = */ 1, DEFAULT_DISPLAY, /* isAutomated= */false),
                createRecentTaskInfo(/* taskId = */ 2, DEFAULT_DISPLAY, /* isAutomated= */true));
        GroupedTaskInfo recentTaskInfo1 = GroupedTaskInfo.forDeskTasks(/* deskId = */1,
                DEFAULT_DISPLAY, tasksInDefaultDesk1, /* minimizedTaskIds = */
                Collections.emptySet());
        List<TaskInfo> tasksInDefaultDesk2 = Arrays.asList(
                createRecentTaskInfo(/* taskId = */ 3, DEFAULT_DISPLAY, /* isAutomated= */true),
                createRecentTaskInfo(/* taskId = */ 4, DEFAULT_DISPLAY, /* isAutomated= */true));
        GroupedTaskInfo recentTaskInfo2 = GroupedTaskInfo.forDeskTasks(/* deskId = */2,
                DEFAULT_DISPLAY, tasksInDefaultDesk2, /* minimizedTaskIds = */
                Collections.emptySet());

        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt())).thenReturn(
                new ArrayList<>(Arrays.asList(recentTaskInfo1, recentTaskInfo2)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertThat(taskList).hasSize(2);

        GroupTask groupTask1 = taskList.get(1);
        assertThat(groupTask1.taskViewType).isEqualTo(TaskViewType.DESKTOP);
        List<Task> tasks1 = groupTask1.getTasks();
        assertThat(tasks1).hasSize(1);
        assertThat(tasks1.get(0).key.id).isEqualTo(1);

        GroupTask groupTask2 = taskList.get(0);
        assertThat(groupTask2.taskViewType).isEqualTo(TaskViewType.DESKTOP);
        assertThat(groupTask2.getTasks()).isEmpty();
    }


    @Test
    @EnableFlags(FLAG_HIDE_AUTOMATED_TASKS_IN_OVERVIEW)
    public void loadTasksInBackground_splitTask_filterOutAutomatedTasks() throws Exception {
        GroupedTaskInfo recentTaskInfo1 = GroupedTaskInfo.forSplitTasks(
                createRecentTaskInfo(/* taskId = */ 1, DEFAULT_DISPLAY, /* isAutomated= */false),
                createRecentTaskInfo(/* taskId = */ 2, DEFAULT_DISPLAY, /* isAutomated= */false),
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 1,
                        /* rightBottomTaskId = */ 2,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        GroupedTaskInfo recentTaskInfo2 = GroupedTaskInfo.forSplitTasks(
                createRecentTaskInfo(/* taskId = */ 3, DEFAULT_DISPLAY, /* isAutomated= */false),
                createRecentTaskInfo(/* taskId = */ 4, DEFAULT_DISPLAY, /* isAutomated= */true),
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 3,
                        /* rightBottomTaskId = */ 4,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        GroupedTaskInfo recentTaskInfo3 = GroupedTaskInfo.forSplitTasks(
                createRecentTaskInfo(/* taskId = */ 5, DEFAULT_DISPLAY, /* isAutomated= */true),
                createRecentTaskInfo(/* taskId = */ 6, DEFAULT_DISPLAY, /* isAutomated= */false),
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 5,
                        /* rightBottomTaskId = */ 6,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        GroupedTaskInfo recentTaskInfo4 = GroupedTaskInfo.forSplitTasks(
                createRecentTaskInfo(/* taskId = */ 7, DEFAULT_DISPLAY, /* isAutomated= */true),
                createRecentTaskInfo(/* taskId = */ 8, DEFAULT_DISPLAY, /* isAutomated= */true),
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ 7,
                        /* rightBottomTaskId = */ 8,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));

        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt())).thenReturn(
                new ArrayList<>(Arrays.asList(recentTaskInfo1, recentTaskInfo2, recentTaskInfo3,
                        recentTaskInfo4)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertThat(taskList).hasSize(3);

        GroupTask groupTask3 = taskList.get(2);
        assertThat(groupTask3.taskViewType).isEqualTo(TaskViewType.GROUPED);
        assertThat(((SplitTask) groupTask3).getTopLeftTask().key.id).isEqualTo(1);
        assertThat(((SplitTask) groupTask3).getBottomRightTask().key.id).isEqualTo(2);

        GroupTask groupTask4 = taskList.get(1);
        assertThat(groupTask4.taskViewType).isEqualTo(TaskViewType.SINGLE);
        assertThat(((SingleTask) groupTask4).getTask().key.id).isEqualTo(3);

        GroupTask groupTask5 = taskList.get(0);
        assertThat(groupTask5.taskViewType).isEqualTo(TaskViewType.SINGLE);
        assertThat(((SingleTask) groupTask5).getTask().key.id).isEqualTo(6);
    }


    @Test
    @EnableFlags(FLAG_HIDE_AUTOMATED_TASKS_IN_OVERVIEW)
    public void loadTasksInBackground_singleTask_filterOutAutomatedTasks() throws Exception {
        GroupedTaskInfo recentTaskInfo1 = GroupedTaskInfo.forFullscreenTasks(
                createRecentTaskInfo(/* taskId = */ 1, DEFAULT_DISPLAY, /* isAutomated= */false));
        GroupedTaskInfo recentTaskInfo2 = GroupedTaskInfo.forFullscreenTasks(
                createRecentTaskInfo(/* taskId = */ 2, DEFAULT_DISPLAY, /* isAutomated= */true));

        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt())).thenReturn(
                new ArrayList<>(Arrays.asList(recentTaskInfo1, recentTaskInfo2)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertThat(taskList).hasSize(1);

        GroupTask groupTask6 = taskList.get(0);
        assertThat(groupTask6.taskViewType).isEqualTo(TaskViewType.SINGLE);
        assertThat(((SingleTask) groupTask6).getTask().key.id).isEqualTo(1);
    }

    private RecentTaskInfo createRecentTaskInfo(int taskId) {
        return (RecentTaskInfo) createRecentTaskInfo(
                taskId, DEFAULT_DISPLAY, /* isAutomated= */false);
    }

    private TaskInfo createRecentTaskInfo(int taskId, int displayId) {
        return createRecentTaskInfo(taskId, displayId, /* isAutomated= */false);
    }

    private TaskInfo createRecentTaskInfo(int taskId, int displayId, boolean isAutomated) {
        RecentTaskInfo recentTaskInfo = new RecentTaskInfo();
        recentTaskInfo.taskId = taskId;
        recentTaskInfo.displayId = displayId;
        recentTaskInfo.userId = 10;
        String packageName = String.format("com.test.%d", taskId);
        recentTaskInfo.baseIntent = new Intent().setPackage(packageName);
        when(mAutomationRepository.isPackageAutomated(recentTaskInfo.userId, packageName))
                .thenReturn(isAutomated);
        return recentTaskInfo;
    }
}
