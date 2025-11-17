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

package com.android.launcher3.touch

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.ItemInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ItemMouseEventHandlerTest {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    @Mock private lateinit var mockLauncher: Launcher
    @Mock private lateinit var mockView: BubbleTextView

    private lateinit var itemMouseEventHandler: ItemMouseEventHandler

    @Before
    fun setUp() {
        initMock(mockLauncher)
        initMock(mockView)

        itemMouseEventHandler = ItemMouseEventHandler(mockLauncher, mockView)
    }

    private fun obtainMouseEvent(
        action: Int,
        buttonState: Int,
        x: Float = 10f,
        y: Float = 10f,
    ): MotionEvent {
        return MotionEvent.obtain(0, System.currentTimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
            setButtonState(buttonState)
        }
    }

    @Test
    fun onTouch_nonMouseEvent_isIgnored() {
        val touchEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val result = itemMouseEventHandler.onTouch(mockView, touchEvent)
        assertFalse(result)
    }

    @Test
    fun onTouch_rightClickMoveAndUp_areConsumedAfterRightClickDown() {
        val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        itemMouseEventHandler.onDown(downEvent) // Activate right click mode

        val moveEvent = obtainMouseEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_SECONDARY)
        val moveResult = itemMouseEventHandler.onTouch(mockView, moveEvent)
        assertTrue(moveResult)

        val upEvent = obtainMouseEvent(MotionEvent.ACTION_UP, 0) // Button state is 0 on UP
        val upResult = itemMouseEventHandler.onTouch(mockView, upEvent)
        assertTrue(upResult)
    }

    @Test
    fun onTouch_leftClickDragOutsideView_startsDrag() {
        val downEvent =
            obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, 10f, 10f)
        itemMouseEventHandler.onDown(downEvent)

        val moveEvent =
            obtainMouseEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY, 10f, 10f)
        val result = itemMouseEventHandler.onTouch(mockView, moveEvent)
        assertTrue(result)
        // Verify that drag was started.
        verify(mockLauncher.getWorkspace()).startDrag(any(), any())
    }

    private fun initMock(launcher: Launcher) {
        // Required for ItemLongClickListener.canStartDrag().
        whenever(launcher.isWorkspaceLocked).thenReturn(false)
        whenever(launcher.dragController).thenReturn(mock())
        whenever(launcher.dragController.isDragging).thenReturn(false)
        whenever(launcher.isSplitSelectionActive).thenReturn(false)

        // Required for ItemLongClickListener.beginDrag().
        whenever(launcher.cellPosMapper).thenReturn(mock())
        whenever(launcher.cellPosMapper.mapModelToPresenter(any())).thenReturn(mock())
        whenever(launcher.workspace).thenReturn(mock())
    }

    private fun initMock(btv: BubbleTextView) {
        whenever(btv.tag).thenReturn(ItemInfo())
    }
}
