/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.picker.model.data

import com.android.launcher3.model.WidgetItem
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import com.android.launcher3.widget.model.WidgetsListContentEntry

// This file contains WidgetPickerData and utility functions to operate on it.

/** Widget data for display in the widget picker. */
data class WidgetPickerData(
    val allWidgets: List<WidgetsListBaseEntry> = listOf(),
    val defaultWidgets: List<WidgetsListBaseEntry> = listOf(),
)

/** Provides utility methods to work with a [WidgetPickerData] object. */
object WidgetPickerDataUtils {
    /**
     * Returns a [WidgetPickerData] with the provided widgets.
     *
     * When [defaultWidgets] is not passed, defaults from previous object are not copied over.
     * Defaults (if supported) should be updated when all widgets are updated.
     */
    fun WidgetPickerData.withWidgets(
        allWidgets: List<WidgetsListBaseEntry>,
        defaultWidgets: List<WidgetsListBaseEntry> = listOf(),
    ): WidgetPickerData {
        return copy(allWidgets = allWidgets, defaultWidgets = defaultWidgets)
    }

    /** Finds all [WidgetItem]s available for the provided package user. */
    @JvmStatic
    fun findAllWidgetsForPackageUser(
        widgetPickerData: WidgetPickerData,
        packageUserKey: PackageUserKey,
    ): List<WidgetItem> {
        return findContentEntryForPackageUser(widgetPickerData, packageUserKey)?.mWidgets
            ?: emptyList()
    }

    /**
     * Finds and returns the [WidgetsListContentEntry] for the given package user.
     *
     * Set [fromDefaultWidgets] to true to limit the content entry to default widgets.
     */
    @JvmOverloads
    @JvmStatic
    fun findContentEntryForPackageUser(
        widgetPickerData: WidgetPickerData,
        packageUserKey: PackageUserKey,
        fromDefaultWidgets: Boolean = false,
    ): WidgetsListContentEntry? {
        val widgetsListBaseEntries =
            if (fromDefaultWidgets) {
                widgetPickerData.defaultWidgets
            } else {
                widgetPickerData.allWidgets
            }

        return widgetsListBaseEntries.filterIsInstance<WidgetsListContentEntry>().firstOrNull {
            PackageUserKey.fromPackageItemInfo(it.mPkgItem) == packageUserKey
        }
    }
}
