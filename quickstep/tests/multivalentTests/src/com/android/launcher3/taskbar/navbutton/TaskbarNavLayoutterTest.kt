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
 * limitations under the License
 */

package com.android.launcher3.taskbar.navbutton

import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.DeviceConfiguration
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@SkipOnDeviceless // Causing module errors: b/486737856 raised to fix this
@RunWith(AndroidJUnit4::class)
class TaskbarNavLayoutterTest : NavButtonLayoutterTest() {

    @get:Rule val limitDevicesRule = LimitDevicesRule()

    private val mockTaskbarActivityContext: TaskbarActivityContext = mock {
        val realContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        on { theme } doReturn realContext.theme
        on { resources } doReturn realContext.resources
        on { obtainStyledAttributes(any<IntArray>()) } doAnswer
            {
                realContext.obtainStyledAttributes(it.getArgument<IntArray>(0))
            }
        on { obtainStyledAttributes(any<Int>(), any<IntArray>()) } doAnswer
            {
                realContext.obtainStyledAttributes(
                    it.getArgument<Int>(0),
                    it.getArgument<IntArray>(1),
                )
            }
    }
    private val mockDeviceProfile: DeviceProfile = mock()
    private val mockDeviceProperties: DeviceProperties = mock()
    private val mockDeviceConfiguration: DeviceConfiguration = mock()
    private val mockInvariantDeviceProfile: InvariantDeviceProfile = mock()
    private val mockDisplay: Display = mock()
    private val endContextualContainerReal by lazy {
        FrameLayout(ApplicationProvider.getApplicationContext())
    }

    init {
        whenever(mockTaskbarActivityContext.deviceProfile).thenReturn(mockDeviceProfile)
        whenever(mockTaskbarActivityContext.display).thenReturn(mockDisplay)

        try {
            val invField = DeviceProfile::class.java.getField("inv")
            invField.isAccessible = true
            invField.set(mockDeviceProfile, mockInvariantDeviceProfile)
        } catch (e: Exception) {
            throw RuntimeException("Failed to set DeviceProfile.inv field via reflection", e)
        }

        whenever(mockDeviceProfile.deviceProperties).thenReturn(mockDeviceProperties)
        whenever(mockDeviceProperties.deviceConfiguration).thenReturn(mockDeviceConfiguration)

        // Mock some dimensions to avoid NPE
        whenever(resources.getDimensionPixelSize(any())).thenReturn(10)
        whenever(resources.getDimension(any())).thenReturn(10f)

        mockInvariantDeviceProfile.inlineNavButtonsEndSpacing =
            R.dimen.taskbar_button_margin_default
    }

    @Test
    fun layoutButtons_a11yAndMoreOptionsVisible_pillIsShown() {
        val layoutter = createLayoutter(endContextualContainer = endContextualContainerReal)

        layoutter.layoutButtons(
            context = mockTaskbarActivityContext,
            isA11yButtonPersistent = false,
            isA11yVisible = true,
            isMoreOptionsVisible = true,
        )

        assertThat(endContextualContainerReal.visibility).isEqualTo(View.VISIBLE)
        // Verify we added something to endContextualContainer (the pill)
        assertThat(endContextualContainerReal.childCount).isGreaterThan(0)
    }

    @Test
    fun layoutButtons_onlyA11yVisible_soloButtonIsShown() {
        val layoutter = createLayoutter(endContextualContainer = endContextualContainerReal)

        layoutter.layoutButtons(
            context = mockTaskbarActivityContext,
            isA11yButtonPersistent = false,
            isA11yVisible = true,
            isMoreOptionsVisible = false,
        )

        assertThat(endContextualContainerReal.visibility).isEqualTo(View.VISIBLE)
        assertThat(endContextualContainerReal.getChildAt(0)).isEqualTo(a11yButton)
    }

    @Test
    fun layoutButtons_nothingVisible_containerIsGone() {
        val layoutter = createLayoutter(endContextualContainer = endContextualContainerReal)

        layoutter.layoutButtons(
            context = mockTaskbarActivityContext,
            isA11yButtonPersistent = false,
            isA11yVisible = false,
            isMoreOptionsVisible = false,
        )

        assertThat(endContextualContainerReal.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun layoutButtons_a11yPersistent_doesNotCrash() {
        val layoutter = createLayoutter(endContextualContainer = endContextualContainerReal)

        layoutter.layoutButtons(
            context = mockTaskbarActivityContext,
            isA11yButtonPersistent = true,
            isA11yVisible = true,
            isMoreOptionsVisible = false,
        )

        assertThat(endContextualContainerReal.visibility).isEqualTo(View.VISIBLE)
    }

    private fun createLayoutter(
        endContextualContainer: ViewGroup = this.endContextualContainer
    ): TaskbarNavLayoutter {
        return TaskbarNavLayoutter(
            resources,
            navButtonContainer,
            endContextualContainer,
            startContextualContainer,
            imeSwitcher,
            a11yButton,
            moreOptionsButton,
            space,
            backButton,
            homeButton,
            recentsButton,
        )
    }
}
