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

package com.android.launcher3.popup

import com.android.launcher3.Flags.enableHomeScreenFilesRenaming
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import javax.inject.Inject

@LauncherAppSingleton
class PopupDataMapperImpl @Inject constructor(popupDataSource: PopupDataSource) : PopupDataMapper {
    private val folderSystemShortcuts = listOf(popupDataSource.removePopupData)
    private val appPairSystemShortcuts = listOf(popupDataSource.removePopupData)
    private val widgetSystemShortcuts = listOf(popupDataSource.removePopupData)
    private val widgetWithSettingsSystemShortcuts =
        listOf(popupDataSource.removePopupData, popupDataSource.widgetSettingsPopupData)
    private val homeScreenFileShortcuts = buildList {
        add(popupDataSource.openHomeScreenFile)
        if (enableHomeScreenFilesRenaming()) {
            add(popupDataSource.renameFileSystemItem)
        }
        add(popupDataSource.deleteFileSystemItem)
    }

    /**
     * Retrieves the popup data for a specific [ItemInfo].
     *
     * @param itemInfo The item to retrieve popup data for.
     * @return the list of [PopupData] if available, or null if the item type is not supported.
     */
    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? {
        return when (itemInfo.itemType) {
            ITEM_TYPE_FOLDER -> folderSystemShortcuts
            ITEM_TYPE_APP_GROUP -> appPairSystemShortcuts
            ITEM_TYPE_APPWIDGET -> {
                if (itemInfo is LauncherAppWidgetInfo && itemInfo.isReconfigurable) {
                    widgetWithSettingsSystemShortcuts
                } else {
                    widgetSystemShortcuts
                }
            }
            ITEM_TYPE_CUSTOM_APPWIDGET -> widgetSystemShortcuts
            ITEM_TYPE_FILE_SYSTEM_FILE,
            ITEM_TYPE_FILE_SYSTEM_FOLDER -> homeScreenFileShortcuts
            else -> null
        }
    }
}
