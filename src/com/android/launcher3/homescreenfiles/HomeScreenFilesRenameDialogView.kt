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

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// TODO(b/493665827): Add test coverage.
/** Composable which renders the content view for the home screen files rename dialog. */
@Composable
fun HomeScreenFilesRenameDialogView(viewModel: HomeScreenFilesRenameDialogViewModel) {
    val name by viewModel.name.collectAsStateWithLifecycle()

    OutlinedTextField(
        modifier = Modifier.testTag(TEXT_FIELD_TAG).fillMaxWidth(),
        value = name,
        onValueChange = { viewModel.name.value = it },
    )
}

// Used to locate the text field node in tests.
@VisibleForTesting const val TEXT_FIELD_TAG = "textField"
