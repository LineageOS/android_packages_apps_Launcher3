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
import com.android.quickstep.recents.domain.model.DesktopTaskPosition
import com.android.quickstep.recents.domain.model.TaskId
import com.android.quickstep.recents.domain.model.TaskPosition.Hidden
import com.android.quickstep.recents.domain.model.TaskPosition.Rendered
import com.android.quickstep.recents.domain.usecase.GetDesktopTaskFullscreenPositionUseCase
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase
import com.android.systemui.shared.recents.model.Task
import javax.inject.Inject

/** ViewModel used for [com.android.quickstep.views.DesktopTaskView]. */
class DesktopTaskViewModel
@Inject
constructor(
    private val organizeDesktopTasksUseCase: OrganizeDesktopTasksUseCase,
    private val getDesktopTaskFullscreenPositionUseCase: GetDesktopTaskFullscreenPositionUseCase,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
) {

    /** Map of desktop task IDs to calculated layout positions for both Fullscreen and Overview. */
    var desktopTaskPositions = emptyMap<TaskId, DesktopTaskPosition>()
        @VisibleForTesting set

    /**
     * Computes new task positions using [organizeDesktopTasksUseCase] and obscured states using
     * [getDesktopTaskFullscreenPositionUseCase]. The combined layout results are stored in
     * [desktopTaskPositions].
     *
     * This is used for the exploded desktop view where the use cases will calculate non-overlapping
     * bounds for Overview while preserving or determining the obscured state for Fullscreen.
     *
     * @param tasks The list of desktop tasks to organize.
     * @param layoutConfig The pre-scaled dimension configuration for the desktop layout.
     * @param dismissedTaskId Optional ID of a task being dismissed. If provided, the use case will
     *   decide whether to reflow or fully reorganize.
     */
    fun organizeDesktopTasks(
        tasks: List<Task>,
        layoutConfig: DesktopLayoutConfig,
        dismissedTaskId: Int? = null,
    ) {
        val validTasks = tasks.filterNot { it.appBounds == null }
        val (transparentOverlayTasks, normalTasks) =
            validTasks.partition {
                desktopModeCompatPolicy.isTransparentOverlay(
                    it.key.isActivityStackTransparent,
                    it.key.numActivities,
                    it.key.windowingMode,
                )
            }
        val fullscreenPositions =
            getDesktopTaskFullscreenPositionUseCase(validTasks).associateBy { it.taskId }

        val oldOverviewTaskPositions = desktopTaskPositions.map { it.value.overviewPosition }

        // TODO(b/456480920) change allCurrentOriginalTaskBounds to be map of id and bounds
        val newOverviewTaskPositions =
            organizeDesktopTasksUseCase(
                allCurrentOriginalTaskBounds =
                    normalTasks.map { Rendered(it.key.id, it.appBounds) },
                layoutConfig = layoutConfig,
                taskPositionsHint = oldOverviewTaskPositions,
                dismissedTaskId = dismissedTaskId,
            ) + transparentOverlayTasks.map { Hidden(taskId = it.key.id) }

        val overviewPositions = newOverviewTaskPositions.associateBy { it.taskId }
        desktopTaskPositions =
            validTasks
                .mapNotNull { task ->
                    val taskId = task.key.id
                    fullscreenPositions[taskId]?.let { fullscreenPosition ->
                        taskId to
                            DesktopTaskPosition(
                                fullscreenPosition = fullscreenPosition,
                                overviewPosition = overviewPositions[taskId] ?: Hidden(taskId),
                            )
                    }
                }
                .toMap()
    }
}
