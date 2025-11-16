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

package com.android.launcher3.widgetpicker.datasource

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.android.launcher3.Flags
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import javax.inject.Inject

/** Data source for fetching the information about the activity for creating custom widgets. */
interface WidgetCreatorAppPackageProvider {
    fun get(): ComponentName?
}

@LauncherAppSingleton
class WidgetCreatorAppPackageProviderImpl
@Inject
constructor(@param:ApplicationContext private val appContext: Context) :
    WidgetCreatorAppPackageProvider {
    override fun get(): ComponentName? {
        if (!Flags.showCreateWidgetBtnInPicker() || Build.VERSION.SDK_INT < 36) return null

        val packageManager = appContext.packageManager
        val resolveInfos =
            packageManager.queryIntentActivities(
                Intent(WIDGET_CREATE_ACTION),
                /*flags*/ PackageManager.MATCH_SYSTEM_ONLY,
            )

        val activityInfo = resolveInfos.firstOrNull { it.activityInfo != null }?.activityInfo
        return activityInfo?.let { ComponentName(it.packageName, it.name) }
    }

    companion object {
        // Intent action to discover an app activity that allows creating custom widget.
        const val WIDGET_CREATE_ACTION = "com.android.launcher3.widgets.ACTION_CREATE"
    }
}
