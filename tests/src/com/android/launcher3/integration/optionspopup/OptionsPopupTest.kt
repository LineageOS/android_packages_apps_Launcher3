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

package com.google.android.apps.nexuslauncher.integration.optionspopup

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherState
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.OptionsPopupView
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class OptionsPopupTest {

    companion object {
        const val APPS_OPTION_LABEL = "Apps list"
        const val CREATE_NEW_FOLDER_OPTION_LABEL = "New folder"
        const val TAG = "OptionsPopupTest"
    }

    var targetContext: Context = getInstrumentation().targetContext

    var launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun `test long press Apps option`() {
        val allAppsStateWaiter = launcherActivity.createStateWaiter(LauncherState.ALL_APPS)
        launcherActivity.executeOnLauncher {
            val appsOption = findOption(it, APPS_OPTION_LABEL)
            assertWithMessage("No option for apps found").that(appsOption).isNotNull()
            appsOption!!.clickListener.onLongClick(it.rootView)
        }
        allAppsStateWaiter.waitForSignal(TimeUnit.SECONDS.toMillis(10))
    }

    @Test
    fun `test long press create new folder option`() {
        val expectCreateNewFolderOption =
            isHomeScreenFilesEnabled() && isExternalStorageDirectoryMounted()

        // Verify option (in-)existence and long press if applicable.
        launcherActivity.executeOnLauncher { launcher ->
            val createNewFolderOption = findOption(launcher, CREATE_NEW_FOLDER_OPTION_LABEL)
            when (expectCreateNewFolderOption) {
                false -> {
                    assertWithMessage("Option for create new folder found")
                        .that(createNewFolderOption)
                        .isNull()
                }
                true -> {
                    assertWithMessage("No option for create new folder found")
                        .that(createNewFolderOption)
                        .isNotNull()

                    createNewFolderOption!!.clickListener.onLongClick(launcher.rootView)
                }
            }
        }

        // If the option does not exist, there's nothing left to verify.
        if (!expectCreateNewFolderOption) {
            return
        }

        // Verify new folder creation.
        val view =
            launcherActivity.getOnceNotNull("No new folder created") { launcher ->
                launcher.workspace.mapOverItems { itemInfo, _ ->
                    itemInfo?.itemType == ITEM_TYPE_FILE_SYSTEM_FOLDER &&
                        itemInfo.title?.matches(Regex("^(New folder)( \\(\\d+\\))?$")) == true
                }
            }!!

        // Clean up newly created folder on a best-effort basis.
        try {
            val itemInfo = view.tag as WorkspaceItemInfo
            targetContext.contentResolver.delete(itemInfo.intent.data!!, null)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete new folder", e)
        }
    }

    private fun findOption(launcher: Launcher, label: String): OptionsPopupView.OptionItem? =
        OptionsPopupView.getOptions(launcher).find { it.label == label }

    private fun isExternalStorageDirectoryMounted(): Boolean =
        Environment.getExternalStorageState(Environment.getExternalStorageDirectory()) ==
            Environment.MEDIA_MOUNTED

    private fun isHomeScreenFilesEnabled(): Boolean =
        HomeScreenFilesProvider.INSTANCE[targetContext] !is HomeScreenFilesNoOpProvider
}
