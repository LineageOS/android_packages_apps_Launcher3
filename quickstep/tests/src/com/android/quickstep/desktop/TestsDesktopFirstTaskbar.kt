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

package com.android.quickstep.desktop

import android.app.WindowConfiguration
import android.content.Intent
import android.os.RemoteException
import android.util.Log
import android.view.Display
import android.view.WindowManagerGlobal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags.enableFallbackOverviewInWindow
import com.android.launcher3.Flags.enableLauncherOverviewInWindow
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.rule.SetPropRule
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch
import com.android.quickstep.integration.BaseTaskbarIntegrationTest
import com.android.quickstep.taskbar.util.IntegrationTaskbarModeSwitchRule.Mode
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import org.junit.After
import org.junit.Assume
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TestsDesktopFirstTaskbar : BaseTaskbarIntegrationTest() {

    val TAG: String = "TaplTestsDesktopFirstTaskbar"

    @get:Rule
    var mSetPropRule: SetPropRule =
        SetPropRule(DesktopModeStatus.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP, "true")

    var mOriginalWindowingMode = WindowConfiguration.WINDOWING_MODE_UNDEFINED

    var originalTaskbarMode: Mode = Mode.TRANSIENT

    @Before
    fun setUp() {
        // Default-to-desktop feature requires the display to be freeform mode.
        originalTaskbarMode = taskbarModeSwitchRule.currentMode()
        taskbarModeSwitchRule.setTaskbarMode(Mode.PERSISTENT)
        mOriginalWindowingMode =
            setDisplayWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
        super.setup()
        Assume.assumeTrue(Flags.enterDesktopByDefaultOnFreeformDisplays())
        Assume.assumeTrue(
            "Ignoring test because device does not support desktop mode",
            DesktopModeStatus.canEnterDesktopMode(
                InstrumentationRegistry.getInstrumentation().targetContext
            ),
        )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (mOriginalWindowingMode != WindowConfiguration.WINDOWING_MODE_UNDEFINED) {
            setDisplayWindowingMode(mOriginalWindowingMode)
        }
        // This is needed because the rule taskbarModeSwitchRule will try to set the Taskbar mode
        // before tearDown has run and they conflict with each other since the Transient taskbar
        // can't be set along  setDisplayWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
        taskbarModeSwitchRule.setTaskbarMode(originalTaskbarMode)
    }

    override fun startCalendarAppDuringSetup(): Boolean {
        return false
    }

    private fun startActivityFast(activity: String) {
        interactions.startAppFast(
            activity,
            Intent().setClassName(LauncherModelHelper.TEST_PACKAGE, activity),
        )
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    fun testTaskbarOnHome() {
        // Go home - taskbar should be visible in desktop-first display context.
        uiDevice.pressHome()
        launcherActivity.waitForResumed()
        waitForTaskbarVisible()

        // Open an app, and go back home.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()

        clickTaskbarAppIcon("Calculator")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    fun testTaskbarForFullscreenApp() {
        // TODO(b/377678992): revert ag/36346262 once NexusLauncherTests-OverviewInWindowEnabled is
        //  successfully blocking presubmit.
        assumeFalse(
            "Skipping test because overview in window flags are enabled",
            enableLauncherOverviewInWindow() || enableFallbackOverviewInWindow(),
        )
        clearAllRecentTasks()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Open two apps - they are both expected to open in desktop windowing on desktop-first
        // display, then move the second one into fullscreen.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        startActivityFast(LauncherModelHelper.TEST_ACTIVITY2)
        waitForIcons("TestActivity3", "Calculator")

        moveFocusedActivityToFullscreen(LauncherModelHelper.TEST_PACKAGE, "TestActivity3")

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()
        clickTaskbarAppIcon("Calculator")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    fun testTaskbarForDesktopMode() {
        // TODO(b/377678992): revert ag/36346262 once NexusLauncherTests-OverviewInWindowEnabled is
        //  successfully blocking presubmit.
        assumeFalse(
            "Skipping test because overview in window flags are enabled",
            enableLauncherOverviewInWindow() || enableFallbackOverviewInWindow(),
        )
        clearAllRecentTasks()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Start two apps - they are both expected to open in desktop windowing on desktop-first
        // display.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        startActivityFast(LauncherModelHelper.TEST_ACTIVITY2)
        waitForTaskbarVisible()
        waitForIcons("TestActivity3", "Calculator")

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()
        clickTaskbarAppIcon("TestActivity3")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    private fun setDisplayWindowingMode(windowingMode: Int): Int {
        try {
            val originalWindowingMode =
                WindowManagerGlobal.getWindowManagerService()!!.getWindowingMode(
                    Display.DEFAULT_DISPLAY
                )
            WindowManagerGlobal.getWindowManagerService()!!.setWindowingMode(
                Display.DEFAULT_DISPLAY,
                windowingMode,
            )
            uiDevice.pressHome()
            uiDevice.waitForIdle()
            return originalWindowingMode
        } catch (e: RemoteException) {
            Log.e(TAG, "error setting windowing mode", e)
            throw RuntimeException(e)
        }
    }
}
