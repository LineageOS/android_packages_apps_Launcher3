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

package com.android.launcher3.taskbar.edu

import android.content.Intent
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.android.launcher3.Flags.enableRecentsInTaskbar
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarStashController
import com.android.launcher3.taskbar.edu.TooltipsEduPage.DisplayLocation
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_FEATURES_EDU_SEEN
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_PINNING_EDU_SEEN
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_SEARCH_EDU_SEEN
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_SWIPE_EDU_SEEN
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.ContextualSearchInvoker
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import javax.inject.Inject

/**
 * Class that encapsulates the logic for determining which educational tooltip should be shown to
 * the user. It manages the display conditions and content for various educational tooltips related
 * to the taskbar, such as swipe gestures, features, pinning, and search.
 */
class TooltipEduCombinator
@Inject
constructor(
    activityContext: ActivityContext,
    private val taskbarStashController: TaskbarStashController,
) {

    private val context = activityContext as TaskbarActivityContext

    /** Determines if not a test harnesses, phone mode, and when the taskbar is tiny. */
    private val isTooltipEnabled: Boolean
        get() {
            return !Utilities.isRunningInTestHarness() &&
                !context.isPhoneMode &&
                !context.isTinyTaskbar
        }

    /** Indicates whether the user has seen the original educational flow for the taskbar. */
    private val userHasSeenOldEdu: Boolean
        get() = TASKBAR_EDU_TOOLTIP_STEP.get(context) > 0

    /**
     * Indicates whether the conditions for showing the search-related educational tooltip are met.
     * This typically involves checking if contextual search can be invoked successfully.
     */
    private val shouldShowSearchEdu: Boolean
        get() = shouldShowSearchEduResolver.invoke()

    /** Indicates whether the search educational tooltip should be shown. */
    @VisibleForTesting
    var shouldShowSearchEduResolver: () -> Boolean = {
        ContextualSearchInvoker(context).runContextualSearchInvocationChecksAndLogFailures()
    }

    /** Indicates whether the createAnyBubbleEnabled is enabled. */
    @VisibleForTesting
    var createAnyBubbleEnabled: Boolean = BubbleAnythingFlagHelper.enableCreateAnyBubble()

    /** Tracks whether the user has seen the educational tooltip for the swipe gesture. */
    @VisibleForTesting
    var userHasSeenSwipeEdu: Boolean
        get() = TASKBAR_SWIPE_EDU_SEEN.get(context)
        private set(seen) {
            LauncherPrefs.get(context).put(TASKBAR_SWIPE_EDU_SEEN, seen)
        }

    /** Tracks whether the user has seen the educational flow for general taskbar features. */
    @VisibleForTesting
    var userHasSeenFeaturesEdu: Boolean
        get() = TASKBAR_FEATURES_EDU_SEEN.get(context)
        private set(seen) {
            LauncherPrefs.get(context).put(TASKBAR_FEATURES_EDU_SEEN, seen)
        }

    /** Tracks whether the user has seen the educational tooltip for taskbar pinning. */
    @VisibleForTesting
    var userHasSeenPinningEdu: Boolean
        get() = TASKBAR_PINNING_EDU_SEEN.get(context)
        private set(seen) {
            LauncherPrefs.get(context).put(TASKBAR_PINNING_EDU_SEEN, seen)
        }

    /** Tracks whether the user has seen the educational tooltip for taskbar search. */
    @VisibleForTesting
    var userHasSeenSearchEdu: Boolean
        get() = TASKBAR_SEARCH_EDU_SEEN.get(context)
        private set(seen) {
            LauncherPrefs.get(context).put(TASKBAR_SEARCH_EDU_SEEN, seen)
        }

    /** Creates the [TooltipInfo] for the split-screen educational tooltip. */
    private val splitTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_splitscreen),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_splitscreen_transient
                    } else {
                        R.raw.taskbar_edu_splitscreen_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_split_screen_animation_description),
            )

    /** Creates the [TooltipInfo] for the bubbles educational tooltip. */
    private val bubbleTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_bubbles),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_bubbles_transient
                    } else {
                        R.raw.taskbar_edu_bubbles_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_bubbles_animation_description),
            )

    /** Creates the [TooltipInfo] for the bubbles educational tooltip when it is single item. */
    private val bubbleTooltipInfoStandAlone: TooltipInfo
        get() =
            bubbleTooltipInfo.copy(
                message = context.getString(R.string.taskbar_edu_bubbles_standalone)
            )

    /** Creates the [TooltipInfo] for the app suggestions educational tooltip. */
    private val suggestionsTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_suggestions),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_suggestions_transient
                    } else {
                        R.raw.taskbar_edu_suggestions_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_suggested_app_animation_description),
            )

    /** Creates the [TooltipInfo] for the taskbar pinning educational tooltip. */
    private val pinningTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_pinning),
                animationResId = R.raw.taskbar_edu_pinning_transient,
                animationDescription =
                    context.getString(R.string.taskbar_edu_pinning_animation_description),
            )

    /** Creates the [TooltipInfo] for the pinning educational tooltip when it is single item. */
    private val pinningTooltipInfoStandAlone: TooltipInfo
        get() =
            pinningTooltipInfo.copy(
                message = context.getString(R.string.taskbar_edu_pinning_standalone)
            )

    /** Creates the [TooltipInfo] for the taskbar search educational tooltip. */
    private val searchTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = getDisclosureText(),
                animationResId = R.raw.taskbar_edu_search,
                animationDescription =
                    context.getString(R.string.taskbar_edu_suggested_search_animation_description),
            )

    /**
     * Returns the [TooltipsEduPage] page for the swipe gesture to stash the taskbar, or `null` if
     * it should not be shown (e.g., if tooltips are disabled, in phone mode, in tiny taskbar mode,
     * or if the user has already seen this or an older educational flow).
     */
    fun getSwipeEdu(): TooltipsEduPage? {
        if (
            !isTooltipEnabled ||
                !context.isTransientTaskbar ||
                userHasSeenOldEdu ||
                userHasSeenSwipeEdu
        ) {
            return null
        }
        userHasSeenSwipeEdu = true
        return TooltipsEduPage(
            title = context.getString(R.string.taskbar_edu_stashing),
            canBeSkipped = true,
            tooltips =
                listOf(
                    TooltipInfo(
                        animationResId = R.raw.taskbar_edu_stashing,
                        animationDescription =
                            context.getString(R.string.taskbar_edu_swipe_animation_description),
                    )
                ),
            location = DisplayLocation.TASKBAR_HANDLE,
        )
    }

    /**
     * Returns a list of [TooltipsEduPage]s for taskbar features, or `null` if feature education
     * should not be shown (e.g., if tooltips are disabled).
     *
     * This method also handles marking the swipe EDU as seen, as a swipe up is necessary to show
     * these feature tooltips.
     */
    fun getFeaturesTooltipsEduPages(): List<TooltipsEduPage>? {
        if (!isTooltipEnabled) {
            return null
        }
        userHasSeenSwipeEdu = true
        if (userHasSeenFeaturesEdu) {
            return onFeaturesEduShown()
        }
        userHasSeenFeaturesEdu = true
        val tooltipsToShow = collectFeatureTooltipsUpdatePinning()
        return convertToFeaturesEduPages(tooltipsToShow)
    }

    /**
     * Returns the [TooltipsEduPage] for taskbar search, or `null` if it should not be shown (e.g.,
     * if taskbar pinning is not enabled, not in pinned taskbar mode, etc.).
     */
    fun getSearchEdu(): TooltipsEduPage? {
        if (
            !enableTaskbarPinning() ||
                !context.isPinnedTaskbar ||
                !isTooltipEnabled ||
                !shouldShowSearchEdu ||
                userHasSeenSearchEdu ||
                !taskbarStashController.isTaskbarVisibleAndNotStashing
        ) {
            return null
        }
        userHasSeenSearchEdu = true
        return TooltipsEduPage(
            title = context.getString(R.string.taskbar_search_edu_title),
            canBeSkipped = true,
            tooltips = listOf(searchTooltipInfo),
            location = DisplayLocation.SEARCH_ICON,
        )
    }

    /**
     * Determines the next educational flow to show after the main features EDU has been completed.
     * It prioritizes the pinning EDU, followed by the search EDU.
     *
     * This method also marks the pinning EDU as seen if it's included in the returned tooltips.
     */
    private fun onFeaturesEduShown(): List<TooltipsEduPage>? {
        val pinningEdu = getPinningEdu()
        if (pinningEdu != null) {
            userHasSeenPinningEdu = true
            return listOf(pinningEdu)
        }
        val searchEdu = getSearchEdu()
        if (searchEdu != null) {
            return listOf(searchEdu)
        }
        return null
    }

    /**
     * Converts a collection of [TooltipInfo] objects into a list of [TooltipsEduPage] objects,
     * paginating them based on [MAX_TOOLTIPS_PER_PAGE].
     */
    private fun convertToFeaturesEduPages(
        tooltipsToShow: MutableCollection<TooltipInfo>
    ): List<TooltipsEduPage>? {
        if (tooltipsToShow.isEmpty()) {
            return null
        }
        val isSinglePage = tooltipsToShow.size <= MAX_TOOLTIPS_PER_PAGE
        val tooltipsPages = mutableListOf<TooltipsEduPage>()
        while (tooltipsToShow.isNotEmpty()) {
            // Get tooltips for current page, not exceeding list size
            val currentPageTooltipsCount = minOf(MAX_TOOLTIPS_PER_PAGE, tooltipsToShow.size)
            val currentPageTooltips = tooltipsToShow.take(currentPageTooltipsCount)
            // Remove taken tooltips from the list
            tooltipsToShow.removeAll(currentPageTooltips.toSet())
            // Check if there are more tooltips to show
            val hasMorePages = tooltipsToShow.isNotEmpty()
            // Determine action button text for current page
            val actionButtonTextResId =
                when {
                    isSinglePage -> R.string.taskbar_edu_done // done for single page tooltip
                    hasMorePages -> R.string.taskbar_edu_next // next for multi-page, non last page
                    else -> null // nothing for the last tutorial page
                }
            // check if current page is standalone pining tooltip page
            val isStandAlonePinningPage =
                currentPageTooltipsCount == 1 &&
                    currentPageTooltips.first() == pinningTooltipInfoStandAlone
            tooltipsPages.add(
                TooltipsEduPage(
                    title =
                        if (isStandAlonePinningPage) {
                            context.getString(R.string.taskbar_edu_pinning_title)
                        } else {
                            context.getString(R.string.taskbar_edu_features)
                        },
                    // can be skipped if not single page and it is the last page (no more pages)
                    canBeSkipped = !isSinglePage && !hasMorePages,
                    tooltips = currentPageTooltips,
                    actionButton = actionButtonTextResId?.let { context.getString(it) },
                    location =
                        if (isStandAlonePinningPage) {
                            DisplayLocation.SEARCH_DIVIDER
                        } else {
                            DisplayLocation.TASKBAR_CENTER
                        },
                )
            )
        }
        return tooltipsPages
    }

    /**
     * Gathers the appropriate feature tooltips to be shown to the user. This method also marks the
     * pinning EDU as seen if it's included in the returned tooltips.
     */
    private fun collectFeatureTooltipsUpdatePinning(): MutableCollection<TooltipInfo> {
        val tooltipsToShow = mutableListOf<TooltipInfo>()
        var bubblesTooltipIndex = -1
        var pinningTooltipIndex = -1
        if (!userHasSeenOldEdu) {
            tooltipsToShow.add(splitTooltipInfo)
        }
        if (createAnyBubbleEnabled) {
            bubblesTooltipIndex = tooltipsToShow.size
            tooltipsToShow.add(bubbleTooltipInfo)
        }
        if (enableRecentsInTaskbar()) {
            tooltipsToShow.add(suggestionsTooltipInfo)
        }
        if (context.isTransientTaskbar && enableTaskbarPinning() && !userHasSeenPinningEdu) {
            userHasSeenPinningEdu = true
            pinningTooltipIndex = tooltipsToShow.size
            tooltipsToShow.add(pinningTooltipInfo)
        }
        // adjust messages if tooltip is the single item one on the edu page
        if (tooltipsToShow.isSingleItemOnTheLastPage(bubblesTooltipIndex)) {
            tooltipsToShow[bubblesTooltipIndex] = bubbleTooltipInfoStandAlone
        }
        if (tooltipsToShow.isSingleItemOnTheLastPage(pinningTooltipIndex)) {
            tooltipsToShow[pinningTooltipIndex] = pinningTooltipInfoStandAlone
        }
        return tooltipsToShow
    }

    /**
     * Check weather index corresponds to the single item on the last page. The page size is taken
     * from [MAX_TOOLTIPS_PER_PAGE].
     */
    private fun List<Any>.isSingleItemOnTheLastPage(index: Int) =
        index % MAX_TOOLTIPS_PER_PAGE == 0 && index == lastIndex

    /**
     * Returns the [TooltipsEduPage] for taskbar pinning if the conditions are met (e.g., pinning is
     * enabled, in transient taskbar mode, tooltips are enabled, and the user has not seen this EDU
     * before).
     */
    private fun getPinningEdu(): TooltipsEduPage? {
        if (
            !enableTaskbarPinning() ||
                !context.isTransientTaskbar ||
                !isTooltipEnabled ||
                userHasSeenPinningEdu
        ) {
            return null
        }
        return TooltipsEduPage(
            title = context.getString(R.string.taskbar_edu_pinning_title),
            canBeSkipped = true,
            tooltips =
                listOf(
                    pinningTooltipInfo.copy(
                        message = context.getString(R.string.taskbar_edu_pinning_standalone)
                    )
                ),
            location = DisplayLocation.SEARCH_DIVIDER,
        )
    }

    /**
     * Generates the formatted disclosure text for the search educational tooltip, including
     * clickable links for the Privacy Policy and Terms of Service.
     */
    private fun getDisclosureText(
        stringId: Int = R.string.taskbar_edu_search_disclosure
    ): CharSequence {
        val resources = context.resources
        val locale = resources.configuration.locales[0]
        val text =
            SpannableString(
                HtmlCompat.fromHtml(
                    resources.getString(
                        stringId,
                        PRIVACY_POLICY_BASE_URL + locale.language,
                        TOS_BASE_URL + locale.language,
                    ),
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            )
        // Directly process URLSpan clicks
        text.getSpans(0, text.length, URLSpan::class.java).forEach { urlSpan ->
            val url: URLSpan =
                object : URLSpan(urlSpan.url) {
                    override fun onClick(widget: View) {
                        val uri = urlSpan.url.toUri()
                        val context = widget.context
                        val intent =
                            Intent(Intent.ACTION_VIEW, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }

            val spanStart = text.getSpanStart(urlSpan)
            val spanEnd = text.getSpanEnd(urlSpan)
            val spanFlags = text.getSpanFlags(urlSpan)
            text.removeSpan(urlSpan)
            text.setSpan(url, spanStart, spanEnd, spanFlags)
        }
        return text
    }

    companion object {
        /** The maximum amount of the tooltips that can be shown per page. */
        const val MAX_TOOLTIPS_PER_PAGE = 3

        /** The base URL for the Privacy Policy that will later be localized. */
        private const val PRIVACY_POLICY_BASE_URL =
            "https://policies.google.com/privacy/embedded?hl="

        /** The base URL for the Terms of Service that will later be localized. */
        private const val TOS_BASE_URL = "https://policies.google.com/terms?hl="
    }
}
