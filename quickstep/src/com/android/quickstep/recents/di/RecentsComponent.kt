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

package com.android.quickstep.recents.di

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.quickstep.recents.viewmodel.RecentsViewModel
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.RecentsDismissUtils
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.RecentsViewModelHelper
import com.android.quickstep.views.RecentsViewUtils
import com.android.quickstep.views.TaskView
import dagger.BindsInstance
import dagger.Subcomponent

/** A sub-component that controls the lifecycle of an instance of Recents. */
@RecentsSingleton
@RecentsScope
@Subcomponent(modules = [LauncherRecentsModule::class])
interface RecentsComponent {
    fun getRecentsViewUtilsFactory(): RecentsViewUtils.Factory

    fun getRecentsDismissUtilsFactory(): RecentsDismissUtils.Factory

    fun getRecentsViewModel(): RecentsViewModel

    fun getRecentsViewModelHelper(): RecentsViewModelHelper

    fun inject(taskView: TaskView)

    fun inject(taskView: GroupedTaskView)

    fun inject(taskView: DesktopTaskView)

    @LauncherAppSingleton
    @Subcomponent.Factory
    interface Factory {
        fun build(@BindsInstance recentsViewContainer: RecentsViewContainer): RecentsComponent
    }
}
