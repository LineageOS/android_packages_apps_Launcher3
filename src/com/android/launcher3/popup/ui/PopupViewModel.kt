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
import com.android.launcher3.popup.ui.PopupDisplayMode.*

/** Represents the currently expanded section within the collapsible popup menu. */
enum class ExpandedSection {
    /** Indicates that the system shortcuts section is expanded. */
    SYSTEM,
    /** Indicates that the deep shortcuts section is expanded. */
    DEEP,
}

/**
 * Defines the different visual display modes for the popup menu based on the number and type of
 * shortcuts.
 */
private enum class PopupDisplayMode {
    /** Only system shortcuts are present, and there are fewer than 6, shown as a simple list. */
    SYSTEM_LIST_ONLY,
    /**
     * System shortcuts are present (6 or more) with a compact top bar. Deep shortcuts are absent.
     */
    SYSTEM_LIST_WITH_TOP_BAR,
    /**
     * Combined system and deep shortcuts, with a total count of 7 or fewer, shown as a single list.
     */
    COMBINED_LIST,
    /**
     * Combined system and deep shortcuts, where the total count minus compact system shortcuts is 6
     * or fewer, using a top bar.
     */
    COMBINED_LIST_WITH_TOP_BAR,
    /**
     * Too many shortcuts to display in a simple list, requiring an accordion-style layout with the
     * System section initially expanded.
     */
    ACCORDION_SYSTEM_EXPANDED,
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
        val compactSystemShortcuts =
            systemShortcuts.filter { it.category == PopupCategory.SYSTEM_SHORTCUT }
        val standardSystemShortcuts =
            systemShortcuts.filter { it.category == PopupCategory.SYSTEM_SHORTCUT_FIXED }

        expandedSection = null

        val deepShortcuts = List(deepShortcutCount) { null }
        val displayMode = determineDisplayMode(systemShortcuts, deepShortcutCount)

        state =
            when (displayMode) {
                SYSTEM_LIST_ONLY -> PopupUiState(standardSystemShortcuts = systemShortcuts)

                SYSTEM_LIST_WITH_TOP_BAR ->
                    PopupUiState(
                        compactSystemShortcuts = compactSystemShortcuts,
                        standardSystemShortcuts = standardSystemShortcuts,
                    )

                COMBINED_LIST ->
                    PopupUiState(
                        standardSystemShortcuts = systemShortcuts,
                        deepShortcuts = deepShortcuts,
                    )

                COMBINED_LIST_WITH_TOP_BAR ->
                    PopupUiState(
                        compactSystemShortcuts = compactSystemShortcuts,
                        standardSystemShortcuts = standardSystemShortcuts,
                        deepShortcuts = deepShortcuts,
                    )

                ACCORDION_SYSTEM_EXPANDED -> {
                    expandedSection = ExpandedSection.SYSTEM
                    PopupUiState(
                        compactSystemShortcuts = compactSystemShortcuts,
                        standardSystemShortcuts = standardSystemShortcuts,
                        deepShortcuts = deepShortcuts,
                        mainSegmentsStyle = MainSegmentsStyle.ACCORDION,
                    )
                }
            }
    }

    /**
     * Determines the appropriate [PopupDisplayMode] based on the number of system and deep
     * shortcuts.
     */
    private fun determineDisplayMode(
        systemShortcuts: List<PopupItem>,
        deepShortcutCount: Int,
    ): PopupDisplayMode {
        val compactSystemShortcutSize =
            systemShortcuts.count { it.category == PopupCategory.SYSTEM_SHORTCUT }
        val totalShortcutCount = systemShortcuts.size + deepShortcutCount

        return when {
            // System shortcuts only.
            deepShortcutCount == 0 && systemShortcuts.size < 6 -> PopupDisplayMode.SYSTEM_LIST_ONLY
            // System shortcuts with top bar.
            deepShortcutCount == 0 -> PopupDisplayMode.SYSTEM_LIST_WITH_TOP_BAR
            // System shortcuts and deep shortcuts, total count <= 7.
            totalShortcutCount <= 7 -> PopupDisplayMode.COMBINED_LIST
            // System shortcuts, deep shortcuts, and top bar, with standard/deep count <= 6.
            totalShortcutCount - compactSystemShortcutSize <= 6 ->
                PopupDisplayMode.COMBINED_LIST_WITH_TOP_BAR
            // Accordion-style is needed.
            else -> PopupDisplayMode.ACCORDION_SYSTEM_EXPANDED
        }
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
}
