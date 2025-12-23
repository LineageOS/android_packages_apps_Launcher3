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

package com.android.launcher3.taskbar.rules

import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import com.android.launcher3.taskbar.TaskbarManagerImpl
import com.android.launcher3.taskbar.rules.TaskbarSandboxComponent.Builder
import com.android.quickstep.dagger.SysUIConnectionComponent
import com.android.quickstep.dagger.SysUIConnectionModule
import com.android.quickstep.dagger.SysUIConnectionSingleton
import com.android.quickstep.dagger.SysUIConnectionTestableModule.TESTABLE_DISPLAY_PROVIDER
import dagger.BindsInstance
import dagger.Subcomponent
import javax.inject.Named

@SysUIConnectionSingleton
@Subcomponent(modules = [SysUIConnectionModule::class])
interface TaskbarSysUIConnectionComponent : SysUIConnectionComponent {

    val taskbarImpl: TaskbarManagerImpl

    @Subcomponent.Builder
    interface Builder : SysUIConnectionComponent.Builder {

        @BindsInstance
        fun bindDisplayDecorationProvider(
            @Named(TESTABLE_DISPLAY_PROVIDER) repo: DisplaysWithDecorationsRepositoryCompat
        ): Builder

        override fun build(): TaskbarSysUIConnectionComponent
    }
}
