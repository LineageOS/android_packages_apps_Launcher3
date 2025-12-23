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

package com.android.quickstep.interaction

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.spy

@RunWith(AndroidJUnit4::class)
class AllSetActivityTest {
    private companion object {
        // Matches the value in AllSetActivity.java
        private const val WALLPAPER_BLUR_RADIUS = 30
    }

    private lateinit var scenario: ActivityScenario<AllSetActivity>
    private lateinit var spiedWindow: Window

    @Before
    fun setUp() {
        // Ensure the test is in expressive theming
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("setprop setupwizard.theme glif_expressive")

        // Spy the Activity's Window
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                // Since onActivityPreCreated() runs before onCreate(), spy the Window here
                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity is AllSetActivity) {
                        val realWindow = activity.window
                        spiedWindow = spy(realWindow)

                        // Replace the real Window with the spied one
                        try {
                            val activityClass = Activity::class.java
                            val windowField = activityClass.getDeclaredField("mWindow")
                            windowField.isAccessible = true
                            windowField[activity] = spiedWindow
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            }
        )

        val intent = Intent(app, AllSetActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("setprop setupwizard.theme \"\"")
    }

    @Test
    fun swipeUp_blurRadiusUnchanged() {
        verifyBlurRadius()

        // Swipe up partially and verify blur radius hasn't changed
        scenario.onActivity { activity -> activity.mSwipeProgress.updateValue(0.3f) }
        verifyBlurRadius()

        // Swipe up more and verify blur radius hasn't changed
        scenario.onActivity { activity -> activity.mSwipeProgress.updateValue(1.2f) }
        verifyBlurRadius()
    }

    private fun verifyBlurRadius() {
        scenario.onActivity { _ ->
            val lastBackgroundBlurRadiusInvocation =
                mockingDetails(spiedWindow).invocations.findLast {
                    it.method.name == "setBackgroundBlurRadius"
                }
            assertThat(lastBackgroundBlurRadiusInvocation?.arguments?.get(0))
                .isEqualTo(WALLPAPER_BLUR_RADIUS)
        }
    }
}
