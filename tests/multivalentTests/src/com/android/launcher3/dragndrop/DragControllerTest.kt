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

package com.android.launcher3.dragndrop

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragEvent
import android.view.View
import androidx.test.filters.SmallTest
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags.FLAG_ENABLE_DRAG_ENTER_EXIT_SUPPORT
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.Flags.enableDragEnterExitSupport
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.dragndrop.DragController.DragListener
import com.android.launcher3.dragndrop.DragController.SystemDragHandler
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestActivityContext
import java.util.function.Function
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link DragController}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class DragControllerTest {

    @get:Rule val context = TestActivityContext()
    @get:Rule val flags: SetFlagsRule = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var mockDragListener: DragListener
    @Mock private lateinit var mockSystemDragHandler1: SystemDragHandler
    @Mock private lateinit var mockSystemDragHandler2: SystemDragHandler

    private lateinit var controller: TestDragController

    @Before
    fun setUp() {
        controller = TestDragController(context)
    }

    @Test
    fun testConstructor() {
        // NOTE: The constructor registers the default handler for system-level drag events.
        assertEquals(
            if (enableSystemDrag()) listOf(context.activityComponent.systemDragController)
            else emptyList(),
            controller.mSystemDragHandlers,
        )
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DRAG_ENTER_EXIT_SUPPORT)
    fun testDragEnterExitSupportWithFlagDisabled() {
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = true, usePreDrag = true)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = true, usePreDrag = false)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = false, usePreDrag = true)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = false, usePreDrag = false)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_ENTER_EXIT_SUPPORT)
    fun testDragEnterExitSupportWithFlagEnabled() {
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = true, usePreDrag = true)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = true, usePreDrag = false)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = false, usePreDrag = true)
        testDragEnterExitSupport(useDragStartsWithinLauncherWindow = false, usePreDrag = false)
    }

    private fun testDragEnterExitSupport(
        useDragStartsWithinLauncherWindow: Boolean,
        usePreDrag: Boolean,
    ) {
        controller = TestDragController(context)

        val dragDriver = mock<DragDriver>()
        val dragOptions = mock<DragOptions>().apply { if (usePreDrag) preDragCondition = mock() }
        val eventX = 1.0f
        val eventY = 2.0f
        val listener = mock<DragListener>().also(controller::addDragListener)
        val target = controller.defaultDropTarget

        whenever(dragDriver.isDragWithinLauncherWindow)
            .thenReturn(useDragStartsWithinLauncherWindow)

        // Step 1: Drag start.
        controller.startDrag(dragDriver, dragOptions)
        if (!usePreDrag) {
            verify(listener).onDragStart(controller.mDragObject, dragOptions)
            if (enableDragEnterExitSupport() && useDragStartsWithinLauncherWindow) {
                verify(listener).onDragEnterWindow(controller.mDragObject, dragOptions)
            }
            if (!enableDragEnterExitSupport() || useDragStartsWithinLauncherWindow) {
                verify(target).onDragEnter(controller.mDragObject)
                verify(target).onDragOver(controller.mDragObject)
            }
        }
        verifyNoMoreInteractions(listener, target)
        clearInvocations(listener, target)

        // Step 2: Drag move.
        controller.onDriverDragMove(eventX, eventY)
        if (!usePreDrag && (!enableDragEnterExitSupport() || useDragStartsWithinLauncherWindow)) {
            verify(target).onDragOver(controller.mDragObject)
        }
        verifyNoMoreInteractions(listener, target)
        clearInvocations(listener, target)

        // Step 3: Drag enter.
        whenever(dragDriver.isDragWithinLauncherWindow).thenReturn(true)
        controller.onDriverDragEnterWindow()
        if (!usePreDrag && enableDragEnterExitSupport()) {
            verify(listener).onDragEnterWindow(controller.mDragObject, dragOptions)
        }
        verifyNoMoreInteractions(listener, target)
        clearInvocations(listener, target)

        // Step 4: Drag move.
        controller.onDriverDragMove(eventX, eventY)
        if (!usePreDrag) {
            if (enableDragEnterExitSupport() && !useDragStartsWithinLauncherWindow) {
                verify(target).onDragEnter(controller.mDragObject)
            }
            verify(target).onDragOver(controller.mDragObject)
        }
        verifyNoMoreInteractions(listener, target)
        clearInvocations(listener, target)

        // Step 5: Drag exit.
        whenever(dragDriver.isDragWithinLauncherWindow).thenReturn(false)
        controller.onDriverDragExitWindow()
        if (!usePreDrag) {
            if (enableDragEnterExitSupport()) {
                verify(listener).onDragExitWindow(controller.mDragObject, dragOptions)
            }
            verify(target).onDragExit(controller.mDragObject)
        }
        verifyNoMoreInteractions(listener, target)
        clearInvocations(listener, target)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventCancelsDragWhenHandlersBecomeUninterested() {
        controller.addDragListener(mockDragListener)
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler2.onDrag(mockDragEvent)).thenReturn(false)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).then {
            controller.startDrag() != null
        }

        // Dispatch drag event. The drag controller should transition to a dragging state.
        assertTrue(controller.onDragEvent(mockDragEvent))
        assertTrue(controller.isDragging)
        verify(mockDragListener).onDragStart(any(), any())
        inOrder(mockSystemDragHandler1, mockSystemDragHandler2).apply {
            verify(mockSystemDragHandler2).onDrag(mockDragEvent)
            verify(mockSystemDragHandler1).onDrag(mockDragEvent)
        }

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).thenReturn(false)

        // Dispatch drag event. This should result in drag cancellation since the handler which
        // originally indicated interest in the drag sequence is no longer interested.
        assertFalse(controller.onDragEvent(mockDragEvent))
        verify(mockDragListener).onDragEnd()
        verify(mockSystemDragHandler1, times(2)).onDrag(mockDragEvent)
        verifyNoMoreInteractions(mockSystemDragHandler2)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventDoesntInteractWithHandlersWhenFlagIsDisabled() {
        controller.addSystemDragHandler(mockSystemDragHandler1)
        assertFalse(controller.onDragEvent(mockDragEvent))
        verifyNoInteractions(mockSystemDragHandler1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventFallsBackToLessRecentlyRegisteredHandlers() {
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).thenReturn(true)
        whenever(mockSystemDragHandler2.onDrag(mockDragEvent)).thenReturn(false)

        // Dispatch drag event. The drag event should be propagated to the less recently registered
        // handler after the most recently registered handler because the latter is uninterested.
        assertTrue(controller.onDragEvent(mockDragEvent))
        inOrder(mockSystemDragHandler1, mockSystemDragHandler2).apply {
            verify(mockSystemDragHandler2).onDrag(mockDragEvent)
            verify(mockSystemDragHandler1).onDrag(mockDragEvent)
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventPrioritizesMostRecentlyRegisteredHandlers() {
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler2.onDrag(mockDragEvent)).thenReturn(true)

        // Dispatch drag event. The drag event should *not* be propagated to the less recently
        // registered handler because the most recently registered handler is interested.
        assertTrue(controller.onDragEvent(mockDragEvent))
        verify(mockSystemDragHandler2).onDrag(mockDragEvent)
        verifyNoInteractions(mockSystemDragHandler1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventDispatchesSubsequentEventsToInterestedHandlers() {
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).thenReturn(true)

        // Dispatch drag event. The drag event should be propagated to the less recently registered
        // handler after the most recently registered handler because the latter is uninterested.
        assertTrue(controller.onDragEvent(mockDragEvent))
        inOrder(mockSystemDragHandler1, mockSystemDragHandler2).apply {
            verify(mockSystemDragHandler2).onDrag(mockDragEvent)
            verify(mockSystemDragHandler1).onDrag(mockDragEvent)
        }

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)

        // Dispatch drag event. The drag event should only be propagated to the handler which
        // originally indicated interest in the drag sequence.
        assertTrue(controller.onDragEvent(mockDragEvent))
        verify(mockSystemDragHandler1, times(2)).onDrag(mockDragEvent)
        verifyNoMoreInteractions(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_ENDED)

        // Dispatch drag event. The drag event should only be propagated to the handler which
        // indicated a continued interest in the drag sequence.
        assertTrue(controller.onDragEvent(mockDragEvent))
        verify(mockSystemDragHandler1, times(3)).onDrag(mockDragEvent)
        verifyNoMoreInteractions(mockSystemDragHandler2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventWithholdsSubsequentEventsFromUninterestedHandlers() {
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).thenReturn(true)

        // Dispatch drag event. The drag event should be propagated to the less recently registered
        // handler after the most recently registered handler because the latter is uninterested.
        assertTrue(controller.onDragEvent(mockDragEvent))
        inOrder(mockSystemDragHandler1, mockSystemDragHandler2).apply {
            verify(mockSystemDragHandler2).onDrag(mockDragEvent)
            verify(mockSystemDragHandler1).onDrag(mockDragEvent)
        }

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).thenReturn(false)

        // Dispatch drag event. The drag event should only be propagated to the handler which
        // originally indicated interest in the drag sequence.
        assertFalse(controller.onDragEvent(mockDragEvent))
        verify(mockSystemDragHandler1, times(2)).onDrag(mockDragEvent)
        verifyNoMoreInteractions(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_ENDED)

        // Dispatch drag event. The drag event should not be propagated since the handler which
        // originally indicated interest in the drag sequence has not indicated continued interest.
        assertFalse(controller.onDragEvent(mockDragEvent))
        verifyNoMoreInteractions(mockSystemDragHandler1)
        verifyNoMoreInteractions(mockSystemDragHandler2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testOnDragEventWontCrashWithoutRegisteredHandlers() {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)

        // Dispatch drag event prior to any handler registration.
        assertFalse(controller.onDragEvent(mockDragEvent))
        verifyNoInteractions(mockSystemDragHandler1)
        verifyNoInteractions(mockSystemDragHandler2)

        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)
        controller.removeSystemDragHandler(mockSystemDragHandler1)
        controller.removeSystemDragHandler(mockSystemDragHandler2)

        // Dispatch drag event after removing all registered handlers.
        assertFalse(controller.onDragEvent(mockDragEvent))
        verifyNoInteractions(mockSystemDragHandler1)
        verifyNoInteractions(mockSystemDragHandler2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testRemoveSystemDragHandlerCancelsDrag() {
        controller.addDragListener(mockDragListener)
        controller.addSystemDragHandler(mockSystemDragHandler1)
        controller.addSystemDragHandler(mockSystemDragHandler2)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockSystemDragHandler2.onDrag(mockDragEvent)).thenReturn(false)
        whenever(mockSystemDragHandler1.onDrag(mockDragEvent)).then {
            controller.startDrag() != null
        }

        // Dispatch drag event. The drag controller should transition to a dragging state.
        assertTrue(controller.onDragEvent(mockDragEvent))
        assertTrue(controller.isDragging)
        verify(mockDragListener).onDragStart(any(), any())
        inOrder(mockSystemDragHandler1, mockSystemDragHandler2).apply {
            verify(mockSystemDragHandler2).onDrag(mockDragEvent)
            verify(mockSystemDragHandler1).onDrag(mockDragEvent)
        }

        // Remove the handler which did *not* originally indicate interest in the drag sequence.
        // This should *not* result in drag cancellation.
        controller.removeSystemDragHandler(mockSystemDragHandler2)
        verifyNoMoreInteractions(mockDragListener)
        verifyNoMoreInteractions(mockSystemDragHandler1)
        verifyNoMoreInteractions(mockSystemDragHandler2)

        // Remove the handler which *did* originally indicate interest in the drag sequence.
        // This *should* result in drag cancellation.
        controller.removeSystemDragHandler(mockSystemDragHandler1)
        verify(mockDragListener).onDragEnd()
        verifyNoMoreInteractions(mockSystemDragHandler1)
        verifyNoMoreInteractions(mockSystemDragHandler2)
    }

    private class TestDragController(context: TestActivityContext) : DragController(context) {
        val defaultDropTarget: DropTarget = mock()

        val dragDriverFactory =
            mock<Function<DragOptions?, DragDriver>>().apply {
                whenever(apply(any())).thenAnswer {
                    DragDriver.create(this@TestDragController, it.getArgument(0)) {}
                }
            }

        override fun exitDrag() {}

        override fun getDefaultDropTarget(dropCoordinates: IntArray?): DropTarget =
            defaultDropTarget

        fun startDrag(driver: DragDriver? = null, options: DragOptions? = null): DragView {
            val options = options ?: mock()
            if (driver != null) {
                whenever(dragDriverFactory.apply(options)).thenReturn(driver)
            }
            return startDrag(
                mock<Drawable>(),
                /*originalView=*/ mock(),
                /*dragLayerX=*/ 0,
                /*dragLayerY=*/ 0,
                /*source=*/ mock(),
                /*dragInfo=*/ mock(),
                /*dragRegion=*/ Rect(),
                /*initialDragViewScale=*/ 1.0f,
                /*dragViewScaleOnDrop=*/ 1.0f,
                options,
            )
        }

        override fun startDrag(
            drawable: Drawable?,
            view: View?,
            originalView: DraggableView?,
            dragLayerX: Int,
            dragLayerY: Int,
            source: DragSource?,
            dragInfo: ItemInfo?,
            dragRegion: Rect?,
            initialDragViewScale: Float,
            dragViewScaleOnDrop: Float,
            options: DragOptions?,
        ): DragView =
            mock<DragView>().also { dv ->
                mOptions = options
                mDragDriver = dragDriverFactory.apply(mOptions)
                mDragObject =
                    mock<DragObject>().apply {
                        dragSource = source
                        dragView = dv
                    }

                mIsInPreDrag =
                    mOptions?.preDragCondition != null &&
                        !mOptions.preDragCondition.shouldStartDrag(0.0)

                if (!mIsInPreDrag) {
                    callOnDragStart()
                }

                handleMoveEvent(mLastTouch.x, mLastTouch.y)
            }
    }
}
