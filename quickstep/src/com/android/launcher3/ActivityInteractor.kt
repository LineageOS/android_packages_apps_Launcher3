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

package com.android.launcher3

import androidx.annotation.AnyThread
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener
import com.android.launcher3.Flags.enableUnfoldStateAnimation
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

/** Expose [StatefulActivity] APIs to taskbar rendered on per-window UI thread. */
@ThreadSafe
open class ActivityInteractor(private val statefulActivity: StatefulActivity<*>) {

    // TODO(b/404636836): Evaluate if exposing RecentsViewContainer to taskbar is thread safe.:
    @get:AnyThread val recentsViewContainer = statefulActivity as? RecentsViewContainer

    @AnyThread fun isActivitySameObj(obj: Any?) = statefulActivity === obj

    @AnyThread fun getDeviceProfile(): DeviceProfile = statefulActivity.mDeviceProfile

    @AnyThread fun getDisplayId(): Int = statefulActivity.displayId

    @AnyThread
    fun addEventCallback(
        event: Int,
        callback: Runnable,
        callbackExecutor: Executor,
    ): SafeCloseable {
        val wrappedCallback = Runnable { callbackExecutor.execute(callback) }
        MAIN_EXECUTOR.execute { statefulActivity.addEventCallback(event, wrappedCallback) }
        return SafeCloseable {
            MAIN_EXECUTOR.execute { statefulActivity.removeEventCallback(event, wrappedCallback) }
        }
    }

    @AnyThread
    open fun getUnfoldTransitionProvider(): UnfoldTransitionProgressProvider? {
        return if (enableUnfoldStateAnimation()) {
            SystemUiProxy.INSTANCE.get(statefulActivity).unfoldTransitionProvider
        } else {
            null
        }
    }

    @AnyThread
    fun addUnfoldTransitionCallback(
        callback: TransitionProgressListener,
        callbackExecutor: Executor,
    ): SafeCloseable? {
        val unfoldTransitionProvider = getUnfoldTransitionProvider() ?: return null
        val wrappedCallback =
            object : TransitionProgressListener {
                override fun onTransitionStarted() {
                    callbackExecutor.execute { callback.onTransitionStarted() }
                }

                override fun onTransitionProgress(progress: Float) {
                    callbackExecutor.execute { callback.onTransitionProgress(progress) }
                }

                override fun onTransitionFinishing() {
                    callbackExecutor.execute { callback.onTransitionFinishing() }
                }

                override fun onTransitionFinished() {
                    callbackExecutor.execute { callback.onTransitionFinished() }
                }
            }
        MAIN_EXECUTOR.execute { unfoldTransitionProvider.addCallback(wrappedCallback) }
        return SafeCloseable {
            MAIN_EXECUTOR.execute { unfoldTransitionProvider.removeCallback(wrappedCallback) }
        }
    }

    @AnyThread
    fun addOnDeviceProfileChangeListener(
        listener: OnDeviceProfileChangeListener,
        callbackExecutor: Executor,
    ): SafeCloseable {
        val wrappedListener = OnDeviceProfileChangeListener { dp ->
            callbackExecutor.execute { listener.onDeviceProfileChanged(dp) }
        }
        MAIN_EXECUTOR.execute { statefulActivity.addOnDeviceProfileChangeListener(wrappedListener) }
        return SafeCloseable {
            MAIN_EXECUTOR.execute {
                statefulActivity.removeOnDeviceProfileChangeListener(wrappedListener)
            }
        }
    }
}
