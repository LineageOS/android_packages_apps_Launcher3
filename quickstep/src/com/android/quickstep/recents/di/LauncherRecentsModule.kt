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

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.LauncherApps
import android.hardware.input.InputManager
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.core.content.getSystemService
import com.android.launcher3.DeviceProfile
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.DisplayId
import com.android.quickstep.RecentsModel
import com.android.quickstep.recents.data.AppTimersRepository
import com.android.quickstep.recents.data.AppTimersRepositoryImpl
import com.android.quickstep.recents.data.HighResLoadingStateNotifier
import com.android.quickstep.recents.data.InputDeviceDataSource
import com.android.quickstep.recents.data.InputManagerWrapper
import com.android.quickstep.recents.data.PointerRepository
import com.android.quickstep.recents.data.PointerRepositoryImpl
import com.android.quickstep.recents.data.RecentTasksDataSource
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.data.RecentsDeviceProfileRepository
import com.android.quickstep.recents.data.RecentsDeviceProfileRepositoryImpl
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.quickstep.recents.data.RecentsRotationStateRepositoryImpl
import com.android.quickstep.recents.data.TaskVisualsChangeNotifier
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate
import com.android.quickstep.recents.data.TaskVisualsChangedDelegateImpl
import com.android.quickstep.recents.data.TasksRepository
import com.android.quickstep.recents.data.UserLockedRepository
import com.android.quickstep.recents.data.UserLockedStateRepository
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.views.RecentsViewContainer
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Module that provides dependencies required for an instance of Recents. */
@Module
interface LauncherRecentsModule {
    @RecentsSingleton
    @Binds
    fun bindRecentTasksRepository(impl: TasksRepository): RecentTasksRepository

    @RecentsSingleton
    @Binds
    fun bindRecentTasksDataSource(impl: RecentsModel): RecentTasksDataSource

    @Binds
    fun bindTaskVisualsChangedDelegate(
        impl: TaskVisualsChangedDelegateImpl
    ): TaskVisualsChangedDelegate

    @Binds fun bindTaskVisualsChangeNotifier(impl: RecentsModel): TaskVisualsChangeNotifier

    @RecentsSingleton
    @Binds
    fun bindUserLockedStateRepository(impl: UserLockedRepository): UserLockedStateRepository

    @RecentsSingleton
    @Binds
    fun bindInputDeviceDataSource(impl: InputManagerWrapper): InputDeviceDataSource

    @RecentsSingleton
    @Binds
    fun bindAppTimersRepository(impl: AppTimersRepositoryImpl): AppTimersRepository

    @RecentsSingleton
    @Binds
    fun bindPointerRepository(impl: PointerRepositoryImpl): PointerRepository

    @RecentsSingleton
    @Binds
    fun bindRecentsDeviceProfileRepository(
        impl: RecentsDeviceProfileRepositoryImpl
    ): RecentsDeviceProfileRepository

    companion object {
        @RecentsSingleton
        @Provides
        fun provideTaskIconDataSource(model: RecentsModel): TaskIconDataSource = model.iconCache

        @RecentsSingleton
        @Provides
        fun provideRecentsCoroutineScope(
            @DisplayId displayId: Int,
            @LightweightBackground(UI) lightweightBackgroundDispatcher: CoroutineDispatcher,
        ): CoroutineScope =
            CoroutineScope(
                SupervisorJob() +
                    lightweightBackgroundDispatcher +
                    CoroutineName("RecentsView-Display$displayId")
            )

        @RecentsSingleton
        @Provides
        fun provideTaskThumbnailDataSource(model: RecentsModel): TaskThumbnailDataSource =
            model.thumbnailCache

        @RecentsSingleton
        @Provides
        fun provideHighResLoadingStateNotifier(model: RecentsModel): HighResLoadingStateNotifier =
            model.thumbnailCache.highResLoadingState

        @RecentsSingleton
        @Provides
        fun provideKeyguardManager(@ApplicationContext context: Context): KeyguardManager =
            context.getSystemService<KeyguardManager>()!!

        @RecentsSingleton
        @Provides
        fun provideInputManager(@ApplicationContext context: Context): InputManager =
            context.getSystemService<InputManager>()!!

        @RecentsSingleton
        @Provides
        fun provideLauncherApps(@ApplicationContext context: Context): LauncherApps =
            context.getSystemService<LauncherApps>()!!

        @RecentsSingleton
        @Provides
        fun provideWindowManager(): IWindowManager = WindowManagerGlobal.getWindowManagerService()!!

        @RecentsSingleton
        @Provides
        fun provideRecentsOrientedState(
            @ApplicationContext context: Context,
            recentsViewContainer: RecentsViewContainer,
        ): RecentsOrientedState =
            RecentsOrientedState(context, recentsViewContainer.getContainerInterface())

        @RecentsSingleton
        @Provides
        fun provideRecentsRotationStateRepository(
            recentsOrientedState: RecentsOrientedState
        ): RecentsRotationStateRepository = RecentsRotationStateRepositoryImpl(recentsOrientedState)

        @RecentsSingleton
        @Provides
        fun provideDeviceProfileGetter(
            recentsViewContainer: RecentsViewContainer
        ): DeviceProfile.Getter = DeviceProfile.Getter { recentsViewContainer.deviceProfile }
    }
}
