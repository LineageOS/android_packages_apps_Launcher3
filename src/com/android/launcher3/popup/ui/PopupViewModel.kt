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

package com.android.launcher3.popup.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupPopulator
import kotlin.math.min

/** Represents the currently expanded section within the collapsible popup menu. */
enum class ExpandedSection {
    /** Indicates that the system shortcuts section is expanded. */
    SYSTEM,
    /** Indicates that the deep shortcuts section is expanded. */
    DEEP,
}

/**
 * ViewModel for managing the UI state and interactions of the Launcher3 popup menu.
 *
 * This class holds the [PopupUiState] which determines how the popup content is rendered and
 * manages the expansion state of the sections. It also handles the loading of deep shortcuts
 * asynchronously.
 */
class PopupViewModel {

    /** The current UI state of the popup */
    var state: PopupUiState by mutableStateOf(PopupUiState())
        private set

    /** The currently expanded section in an accordion popup menu, or null if none are expanded. */
    var expandedSection: ExpandedSection? by mutableStateOf(null)
        private set

    /**
     * Initializes the ViewModel with the initial set of system shortcuts and a placeholder count
     * for deep shortcuts. This method determines the initial [PopupUiState].
     *
     * @param systemShortcuts The list of available system shortcuts.
     * @param deepShortcutCount The initial count of deep shortcuts. Actual deep shortcuts are
     *   loaded asynchronously later.
     */
    fun init(systemShortcuts: List<PopupItem>, deepShortcutCount: Int) {
        updateState(systemShortcuts, deepShortcutCount)
    }

    /**
     * Updates the [PopupUiState] based on the number of deep shortcuts.
     *
     * This method determines whether the popup should display only a list, an accordion, and if we
     * should show compact system shortcuts.
     *
     * @param systemShortcuts The list of available system shortcuts.
     * @param deepShortcutCount The number of deep shortcuts to consider for the current state.
     */
    private fun updateState(systemShortcuts: List<PopupItem>, deepShortcutCount: Int) {
        val numDeep = min(deepShortcutCount, PopupPopulator.MAX_SHORTCUTS)
        val deepPlaceholder = List(numDeep) { null }
        expandedSection = null

        // Scenario where we don't surpass the max amount of rows with the items we have.
        if (systemShortcuts.size + numDeep <= MAX_ROWS) {
            state =
                PopupUiState(
                    standardSystemShortcuts = systemShortcuts,
                    deepShortcuts = deepPlaceholder,
                )
            return
        }

        // Here we determine if we should show the accordion, which should only show up if
        // there are at least DEEP_SHORTCUTS_ACCORDION_THRESHOLD deep shortcuts. We also determine
        // how many system shortcuts should be compact, and we try to minimize the amount as much
        // as possible.
        val (fixed, compactEligible) =
            systemShortcuts.partition { it.category == PopupCategory.SYSTEM_SHORTCUT_FIXED }
        val isAccordion = numDeep >= DEEP_SHORTCUTS_ACCORDION_THRESHOLD
        val totalSystemItems = fixed.size + compactEligible.size

        // Calculate how many compactEligible items should be promoted to the standard list.
        val numToPromote =
            if (totalSystemItems <= MAX_STANDARD_ITEMS_ACCORDION) {
                // If totalSystemItems does not exceed MAX_STANDARD_ITEMS, promote all
                // compactEligible items. This ensures compactSystemShortcuts will be empty.
                compactEligible.size
            } else {
                // totalSystemItems > MAX_STANDARD_ITEMS, so compact items are allowed.
                val targetSize =
                    if (isAccordion) MAX_STANDARD_ITEMS_WITH_ACCORDION_WITH_TOP_BAR
                    else MAX_STANDARD_ITEMS_WITH_TOP_BAR - numDeep
                val availableSlots = maxOf(0, targetSize - fixed.size)
                min(compactEligible.size, availableSlots)
            }

        if (isAccordion) {
            expandedSection = ExpandedSection.SYSTEM
        }

        state =
            PopupUiState(
                compactSystemShortcuts = compactEligible.drop(numToPromote),
                standardSystemShortcuts = fixed + compactEligible.take(numToPromote),
                deepShortcuts = deepPlaceholder,
                mainSegmentsStyle =
                    if (isAccordion) MainSegmentsStyle.ACCORDION else MainSegmentsStyle.LIST,
            )
    }

    /**
     * Called when the actual deep shortcuts have been loaded asynchronously. This updates the
     * [PopupUiState] with the concrete deep shortcut items.
     *
     * @param deepShortcuts The list of loaded [ItemInfoWithIcon] for deep shortcuts.
     */
    fun onDeepShortcutsLoaded(deepShortcuts: List<ItemInfoWithIcon>) {
        state = state.copy(deepShortcuts = deepShortcuts)
    }

    /**
     * Toggles the expansion state of a specific section (System or Deep Shortcuts). This method
     * only has an effect if the current [PopupUiState.mainSegmentsStyle] is ACCORDION.
     *
     * If the [sectionToToggle] is currently expanded, it will be collapsed (set to null).
     * Otherwise, the specified section will be expanded.
     *
     * @param sectionToToggle The section to expand or collapse.
     */
    fun toggleSectionExpansion(sectionToToggle: ExpandedSection) {
        if (state.mainSegmentsStyle == MainSegmentsStyle.ACCORDION) {
            expandedSection =
                if (expandedSection == sectionToToggle) {
                    null
                } else {
                    sectionToToggle
                }
        }
    }

    companion object {
        /** The maximum number of rows that can be displayed */
        private const val MAX_ROWS = 7

        /**
         * The maximum number of full-size (standard) shortcuts that can be displayed with an
         * accordion menu.
         */
        private const val MAX_STANDARD_ITEMS_ACCORDION = 6

        /**
         * The maximum number of full-size (standard) shortcuts that can be displayed when a top bar
         * is present.
         */
        private const val MAX_STANDARD_ITEMS_WITH_TOP_BAR = 6

        /**
         * The maximum number of full-size (standard) shortcuts that can be displayed when a top bar
         * is present with an accordion menu.
         */
        private const val MAX_STANDARD_ITEMS_WITH_ACCORDION_WITH_TOP_BAR = 5

        /** The minimum number of deep shortcuts that can trigger the accordion layout. */
        private const val DEEP_SHORTCUTS_ACCORDION_THRESHOLD = 2
    }
}
