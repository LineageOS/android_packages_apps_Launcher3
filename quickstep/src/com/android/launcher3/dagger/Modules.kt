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

package com.android.launcher3.dagger

import android.annotation.ElapsedRealtimeLong
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.CrossWindowBlurListeners
import android.widget.ImageView
import com.android.app.displaylib.PerDisplayRepository
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.internal.R
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.DisplayControllerImpl
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerImpl
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.dragndrop.SystemDragListener
import com.android.launcher3.homescreenfiles.EnvironmentWrapper
import com.android.launcher3.homescreenfiles.HomeScreenFilesMediaStoreProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIconProviderImpl
import com.android.launcher3.logging.StatsLogManager.StatsLogManagerFactory
import com.android.launcher3.secondarydisplay.SecondaryDisplayDelegate
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher
import com.android.launcher3.secondarydisplay.SecondaryDisplayQuickstepDelegateImpl
import com.android.launcher3.taskbar.BaseTaskbarContext
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.testing.TestInformationHandler
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepWidgetHolderFactory
import com.android.launcher3.uioverrides.SystemApiWrapper
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.quickstep.AspectRatioSystemShortcut
import com.android.quickstep.AutomationRepositoryImpl
import com.android.quickstep.DesktopShortcut
import com.android.quickstep.ExternalDisplayShortcut
import com.android.quickstep.InstantAppResolverImpl
import com.android.quickstep.LauncherRestoreEventLoggerImpl
import com.android.quickstep.QuickstepTestInformationHandler
import com.android.quickstep.TaskShortcutFactory
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogCompatManagerFactory
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.quickstep.util.ContextualSearchStateManager
import com.android.quickstep.util.GestureExclusionManager
import com.android.quickstep.util.SystemWindowManagerProxy
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.wm.shell.shared.desktopmode.DesktopState
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import javax.inject.Named

private object Modules {}

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: SystemWindowManagerProxy): WindowManagerProxy
}

@Module(includes = [SystemDragModule::class])
abstract class ActivityContextModule {
    @Binds
    abstract fun bindSecondaryDisplayDelegate(
        impl: SecondaryDisplayQuickstepDelegateImpl
    ): SecondaryDisplayDelegate

    companion object {
        @JvmStatic
        @Provides
        @ActivityContextSingleton
        @DisplayId
        fun provideDisplayId(activityContext: ActivityContext): Int =
            activityContext.asContext().displayId

        @JvmStatic
        @Provides
        @ActivityContextSingleton
        fun provideTaskbarFeatureEvaluator(
            @DisplayId displayId: Int,
            repository: PerDisplayRepository<TaskbarFeatureEvaluator>,
        ): TaskbarFeatureEvaluator {
            return checkNotNull(repository[displayId]) {
                "no TaskbarFeatureEvaluator for display id : $displayId"
            }
        }
    }
}

@Module
abstract class ApiWrapperModule {
    @Binds
    abstract fun bindStatsLogManagerFactory(
        impl: StatsLogCompatManagerFactory
    ): StatsLogManagerFactory

    @Binds abstract fun bindApiWrapper(systemApiWrapper: SystemApiWrapper): ApiWrapper

    @Binds
    abstract fun bindIconProvider(iconProviderImpl: LauncherIconProviderImpl): LauncherIconProvider

    @Binds abstract fun bindInstantAppResolver(impl: InstantAppResolverImpl): InstantAppResolver

    @Binds
    abstract fun bindRestoreEventLogger(
        impl: LauncherRestoreEventLoggerImpl
    ): LauncherRestoreEventLogger

    @Binds
    abstract fun bindTestInformationHandler(
        impl: QuickstepTestInformationHandler
    ): TestInformationHandler

    @Binds abstract fun bindDisplayController(impl: DisplayControllerImpl): DisplayController

    companion object {
        @Provides
        @LauncherAppSingleton
        fun provideRecentsWindowContextRepo(
            repositoryFactory: PerDisplayComponentRepository.Factory<Context>
        ): PerDisplayRepository<Context> =
            repositoryFactory.create("WindowContext", PerDisplayComponent::getWindowContext)
    }
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: QuickstepWidgetHolderFactory): WidgetHolderFactory
}

@Module
abstract class PluginManagerWrapperModule {
    @Binds
    abstract fun bindPluginManagerWrapper(impl: PluginManagerWrapperImpl): PluginManagerWrapper
}

@Module
object StaticObjectModule {

    @Provides
    fun provideGestureExclusionManager(): GestureExclusionManager = GestureExclusionManager.INSTANCE

    @Provides fun provideRefreshRateTracker(): RefreshRateTracker = ChoreographerFrameRateTracker

