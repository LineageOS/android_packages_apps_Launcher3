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

package com.android.launcher3.util.rule

import android.util.Log
import com.android.launcher3.Flags
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// TODO(b/377678992): revert ag/36923883 once NexusLauncherTests-OverviewInWindowEnabled is
//  successfully blocking presubmit.
class RecentsWindowTestFilterRule : TestRule {

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class AllowInRecentsWindowTests

    override fun apply(base: Statement, description: Description): Statement {
        val annotation =
            description.getAnnotation(AllowInRecentsWindowTests::class.java)
                ?: description.testClass.getAnnotation(AllowInRecentsWindowTests::class.java)
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                try {
                    val launcherOverviewInWindowEnabled = Flags.enableLauncherOverviewInWindow()
                    val fallbackOverviewInWindowEnabled = Flags.enableFallbackOverviewInWindow()
                    val overviewInWindowEnabled =
                        launcherOverviewInWindowEnabled || fallbackOverviewInWindowEnabled
                    assumeTrue(
                        "Skipping unannotated test because ${
                            if (launcherOverviewInWindowEnabled)
                                "enable_launcher_overview_in_window"
                            else
                                "enable_fallback_overview_in_window"} is enabled",
                        !overviewInWindowEnabled || annotation != null,
                    )
                    base.evaluate()
                } catch (e: Throwable) {
                    Log.e("RecentsWindowTestFilterRule", "Error", e)
                    throw e
                }
            }
        }
    }
}
