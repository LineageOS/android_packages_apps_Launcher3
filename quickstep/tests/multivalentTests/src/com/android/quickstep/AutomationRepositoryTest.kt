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

package com.android.quickstep

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.extensions.computercontrol.AutomatedPackageListener
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Test for [AutomationRepositoryImpl] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AutomationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val computerControlExtensions = mock<ComputerControlExtensions>()

    private lateinit var automationRepo: AutomationRepositoryImpl
    private lateinit var automatedPackageListener: AutomatedPackageListener

    @Before
    fun setup() {
        automationRepo =
            AutomationRepositoryImpl(context, computerControlExtensions, IMMEDIATE_EXECUTOR)

        val automatedPackageListenerCaptor = argumentCaptor<AutomatedPackageListener>()
        verify(computerControlExtensions)
            .registerAutomatedPackageListener(
                eq(context),
                eq(IMMEDIATE_EXECUTOR),
                automatedPackageListenerCaptor.capture(),
            )
        assertThat(automatedPackageListenerCaptor.lastValue).isNotNull()
        automatedPackageListener = automatedPackageListenerCaptor.lastValue

        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
    }

    @Test
    fun isPackageAutomated_automated_returnsTrue() {
        assertThat(automationRepo.isPackageAutomated(AUTOMATED_PACKAGE)).isTrue()
    }

    @Test
    fun isPackageAutomated_notAutomated_returnsFalse() {
        assertThat(automationRepo.isPackageAutomated(NOT_AUTOMATED_PACKAGE)).isFalse()
    }

    @Test
    fun isPackageAutomated_noLongerAutomated_returnsTrue() {
        assertThat(automationRepo.isPackageAutomated(AUTOMATED_PACKAGE)).isTrue()
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(),
            USER_HANDLE,
        )
        assertThat(automationRepo.isPackageAutomated(AUTOMATED_PACKAGE)).isFalse()
    }

    companion object {
        private val USER_HANDLE = UserHandle(0)
        private const val AUTOMATING_PACKAGE = "com.test.automating"
        private const val AUTOMATED_PACKAGE = "com.test.automated1"
        private const val NOT_AUTOMATED_PACKAGE = "com.test.automated2"
    }
}