    @Provides
    fun provideActivityManagerWrapper(): ActivityManagerWrapper =
        ActivityManagerWrapper.getInstance()

    @Provides
    @JvmStatic
    fun provideTaskStackChangeListeners(): TaskStackChangeListeners =
        TaskStackChangeListeners.getInstance()

    @Provides
    @JvmStatic
    @ElapsedRealtimeLong
    fun provideElapsedRealTime(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @ElementsIntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideSearchEntryPointsDefault(@ApplicationContext ctx: Context): Set<Uri> =
        if (ctx.resources.getBoolean(R.bool.config_searchAllEntrypointsEnabledDefault)) {
            setOf(ContextualSearchStateManager.SEARCH_ALL_ENTRYPOINTS_ENABLED_URI)
        } else emptySet()

    @Provides
    @JvmStatic
    @LauncherAppSingleton
    @Named(WINDOW_BLUR_STATE)
    fun provideWindowBlurState(lifecycle: DaggerSingletonTracker): ListenableRef<Boolean> {
        val blurListeners = CrossWindowBlurListeners.getInstance()
        val value = MutableListenableRef(blurListeners.isCrossWindowBlurEnabled)

        val callback = Consumer<Boolean> { value.dispatchValue(it) }
        blurListeners.addListener(IMMEDIATE_EXECUTOR, callback)
        lifecycle.addCloseable { blurListeners.removeListener(callback) }
        return value.asListenable()
    }

    @Provides fun provideAbstractFloatingViewHelper() = AbstractFloatingViewHelper

    @Provides
    @JvmStatic
    fun provideComputerControlExtensions(
        @ApplicationContext context: Context
    ): ComputerControlExtensions? = ComputerControlExtensions.getInstance(context)
}

@Module
object SystemDragModule {
    @Provides
    @ActivityContextSingleton
    fun provideSystemDragController(
        context: ActivityContext,
        idp: InvariantDeviceProfile,
    ): SystemDragController =
        // TODO(b/456787959): Fix drop targets before enabling for secondary display launcher.
        // TODO(b/456787959): Fix drop targets before enabling for taskbar.
        if (
            enableSystemDrag() &&
                context !is BaseTaskbarContext &&
                context !is SecondaryDisplayLauncher
        ) {
            SystemDragControllerImpl(
                context,
                { ctx, params -> SystemDragListener(ctx, idp, ::ImageView, params) },
            )
        } else {
            SystemDragControllerStub()
        }
}

/** A dagger module responsible for managing files on the home screen. */
@Module
object HomeScreenFilesModule {
    @Provides
    @LauncherAppSingleton
    fun provideHomeScreenFilesProvider(
        @ApplicationContext context: Context,
        @ThreadPool executorService: ExecutorService,
        environmentWrapper: EnvironmentWrapper,
        tracker: DaggerSingletonTracker,
    ): HomeScreenFilesProvider {
        return if (HomeScreenFilesUtils.isFeatureEnabled) {
            HomeScreenFilesMediaStoreProvider(
                context,
                executorService,
                ::File,
                environmentWrapper,
                tracker,
            )
        } else {
            HomeScreenFilesNoOpProvider()
        }
    }
}

@Module
object DesktopModule {
    @Provides
    @LauncherAppSingleton
    fun provideDesktopModeCompatPolicy(@ApplicationContext context: Context) =
        DesktopModeCompatPolicy(context)

    @Provides
    fun provideDesktopState(@ApplicationContext context: Context): DesktopState =
        DesktopState.getInstance(context)
}

@Module
object TaskOverlayModule {
    @Provides
    @LauncherAppSingleton
    fun providePerTaskShortcutFactories(
        desktopShortcutFactory: DesktopShortcut.Factory,
        externalDisplayShortcutFactory: ExternalDisplayShortcut.Factory,
        aspectRatioSystemShortcutFactory: AspectRatioSystemShortcut.Factory,
    ): List<TaskShortcutFactory> =
        listOf(
            TaskShortcutFactory.APP_INFO,
            TaskShortcutFactory.SPLIT_SELECT,
            TaskShortcutFactory.PIN,
            TaskShortcutFactory.INSTALL,
            TaskShortcutFactory.FREE_FORM,
            desktopShortcutFactory,
            externalDisplayShortcutFactory,
            aspectRatioSystemShortcutFactory,
            TaskShortcutFactory.WELLBEING,
            TaskShortcutFactory.SAVE_APP_PAIR,
            TaskShortcutFactory.SCREENSHOT,
            TaskShortcutFactory.MODAL,
        )
}

/** Used by both Recents and Launcher for package automation */
@Module
interface AutomationModule {
    @Binds fun bindAutomatedRepository(impl: AutomationRepositoryImpl): AutomationRepository
}
