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

package com.android.quickstep.cuebar.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.app.assist.ActivityId
import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.plugins.cuebar.IconModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: StateFlow<List<ActionModel>>

    /** If the root view is attached to the WindowManager. */
    val isRootViewAttached: StateFlow<Boolean>

    /** If IME is visible or not. */
    val isImeVisible: StateFlow<Boolean>

    /** If the UI is occluded. (Hard to determine globally from Launcher) */
    val isOccludedBySystemUi: StateFlow<Boolean>

    /** If the UI is deactivated, such as closed by user or not used for a long period. */
    val isDeactivated: MutableStateFlow<Boolean>

    /** If the taskbar is fully visible and not stashed. */
    val isTaskBarVisible: MutableStateFlow<Boolean>

    /** True if in gesture nav mode, false when in 3-button navbar. */
    val isGestureNav: MutableStateFlow<Boolean>

    val recentsButtonPosition: MutableStateFlow<Rect?>

    /* If AmbientCue is enabled. */
    val isAmbientCueEnabled: StateFlow<Boolean>

    /* The timeout for Ambient Cue to disappear. */
    val ambientCueTimeoutMs: StateFlow<Int>

    fun updateActions(newActions: List<ActionModel>)
    fun connectToSmartspace()
    fun disconnectFromSmartspace()
    fun dump(pw: PrintWriter, prefix: String)
}

