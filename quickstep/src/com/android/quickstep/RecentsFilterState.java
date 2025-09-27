/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.quickstep.window.RecentsWindowFlags.enableOverviewOnConnectedDisplays;

import com.android.quickstep.util.DesksUtils;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.TaskViewType;

import java.util.function.Predicate;

/**
 * Keeps track of the state of {@code RecentsView}.
 *
 * <p> More specifically, used for keeping track of the state of filters applied on tasks
 * in {@code RecentsView} for multi-instance management.
 */
public class RecentsFilterState {
    // default filter that returns true for any input
    public static final Predicate<GroupTask> EMPTY_FILTER = groupTask -> true;

    /** Returns a predicate for filtering out GroupTasks by displayId. */
    public static Predicate<GroupTask> getFilter(int displayId) {
        Predicate<GroupTask> filter = getDesktopTaskFilter();
        if (enableOverviewOnConnectedDisplays()) {
            filter = filter.and(groupTask -> groupTask.matchesDisplayId(displayId));
        }
        return filter;
    }

    /**
     * Returns a predicate that filters out desk tasks that contain no non-minimized desktop tasks,
     * unless the multiple desks feature is enabled, which allows empty desks.
     */
    public static Predicate<GroupTask> getDesktopTaskFilter() {
        return RecentsFilterState::shouldKeepGroupTask;
    }

    /**
     * Returns true if the given `groupTask` should be kept, and false if it should be filtered out.
     * Desks will be filtered out if they are empty unless the multiple desks feature is enabled.
     *
     * @param groupTask The group task to check.
     */
    private static boolean shouldKeepGroupTask(GroupTask groupTask) {
        if (groupTask.taskViewType != TaskViewType.DESKTOP) {
            return true;
        }

        if (DesksUtils.areMultiDesksFlagsEnabled()) {
            return true;
        }

        return groupTask.getTasks().stream().anyMatch(task -> !task.isMinimized);
    }
}
