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
package com.android.launcher3.util

import android.content.Context
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.BoxSelectionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class BoxSelectionHelperTest {

    private lateinit var host: BoxSelectionHelper.BoxSelectionHost
    private lateinit var hostContainer: ViewGroup
    private lateinit var helper: BoxSelectionHelper
    private var capturedRect: Rect? = null
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        host = mock(BoxSelectionHelper.BoxSelectionHost::class.java)
        hostContainer = mock(ViewGroup::class.java)
        Mockito.`when`(host.getBoxSelectionHostContainer()).thenReturn(hostContainer)
        Mockito.`when`(hostContainer.context).thenReturn(context)
        Mockito.doAnswer { capturedRect = it.getArgument(0) }.`when`(host).onBoxSelection(any())

        helper = BoxSelectionHelper(host)
    }

    @Test
    fun onTouchEvent_fromNonMouseSource_shouldNotStartSelection_returnsFalse() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        assert(!helper.onTouchEvent(downEvent))
        assertNull(capturedRect)
    }

    @Test
    fun onTouchEvent_shouldStartSelection_dragAndRelease_callsOnBoxSelection() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100f, 120f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100f, 120f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(10, 20, 100, 120)
        assertEquals(expectedRect, capturedRect)
    }

    @Test
    fun onTouchEvent_tapWithoutDrag_callsOnBoxSelectionWithZeroSizeRect() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 50f, 50f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(50, 50, 50, 50)
        assertEquals(expectedRect, capturedRect)
    }

    @Test
    fun onTouchEvent_reverseDrag_rightToLeft_callsOnBoxSelectionWithCorrectRect() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 10f, 120f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 120f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(10, 20, 100, 120)
        assertEquals(expectedRect, capturedRect)
    }

    @Test
    fun onTouchEvent_reverseDrag_bottomToTop_callsOnBoxSelectionWithCorrectRect() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 120f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100f, 20f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100f, 20f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(10, 20, 100, 120)
        assertEquals(expectedRect, capturedRect)
    }

    @Test
    fun onTouchEvent_actionCancel_removesSelectionView() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        verify(hostContainer).addView(any<View>())

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100f, 120f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 100f, 120f, 0)
        cancelEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(cancelEvent)
        verify(hostContainer).removeView(any<View>())
    }

    @Test
    fun onTouchEvent_selectionStarted_addsAndRemovesView() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        verify(hostContainer).addView(any<View>())

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100f, 120f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)
        verify(hostContainer).removeView(any<View>())
    }

    @Test
    fun onTouchEvent_rtlLayout_rightToLeftDrag_callsOnBoxSelectionWithCorrectRect() {
        Mockito.`when`(hostContainer.layoutDirection).thenReturn(View.LAYOUT_DIRECTION_RTL)

        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 10f, 120f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 120f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(10, 20, 100, 120)
        assertEquals(expectedRect, capturedRect)
    }

    @Test
    fun onTouchEvent_rtlLayout_leftToRightDrag_callsOnBoxSelectionWithCorrectRect() {
        Mockito.`when`(hostContainer.layoutDirection).thenReturn(View.LAYOUT_DIRECTION_RTL)

        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0)
        downEvent.source = InputDevice.SOURCE_MOUSE
        assert(helper.onTouchEvent(downEvent))
        assertNotNull(capturedRect)

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100f, 120f, 0)
        moveEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100f, 120f, 0)
        upEvent.source = InputDevice.SOURCE_MOUSE
        helper.onTouchEvent(upEvent)

        val expectedRect = Rect(10, 20, 100, 120)
        assertEquals(expectedRect, capturedRect)
    }
}
