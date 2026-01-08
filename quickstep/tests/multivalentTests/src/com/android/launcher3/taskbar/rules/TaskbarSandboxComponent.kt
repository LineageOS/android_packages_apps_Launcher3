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

import android.content.Context
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.AutomationModule
import com.android.launcher3.dagger.BasePerDisplayModule
import com.android.launcher3.dagger.DesktopModule
import com.android.launcher3.dagger.HomeScreenFilesModule
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherModelModule
import com.android.launcher3.dagger.PerDisplayComponent
import com.android.launcher3.dagger.SettingsModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.TaskOverlayModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.dagger.WindowContext
import com.android.launcher3.display.DisplayController
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SandboxWmProxyModule
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.TaskbarModeUtil
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.widgetpicker.NoOpWidgetPickerModule
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.dagger.SysUIConnectionComponent.Builder
import com.android.quickstep.window.RecentsWindowManager
import com.android.quickstep.window.RecentsWindowTracker
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy

@LauncherAppSingleton
@Component(modules = [AllTaskbarSandboxModules::class])
interface TaskbarSandboxComponent : LauncherAppComponent {

    override fun getSysUIConnectionComponentBuilder(): TaskbarSysUIConnectionComponent.Builder

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindSystemUiProxy(proxy: SystemUiProxy): Builder

        @BindsInstance fun bindSettingsCache(settingsCache: SettingsCache): Builder

        override fun build(): TaskbarSandboxComponent
    }
}

@Module(
    includes =
        [
            ApiWrapperModule::class,
            StaticObjectModule::class,
            WidgetModule::class,
            AppModule::class,
            BasePerDisplayModule::class,
            ExecutorsModule::class,
            LauncherExecutorsModule::class,
            FakePrefsModule::class,
            TaskbarModule::class,
            SandboxWmProxyModule::class,
            TaskbarPerDisplayReposModule::class,
            DesktopVisibilityControllerModule::class,
            NoOpWidgetPickerModule::class,
            LauncherModelModule::class,
            HomeScreenFilesModule::class,
            DesktopModule::class,
            SettingsModule::class,
            AutomationModule::class,
            TaskOverlayModule::class,
        ]
)
interface AllTaskbarSandboxModules

@Module
object TaskbarModule {
    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideTaskbarModeUtil(
        @ApplicationContext context: Context,
        displayController: DisplayController,
        windowManagerProxy: WindowManagerProxy,
        launcherPrefs: LauncherPrefs,
    ): TaskbarModeUtil {
        return spy(TaskbarModeUtil(context, displayController, windowManagerProxy, launcherPrefs))
    }

    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideTaskbarFeatureEvaluator(
        @ApplicationContext context: Context,
        displayController: DisplayController,
        desktopVisibilityController: DesktopVisibilityController,
        launcherPrefs: LauncherPrefs,
    ): TaskbarFeatureEvaluator {
        return spy(
            TaskbarFeatureEvaluator(
                context,
                displayController,
                desktopVisibilityController,
                launcherPrefs,
            )
        )
    }
}

@Module
object DesktopVisibilityControllerModule {
    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideDesktopVisibilityController(
        @ApplicationContext context: Context,
        systemUiProxy: SystemUiProxy,
        lifecycleTracker: DaggerSingletonTracker,
    ): DesktopVisibilityController {
        return spy(DesktopVisibilityController(context, systemUiProxy, lifecycleTracker))
    }
}

@Module
object TaskbarPerDisplayReposModule {
    @Provides
    @LauncherAppSingleton
    fun provideRecentsAnimationDeviceStateRepo():
        PerDisplayRepository<RecentsAnimationDeviceState> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideTaskAnimationManagerRepo(): PerDisplayRepository<TaskAnimationManager> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideRotationTouchHandlerRepo(): PerDisplayRepository<RotationTouchHelper> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowManagerRepo(): PerDisplayRepository<RecentsWindowManager> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowTrackerRepo(): PerDisplayRepository<RecentsWindowTracker> = mock()

    @Provides
    @LauncherAppSingleton
    @WindowContext
    fun provideWindowContext(): PerDisplayRepository<Context> = mock()

    @Provides
    @LauncherAppSingleton
    fun providePerDisplayComponentRepository(): PerDisplayRepository<PerDisplayComponent> = mock()
}
