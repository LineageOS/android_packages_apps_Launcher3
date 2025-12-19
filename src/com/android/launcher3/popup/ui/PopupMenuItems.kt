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

package com.android.launcher3.popup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.ui.PopupMenuItemDimens.deepShortcutAddButtonSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.deepShortcutAddIconSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.deepShortcutIconSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHeight
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHorizontalPadding
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemIconSpacerWidth
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemTextSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemVerticalPadding
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemWidth
import com.android.launcher3.popup.ui.PopupMenuItemDimens.systemShortcutIconPadding
import com.android.launcher3.popup.ui.PopupMenuItemDimens.systemShortcutIconSize

/**
 * A Composable for displaying a shortcut item with an icon, title, and optional trailing content.
 *
 * @param modifier Optional [Modifier] for this composable.
 * @param icon A Composable function that renders the leading icon.
 * @param title The primary text to display.
 * @param itemContentDescription Content description for the entire clickable item and the main
 *   icon.
 * @param onClick The click listener for the main item area.
 * @param onLongClick An optional long click listener for the main item area.
 * @param trailingContent An optional Composable function for content aligned to the end of the
 *   item.
 */
@Composable
fun PopupMenuItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    itemContentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier.height(popupMenuItemHeight), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier =
                Modifier.size(popupMenuItemWidth, popupMenuItemHeight)
                    .combinedClickable(
                        onClickLabel = itemContentDescription,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(
                        horizontal = popupMenuItemHorizontalPadding,
                        vertical = popupMenuItemVerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                icon()
                Spacer(modifier = Modifier.width(popupMenuItemIconSpacerWidth))
                Text(
                    text = title,
                    color = colorResource(R.color.materialColorOnSurfaceVariant),
                    fontSize = popupMenuItemTextSize,
                )
            }

            trailingContent?.let { it() }
        }
    }
}

/**
 * Composable for displaying a system shortcut item. This can render as an icon-only item or a full
 * menu item with text and an icon, based on [isIconOnly].
 *
 * @param shortcut The [SystemShortcut] data to display.
 * @param onClick The callback function to invoke when the shortcut item is clicked.
 * @param isIconOnly If true, renders only the icon; otherwise, renders the icon with text.
 */
@Composable
fun SystemShortcutMenuItem(
    shortcut: PopupItem,
    onClick: (PopupClickEvent) -> Unit,
    isIconOnly: Boolean = false,
) {
    val title = stringResource(id = shortcut.labelResId)
    val painter: Painter = painterResource(id = shortcut.iconResId)

    if (isIconOnly) {
        Box(
            modifier =
                Modifier.clickable(onClickLabel = title) {
                        onClick(SystemShortcutClickEvent(shortcut))
                    }
                    .padding(systemShortcutIconPadding),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = title,
                modifier = Modifier.size(systemShortcutIconSize),
                colorFilter = ColorFilter.tint(colorResource(R.color.materialColorOnSurfaceVariant)),
            )
        }
    } else {
        val iconComposable: @Composable () -> Unit = {
            Spacer(modifier = Modifier.width(systemShortcutIconPadding))
            Image(
                painter = painter,
                contentDescription = title,
                modifier = Modifier.size(systemShortcutIconSize),
                colorFilter = ColorFilter.tint(colorResource(R.color.materialColorOnSurfaceVariant)),
            )
        }

        PopupMenuItem(
            icon = iconComposable,
            title = title,
            itemContentDescription = title,
            onClick = { onClick(SystemShortcutClickEvent(shortcut)) },
            trailingContent = null,
        )
    }
}

/**
 * Composable for displaying a deep shortcut item.
 *
 * This composable displays a single deep shortcut, including its icon, title, and an "add to home
 * screen" button. It handles both the display of a valid shortcut and a placeholder state for when
 * the shortcut data is null (e.g., during loading).
 *
 * @param shortcut The [ItemInfoWithIcon] data for the deep shortcut. If null, a placeholder UI is
 *   shown.
 * @param onClick The callback function to invoke when the main body of the shortcut item is
 *   clicked.
 * @param onAddButtonClick The callback function to invoke when the "add to home screen" icon is
 *   clicked.
 * @param onLongClick The callback function to invoke when the shortcut item is long-pressed.
 */
@Composable
fun DeepShortcutMenuItem(
    shortcut: ItemInfoWithIcon?,
    onClick: (PopupClickEvent) -> Unit, // onClick for the whole item
    onAddButtonClick:
        (ItemInfoWithIcon) -> Unit, // New parameter for the right-aligned icon's click
    onLongClick: (ItemInfoWithIcon) -> Unit,
) {
    val iconComposable: @Composable () -> Unit
    val itemTitle: String
    val itemContentDescription: String
    val itemOnClick: () -> Unit
    val itemOnLongClick: (() -> Unit)?
    val itemTrailingContent: @Composable (() -> Unit)?

    if (shortcut != null) {
        itemTitle = shortcut.title.toString()
        itemContentDescription = itemTitle
        iconComposable = {
            Image(
                bitmap = shortcut.bitmap.icon.asImageBitmap(),
                contentDescription = itemTitle,
                modifier = Modifier.size(deepShortcutIconSize).clip(CircleShape),
            )
        }
        itemOnClick = { onClick(DeepShortcutClickEvent(shortcut)) }
        itemOnLongClick = { onLongClick(shortcut) }
        itemTrailingContent = {
            Box(
                modifier =
                    Modifier.size(deepShortcutAddButtonSize).clickable {
                        onAddButtonClick(shortcut)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    modifier = Modifier.size(deepShortcutAddIconSize),
                    painter = painterResource(id = R.drawable.ic_add_circle_filled),
                    colorFilter = ColorFilter.tint(colorResource(R.color.materialColorSecondary)),
                    contentDescription = stringResource(R.string.action_add_to_workspace),
                )
            }
        }
    } else {
        // When shortcut is null, render a generic item without title or drawable
        itemTitle = ""
        itemContentDescription = stringResource(id = R.string.loading_shortcut)
        iconComposable = {
            Spacer(modifier = Modifier.size(deepShortcutIconSize))
        } // Empty space for icon
        itemOnClick = { /* Do nothing */ }
        itemOnLongClick = null
        itemTrailingContent = null
    }

    PopupMenuItem(
        icon = iconComposable,
        title = itemTitle,
        itemContentDescription = itemContentDescription,
        onClick = itemOnClick,
        onLongClick = itemOnLongClick,
        trailingContent = itemTrailingContent,
    )
}

object PopupMenuItemDimens {
    val popupMenuItemHeight = 52.dp
    val popupMenuItemWidth = 216.dp
    val popupMenuItemTextSize = 14.sp
    val popupMenuItemHorizontalPadding = 8.dp
    val popupMenuItemVerticalPadding = 10.dp
    val popupMenuItemIconSpacerWidth = 8.dp
    val systemShortcutIconPadding = 4.dp
    val systemShortcutIconSize = 24.dp
    val deepShortcutIconSize = 32.dp
    val deepShortcutAddIconSize = 20.dp
    val deepShortcutAddButtonSize = 48.dp
}
