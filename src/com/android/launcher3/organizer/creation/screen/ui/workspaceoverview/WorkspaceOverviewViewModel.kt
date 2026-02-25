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

package com.android.launcher3.organizer.creation.screen.ui.workspaceoverview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.launcher3.organizer.creation.screen.ui.BitmapBackedPageUI
import com.android.launcher3.organizer.creation.screen.ui.WorkspacePreviewRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model used by the WorkspaceOverview activity and its composables. */
class WorkspaceOverviewViewModel
@Inject
constructor(workspacePagesRepository: WorkspacePreviewRepository) : ViewModel() {

    val workspacePages: StateFlow<List<WorkspacePage>> =
        workspacePagesRepository
            .getPages()
            .map { it.map { pages -> WorkspacePage((pages as BitmapBackedPageUI).bitmap) } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = listOf(),
            )

    var workspaceOverviewState: WorkspaceOverviewState by mutableStateOf(WorkspaceOverviewState())
        private set

    fun increaseSelectedWorkspacePage(delta: Int) {
        workspaceOverviewState =
            workspaceOverviewState.copy(
                selectedPage =
                    (workspaceOverviewState.selectedPage + delta) % workspacePages.value.size
            )
    }

    fun setSelectedWorkspacePage(index: Int) {
        workspaceOverviewState =
            workspaceOverviewState.copy(selectedPage = (index) % workspacePages.value.size)
    }
}
