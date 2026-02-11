/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.app.tracing.TraceStateLogger
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StateManager.StateListener
import com.android.launcher3.statemanager.StatefulContainer

class TraceStateLoggerHelper
@JvmOverloads
constructor(
    val displayId: Int,
    val traceStateLogger: TraceStateLogger =
        TraceStateLogger("Current state display $displayId", logcat = true),
) {
    fun <T : BaseState<T>> startTraceStateLogger(statefulContainer: StatefulContainer<T>) {
        val stateListener =
            object : StateListener<T> {
                override fun onStateTransitionStart(toState: T) {
                    super.onStateTransitionStart(toState)
                    traceStateLogger.log(toState.toString())
                }
            }
        statefulContainer.stateManager.addStateListener(stateListener)
        statefulContainer.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    super.onDestroy(owner)
                    traceStateLogger.log(DESTROYED_STATE)
                    statefulContainer.stateManager.removeStateListener(stateListener)
                }
            }
        )
    }

    companion object {
        const val DESTROYED_STATE = "DESTROYED"
    }
}
