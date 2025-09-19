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

package com.android.launcher3.integration.util

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.Wait.atMost
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import java.util.function.Supplier
import org.junit.rules.ExternalResource

open class LauncherActivityScenarioRule<LAUNCHER_TYPE : Launcher> : ExternalResource() {

    private val uiDevice = UiDevice.getInstance(getInstrumentation())

    private var currentScenario: ActivityScenario<LAUNCHER_TYPE>? = null

    val activity: ActivityScenario<LAUNCHER_TYPE>
        get() =
            currentScenario
                ?: ActivityScenario.launch<LAUNCHER_TYPE>(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setPackage(getInstrumentation().targetContext.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        null,
                    )
                    .also { currentScenario = it }

    fun initializeActivity() {
        currentScenario?.recreate()
        activity.moveToState(Lifecycle.State.RESUMED)
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.UI_HELPER_EXECUTOR) {}
        getInstrumentation().waitForIdleSync()
    }

    override fun after() {
        close()
    }

    fun close() {
        currentScenario?.close()
        currentScenario = null
    }

    fun executeOnLauncher(f: ActivityAction<LAUNCHER_TYPE>): ActivityScenario<LAUNCHER_TYPE> =
        activity.onActivity(f)

    fun <T> getFromLauncher(f: Function<in LAUNCHER_TYPE, out T?>): T? {
        val result = AtomicReference<T>()
        activity.onActivity { result.set(f.apply(it)) }
        return result.get()
    }

    fun goToState(state: LauncherState) {
        val stateWaiter = createStateWaiter(state)
        executeOnLauncher { it.stateManager.goToState(state, 0) }
        stateWaiter.waitForSignal()
    }

    fun <T> getOnceNotNull(message: String, f: Function<LAUNCHER_TYPE, T?>): T? {
        var output: T? = null
        atMost(
            message,
            {
                val fromLauncher = getFromLauncher<T> { f.apply(it) }
                output = fromLauncher
                fromLauncher != null
            },
        )
        return output
    }

    @JvmOverloads
    fun injectKeyEvent(keyCode: Int, actionDown: Boolean, metaState: Int = 0) {
        uiDevice.waitForIdle()
        val eventTime = SystemClock.uptimeMillis()
        val event =
            KeyEvent(
                eventTime,
                eventTime,
                if (actionDown) KeyEvent.ACTION_DOWN else MotionEvent.ACTION_UP,
                keyCode,
                /* repeat= */ 0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                /* scancode= */ 0,
                /* flags= */ 0,
                InputDevice.SOURCE_KEYBOARD,
            )
        executeOnLauncher { it.dispatchKeyEvent(event) }
    }

    fun isInState(state: Supplier<LauncherState>): Boolean =
        getFromLauncher { it.stateManager.state == state.get() }!!
}
