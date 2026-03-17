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

package com.android.launcher3.homescreenfiles

import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.whenever

// TODO(b/450710219): Make this suite work w/ [createAndroidComposeRule<ComponentActivity>()].
/** Tests for [HomeScreenFilesRenameDialogView]. */
@LargeTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class HomeScreenFilesRenameDialogViewTest {
    @get:Rule(order = 0) val limitDevices = LimitDevicesRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @get:Rule(order = 2) val mockito = MockitoJUnit.rule()
    @get:Rule(order = 3) val app = SandboxApplication().withModelDependency()
    @get:Rule(order = 4) val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule(order = 5) val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Mock private lateinit var file: HomeScreenFile
    @Mock private lateinit var provider: HomeScreenFilesProvider
    private lateinit var dialog: HomeScreenFilesRenameDialog
    private lateinit var viewModel: HomeScreenFilesRenameDialogViewModel

    @Before
    fun setUp() {
        whenever(file.displayName).thenReturn("File")
        viewModel = createViewModel()
        dialog = createAndShowDialog(viewModel)
        assertTrue(dialog.isShowing())
    }

    @After
    fun tearDown() {
        launcherActivity.executeOnLauncher { dialog.dismiss(animate = false) }
    }

    @Test
    fun testTextField() {
        // Verify initial state.
        var name = file.displayName
        assertThat(viewModel.name.value).isEqualTo(name)
        compose.onNodeWithTag(TEXT_FIELD_TAG).assertTextEquals(name)

        // Verify that typing in the text field updates the model and triggers recomposition.
        name = "$name (1)"
        compose
            .onNodeWithTag(TEXT_FIELD_TAG)
            .apply { performTextClearance() }
            .also { compose.waitForIdle() }
            .apply { performTextInput(name) }
            .also { compose.waitForIdle() }
            .also { assertThat(viewModel.name.value).isEqualTo(name) }
            .assertTextEquals(name)
    }

    /** Creates and shows a dialog. */
    private fun createAndShowDialog(viewModel: HomeScreenFilesRenameDialogViewModel) =
        launcherActivity.getFromLauncher { launcher ->
            HomeScreenFilesRenameDialog(launcher, viewModel).also(HomeScreenFilesRenameDialog::show)
        }!!

    /** Creates a view model to show in a dialog. */
    private fun createViewModel() =
        launcherActivity.getFromLauncher { launcher ->
            HomeScreenFilesRenameDialogViewModel(
                launcher,
                file,
                provider,
                textField = { modifier, value, onValueChange ->
                    BasicTextField(
                        modifier = modifier,
                        value = value,
                        onValueChange = onValueChange,
                    )
                },
            )
        }!!
}
