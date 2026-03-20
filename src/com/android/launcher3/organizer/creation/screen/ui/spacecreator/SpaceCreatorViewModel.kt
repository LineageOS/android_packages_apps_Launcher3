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
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayoutGridSize
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayoutState
import com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer.WorkspaceOrganizerViewModel
import com.android.launcher3.organizer.generator.CreationSession
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpaceCreatorViewModel
@Inject
constructor(
    creationSessionFactory: CreationSession.Factory,
    @LightweightBackgroundContext(priority = UI)
    private val lightweightBackgroundContext: CoroutineContext,
) : ViewModel() {
    var chooseLayoutState: ChooseLayoutState by mutableStateOf(ChooseLayoutState())
        private set

    private val _createScreenState = MutableStateFlow(CreateScreenState())
    val createScreenState: StateFlow<CreateScreenState> = _createScreenState.asStateFlow()

    private val screenCreationSession =
        creationSessionFactory.createSession(CreationSession.SessionType.SCREEN)

    init {
        viewModelScope.launch(lightweightBackgroundContext) {
            val allClassifiedItems = screenCreationSession.startClassification()
            val topics = allClassifiedItems.map { it.topic }.distinct()
            val topicIcons =
                allClassifiedItems
                    .filter { it.itemInfo is AppInfo }
                    .groupBy({ it.topic }, { (it.itemInfo as AppInfo).bitmap.icon })

            val topicDataList = topics.map { TopicData(it, topicIcons[it] ?: emptyList()) }
            updateState(topicDataList)
        }
    }

    /**
     * Update the state with new topics and icons.
     *
     * @param topics The new list of topics.
     */
    private fun updateState(topics: List<TopicData>) {
        _createScreenState.value = _createScreenState.value.copy(topics = topics)
    }

    /**
     * Generates layouts for the selected topic and updates the state.
     *
     * @param topic The selected topic.
     */
    fun prepareLayoutsForTopic(topic: String) {
        viewModelScope.launch(lightweightBackgroundContext) {
            val result = screenCreationSession.startGeneration(listOf(topic))
            if (result is CreationSession.GenerationResult.Screens) {
                updateLayouts(result.pages)
            }
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

    /** Update the Layouts to be shown. Each list is a different page. */
    fun updateLayouts(items: List<List<ItemInfo>>) {
        chooseLayoutState = chooseLayoutState.copy(layouts = items)
    }

    /** Update the [ChooseLayoutGridSize] for the chooseLayoutState. */
    fun updateGridSize(chooseLayoutGridSize: ChooseLayoutGridSize) {
        chooseLayoutState = chooseLayoutState.copy(chooseLayoutGridSize = chooseLayoutGridSize)
    }

    companion object {
        /** Returns a [ViewModelProvider.Factory] for [WorkspaceOrganizerViewModel]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as LauncherApplication
                val appComponent = application.appComponent
                val idp = appComponent.idp
                SpaceCreatorViewModel(
                        creationSessionFactory = appComponent.creationSessionFactory,
                        lightweightBackgroundContext =
                            appComponent.productionDispatchers.lightweightBackgroundUiDispatcher,
                    )
                    .apply { updateGridSize(ChooseLayoutGridSize(idp.numColumns, idp.numRows)) }
            }
        }
    }
}
