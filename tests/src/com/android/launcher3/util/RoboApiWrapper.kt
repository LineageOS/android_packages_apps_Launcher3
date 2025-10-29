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

package com.android.launcher3.util

import android.os.Handler
import android.os.Looper
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.dx.mockito.inline.extended.MockedVoidMethod
import com.android.launcher3.util.rule.ScreenRecordRule
import com.android.launcher3.util.rule.ShellCommandRule
import java.util.concurrent.CompletableFuture
import org.junit.rules.MethodRule
import org.junit.rules.TestRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.mockito.Mockito.withSettings
import org.mockito.stubbing.Answer

object RoboApiWrapper {

    fun waitForLooperSync(looper: Looper) {
        CompletableFuture<Void>().apply { Handler(looper).post { complete(null) } }.get()
    }

    fun yieldToMainLooper() {
        // Intentionally left empty as this is not needed on device.
        // Main looper continues running anyway without the need to yield.
    }

    /** Rule to grant shortcuts permission */
    fun grantShortcutsPermissionRule(): TestRule = ShellCommandRule.setDefaultLauncher()

    /** Rule to grant widget bind permission */
    fun grantWidgetBindPermissionRule(): TestRule = ShellCommandRule.grantWidgetBind()

    /** Rule to screen record the device */
    fun screenRecordRule(): TestRule = ScreenRecordRule()

    fun Any.convertToSpy() {
        spyOn(this)
    }

    inline fun <reified T> staticMockHelper() = StaticMockHelper(T::class.java)

    class StaticMockHelper(val clazz: Class<*>) {
        fun whenever(method: MockedVoidMethod) = Stubber(method)

        class Stubber(val method: MockedVoidMethod) {

            fun thenReturn(value: Any?) = ExtendedMockito.doReturn(value).`when`(method)

            fun thenThrow(vararg throwables: Throwable?) =
                ExtendedMockito.doThrow(*throwables).`when`(method)

            fun thenAnswer(answer: Answer<*>) = ExtendedMockito.doAnswer(answer).`when`(method)
        }
    }

    /**
     * Rule for using static mocks in unit test. Separate implementations are provided for on-device
     * and robolectric tests, while keeping a common API signature.
     */
    class StaticMockRule(vararg val helpers: StaticMockHelper) : MethodRule {

        override fun apply(base: Statement, method: FrameworkMethod?, target: Any) =
            object : Statement() {
                override fun evaluate() {
                    val mockSession =
                        ExtendedMockito.mockitoSession()
                            .initMocks(target)
                            .apply {
                                helpers.forEach { mockStatic(it.clazz, withSettings().lenient()) }
                            }
                            .startMocking()
                    val error = kotlin.runCatching { base.evaluate() }.exceptionOrNull()

                    mockSession.finishMocking(error)
                    error?.let { throw error }
                }
            }
    }
}
