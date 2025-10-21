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

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.android.internal.jank.InteractionJankMonitor
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR
import com.android.quickstep.compose.QuickstepComposeFacade
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl
import com.android.quickstep.cuebar.domain.interactor.AmbientCueInteractor
import com.android.quickstep.cuebar.logger.AmbientCueLoggerImpl
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState
import com.android.quickstep.cuebar.ui.utils.AmbientCueJankMonitor
import com.android.quickstep.cuebar.ui.viewmodel.AmbientCueViewModel
import com.android.systemui.shared.Flags.cueBarAceMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.io.PrintWriter

class CueBarController (
    private val activity: TaskbarActivityContext,
) : TaskbarControllers.LoggableTaskbarController {

    private lateinit var taskbarControllers: TaskbarControllers
    private var internalComposeView: ComposeView? = null
    private val coroutineScope = CoroutineScope(MAIN_EXECUTOR.asCoroutineDispatcher())
    private var mOverlayContext: TaskbarOverlayContext? = null
    private var cueBar: View? = null
    private var isHiding = false
    private val ambientCueLogger = AmbientCueLoggerImpl(activity.packageManager)
    private val ambientCueRepository = AmbientCueRepositoryImpl(activity, ambientCueLogger,
        ORDERED_BG_EXECUTOR, MAIN_EXECUTOR)
    private val ambientCueInteractor = AmbientCueInteractor(ambientCueRepository)
    private val lp = InsettableFrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ).apply {
        ignoreInsets = true
    }

    private val ambientCueViewModel: AmbientCueViewModel = AmbientCueViewModel(
        ambientCueInteractor = ambientCueInteractor,
        launcherPrefs = LauncherPrefs.get(activity),
        scope = coroutineScope,
        ambientCueLogger = ambientCueLogger,
        uiExecutor = MAIN_EXECUTOR
    ). apply {
        onVisibilityChanged = { isCueBarVisible ->
            onCueBarVisibilityChanged(isCueBarVisible)
        }
    }

    fun init(controllers: TaskbarControllers) {
        if (!cueBarAceMigration()) {
            return
        }
        taskbarControllers = controllers
        ambientCueRepository.connectToSmartspace()
        ambientCueViewModel.activate()
        createCueBar()
    }

    private fun createCueBar() {
        val viewModelFactory = object : AmbientCueViewModel.Factory {
            override fun create(): AmbientCueViewModel {
                return ambientCueViewModel
            }
        }
        val composeView = QuickstepComposeFacade.initComposeView(activity) as ComposeView
        val ambientCueJankMonitor =
            AmbientCueJankMonitor(InteractionJankMonitor.getInstance(), composeView)
        cueBar = QuickstepComposeFacade.startCueBar(
            view = composeView,
            ambientCueViewModelFactory = viewModelFactory,
            onShouldInterceptTouches = { _, _ -> },
            onAnimationStateChange = { cujType, animationState ->
                ambientCueJankMonitor.onAnimationStateChange(
                    cujType,
                    animationState,
                )
                handleAnimationStateChange(animationState)
            })
    }

    private fun handleAnimationStateChange(state: AmbientCueAnimationState) {
        if (isHiding && state == AmbientCueAnimationState.END) {
            Log.d(TAG, "Animation finished and view is marked for hiding. Removing view.")
            if (cueBar != null && cueBar?.parent != null) {
                taskbarControllers.taskbarOverlayController.hideWindow()
            }
        }
    }

    fun cleanUpOverlay() {
        if (mOverlayContext == null) {
            return;
        }
        isHiding = false
        internalComposeView?.disposeComposition()
        mOverlayContext?.dragLayer?.removeView(internalComposeView)
        internalComposeView = null
        mOverlayContext = null
    }

    fun onDestroy() {
        if (!cueBarAceMigration()) {
            return
        }
        Log.d(TAG, "CuebarController destroy")
        cleanUpOverlay()
        taskbarControllers.sharedState?.cueBarVisible = false
        ambientCueViewModel.deactivate()
        ambientCueRepository.disconnectFromSmartspace()
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "CueBarController"
        private const val STASHED_HANDLE_ALPHA_ANIMATION_DURATION_MS = 150L
    }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println("$prefix CueBarController:")
    }

    fun onTaskbarStatusUpdated(visible: Boolean, stashed: Boolean) {
        if (!cueBarAceMigration()) {
            return
        }
        ambientCueRepository.isTaskBarVisible.dispatchValue(visible && !stashed)
    }

    private fun onCueBarVisibilityChanged(isCueBarVisible: Boolean) {
        if (!cueBarAceMigration()) {
            return
        }
        taskbarControllers.sharedState?.cueBarVisible = isCueBarVisible

        // Animate stashHandle alpha.
        val stashedHandleAlpha = taskbarControllers.stashedHandleViewController
            .stashedHandleAlpha
            .get(StashedHandleViewController.ALPHA_INDEX_CUEBAR_HIDDEN)
        val targetAlpha = if (isCueBarVisible) 0f else 1f
        stashedHandleAlpha.animateToValue(targetAlpha)
            .setDuration(STASHED_HANDLE_ALPHA_ANIMATION_DURATION_MS).start()

        if (isCueBarVisible) {
            isHiding = false // Cancel any pending hide

            if (cueBar?.parent == null || mOverlayContext == null) {
                Log.w(TAG, "CueBar parent is null. Window was likely destroyed. Re-requesting.")
                ambientCueRepository.connectToSmartspace()
                ambientCueViewModel.activate()
                // Get a new or existing window context
                mOverlayContext = taskbarControllers.taskbarOverlayController.requestWindow()
                createCueBar()
            }
            mOverlayContext?.dragLayer?.addView(cueBar)
            cueBar?.layoutParams = lp
            internalComposeView = cueBar as ComposeView
            cueBar?.visibility = View.GONE
            // Set the persistent view to VISIBLE. The compose state will
            // automatically trigger the entry animation.
            cueBar?.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Marking CueBarView for hiding. Will set GONE after animation.")
            isHiding = true
        }
    }
}
