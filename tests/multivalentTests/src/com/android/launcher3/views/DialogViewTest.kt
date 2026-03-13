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

package com.android.launcher3.views

import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

// TODO(b/450710219): Make this suite work w/ [createAndroidComposeRule<ComponentActivity>()].
/** Tests for [DialogView]. */
@LargeTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class DialogViewTest {
    @get:Rule(order = 0) val limitDevices = LimitDevicesRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @get:Rule(order = 2) val mockito = MockitoJUnit.rule()
    @get:Rule(order = 3) val app = SandboxApplication().withModelDependency()
    @get:Rule(order = 4) val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule(order = 5) val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Mock private lateinit var onPositiveButtonClick: (SimpleDialogViewModel) -> Boolean
    private lateinit var dialog: SimpleDialog

    @After
    fun tearDown() {
        launcherActivity.executeOnLauncher { dialog.dismiss(animate = false) }
    }

    @Test
    fun testComposition() {
        val viewModel = createViewModel()
        dialog = createAndShowDialog(viewModel)

        // The dialog should compose as expected.
        compose.onNodeWithTag(TITLE_TAG).assertTextEquals(viewModel.title)
        compose.onNodeWithTag(CONTENT_TAG).onChild().assertTextEquals(CONTENT)
        compose.onNodeWithTag(NEUTRAL_BUTTON_TAG).assertTextEquals(viewModel.neutralButton!!)
        compose.onNodeWithTag(POSITIVE_BUTTON_TAG).assertTextEquals(viewModel.positiveButton!!)
    }

    @Test
    fun testNeutralButton() {
        val viewModel = createViewModel()
        dialog = createAndShowDialog(viewModel)
        assertTrue(dialog.isShowing())

        // Clicking the neutral button should trigger a dismiss event.
        compose.onNodeWithTag(NEUTRAL_BUTTON_TAG).performClick().also { compose.waitForIdle() }
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testNeutralButtonAbsent() {
        val viewModel = createViewModel(hasNeutralButton = false)
        dialog = createAndShowDialog(viewModel)
        assertTrue(dialog.isShowing())

        // It should be possible to create a dialog without a neutral button.
        compose.onNodeWithTag(NEUTRAL_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun testPositiveButton() {
        val viewModel = createViewModel()
        dialog = createAndShowDialog(viewModel)
        assertTrue(dialog.isShowing())

        // Clicking the positive button should trigger an event.
        whenever(onPositiveButtonClick.invoke(viewModel)).thenReturn(false)
        compose.onNodeWithTag(POSITIVE_BUTTON_TAG).performClick().also { compose.waitForIdle() }
        verify(onPositiveButtonClick).invoke(viewModel)
        assertTrue(dialog.isShowing())

        // Returning [true] from the event handler should trigger a dismiss event.
        whenever(onPositiveButtonClick.invoke(viewModel)).thenReturn(true)
        compose.onNodeWithTag(POSITIVE_BUTTON_TAG).performClick().also { compose.waitForIdle() }
        verify(onPositiveButtonClick, times(2)).invoke(viewModel)
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testPositiveButtonAbsent() {
        val viewModel = createViewModel(hasPositiveButton = false)
        dialog = createAndShowDialog(viewModel)
        assertTrue(dialog.isShowing())

        // It should be possible to create a dialog without a positive button.
        compose.onNodeWithTag(POSITIVE_BUTTON_TAG).assertDoesNotExist()
    }

    /** Creates and shows a simple dialog. */
    private fun createAndShowDialog(viewModel: SimpleDialogViewModel) =
        launcherActivity.getFromLauncher { launcher ->
            SimpleDialog(launcher, viewModel).also(SimpleDialog::show)
        }!!

    /** Creates a view model to show in a dialog with optional neutral and positive buttons. */
    private fun createViewModel(
        hasNeutralButton: Boolean = true,
        hasPositiveButton: Boolean = true,
    ) =
        SimpleDialogViewModel(
            title = "Title",
            content = { Text(CONTENT) },
            neutralButton = if (hasNeutralButton) "Neutral" else null,
            positiveButton = if (hasPositiveButton) "Positive" else null,
            onPositiveButtonClick = onPositiveButtonClick,
        )

    // Used to verify that the content node has been composed.
    private val CONTENT = "content"
}
