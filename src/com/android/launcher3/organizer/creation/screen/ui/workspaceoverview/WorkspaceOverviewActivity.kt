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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.launcher3.dagger.LauncherComponentProvider.get
import com.android.launcher3.organizer.creation.screen.ui.BlurController

class WorkspaceOverviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appComponent = get(application)
        val homeOrganizerRepository = appComponent.workspacePreviewRepository
        homeOrganizerRepository.refreshPages()
        val viewModel = WorkspaceOverviewViewModel(homeOrganizerRepository)
        setContent { WorkspaceOverview(viewModel, {}, {}) }
    }

    override fun onResume() {
        super.onResume()
        BlurController(this).apply()
    }
}
