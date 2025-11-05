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

package com.android.launcher3.views

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragEvent
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.dragndrop.DragController.SystemDragHandler
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.ReflectionHelpers
import com.android.launcher3.util.TestActivityContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link BaseDragLayer}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class BaseDragLayerTest {

    @get:Rule(order = 0) val flags: SetFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val mockito = MockitoJUnit.rule()
    @get:Rule(order = 2) val context = TestActivityContext()

    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var systemDragHandler: SystemDragHandler

    @Before
    fun setUp() {
        context.getDragController().addSystemDragHandler(systemDragHandler)

        // Initialize drag event.
        // NOTE: Reflection is necessary because `ViewGroup` inspects the `DragEvent.mAction` field
        // during event dispatching rather than using the mockable `DragEvent.getAction()` method.
        ReflectionHelpers.setField(mockDragEvent, "mAction", DragEvent.ACTION_DRAG_STARTED)
        doReturn(DragEvent.ACTION_DRAG_STARTED).whenever(mockDragEvent).action
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testDispatchingDragEventsDelegatesToDragControllerIfPresent() {
        context.dragLayer.dispatchDragEvent(mockDragEvent)
        verify(systemDragHandler).onDrag(mockDragEvent)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testDispatchingDragEventsWontDelegateToDragControllerWhenFlagIsDisabled() {
        context.dragLayer.dispatchDragEvent(mockDragEvent)
        verifyNoInteractions(systemDragHandler)
    }
}
