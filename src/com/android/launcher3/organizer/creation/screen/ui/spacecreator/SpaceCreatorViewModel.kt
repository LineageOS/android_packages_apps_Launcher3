/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.organizer.creation.screen.ui.spacecreator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayoutState

enum class ScreenCreationStates {
    CHOOSE_LAYOUT,
    SCREEN_CREATION,
}

/** View model used for [SpaceCreatorActivity] and all it's composables. */
class SpaceCreatorViewModel {

    var createScreenState: CreateScreenState by mutableStateOf(CreateScreenState())
        private set

    var state: ScreenCreationStates by mutableStateOf(ScreenCreationStates.SCREEN_CREATION)
        private set

    var chooseLayoutState: ChooseLayoutState by mutableStateOf(ChooseLayoutState())
        private set

    /**
     * Update the topics list.
     *
     * @param topics The new list of topics.
     */
    fun updateTopics(topics: List<String>) {
        createScreenState = createScreenState.copy(topics = topics)
    }

    /**
     * Go to a new state.
     *
     * @param toState The new state. See [ScreenCreationStates] for all the available states.
     */
    fun goToState(toState: ScreenCreationStates) {
        state = toState
    }

    /**
     * Set to keep track of the currently selected Layout.
     *
     * @param index The index of the selected layout.
     */
    fun setSelectedLayout(index: Int) {
        chooseLayoutState = chooseLayoutState.copy(selectedLayout = index)
    }

    /** TODO(): Add a real implementation to update the Layouts. */
    fun updateLayouts(n: Int) {
        chooseLayoutState =
            chooseLayoutState.copy(layouts = (0 until n).toList(), selectedLayout = 0)
    }
}
