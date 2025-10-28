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

package com.android.quickstep.cuebar.domain.interactor


import android.graphics.Rect
import com.android.quickstep.cuebar.data.repository.AmbientCueRepository
import com.android.systemui.plugins.cuebar.ActionModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Interactor for the Ambient Cue feature within Launcher.
 *
 * This class mediates between the ViewModel and the adapted AmbientCueRepository,
 * providing the necessary data flows and control functions.
 */
class AmbientCueInteractor @Inject constructor(
    private val ambientCueRepository: AmbientCueRepository // Injects the Adapted Repository
) {

    /**
     * Flow of actions to be displayed in the Cuebar.
     */
    val actions: Flow<List<ActionModel>> = ambientCueRepository.actions

    /**
     * Flow indicating if the root view for the cuebar is attached or should be.
     * In the Launcher context, this might represent visibility or enablement.
     */
    val isRootViewAttached: Flow<Boolean> = ambientCueRepository.isRootViewAttached

    /**
     * Flow indicating if the Input Method Editor (keyboard) is visible.
     */
    val isImeVisible: Flow<Boolean> = ambientCueRepository.isImeVisible

    /**
     * Flow indicating if the Cuebar is occluded by other system UI elements.
     * This logic might need significant adaptation in Launcher.
     */
    val isOccludedBySystemUi: Flow<Boolean> = ambientCueRepository.isOccludedBySystemUi

    /**
     * Flow providing the timeout duration for the Ambient Cue.
     */
    val ambientCueTimeoutMs: Flow<Int> = ambientCueRepository.ambientCueTimeoutMs

    /**
     * Flow indicating if gesture navigation is enabled.
     */
    val isGestureNav: Flow<Boolean> = ambientCueRepository.isGestureNav

    /**
     * Flow indicating if the Taskbar is fully visible and not stashed.
     */
    val isTaskBarVisible: Flow<Boolean> = ambientCueRepository.isTaskBarVisible

    /**
     * Flow providing the position of the recents button (in 3-button nav).
     */
    val recentsButtonPosition: Flow<Rect?> = ambientCueRepository.recentsButtonPosition

    /**
     * Sets the deactivated state of the Ambient Cue.
     *
     * @param isDeactivated True to deactivate, false to potentially reactivate.
     */
    fun setDeactivated(isDeactivated: Boolean) {
        ambientCueRepository.isDeactivated.value = isDeactivated
    }
}