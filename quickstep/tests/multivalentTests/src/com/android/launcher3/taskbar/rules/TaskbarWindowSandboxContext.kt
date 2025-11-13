/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.rules

import android.companion.datatransfer.continuity.TaskContinuityManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.launcher3.util.VirtualDisplaysRule
import com.android.launcher3.util.launcheremulator.LauncherCustomizer
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData
import com.android.launcher3.util.launcheremulator.models.EmulationParams
import com.android.quickstep.SystemUiProxy
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [SandboxApplication] for running Taskbar tests.
 *
 * Tests needs to declare this [Context] as a [TestRule] for it to work. Context operations will
 * fail before the rule's setup phase, because [base] will not be initialized and attached yet.
 * Premature access will throw a [NullPointerException].
 *
 * Tests need to run on a [VirtualDisplay] to avoid conflicting with Launcher's Taskbar on the
 * [DEFAULT_DISPLAY] (i.e. test is executing on a device).
 */
class TaskbarWindowSandboxContext private constructor(private val params: SandboxParams) :
    ContextWrapper(null), TestRule {
    lateinit var base: SandboxApplication
    val settingsCacheSandbox = SettingsCacheSandbox()
    lateinit var windowManagerSpy: WindowManager
    val taskContinuityManagerMock: TaskContinuityManager = mock<TaskContinuityManager>()

    val virtualDisplayRule = VirtualDisplaysRule()

    /**
     * Initializes and attaches [base] to `this` wrapper.
     *
     * The wrapper does not initialize and attach [base] in its constructor, because [base] depends
     * on [virtualDisplayRule] running for the display context.
     */
    private val attachBaseContextRule =
        object : ExternalResource() {
            override fun before() {
                val app = ApplicationProvider.getApplicationContext<Context>()
                val defaultDisplay =
                    checkNotNull(
                        app.resources.displayMetrics.let {
                            virtualDisplayRule[
                                virtualDisplayRule.add(
                                    it.widthPixels,
                                    it.heightPixels,
                                    it.densityDpi,
                                )]
                        }
                    )
                base = SandboxApplication(app.createDisplayContext(defaultDisplay.display))
                attachBaseContext(base)
            }
        }

    /**
     * Delegates to [base] at evaluation time, so that it is initialized when applied.
     *
     * [base] cannot be referenced in the [RuleChain] before initialization.
     */
    private val baseDelegateRule = TestRule { s, d ->
        object : Statement() {
            override fun evaluate() {
                base.apply(s, d).evaluate()
            }
        }
    }

    private val sandboxSpyServicesRule =
        object : ExternalResource() {
            override fun before() {
                // Filter out DEFAULT_DISPLAY in case code accesses displays property. The primary
                // virtual display has a different ID.
                val dm = base.spyService(DisplayManager::class.java)
                base.mockService(
                    Context.TASK_CONTINUITY_SERVICE,
                    TaskContinuityManager::class.java,
                    taskContinuityManagerMock,
                )
                whenever(dm.displays).thenAnswer { i ->
                    @Suppress("UNCHECKED_CAST")
                    val displays = i.callRealMethod() as? Array<Display> ?: emptyArray<Display>()
                    displays.filter { it.displayId != DEFAULT_DISPLAY }.toTypedArray()
                }

                windowManagerSpy = base.spyService(WindowManager::class.java)
                if (!DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue) {
                    // Have displays appear as if they support Taskbar.
                    whenever(windowManagerSpy.shouldShowSystemDecors(any())).thenReturn(true)
                }
            }
        }

    private val singletonSetupRule =
        object : ExternalResource() {
            override fun before() {
                val context = this@TaskbarWindowSandboxContext
                val builder =
                    params.builderBase
                        .bindSystemUiProxy(params.systemUiProxyProvider.invoke(context))
                        .bindSettingsCache(settingsCacheSandbox.cache)
                base.initDaggerComponent(builder)
            }
        }

    override fun apply(statement: Statement, description: Description): Statement {
        return RuleChain.outerRule(virtualDisplayRule)
            .around(attachBaseContextRule)
            .around(baseDelegateRule)
            .around(sandboxSpyServicesRule)
            .around(singletonSetupRule)
            .apply(statement, description)
    }

    companion object {
        private const val DEFAULT_DEVICE = "pixelTablet2023"
        private val ON_DEVICE_ONLY = listOf("onDevice")

        /**
         * Creates a [SandboxApplication] for Taskbar tests
         *
         * Specify [emulatedDeviceName] to mimic a device configuration in the deviceless
         * environment. To run a test with multiple configurations, use a parameterized test runner
         * with [getDeviceParams].
         */
        fun create(
            emulatedDeviceName: String = DEFAULT_DEVICE,
            params: SandboxParams = SandboxParams(),
        ): TaskbarWindowSandboxContext {
            if (isRunningInRobolectric) {
                LauncherCustomizer.applyAll(
                    ApplicationProvider.getApplicationContext(),
                    EmulationParams(DeviceEmulationData.getDevice(emulatedDeviceName)),
                )
            }
            return TaskbarWindowSandboxContext(params)
        }

        /**
         * Creates list of [deviceNames] to emulate for parameterized tests.
         *
         * Returns a dummy singleton list for on-device tests, where there is no emulation.
         */
        fun getDeviceParams(vararg deviceNames: String): List<String> {
            return if (isRunningInRobolectric) deviceNames.toList() else ON_DEVICE_ONLY
        }
    }
}

/** Include additional bindings when building a [TaskbarSandboxComponent]. */
data class SandboxParams(
    val systemUiProxyProvider: (Context) -> SystemUiProxy = {
        SystemUiProxy(it, MAIN_EXECUTOR, UI_HELPER_EXECUTOR)
    },
    val builderBase: TaskbarSandboxComponent.Builder = DaggerTaskbarSandboxComponent.builder(),
)
