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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.launcher3.R
import com.android.launcher3.organizer.creation.screen.ui.BlurController
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayout

class SpaceCreatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO(): This is for testing purpose, remove when connecting with the business logic.
        val viewModel = SpaceCreatorViewModel()
        viewModel.updateTopics(
            listOf(
                getString(R.string.organizer_topic_0),
                getString(R.string.organizer_topic_1),
                getString(R.string.organizer_topic_2),
                getString(R.string.organizer_topic_3),
                getString(R.string.organizer_topic_4),
                getString(R.string.organizer_topic_5),
            )
        )
        viewModel.updateLayouts(5)
        setContent {
            if (viewModel.state == ScreenCreationStates.SCREEN_CREATION) CreateScreen(viewModel)
            else ChooseLayout(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        BlurController(this).apply()
    }
}
