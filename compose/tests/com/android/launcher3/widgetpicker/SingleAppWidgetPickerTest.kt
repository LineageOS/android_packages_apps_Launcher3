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

package com.android.launcher3.widgetpicker

import android.content.Intent
import android.os.Process
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.DisableAnimationsRule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags
import com.android.launcher3.helper.launchActivityWithIntent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sanity test to verify that app specific widget picker launches fine. */
@LargeTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_APP_WIDGET_PICKER_REFACTOR)
class SingleAppWidgetPickerTest {
    @get:Rule val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun hasWidgetsFromTestApp() =
        composeRule.launchActivityWithIntent<WidgetPickerActivity>(
            intentProvider = this::buildSingleAppWidgetPickerIntent
        ) {
            composeRule.waitUntilAtLeastOneExists(hasTestTag(WIDGET_PREVIEW_TEST_TAG), TEST_TIMEOUT)
            TestWidgets.forEach {
                composeRule.onNodeWithText(it, substring = true).assertIsDisplayed()
            }
        }

    private fun buildSingleAppWidgetPickerIntent() =
        Intent(Intent.ACTION_PICK).apply {
            val testAppContext = InstrumentationRegistry.getInstrumentation().context
            val launcherContext = InstrumentationRegistry.getInstrumentation().targetContext

            putExtra(Intent.EXTRA_PACKAGE_NAME, testAppContext.packageName)
            putExtra(Intent.EXTRA_USER, Process.myUserHandle())
            setPackage(launcherContext.packageName)
        }

    companion object {
        // sample test widgets from AndroidManifest-common.xml
        val TestWidgets = listOf("With Dialog", "With Config")
        private const val WIDGET_PREVIEW_TEST_TAG =
            "com.android.launcher3.widgetpicker:id/widget_preview"

        private const val TEST_TIMEOUT = 5_000L
    }
}
