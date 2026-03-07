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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.launcher3.LauncherApplication
import com.android.launcher3.concurrent.annotations.LightweightBackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayoutState
import com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer.WorkspaceOrganizerViewModel
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpaceCreatorViewModel
@Inject
constructor(
    @LightweightBackgroundContext(priority = UI)
    private val lightweightBackgroundContext: CoroutineContext
) : ViewModel() {
    var chooseLayoutState: ChooseLayoutState by mutableStateOf(ChooseLayoutState())
        private set

    private val _createScreenState = MutableStateFlow(CreateScreenState())
    val createScreenState: StateFlow<CreateScreenState> = _createScreenState.asStateFlow()

    init {
        viewModelScope.launch {
            updateTopics(
                listOf(
                    "Most used",
                    "Games",
                    "Health & Fitness",
                    "Productivity",
                    "Travel",
                    "Social",
                    "Entertainment",
                )
            )
        }
    }

    /**
     * Update the topics list.
     *
     * @param topics The new list of topics.
     */
    private suspend fun updateTopics(topics: List<String>) {
        withContext(lightweightBackgroundContext) {
            _createScreenState.value = _createScreenState.value.copy(topics = topics)
        }
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

    companion object {
        /** Returns a [ViewModelProvider.Factory] for [WorkspaceOrganizerViewModel]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as LauncherApplication
                val appComponent = application.appComponent
                SpaceCreatorViewModel(
                    lightweightBackgroundContext =
                        appComponent.productionDispatchers.lightweightBackgroundUiDispatcher,
                )
            }
        }
    }
}
