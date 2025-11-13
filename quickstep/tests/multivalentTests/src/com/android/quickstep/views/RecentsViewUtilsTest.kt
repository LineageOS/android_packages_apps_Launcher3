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

package com.android.quickstep.views

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.DisplayController
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.fallback.RecentsState.Companion.BACKGROUND_APP
import com.android.quickstep.fallback.RecentsState.Companion.DEFAULT
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Tests for [RecentsViewUtils]. */
@RunWith(AndroidJUnit4::class)
class RecentsViewUtilsTest {
    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val recentsView = mock<RecentsView<*, *>>()
    private val displayController = mock<DisplayController>()
    private val taskAnimationManager = mock<TaskAnimationManager>()
    private val rotationTouchHelper = mock<RotationTouchHelper>()
    private val systemUiProxy = mock<SystemUiProxy>()

    private lateinit var utils: RecentsViewUtils

    @Before
    fun setUp() {
        utils =
            RecentsViewUtils(
                recentsView,
                displayController,
                FakeDesktopState(),
                DISPLAY_ID,
                taskAnimationManager,
                rotationTouchHelper,
                systemUiProxy,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onStateTransitionComplete_stateChange_notifiesSystemUiProxy() {
        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        verify(systemUiProxy).onOverviewShown(DISPLAY_ID)
        clearInvocations(systemUiProxy)

        utils.onStateTransitionComplete(BACKGROUND_APP) // Not considered in Overview.
        verify(systemUiProxy).onOverviewHidden(DISPLAY_ID)

        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        verify(systemUiProxy).onOverviewShown(DISPLAY_ID)
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onStateTransitionComplete_noOverviewChange_doesNotNotifySystemUiProxy() {
        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        clearInvocations(systemUiProxy)

        utils.onStateTransitionComplete(DEFAULT)

        verify(systemUiProxy, never()).onOverviewShown(DISPLAY_ID)
        verify(systemUiProxy, never()).onOverviewHidden(DISPLAY_ID)
    }

    private companion object {
        private const val DISPLAY_ID = 100
    }
}
