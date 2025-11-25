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
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.taskbar.CueBarInsightRendererService
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.IconModel
import com.android.quickstep.cuebar.data.InsightListener
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
import java.lang.ref.WeakReference
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
    fun connectToAce()
    fun disconnectFromAce()
    fun dump(pw: PrintWriter, prefix: String)
}

class AmbientCueRepositoryImpl
@Inject
constructor(
    private val context: TaskbarActivityContext,
    private val ambientCueLogger: AmbientCueLogger,
    @Background private val bgExecutor: Executor,
    @Ui private val uiExecutor: Executor
) : AmbientCueRepository, InsightListener {

    private val backgroundScope = CoroutineScope(bgExecutor.asCoroutineDispatcher())
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

    private val _globallyFocusedTaskId = MutableListenableRef(INVALID_TASK_ID)
    override val globallyFocusedTaskId: ListenableRef<Int> = _globallyFocusedTaskId

    private val _frontTaskPackageName = MutableListenableRef("")
    override val frontTaskPackageName: ListenableRef<String> = _frontTaskPackageName

    private var debounceTaskJob: Job? = null

    private val taskStackListener = AmbientCueTaskStackListener(WeakReference(this), bgExecutor)

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

    /**
     * The [RunningTaskInfo] for the task that is currently in the foreground. Updated whenever a
     * new task moves to the front. Used to derive the package name for logging and for CueBar to
     * display itself when the user is actually looking at the target app by checking
     * globallyFocusedTaskId == targetTaskId in the viewmodel.
     */
    internal fun onTaskMovedToFront(runningTaskInfo: RunningTaskInfo) {
        debounceTaskJob?.cancel()
        debounceTaskJob =
            backgroundScope.launch {
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
            val timeout =
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS,
                )
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
        pw.println("$prefix   globallyFocusedTaskId: ${globallyFocusedTaskId.value}")
        pw.println("$prefix  debounceTaskJob active: ${debounceTaskJob?.isActive == true}")
        pw.println("$prefix  frontTaskPackageName: ${frontTaskPackageName.value}")
    }

    override fun onInsightReceived(insight: List<ContextInsight>) {
        uiExecutor.execute {
            if (insight.isEmpty()) {
                updateActions(emptyList())
                return@execute
            }
            val actions = insight.flatMap { mapInsightToActions(it) }
            if (actions.isNotEmpty()) {
                isDeactivated.dispatchValue(false)
            }
            updateActions(actions)
        }
    }

    private fun mapInsightToActions(insight: ContextInsight): List<ActionModel> {
        if (insight is ActionableInsight) {
            val originHints = insight.originHints
            if (originHints.isNotEmpty()) {
                originHints.forEach { hint ->
                    if (hint.contextHint is BundleHint) {
                        if ((hint.contextHint as BundleHint).dataBundle.getBoolean(
                                RENDER_IN_CUE_BAR, false)) {
                            return mapActionableInsight(insight)
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun mapActionableInsight(insight: ActionableInsight): List<ActionModel> {
        val display = insight.displayDetails
        val action = insight.actionDetails

        // TODO: Understander need to supply the smartSpaceTargetAction.subtitle to
        //  display.contentDescription? or use bundle extra for this
        val attribution = display.contentDescription?.toString()
        val iconDrawable = display.icon?.loadDrawable(context)
            ?: context.getDrawable(R.drawable.ic_paste_spark)!!

        val title = display.title.toString()
        val extras = action.createActionIntent()?.extras
        val activityId = extras?.getParcelable<ActivityId>(EXTRA_ACTIVITY_ID)
        val actionType = extras?.getString(EXTRA_ACTION_TYPE)
        val oneTapEnabled = extras?.getBoolean(EXTRA_ONE_TAP_ENABLED)
        val oneTapDelayMs = extras?.getLong(
            EXTRA_ONE_TAP_DELAY_MS,
            DEFAULT_ONE_TAP_DELAY_MS,
        )
        return listOf(ActionModel(
            icon = IconModel(
                small = iconDrawable.mutate(),
                large = iconDrawable.mutate(),
                iconId = display.icon?.resPackage + "#" + display.icon?.resId,
            ),
            label = title,
            attribution = attribution,
            onPerformAction = {
                val autofillId = extras?.getParcelable<AutofillId>(EXTRA_AUTOFILL_ID)
                val token = activityId?.token
                if (token != null && autofillId != null) {
                    autofillManager?.autofillRemoteApp(
                        autofillId,
                        title,
                        token,
                        activityId.taskId,
                    )
                } else if (action.hasActionType(
                        InsightActionDetails.ACTION_TYPE_REMOTE_ACTION)) {
                    action.remoteAction?.actionIntent?.send()
                } else if (action.hasActionType(InsightActionDetails.ACTION_TYPE_INTENT)) {
                    action.createActionIntent()?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
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
                // TODO: b/458508340 Proper design for attribution/feedback.
                val pendingIntent =
                    extras?.getParcelable<PendingIntent>(EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT)
                if (pendingIntent != null) {
                    Log.i(TAG, "Performing long click: $pendingIntent")
                    launchPendingIntent(pendingIntent)
                }
            },
            taskId = activityId?.taskId ?: INVALID_TASK_ID,
            actionType = actionType,
            oneTapEnabled = oneTapEnabled == true,
            oneTapDelayMs = oneTapDelayMs ?: DEFAULT_ONE_TAP_DELAY_MS,
        ))
    }

    override fun connectToAce() {
        if (!isAmbientCueEnabled.value) {
            Log.d(TAG, "Ace listener register skipped: Ambient Cue setting is disabled.")
            return
        }
        Log.d(TAG, "connectToAce: " + "connecting TO ACE and registering")
        CueBarInsightRendererService.registerListener(this)
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackListener)
    }

    override fun disconnectFromAce() {
        CueBarInsightRendererService.unregisterListener(this)
        backgroundScope.cancel()
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackListener)
    }

    companion object {
        // In-coming intent extras from the intelligent service.
        @VisibleForTesting const val EXTRA_ACTIVITY_ID = "activityId"
        @VisibleForTesting const val EXTRA_AUTOFILL_ID = "autofillId"
        @VisibleForTesting
        const val EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT = "attributionDialogPendingIntent"
        @VisibleForTesting const val EXTRA_ACTION_TYPE = "actionType"
        private const val EXTRA_ONE_TAP_ENABLED = "oneTapEnabled"
        private const val EXTRA_ONE_TAP_DELAY_MS = "oneTapDelayMs"
        private const val DEFAULT_ONE_TAP_DELAY_MS = 200L
        private const val RENDER_IN_CUE_BAR = "renderInCueBar"

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

/**
 * Wrapper class to hold the TaskStackChangeListener logic outside of the AmbientCueRepositoryImpl
 * instance, using a WeakReference to prevent the global TaskStackChangeListeners singleton
 * from leaking the entire repository and its associated context.
 */
private class AmbientCueTaskStackListener(
    private val repositoryRef: WeakReference<AmbientCueRepositoryImpl>,
    private val bgExecutor: Executor,
) : TaskStackChangeListener {

    override fun onTaskMovedToFront(runningTaskInfo: RunningTaskInfo) {
        val repository = repositoryRef.get() ?: return
        // Defer to background executor to handle any non-UI work since TaskStackChangeListener
        // can be called on a Binder thread. This then dispatches to the UI executor inside the
        // repository.
        bgExecutor.execute {
                repository.onTaskMovedToFront(runningTaskInfo)
        }
    }
}
