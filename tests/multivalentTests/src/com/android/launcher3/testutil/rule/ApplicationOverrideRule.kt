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

package com.android.launcher3.testutil.rule

import android.app.Activity
import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SandboxApplication
import org.junit.rules.ExternalResource
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

/**
 * TestRule to override the base application for an activity
 *
 * @param mockitoRule defined to ensure that the rule is applied in the test
 */
class ApplicationOverrideRule(private val app: SandboxApplication, mockitoRule: MockitoRule) :
    ExternalResource() {

    private lateinit var instrumentation: Instrumentation

    override fun before() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.convertToSpy()

        doAnswer {
                val cl = it.getArgument(0, ClassLoader::class.java)
                val className = it.getArgument(1, String::class.java)
                val activity = spy(cl.loadClass(className)) as Activity
                doReturn(app).whenever(activity).applicationContext
                activity
            }
            .whenever(instrumentation)
            .newActivity(any(), any(), any())
    }

    override fun after() {
        reset(instrumentation)
    }
}
