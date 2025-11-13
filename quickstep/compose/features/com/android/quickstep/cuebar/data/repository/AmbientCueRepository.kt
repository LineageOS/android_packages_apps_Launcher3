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
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.IconModel
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: ListenableRef<List<ActionModel>>

    /** If IME is visible or not. */
    val isImeVisible: ListenableRef<Boolean>

    /** If the UI is occluded. (Hard to determine globally from Launcher) */
    val isOccludedBySystemUi: ListenableRef<Boolean>

    /** If the UI is deactivated, such as closed by user or not used for a long period. */
    val isDeactivated: MutableListenableRef<Boolean>

    /** If the taskbar is fully visible and not stashed. */
    val isTaskBarVisible: MutableListenableRef<Boolean>

    /** True if in gesture nav mode, false when in 3-button navbar. */
    val isGestureNav: MutableListenableRef<Boolean>

    val recentsButtonPosition: MutableListenableRef<Rect?>

    /* If AmbientCue is enabled. */
    val isAmbientCueEnabled: ListenableRef<Boolean>

    /* The timeout for Ambient Cue to disappear. */
    val ambientCueTimeoutMs: ListenableRef<Int>

    /** Task Id which is globally focused on display. */
    val globallyFocusedTaskId: ListenableRef<Int>

    /** The package name of the task that is in the foreground. */
    val frontTaskPackageName: ListenableRef<String>

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

    private val _actions = MutableListenableRef<List<ActionModel>>(emptyList())
    override val actions: MutableListenableRef<List<ActionModel>> = _actions

    override val isDeactivated = MutableListenableRef(false)

    // These need to be updated from outside, e.g., by CuebarController
    override val isTaskBarVisible = MutableListenableRef(true)
    override val isGestureNav = MutableListenableRef(context.isGestureNav)
    override val recentsButtonPosition = MutableListenableRef<Rect?>(null)

    // IME and Occlusion are hard to track from Launcher for other apps.
    private val _isImeVisible = MutableListenableRef(false)
    override val isImeVisible: ListenableRef<Boolean> = _isImeVisible

    private val _isOccludedBySystemUi = MutableListenableRef(false)
    override val isOccludedBySystemUi: ListenableRef<Boolean> = _isOccludedBySystemUi

    private val _isAmbientCueEnabled = MutableListenableRef(isAmbientCueSettingEnabled())
    override val isAmbientCueEnabled: ListenableRef<Boolean> = _isAmbientCueEnabled

    private val _ambientCueTimeoutMs = MutableListenableRef(getAmbientCueTimeoutMs())
    override val ambientCueTimeoutMs: ListenableRef<Int> = _ambientCueTimeoutMs

    private var smartspaceSession: SmartspaceSession? = null
    private var smartspaceJob: Job? = null

    private val _globallyFocusedTaskId = MutableListenableRef(INVALID_TASK_ID)
    override val globallyFocusedTaskId: ListenableRef<Int> = _globallyFocusedTaskId

    private val _frontTaskPackageName = MutableListenableRef("")
    override val frontTaskPackageName: ListenableRef<String> = _frontTaskPackageName

    private var debounceTaskJob: Job? = null

    /**
     * The [RunningTaskInfo] for the task that is currently in the foreground. Updated whenever a
     * new task moves to the front. Used to derive the package name for logging and for CueBar to
     * display itself when the user is actually looking at the target app by checking
     * globallyFocusedTaskId == targetTaskId in the viewmodel.
     */
    private val taskStackListener = object : TaskStackChangeListener {
        override fun onTaskMovedToFront(runningTaskInfo: RunningTaskInfo) {
            debounceTaskJob?.cancel()
            debounceTaskJob = backgroundScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                withContext(uiExecutor.asCoroutineDispatcher()) {
                    _globallyFocusedTaskId.dispatchValue(runningTaskInfo.taskId)
                    _frontTaskPackageName.dispatchValue(
                        runningTaskInfo.baseIntent?.component?.packageName ?: ""
                    )
                    debounceTaskJob = null
                }
            }
        }
    }

    init {
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackListener)
    }

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
        if (actions.isNotEmpty()) {
            isDeactivated.dispatchValue(false)
        }
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
        backgroundScope.cancel()
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackListener)
    }

    override fun updateActions(newActions: List<ActionModel>) {
        _actions.dispatchValue(newActions)
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
        pw.println("$prefix   isDeactivated: ${isDeactivated.value}")
        pw.println("$prefix   isImeVisible: ${isImeVisible.value} (STUBBED)")
        pw.println("$prefix   isOccludedBySystemUi: ${isOccludedBySystemUi.value} (STUBBED)")
        pw.println("$prefix   isTaskBarVisible: ${isTaskBarVisible.value}")
        pw.println("$prefix   isGestureNav: ${isGestureNav.value}")
        pw.println("$prefix   actions: ${actions.value.size} actions")
        pw.println("$prefix   isAmbientCueEnabled: ${isAmbientCueEnabled.value}")
        pw.println("$prefix   ambientCueTimeoutMs: ${ambientCueTimeoutMs.value}")
        pw.println("$prefix   Smartspace Session active: ${smartspaceSession != null}")
        pw.println("$prefix   globallyFocusedTaskId: ${globallyFocusedTaskId.value}")
        pw.println("$prefix  debounceTaskJob active: ${debounceTaskJob?.isActive == true}")
        pw.println("$prefix  frontTaskPackageName: ${frontTaskPackageName.value}")
    }

    companion object {
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
