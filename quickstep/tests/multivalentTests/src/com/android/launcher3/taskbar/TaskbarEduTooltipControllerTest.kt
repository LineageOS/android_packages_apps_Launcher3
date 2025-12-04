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

package com.android.launcher3.taskbar

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_BUBBLES_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_PINNING_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SEARCH_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SPLIT_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SWIPE_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.OnboardingPrefs
import com.android.systemui.shared.Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_RECENTS_IN_TASKBAR)
class TaskbarEduTooltipControllerTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarEduTooltipController: TaskbarEduTooltipController
    private val tooltipEduCombinator
        get() = taskbarEduTooltipController.tooltipEduCombinator

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val taskbarView: TaskbarView
        get() = taskbarContext.dragLayer.findViewById(R.id.taskbar_view)

    private val wasInTestHarness = Utilities.isRunningInTestHarness()

    private var tooltipStep by OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP.prefItem.asProperty(context)
    private var searchEduSeen by OnboardingPrefs.TASKBAR_SEARCH_EDU_SEEN.asProperty(context)

    @Before
    fun setUp() {
        Utilities.disableRunningInTestHarnessForTests()
        taskbarEduTooltipController.shouldShowSearchEduResolver = { true }
    }

    @After
    fun tearDown() {
        if (wasInTestHarness) {
            Utilities.enableRunningInTestHarnessForTests()
        }
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testMaybeShowSwipeEdu_whenTaskbarIsInThreeButtonMode_doesNotShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowSwipeEdu_whenSwipeEduAlreadyShown_doesNotShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_FEATURES
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowSwipeEdu_whenUserHasNotSeen_doesShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowSwipeEdu_whenUserHasNotSeen_doesShowSwipeEdu_eduCombinator() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG)).isTrue()
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowFeaturesEdu_whenFeatureEduAlreadyShown_doesNotShowFeatureEdu() {
        tooltipStep = TOOLTIP_STEP_NONE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowFeaturesEdu_whenFeatureEduAlreadyShown_doesNotShowFeatureEdu_eduCombinator() {
        tooltipEduCombinator.setFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowFeaturesEdu_whenUserHasNotSeen_doesShowFeatureEdu() {
        tooltipStep = TOOLTIP_STEP_FEATURES
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowFeaturesEdu_whenUserHasNotSeen_doesShowFeatureEdu_eduCombinator() {
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG)).isTrue()
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG)).isTrue()
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG)).isTrue()
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG)).isTrue()
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)).isTrue()
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowPinningEdu_whenTaskbarIsInThreeButtonMode_doesNotShowPinningEdu() {
        tooltipStep = TOOLTIP_STEP_PINNING
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowPinningEdu_whenTaskbarIsInThreeButtonMode_doesNotShowPinningEdu_eduCombinator() {
        tooltipEduCombinator.setFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG)
        tooltipEduCombinator.setFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowPinningEdu_whenUserHasNotSeen_doesShowPinningEdu() {
        // Test standalone pinning edu, where user has seen taskbar edu before, but not pinning edu.
        tooltipStep = TOOLTIP_STEP_PINNING
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    fun testMaybeShowPinningEdu_whenUserHasNotSeen_doesShowPinningEdu_eduCombinator() {
        // Test standalone pinning edu, where user has seen taskbar edu before, but not pinning edu.
        tooltipEduCombinator.setFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(tooltipEduCombinator.getFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)).isTrue()
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsBeforeTooltipFeaturesStep_whenUserHasNotSeenFeatureEdu_shouldReturnTrue() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.isBeforeTooltipFeaturesStep).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsBeforeTooltipFeaturesStep_whenUserHasSeenFeatureEdu_shouldReturnFalse() {
        tooltipStep = TOOLTIP_STEP_NONE
        assertThat(taskbarEduTooltipController.isBeforeTooltipFeaturesStep).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHide_whenTooltipIsOpen_shouldCloseTooltip() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
        runOnMainSync { taskbarEduTooltipController.hide() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowSearchEdu_whenTaskbarIsTransient_shouldNotShowSearchEdu() {
        runOnMainSync { taskbarEduTooltipController.maybeShowSearchEdu() }
        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        runOnMainSync { assertThat(taskbarView.allAppsButtonContainer.isAttachedToWindow).isTrue() }

        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        runOnMainSync {
            taskbarEduTooltipController.init(taskbarContext.controllers, TaskbarUiState())
        }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    @TaskbarMode(PINNED)
    fun testMaybeShowSearchEdu_whenTaskbarIsPinned_shouldShowSearchEdu() {
        runOnMainSync { taskbarEduTooltipController.maybeShowSearchEdu() }

        runOnMainSync {
            // The EDU tooltip should show only if all apps button is visible, otherwise it gets
            // positioned incorrectly.
            assertThat(taskbarEduTooltipController.isTooltipOpen)
                .isEqualTo(taskbarView.allAppsButtonContainer.isAttachedToWindow)
        }

        // Updating items will ensure that all apps button gets shown - in response toolltip should
        // show as well.
        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        runOnMainSync {
            assertThat(taskbarView.allAppsButtonContainer.isAttachedToWindow).isTrue()
            assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
        }

        assertThat(taskbarEduTooltipController.userHasSeenSearchEdu).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    @TaskbarMode(PINNED)
    fun testMaybeShowSearchEdu_whenTaskbarIsPinned_shouldShowSearchEdu_eduCombinator() {
        runOnMainSync { taskbarEduTooltipController.maybeShowSearchEdu() }
        runOnMainSync {
            // The EDU tooltip should show only if all apps button is visible, otherwise it gets
            // positioned incorrectly.
            assertThat(taskbarEduTooltipController.isTooltipOpen)
                .isEqualTo(taskbarView.allAppsButtonContainer.isAttachedToWindow)
        }

        // Updating items will ensure that all apps button gets shown - in response toolltip should
        // show as well.
        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        runOnMainSync {
            assertThat(taskbarView.allAppsButtonContainer.isAttachedToWindow).isTrue()
            assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
        }

        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG)).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    @TaskbarMode(PINNED)
    fun testMaybeShowSearchEdu_whenTaskbarIsPinnedUserSeenSearch_noEduShown() {
        searchEduSeen = true
        runOnMainSync { taskbarEduTooltipController.maybeShowSearchEdu() }
        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        runOnMainSync {
            assertThat(taskbarView.allAppsButtonContainer.isAttachedToWindow).isTrue()
            assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        }

        assertThat(taskbarEduTooltipController.userHasSeenSearchEdu).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_TOOLTIP_EDU_COMBINATOR)
    @TaskbarMode(PINNED)
    fun testMaybeShowSearchEdu_whenTaskbarIsPinnedUserSeenSearch_noEduShown_eduCombinator() {
        tooltipEduCombinator.setFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG)
        runOnMainSync { taskbarEduTooltipController.maybeShowSearchEdu() }

        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        runOnMainSync {
            assertThat(taskbarView.allAppsButtonContainer.isAttachedToWindow).isTrue()
            assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        }

        assertThat(tooltipEduCombinator.getFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG)).isTrue()
    }
}
