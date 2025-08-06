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

package com.android.launcher3.util

import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST
import com.android.launcher3.integration.util.LauncherActivityScenarioRule

/** Common shared test interactions with the Launcher activity [LauncherActivityScenarioRule]. */
class LauncherTestInteractions<LAUNCHER_TYPE : Launcher>(
    private val launcherActivity: LauncherActivityScenarioRule<LAUNCHER_TYPE>,
    private val mainThreadExecutor: LooperExecutor = Executors.MAIN_EXECUTOR,
) {

    fun addToWorkspace(view: View) {
        TestUtil.runOnExecutorSync(mainThreadExecutor) {
            view.accessibilityDelegate.performAccessibilityAction(
                view,
                R.id.action_add_to_workspace,
                null,
            )
        }
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }

    fun freezeAllApps() =
        launcherActivity.executeOnLauncher {
            it.appsView.appsStore.enableDeferUpdates(DEFER_UPDATES_TEST)
        }

    /**
     * Match the behavior with how widget is added in reality with "tap to add" (even with screen
     * readers).
     */
    fun addWidgetToWorkspace(view: View) =
        launcherActivity.executeOnLauncher {
            view.performClick()
            UiDevice.getInstance(getInstrumentation()).waitForIdle()
            view.findViewById<View>(R.id.widget_add_button).performClick()
        }

    @JvmOverloads
    fun startAppFast(
        packageName: String,
        intent: Intent =
            getInstrumentation()
                .targetContext
                .packageManager
                .getLaunchIntentForPackage(packageName)!!,
    ) {
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        getInstrumentation().targetContext.startActivity(intent)
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }
}
