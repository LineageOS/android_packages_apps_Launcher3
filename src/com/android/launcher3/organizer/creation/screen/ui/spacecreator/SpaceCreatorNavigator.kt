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

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayout

@Composable
fun SpaceCreatorNavigator(onArrowBack: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel =
        ViewModelProvider(context as ViewModelStoreOwner, SpaceCreatorViewModel.Factory)[
            SpaceCreatorViewModel::class.java]

    NavHost(navController = navController, startDestination = "Topics") {
        composable("Topics") {
            CreateScreen(
                onArrowBack,
                viewModel,
                onNavigateToChooser = { topic ->
                    viewModel.prepareLayoutsForTopic(topic)
                    navController.navigate("Chooser")
                },
            )
        }

        composable("Chooser") { ChooseLayout(viewModel, onBack = { navController.popBackStack() }) }
    }
}
