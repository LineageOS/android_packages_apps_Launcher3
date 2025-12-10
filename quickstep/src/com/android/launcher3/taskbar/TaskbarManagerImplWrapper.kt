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

package com.android.launcher3.taskbar

import android.app.PendingIntent
import androidx.annotation.VisibleForTesting
import com.android.launcher3.ActivityInteractor
import com.android.launcher3.AsyncAnimatorPlaybackController
import com.android.launcher3.LauncherInteractor
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.Executors.TASKBAR_UI_THREAD
import com.android.launcher3.util.ListenableStream
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.dagger.SysUIConnectionSingleton
import com.android.quickstep.views.RecentsViewContainer
import java.io.PrintWriter
import java.util.concurrent.Callable
import javax.annotation.concurrent.ThreadSafe
import javax.inject.Inject
import javax.inject.Provider

/**
 * Wrapper of [TaskbarManagerImpl], this class controls which thread the invocation happens. The
 * goal of this class is to minimize the changes to [TaskbarManagerImpl] during migration of
 * rendering taskbar in per-window ui thread.
 */
@ThreadSafe
@SysUIConnectionSingleton
class TaskbarManagerImplWrapper @Inject constructor(implProvider: Provider<TaskbarManagerImpl>) :
    TaskbarManager {

    private lateinit var impl: TaskbarManagerImpl

    init {
        TASKBAR_UI_THREAD.execute { impl = implProvider.get() }
    }

    override fun onUserUnlocked() {
        TASKBAR_UI_THREAD.execute(impl::onUserUnlocked)
    }

    override fun updateTaskbarsVisibility() {
        TASKBAR_UI_THREAD.execute { impl.updateTaskbarsVisibility() }
    }

    override fun setActivity(activity: StatefulActivity<*>) {
        TASKBAR_UI_THREAD.execute {
            impl.setActivityInteractor(
                if (activity is QuickstepLauncher) LauncherInteractor(activity)
                else ActivityInteractor(activity)
            )
        }
    }

    override fun setRecentsViewContainer(recentsViewContainer: RecentsViewContainer) {
        TASKBAR_UI_THREAD.execute { impl.setRecentsViewContainerInteractor(recentsViewContainer) }
    }

    override fun onSystemUiFlagsChanged(systemUiStateFlags: Long, displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.onSystemUiFlagsChanged(systemUiStateFlags, displayId) }
    }

    override fun onLongPressHomeEnabled(assistantLongPressEnabled: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.onLongPressHomeEnabled(assistantLongPressEnabled) }
    }

    override fun setSetupUIVisible(isVisible: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.setSetupUIVisible(isVisible) }
    }

    override fun setWallpaperVisible(displayId: Int, isVisible: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.setWallpaperVisible(displayId, isVisible) }
    }

    override fun checkNavBarModes(displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.checkNavBarModes(displayId) }
    }

    override fun finishBarAnimations(displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.finishBarAnimations(displayId) }
    }

    override fun touchAutoDim(displayId: Int, reset: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.touchAutoDim(displayId, reset) }
    }

    override fun transitionTo(displayId: Int, barMode: Int, animate: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.transitionTo(displayId, barMode, animate) }
    }

    override fun appTransitionPending(pending: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.appTransitionPending(pending) }
    }

    override fun onRotationProposal(rotation: Int, isValid: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.onRotationProposal(rotation, isValid) }
    }

    override fun disableNavBarElements(displayId: Int, state1: Int, state2: Int, animate: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.disableNavBarElements(displayId, state1, state2, animate) }
    }

    override fun onSystemBarAttributesChanged(displayId: Int, behavior: Int) {
        TASKBAR_UI_THREAD.execute { impl.onSystemBarAttributesChanged(displayId, behavior) }
    }

    override fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.onTransitionModeUpdated(barMode, checkBarModes) }
    }

    override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) {
        TASKBAR_UI_THREAD.execute { impl.onNavButtonsDarkIntensityChanged(darkIntensity) }
    }

    override fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.onNavigationBarLumaSamplingEnabled(displayId, enable) }
    }

    override fun onDisplayAddSystemDecorations(displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.onDisplayAddSystemDecorations(displayId) }
    }

    override fun onDisplayRemoved(displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.onDisplayRemoved(displayId) }
    }

    override fun onDisplayRemoveSystemDecorations(displayId: Int) {
        TASKBAR_UI_THREAD.execute { impl.onDisplayRemoveSystemDecorations(displayId) }
    }

    override fun destroy() {
        TASKBAR_UI_THREAD.execute { impl.destroy() }
    }

    override fun createLauncherStartFromSuwAnim(duration: Int): AsyncAnimatorPlaybackController? {
        return impl.createLauncherStartFromSuwAnim(duration)
    }

    override fun shouldForceAllSetFallbackAnimation(): Boolean {
        return impl.shouldForceAllSetFallbackAnimation()
    }

    override fun hasCurrentActivityContext() = impl.currentActivityContext != null

    override fun toggleTaskbarStash() {
        TASKBAR_UI_THREAD.execute { impl.currentActivityContext?.toggleTaskbarStash() }
    }

    override fun getStashedHandleViewController(): StashedHandleViewControllerProxy? {
        return impl.currentActivityContext?.controllers?.stashedHandleViewController?.let {
            StashedHandleViewControllerProxy(it)
        }
    }

    override fun getPrimaryDisplayUiControllerStream(): ListenableStream<TaskbarUIController> =
        impl.primaryDisplayUiControllerStream

    override fun getTaskbarInteractor(displayId: Int): TaskbarInteractor? {
        return impl.getUIControllerForDisplay(displayId)?.let { TaskbarInteractor(it) }
    }

    /* TODO(b/404636836): Evaluate API calls on returned TaskbarActivityContext */
    override fun getTaskbarForDisplay(displayId: Int): TaskbarActivityContext? {
        return impl.getTaskbarForDisplay(displayId)
    }

    override fun createAllAppsPendingIntent(): PendingIntent {
        return impl.createAllAppsPendingIntent(TASKBAR_UI_THREAD)
    }

    override fun getPrimaryDisplayId(): Int {
        return impl.primaryDisplayId
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        // Stay on caller thread because PrinterWriter is not thread safe.
        impl.dumpLogs(prefix, pw)
    }

    override fun debugPrimaryTaskbar(debugReason: String, verbose: Boolean) {
        TASKBAR_UI_THREAD.execute { impl.debugPrimaryTaskbar(debugReason, verbose) }
    }

    @VisibleForTesting
    override fun getCurrentActivityContext(): TaskbarActivityContext? {
        return impl.currentActivityContext
    }

    @VisibleForTesting
    override fun recreateTaskbars() {
        TASKBAR_UI_THREAD.execute(impl::recreateTaskbars)
    }

    @VisibleForTesting
    override fun removeAllSystemUiBubbles() {
        SystemUiProxy.INSTANCE[impl.currentActivityContext].removeAllBubbles()
    }

    @VisibleForTesting
    override fun unstashBubbleBarIfStashed() {
        TASKBAR_UI_THREAD.execute { impl.currentActivityContext?.unstashBubbleBarIfStashed() }
    }

    @VisibleForTesting
    override fun limitMaxTaskbarIconsNum(maxIconLimitNum: Int) {
        TASKBAR_UI_THREAD.execute {
            impl.currentActivityContext?.limitMaxTaskbarIconsNum(maxIconLimitNum)
        }
    }

    @VisibleForTesting
    override fun getStashedTaskbarScale() = impl.currentActivityContext!!.stashedTaskbarScale

    @VisibleForTesting
    override fun removeAllBubbles() {
        TASKBAR_UI_THREAD.execute { impl.currentActivityContext!!.removeAllBubbles() }
    }

    @VisibleForTesting
    override fun unstashTaskbarIfStashed() {
        TASKBAR_UI_THREAD.execute { impl.currentActivityContext!!.unstashTaskbarIfStashed() }
    }

    @VisibleForTesting
    override fun enableBlockingTimeoutDuringTests(enableBlockingTimeout: Boolean) {
        impl.currentActivityContext?.enableBlockingTimeoutDuringTests(enableBlockingTimeout)
    }

    @VisibleForTesting
    override fun isTransient(): Boolean =
        impl.currentActivityContext?.taskbarFeatureEvaluator?.isTransient ?: false

    @VisibleForTesting
    override fun getTaskbarAllAppsScroll(): Int {
        return TASKBAR_UI_THREAD.submit(
                Callable { impl.currentActivityContext!!.taskbarAllAppsScroll }
            )
            .get()
    }

    @VisibleForTesting
    override fun getTaskbarAllAppsTopPadding(): Int =
        TASKBAR_UI_THREAD.submit(
                Callable { impl.currentActivityContext!!.taskbarAllAppsTopPadding }
            )
            .get()

    @VisibleForTesting
    override fun isImeDocked(): Boolean =
        TASKBAR_UI_THREAD.submit(Callable { impl.currentActivityContext!!.isImeDocked }).get()
}
