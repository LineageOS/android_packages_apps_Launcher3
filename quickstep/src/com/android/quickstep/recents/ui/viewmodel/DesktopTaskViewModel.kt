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

package com.android.quickstep.recents.ui.viewmodel

import androidx.annotation.VisibleForTesting
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.FullscreenPosition
import com.android.quickstep.recents.domain.model.OverviewPosition
import com.android.quickstep.recents.domain.model.OverviewPosition.Hidden
import com.android.quickstep.recents.domain.model.OverviewPosition.Rendered
import com.android.quickstep.recents.domain.model.TaskId
import com.android.quickstep.recents.domain.usecase.GetDesktopTaskFullscreenPositionUseCase
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase
import com.android.quickstep.util.DesktopTask
import javax.inject.Inject

/** ViewModel used for [com.android.quickstep.views.DesktopTaskView]. */
class DesktopTaskViewModel
@Inject
constructor(
    private val organizeDesktopTasksUseCase: OrganizeDesktopTasksUseCase,
    private val getDesktopTaskFullscreenPositionUseCase: GetDesktopTaskFullscreenPositionUseCase,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
) {
    private var desktopTask: DesktopTask? = null

    /** Map of desktop task IDs to calculated layout positions in Overview. */
    var overviewTaskPositions = emptyMap<TaskId, OverviewPosition>()
        @VisibleForTesting set

    /** Holds the default (user placed) positions of task windows. */
    var fullscreenTaskPositions: Map<TaskId, FullscreenPosition> = emptyMap()
        private set

    fun bind(desktopTask: DesktopTask?) {
        this.desktopTask = desktopTask
    }

    /**
     * Computes new task positions using [organizeDesktopTasksUseCase] and obscured states using
     * [getDesktopTaskFullscreenPositionUseCase]. The layout results are stored in
     * [overviewTaskPositions], and original window states are stored in [fullscreenTaskPositions].
     * This is used for the exploded desktop view where the use case will scale and translate tasks
     * so that they don't overlap.
     *
     * @param layoutConfig the pre-scaled dimension configuration for the desktop layout.
     * @param dismissedTaskId Optional ID of a task being dismissed. If provided, the use case will
     *   decide whether to reflow or fully reorganize.
     */
    fun organizeDesktopTasks(layoutConfig: DesktopLayoutConfig, dismissedTaskId: Int? = null) {
        val tasks = desktopTask?.tasks.orEmpty()
        val (transparentOverlayTasks, normalTasks) =
            tasks.partition {
                desktopModeCompatPolicy.isTransparentOverlay(
                    it.key.isActivityStackTransparent,
                    it.key.numActivities,
                    it.key.windowingMode,
                )
            }
        fullscreenTaskPositions =
            getDesktopTaskFullscreenPositionUseCase(
                    tasks.filterNot { it.key.id == dismissedTaskId }
                )
                .associateBy { it.taskId }

        val oldOverviewTaskPositions = overviewTaskPositions.values.toList()

        // TODO(b/456480920) change allCurrentOriginalTaskBounds to be map of id and bounds
        val newOverviewTaskPositions =
            organizeDesktopTasksUseCase(
                allCurrentOriginalTaskBounds =
                    normalTasks.map { Rendered(it.key.id, it.appBounds) },
                layoutConfig = layoutConfig,
                taskPositionsHint = oldOverviewTaskPositions,
                dismissedTaskId = dismissedTaskId,
            ) + transparentOverlayTasks.map { Hidden(taskId = it.key.id) }

        overviewTaskPositions = newOverviewTaskPositions.associateBy { it.taskId }
    }
}
