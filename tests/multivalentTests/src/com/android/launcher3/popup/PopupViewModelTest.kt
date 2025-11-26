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

package com.android.launcher3.popup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.ui.ExpandedSection
import com.android.launcher3.popup.ui.MainSegmentsStyle
import com.android.launcher3.popup.ui.PopupItem
import com.android.launcher3.popup.ui.PopupViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupViewModelTest {

    private lateinit var viewModel: PopupViewModel

    @Before
    fun setUp() {
        viewModel = PopupViewModel()
    }

    private val itemInfoWithIcon =
        object : ItemInfoWithIcon() {
            override fun clone(): ItemInfoWithIcon? {
                return null
            }
        }

    private val popupDataUi =
        PopupItem(
            iconResId = 0,
            labelResId = 0,
            popupAction = {},
            category = PopupCategory.SYSTEM_SHORTCUT,
        )
    private val popupDataUiFixed =
        PopupItem(
            iconResId = 0,
            labelResId = 0,
            popupAction = {},
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    @Test
    fun init_noDeepShortcuts_fewSystemShortcuts_showsList() {
        val systemShortcuts = List(3) { popupDataUi }

        viewModel.init(systemShortcuts, deepShortcutCount = 0)

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts).isEmpty()
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(0)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_noDeepShortcuts_manySystemShortcuts_showsListWithCompact() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(systemShortcuts, deepShortcutCount = 0)

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(4)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(0)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_withDeepShortcuts_totalLessThanOrEqualTo7_showsList() {
        val systemShortcuts = List(3) { popupDataUiFixed }

        viewModel.init(systemShortcuts, deepShortcutCount = 4)

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts).isEmpty()
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(4)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_withDeepShortcuts_totalMoreThan7_compactFits_showsListWithCompact() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(2) { popupDataUiFixed })

        viewModel.init(systemShortcuts, deepShortcutCount = 2)

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(4)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(2)
        assertThat(state.deepShortcuts.size).isEqualTo(2)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_withDeepShortcuts_totalLarge_showsAccordion() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(5) { popupDataUiFixed })

        viewModel.init(systemShortcuts, deepShortcutCount = 3)

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(4)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(5)
        assertThat(state.deepShortcuts.size).isEqualTo(3)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun onDeepShortcutsLoaded_updatesDeepShortcutsList() {
        val systemShortcuts = List(2) { popupDataUiFixed }
        viewModel.init(systemShortcuts, deepShortcutCount = 2)
        val deepShortcuts = listOf(itemInfoWithIcon, itemInfoWithIcon)

        viewModel.onDeepShortcutsLoaded(deepShortcuts)

        assertThat(viewModel.state.deepShortcuts[0]).isEqualTo(deepShortcuts[0])
        assertThat(viewModel.state.deepShortcuts[1]).isEqualTo(deepShortcuts[1])
    }

    @Test
    fun toggleSectionExpansion_notInAccordion_doesNothing() {
        val systemShortcuts = List(2) { popupDataUi }
        viewModel.init(systemShortcuts, deepShortcutCount = 2)
        assertThat(viewModel.expandedSection).isNull()

        viewModel.toggleSectionExpansion(ExpandedSection.DEEP)

        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun toggleSectionExpansion_inAccordion_togglesSection() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })
        viewModel.init(systemShortcuts, deepShortcutCount = 4)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)

        viewModel.toggleSectionExpansion(ExpandedSection.DEEP)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.DEEP)

        viewModel.toggleSectionExpansion(ExpandedSection.DEEP)
        assertThat(viewModel.expandedSection).isNull()

        viewModel.toggleSectionExpansion(ExpandedSection.SYSTEM)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }
}
