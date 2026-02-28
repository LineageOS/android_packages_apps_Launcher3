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

package com.android.launcher3.organizer.creation.screen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.repository.HomeScreenRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BitmapBackedPageUI(val bitmap: Bitmap) : PageUI

/**
 * Placeholder implementation of [WorkspacePreviewRepository].
 *
 * It needs to be removed by a real implementation that constructs the real pages.
 */
@LauncherAppSingleton
class WorkspacePreviewRepositoryFakeImpl
@Inject
constructor(
    @ApplicationContext val context: Context,
    @LauncherAppSingleton val model: LauncherModel,
    @LauncherAppSingleton val homeScreenRepository: HomeScreenRepository,
) : WorkspacePreviewRepository {

    private val _pages = MutableStateFlow<List<BitmapBackedPageUI>>(emptyList())

    private val fakeImagesRes =
        listOf(R.drawable.desktop_1_, R.drawable.desktop_2_, R.drawable.desktop_3_)

    override fun refreshPages() {
        val workspacesCount =
            homeScreenRepository.workspaceState.value.collectWorkspaceScreens().size()
        if (workspacesCount != _pages.value.size) {
            _pages.value =
                List(workspacesCount) {
                    BitmapBackedPageUI(
                        BitmapFactory.decodeResource(
                            context.resources,
                            fakeImagesRes[it % fakeImagesRes.size],
                        )
                    )
                }
        }
    }

    override fun getPages(): StateFlow<List<PageUI>> {
        return _pages.asStateFlow()
    }
}
