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

package com.android.quickstep.cuebar.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeRect
import androidx.core.content.edit
import com.android.launcher3.LauncherPrefs
import com.android.quickstep.cuebar.domain.interactor.AmbientCueInteractor
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.PrintWriter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class AmbientCueViewModel
@AssistedInject
constructor(
    private val ambientCueInteractor: AmbientCueInteractor,
    private val launcherPrefs: LauncherPrefs,
    private val applicationContext: Context,
    private val ambientCueLogger: AmbientCueLogger,
    private val scope: CoroutineScope
) : ViewModel {

    val isRootViewAttached: StateFlow<Boolean> = ambientCueInteractor.isRootViewAttached
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    val isImeVisible: StateFlow<Boolean> = ambientCueInteractor.isImeVisible
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    // Assuming a similar concept can be driven from Launcher's state
    val isOccluded: StateFlow<Boolean> = ambientCueInteractor.isOccludedBySystemUi
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    val ambientCueTimeoutMs: StateFlow<Int> = ambientCueInteractor.ambientCueTimeoutMs
        .stateIn(scope, SharingStarted.WhileSubscribed(), AMBIENT_CUE_TIMEOUT_MS)

    private val _isVisible =
        combine(isRootViewAttached, isImeVisible, isOccluded) { attached, ime, occluded
            -> attached && !ime && !occluded
    }.stateIn(scope, SharingStarted.WhileSubscribed(), false)
    val isVisible: Boolean get() = _isVisible.value

    var isExpanded: Boolean by mutableStateOf(false)
        private set

    // TODO: LauncherPrefs integration
    private fun getSharedPreferences(): SharedPreferences =
        applicationContext.getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    private val firstTimeEducationShownAt: Flow<Long> =
        kotlinx.coroutines.flow.flow {
            // TODO: Implement a way to observe Long from SharedPreferences in Launcher
            // This is a simplified version. LauncherPrefs might need a listener.
            emit(getSharedPreferences().getLong(KEY_FIRST_TIME_ONBOARDING_SHOWN_AT, -1L))
        }

    private val shouldShowLongPressEducation: Flow<Boolean> =
        kotlinx.coroutines.flow.flow {
            // TODO: Implement a way to observe Boolean from SharedPreferences in Launcher
            emit(getSharedPreferences().getBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, true))
        }

    val showFirstTimeEducation: StateFlow<Boolean> = firstTimeEducationShownAt
        .map { it == -1L }
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    val showLongPressEducation: StateFlow<Boolean> = combine(
        shouldShowLongPressEducation,
        firstTimeEducationShownAt,
        ambientCueInteractor.isRootViewAttached,
    ) { shouldShow, firstTimeShown, _ ->
        val firstTimeSeenAtMs =
            (if (firstTimeShown == -1L) SystemClock.elapsedRealtime() else firstTimeShown)
                .milliseconds
        firstTimeSeenAtMs + ONBOARDING_DELAY <
                SystemClock.elapsedRealtime().milliseconds && shouldShow
    }.stateIn(scope, SharingStarted.WhileSubscribed(), false)

    val pillStyle: StateFlow<PillStyleViewModel> = combine(
        ambientCueInteractor.isGestureNav,
        ambientCueInteractor.isTaskBarVisible,
        ambientCueInteractor.recentsButtonPosition,
    ) { isGestureNav, isTaskBarVisible, recentsButtonPosition ->
        if (isGestureNav && !isTaskBarVisible) {
            PillStyleViewModel.NavBarPillStyle
        } else {
            val position = if (isGestureNav) null else recentsButtonPosition
            PillStyleViewModel.ShortPillStyle(position?.toComposeRect())
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), PillStyleViewModel.Uninitialized)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val actions: StateFlow<List<ActionViewModel>> = ambientCueInteractor.actions
        .debounce { actions -> if (actions.isEmpty()) ACTIONS_DEBOUNCE_MS else 0L }
        .map { actions ->
            actions.map { action ->
                ActionViewModel(
                    icon = IconViewModel(
                        large = action.icon.large,
                        small = action.icon.small,
                        iconId = action.icon.iconId,
                        repeatCount = 0,
                    ),
                    label = action.label,
                    attribution = action.attribution,
                    onClick = {
                        action.onPerformAction()
                        collapse()
                    },
                    onLongClick = {
                        action.onPerformLongClick()
                        getSharedPreferences().edit {
                            putBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, false)
                        }
                    },
                    actionType = when (action.actionType) {
                        "ma" -> ActionType.MA
                        "mr" -> ActionType.MR
                        else -> ActionType.Unknown
                    },
                    oneTapEnabled = action.oneTapEnabled,
                    oneTapDelayMs = action.oneTapDelayMs,
                )
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    fun expand() {
        isExpanded = true
        disableFirstTimeHint()
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            disableLongPressHint()
        }
    }

    fun hide() {
        ambientCueInteractor.setDeactivated(true)
        isExpanded = false
        disableFirstTimeHint()
        ambientCueLogger.setClickedCloseButtonStatus()
    }

    private var deactivateCueBarJob: Job? = null
    private var lifecycleJob: Job? = null

    fun cancelDeactivation() {
        deactivateCueBarJob?.cancel()
    }

    suspend fun delayAndDeactivateCueBar() {
        deactivateCueBarJob?.cancel()
        deactivateCueBarJob = scope.launch {
            delay(ambientCueTimeoutMs.value.milliseconds)
            ambientCueInteractor.setDeactivated(true)
            Log.i(TAG, "Cuebar deactivated due to timeout")
            ambientCueLogger.setReachedTimeoutStatus()
        }
    }

    fun activate() {
        lifecycleJob = scope.launch {
            ambientCueInteractor.isRootViewAttached.collectLatest { isAttached ->
                if (!isAttached) {
                    cancelDeactivation()
                    return@collectLatest
                }
                delayAndDeactivateCueBar()
            }
        }
    }

    fun deactivate() {
        lifecycleJob?.cancel()
        cancelDeactivation()
    }

    fun disableFirstTimeHint() {
        if (showFirstTimeEducation.value) {
            getSharedPreferences().edit {
                Log.i(TAG, "Suppressing first time tooltip")
                putLong(KEY_FIRST_TIME_ONBOARDING_SHOWN_AT, SystemClock.elapsedRealtime())
            }
        }
    }

    private fun disableLongPressHint() {
        if (showLongPressEducation.value) {
            getSharedPreferences().edit {
                Log.i(TAG, "Suppressing long press tooltip")
                putBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, false)
            }
        }
    }

    fun dump(pw: PrintWriter, prefix: String) {
        pw.println("$prefix AmbientCueViewModel:")
        pw.println("$prefix   isRootViewAttached: ${isRootViewAttached.value}")
        pw.println("$prefix   isImeVisible: ${isImeVisible.value}")
        pw.println("$prefix   isVisible: $isVisible")
        pw.println("$prefix   isExpanded: $isExpanded")
        pw.println("$prefix   pillStyle: ${pillStyle.value}")
        pw.println("$prefix   deactivateCueBarJob: $deactivateCueBarJob")
        pw.println("$prefix   actions: ${actions.value.size} actions")
        pw.println("$prefix   ambientCueTimeoutMs: ${ambientCueTimeoutMs.value}")
    }

    @AssistedFactory
    interface Factory {
        fun create(): AmbientCueViewModel
    }

    companion object {
        private const val TAG = "LauncherAmbientCueVM"
        const val AMBIENT_CUE_TIMEOUT_MS = 30_000
        private val ONBOARDING_DELAY = 7.days
        private const val SHARED_PREFERENCES_FILE_NAME = "ambientcue_pref_launcher"
        private const val KEY_FIRST_TIME_ONBOARDING_SHOWN_AT = "show_first_time_onboarding"
        private const val KEY_SHOW_LONG_PRESS_ONBOARDING = "show_long_press_onboarding"
        private const val ACTIONS_DEBOUNCE_MS = 300L
    }
}
