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
package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import javax.inject.Inject
import kotlin.math.min

/** [ItemInfo] object which serializes specific attributes to allow reconstruction */
class SerializedItemItem(itemType: Int, user: UserHandle, private val intent: Intent) : ItemInfo() {

    init {
        this.itemType = itemType
        this.user = user
    }

    override fun getIntent() = intent

    /** Initializes a PendingInstallShortcutInfo to represent a pending launcher target. */
    constructor(
        packageName: String,
        userHandle: UserHandle,
    ) : this(
        itemType = ITEM_TYPE_APPLICATION,
        intent = Intent().setPackage(packageName),
        user = userHandle,
    )

    /** Initializes a PendingInstallShortcutInfo to represent a deep shortcut. */
    constructor(
        info: ShortcutInfo
    ) : this(
        itemType = ITEM_TYPE_DEEP_SHORTCUT,
        intent = ShortcutKey.makeIntent(info),
        user = info.userHandle,
    )

    /** Initializes a PendingInstallShortcutInfo to represent an app widget. */
    constructor(
        info: AppWidgetProviderInfo,
        widgetId: Int,
    ) : this(
        itemType = ITEM_TYPE_APPWIDGET,
        intent = Intent().setComponent(info.provider).putExtra(EXTRA_APPWIDGET_ID, widgetId),
        user = info.profile,
    )

    override fun equals(other: Any?): Boolean {
        return other is SerializedItemItem &&
            user == other.user &&
            itemType == other.itemType &&
            intent.toUri(0) == other.intent.toUri(0)
    }
}

/** Helper class to create a valid [ItemInfo] form [SerializedItemItem] */
class WorkspaceItemSerializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
) {

    fun decode(item: SerializedItemItem): ItemInfo? {
        when (item.itemType) {
            ITEM_TYPE_APPLICATION -> {
                val packageName = item.intent.getPackage() ?: return null
                val laiList =
                    context
                        .getSystemService(LauncherApps::class.java)!!
                        .getActivityList(packageName, item.user)

                val si = WorkspaceItemInfo()
                si.user = item.user

                val lai: LauncherActivityInfo?
                val usePackageIcon = laiList.isEmpty()
                if (usePackageIcon) {
                    lai = null
                    si.intent =
                        AppInfo.makeLaunchIntent(ComponentName(packageName, ""))
                            .setPackage(packageName)
                    si.status = si.status or WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON
                } else {
                    lai = laiList[0]
                    si.intent = AppInfo.makeLaunchIntent(lai)
                    if (Flags.enableSupportForArchiving() && lai.activityInfo.isArchived) {
                        si.runtimeStatusFlags =
                            si.runtimeStatusFlags or ItemInfoWithIcon.FLAG_ARCHIVED
                    }
                }
                iconCache.getTitleAndIcon(
                    si,
                    { lai },
                    DESKTOP_ICON_FLAG.withUsePackageIcon(usePackageIcon),
                )
                return si
            }

            ITEM_TYPE_DEEP_SHORTCUT ->
                return ShortcutKey.fromIntent(item.intent, item.user)
                    .buildRequest(context)
                    .query(ShortcutRequest.ALL)
                    .getOrNull(0)
                    ?.let {
                        WorkspaceItemInfo(it, context).apply { iconCache.getShortcutIcon(this, it) }
                    }

            ITEM_TYPE_APPWIDGET -> {
                val widgetId = item.intent.getIntExtra(EXTRA_APPWIDGET_ID, 0)
                val providerInfo =
                    AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return null
                val info = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)
                if (info.provider != item.intent.component || info.profile != item.user) return null

                val widgetInfo = LauncherAppWidgetInfo(widgetId, info.provider)
                widgetInfo.minSpanX = info.minSpanX
                widgetInfo.minSpanY = info.minSpanY
                widgetInfo.spanX = min(info.spanX, idp.numColumns)
                widgetInfo.spanY = min(info.spanY, idp.numRows)
                widgetInfo.user = item.user
                return widgetInfo
            }
        }
        return null
    }
}
