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

package com.android.launcher3.widgetpicker.ui.components.floatingsheet

import android.platform.test.rule.DeniedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.NoOpWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.components.LocalWidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.WidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.accessibility.AccessibilityState
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeniedDevices(denied = [DeviceProduct.ROBOLECTRIC])
class TitledFloatingSheetTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var accessibilityState = AccessibilityState(isEnabled = false)

    private val topResumedChangedCallbacks: MutableList<(Boolean) -> Unit> = mutableListOf()

    @Test
    fun displayTitleAndContent() {
        composeTestRule.setContent { FloatingSheetTestContent() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(SHEET_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(CONTENT_TEXT).assertIsDisplayed()
    }

    @Test
    fun canCloseWithCloseButton() {
        composeTestRule.setContent { FloatingSheetTestContent() }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertExists()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription("Close sheet")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertDoesNotExist()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertExists()
    }

    @Test
    fun closesIfHostActivityIsNotTopResumed() {
        composeTestRule.setContent { FloatingSheetTestContent() }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertExists()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertDoesNotExist()

        topResumedChangedCallbacks.forEach { it(true) }

        // Remain open if the host activity is still top resumed.
        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertExists()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertDoesNotExist()

        topResumedChangedCallbacks.forEach { it(false) }

        // Remain open if the host activity becoming not top resumed closes the sheet.
        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertDoesNotExist()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertExists()
    }

    @Composable
    private fun FloatingSheetTestContent() {
        var isClosed by remember { mutableStateOf(false) }

        val hostStateProvider =
            object : WidgetPickerHostStateProvider {
                override fun observeIsTopResumed(listener: (Boolean) -> Unit) {
                    topResumedChangedCallbacks.add(listener)
                }

                override fun stopObservingIsTopResumed(listener: (Boolean) -> Unit) {
                    topResumedChangedCallbacks.remove(listener)
                }
            }

        WidgetPickerTheme {
            CompositionLocalProvider(
                LocalWidgetPickerCuiReporter provides NoOpWidgetPickerCuiReporter(),
                LocalAccessibilityState provides accessibilityState,
                LocalWidgetPickerHostStateProvider provides hostStateProvider,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isClosed) {
                        Text(CLOSED_TEXT)
                    } else {
                        TitledFloatingSheet(
                            title = SHEET_TITLE,
                            description = null,
                            onDismissSheet = { isClosed = true },
                            onSheetOpen = {},
                            onSheetProgress = {},
                        ) {
                            LazyColumn(modifier = Modifier.testTag(LIST_TEST_TAG)) {
                                item { Text(CONTENT_TEXT) }
                                items(1000) { index -> Text("$index") }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val SHEET_TITLE = "title"
        private const val CONTENT_TEXT = "Content"
        private const val CLOSED_TEXT = "Closed"

        private const val LIST_TEST_TAG = "list"
    }
}
