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

package com.android.launcher3.widgetpicker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import javax.inject.Inject

/** Provides styled create button */
open class CreateButtonProvider @Inject constructor() {
    @Composable
    open fun CreateButton(modifier: Modifier, onClick: () -> Unit) {
        FilledTonalButton(
            modifier = Modifier.height(Dimensions.CreateButtonHeight),
            colors =
                ButtonDefaults.buttonColors(
                    contentColor = WidgetPickerTheme.colors.addButtonContent,
                    containerColor = WidgetPickerTheme.colors.addButtonBackground,
                ),
            onClick = onClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.widget_create_button),
                contentDescription = null, // decorative
                tint = WidgetPickerTheme.colors.addButtonContent,
            )
            Spacer(modifier = Modifier.width(Dimensions.CreateButtonIconTextSpacing))
            Text(stringResource(R.string.widget_create_button))
        }
    }
}

private object Dimensions {
    val CreateButtonHeight = 52.dp
    val CreateButtonIconTextSpacing = 8.dp
}