class AmbientCueRepositoryImpl
@Inject constructor(
    private val context: TaskbarActivityContext,
    private val ambientCueLogger: AmbientCueLogger,
    @Background private val bgExecutor: Executor,
    @Ui private val uiExecutor: Executor
) : AmbientCueRepository {

    private val backgroundScope = CoroutineScope(bgExecutor.asCoroutineDispatcher())
    private val smartSpaceManager: SmartspaceManager? =
        context.getSystemService(SmartspaceManager::class.java)
    private val autofillManager: AutofillManager? =
        context.getSystemService(AutofillManager::class.java)

    private val _actions = MutableStateFlow<List<ActionModel>>(emptyList())
    override val actions: StateFlow<List<ActionModel>> = _actions.asStateFlow()

    override val isDeactivated = MutableStateFlow(false)

    /**
     * The [RunningTaskInfo] for the task that is currently in the foreground. Updated whenever a
     * new task moves to the front. Used to derive the package name for logging.
     */
    private var frontRunningTask: RunningTaskInfo? = null

    override val isTaskBarVisible = MutableStateFlow(true)
    override val isGestureNav = MutableStateFlow(context.isGestureNav)
    override val recentsButtonPosition = MutableStateFlow<Rect?>(null)

    private val _isImeVisible = MutableStateFlow(false)
    override val isImeVisible: StateFlow<Boolean> = _isImeVisible.asStateFlow()
    private val _isOccludedBySystemUi = MutableStateFlow(false)
    override val isOccludedBySystemUi: StateFlow<Boolean> = _isOccludedBySystemUi.asStateFlow()


    val targetTaskId: MutableStateFlow<Int> = MutableStateFlow(INVALID_TASK_ID)
    var isSessionStarted = false

    private val _isAmbientCueEnabled = MutableStateFlow(isAmbientCueSettingEnabled())
    override val isAmbientCueEnabled: StateFlow<Boolean> = _isAmbientCueEnabled.asStateFlow()

    private val _ambientCueTimeoutMs = MutableStateFlow(getAmbientCueTimeoutMs())
    override val ambientCueTimeoutMs: StateFlow<Int> = _ambientCueTimeoutMs.asStateFlow()

    override val isRootViewAttached: StateFlow<Boolean> =
        combine(isDeactivated, actions, isAmbientCueEnabled) {
                isDeactivated,
                actions,
                isAmbientCueEnabled ->
            actions.isNotEmpty() &&
                    isAmbientCueEnabled &&
                    !isDeactivated
        }
            .onEach { isAttached ->
                if (isAttached && !isSessionStarted) {
                    isSessionStarted = true
                    var maCount = 0
                    var mrCount = 0
                    val packageName = frontRunningTask?.baseIntent?.component?.packageName ?: ""
                    actions.value.forEach { action ->
                        when (action.actionType) {
                            MA_ACTION_TYPE_NAME -> maCount++
                            MR_ACTION_TYPE_NAME -> mrCount++
                            else -> {}
                        }
                    }
                    ambientCueLogger.setPackageName(packageName)
                    ambientCueLogger.setAmbientCueDisplayStatus(maCount, mrCount)
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    private var smartspaceSession: SmartspaceSession? = null
    private var smartspaceJob: Job? = null

    private val smartspaceListener = OnTargetsAvailableListener { targets ->
        Log.i(TAG, "Receiving SmartSpace targets # ${targets.size}")
        if (targets.none { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }) {
            return@OnTargetsAvailableListener
        }
        val actions =
            targets
                .filter { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }
                .flatMap { target -> target.actionChips }
                .mapNotNull { chip ->
                    val title = chip.title.toString()
                    val activityId = chip.extras?.getParcelable<ActivityId>(EXTRA_ACTIVITY_ID)
                    val actionType = chip.extras?.getString(EXTRA_ACTION_TYPE)
                    val oneTapEnabled = chip.extras?.getBoolean(EXTRA_ONE_TAP_ENABLED)
                    val oneTapDelayMs =
                        chip.extras?.getLong(
                            EXTRA_ONE_TAP_DELAY_MS,
                            DEFAULT_ONE_TAP_DELAY_MS,
                        )
                    ActionModel(
                        icon =
                            IconModel(
                                small =
                                    (chip.icon?.loadDrawable(context)
                                        ?: context.getDrawable(
                                            R.drawable.ic_paste_spark
                                        )!!)
                                        .mutate(),
                            large =
                                (chip.icon?.loadDrawable(context)
                                    ?: context.getDrawable(
                                        R.drawable.ic_paste_spark
                                    )!!)
                                    .mutate(),
                            iconId = chip.icon?.resPackage + "#" + chip.icon?.resId,
                        ),
                        label = title,
                        attribution = chip.subtitle?.toString(),
                        onPerformAction = {
                            val intent = chip.intent
                            val pendingIntent = chip.pendingIntent
                            val activityId =
                                chip.extras?.getParcelable<ActivityId>(
                                    EXTRA_ACTIVITY_ID
                                )
                            val autofillId =
                                chip.extras?.getParcelable<AutofillId>(EXTRA_AUTOFILL_ID)
                            val token = activityId?.token
                            Log.i(
                                TAG,
                                "Performing action: $activityId, $autofillId, " +
                                        "$pendingIntent, $intent",
                            )
                            if (token != null && autofillId != null) {
                                autofillManager?.autofillRemoteApp(
                                    autofillId,
                                    title,
                                    token,
                                    activityId.taskId,
                                )
                            } else if (pendingIntent != null) {
                                launchPendingIntent(pendingIntent)
                            } else if (intent != null) {
                                context.startActivity(intent)
                            }
                            if (actionType == MA_ACTION_TYPE_NAME) {
                                ambientCueLogger.setFulfilledWithMaStatus()
                            }
                            if (actionType == MR_ACTION_TYPE_NAME) {
                                ambientCueLogger.setFulfilledWithMrStatus()
                            }
                        },
                        onPerformLongClick = {
                            Log.i(TAG, "AmbientCueRepositoryImpl: onPerformLongClick")
                            val pendingIntent =
                                chip.extras?.getParcelable<PendingIntent>(
                                    EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT
                                )
                            if (pendingIntent != null) {
                                Log.i(TAG, "Performing long click: $pendingIntent")
                                launchPendingIntent(pendingIntent)
                            }
                        },
                        taskId = activityId?.taskId ?: -1,
                        actionType = actionType,
                        oneTapEnabled = oneTapEnabled == true,
                        oneTapDelayMs = oneTapDelayMs ?: DEFAULT_ONE_TAP_DELAY_MS,
                    )
                }
        Log.i(TAG, "SmartSpace actions $actions")
        updateActions(actions)
    }

    private fun launchPendingIntent(pendingIntent: PendingIntent) {
        val options = BroadcastOptions.makeBasic()
        options.isInteractive = true
        options.pendingIntentBackgroundActivityStartMode =
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        try {
            pendingIntent.send(options.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "pending intent of $pendingIntent was canceled", e)
        }
    }

    override fun connectToSmartspace() {
        if (!isAmbientCueEnabled.value) {
            Log.d(TAG, "Smartspace connection skipped: Ambient Cue setting is disabled.")
            return
        }
        if (smartspaceSession != null) {
            return
        }
        smartspaceJob = backgroundScope.launch {
            if (smartSpaceManager == null) {
                return@launch
            }
            val config = SmartspaceConfig.Builder(context, AMBIENT_CUE_SURFACE).build()
            smartspaceSession = smartSpaceManager.createSmartspaceSession(config)
            smartspaceSession?.addOnTargetsAvailableListener(uiExecutor, smartspaceListener)
            smartspaceSession?.requestSmartspaceUpdate()
        }
    }

    override fun disconnectFromSmartspace() {
        smartspaceJob?.cancel()
        smartspaceJob = null
        try {
            smartspaceSession?.removeOnTargetsAvailableListener(smartspaceListener)
            smartspaceSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Exception on closing Smartspace: $e", e)
        }
        smartspaceSession = null
    }

    override fun updateActions(newActions: List<ActionModel>) {
        _actions.value = newActions
    }

    private fun isAmbientCueSettingEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, AMBIENT_CUE_SETTING) == OPTED_IN
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(TAG, "$AMBIENT_CUE_SETTING not found, feature disabled", e)
            false
        }
    }

    private fun getAmbientCueTimeoutMs(): Int {
        return try {
            val timeout = Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS)
            if (timeout == 0) AMBIENT_CUE_DEFAULT_TIMEOUT_MS else timeout
        } catch (e: Settings.SettingNotFoundException) {
            AMBIENT_CUE_DEFAULT_TIMEOUT_MS
        }
    }

    override fun dump(pw: PrintWriter, prefix: String) {
        pw.println("$prefix AmbientCueRepositoryImpl:")
        pw.println("$prefix   isRootViewAttached: ${isRootViewAttached.value}")
        pw.println("$prefix   isDeactivated: ${isDeactivated.value}")
        pw.println("$prefix   isImeVisible: ${isImeVisible.value} (STUBBED)")
        pw.println("$prefix   isOccludedBySystemUi: ${isOccludedBySystemUi.value} (STUBBED)")
        pw.println("$prefix   isTaskBarVisible: ${isTaskBarVisible.value}")
        pw.println("$prefix   isGestureNav: ${isGestureNav.value}")
        pw.println("$prefix   actions: ${actions.value.size} actions")
        pw.println("$prefix   isAmbientCueEnabled: ${isAmbientCueEnabled.value}")
        pw.println("$prefix   ambientCueTimeoutMs: ${ambientCueTimeoutMs.value}")
        pw.println("$prefix   Smartspace Session active: ${smartspaceSession != null}")
    }

    companion object {
        // Surface that PCC wants to push cards into
        @VisibleForTesting const val AMBIENT_CUE_SURFACE = "ambientcue"
        // In-coming intent extras from the intelligent service.
        @VisibleForTesting const val EXTRA_ACTIVITY_ID = "activityId"
        @VisibleForTesting const val EXTRA_AUTOFILL_ID = "autofillId"
        @VisibleForTesting
        const val EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT = "attributionDialogPendingIntent"
        @VisibleForTesting const val EXTRA_ACTION_TYPE = "actionType"
        private const val EXTRA_ONE_TAP_ENABLED = "oneTapEnabled"
        private const val EXTRA_ONE_TAP_DELAY_MS = "oneTapDelayMs"
        private const val DEFAULT_ONE_TAP_DELAY_MS = 200L

        // Timeout to hide cuebar if it wasn't interacted with
        private const val TAG = "AmbientCueRepository"
        private const val DEBUG = false
        private const val INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID
        @VisibleForTesting const val AMBIENT_CUE_SETTING = "spoonBarOptedIn"
        @VisibleForTesting const val OPTED_IN = 0x10
        const val DEBOUNCE_DELAY_MS = 100L
        private const val AMBIENT_CUE_DEFAULT_TIMEOUT_MS = 30_000
        @VisibleForTesting const val MA_ACTION_TYPE_NAME = "ma"
        @VisibleForTesting const val MR_ACTION_TYPE_NAME = "mr"
    }
}
