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

import android.net.Uri
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragEvent
import android.view.View.DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON
import android.view.View.DRAG_FLAG_GLOBAL
import android.view.View.DRAG_FLAG_GLOBAL_URI_READ
import android.view.View.DRAG_FLAG_GLOBAL_URI_WRITE
import android.view.View.DRAG_FLAG_OPAQUE
import android.view.View.DragShadowBuilder
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.views.ActivityContext
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link SystemDragControllerImpl}. */
@SmallTest
@EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
@RunWith(LauncherMultivalentJUnit::class)
class SystemDragControllerImplTest {

    @get:Rule val app = SandboxApplication()
    @get:Rule val flags = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var mockContext: ActivityContext
    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var mockItemInfo: SystemDragItemInfo
    @Mock private lateinit var mockSystemDragListener: SystemDragListener

    @Mock lateinit var mockSystemDragListenerFactory: SystemDragListenerFactory
    @Mock private lateinit var mockUri: Uri

    private lateinit var controller: SystemDragControllerImpl

    @Before
    fun setUp() {
        initMock(mockContext)
        initMock(mockSystemDragListener)
        initMock(mockSystemDragListenerFactory)

        controller = SystemDragControllerImpl(mockContext, mockSystemDragListenerFactory)
    }

    @Test
    fun testAcceptDropWhenUriListIsEmpty() {
        whenever(mockItemInfo.permissions).thenReturn(mock())
        whenever(mockItemInfo.uriList).thenReturn(emptyList())
        assertFalse(controller.acceptDrop(mockItemInfo))
    }

    @Test
    fun testAcceptDropWhenUriListIsNull() {
        whenever(mockItemInfo.permissions).thenReturn(mock())
        whenever(mockItemInfo.uriList).thenReturn(null)
        assertFalse(controller.acceptDrop(mockItemInfo))
    }

    @Test
    fun testAcceptDropWhenUriListIsPopulated() {
        whenever(mockItemInfo.permissions).thenReturn(mock())
        whenever(mockItemInfo.uriList).thenReturn(listOf(mockUri))
        assertTrue(controller.acceptDrop(mockItemInfo))
    }

    @Test
    fun testAcceptDropWhenUriListIsPopulatedButPermissionsAreNotObtained() {
        whenever(mockItemInfo.permissions).thenReturn(null)
        whenever(mockItemInfo.uriList).thenReturn(listOf(mockUri))
        assertFalse(controller.acceptDrop(mockItemInfo))
    }

    @Test
    fun testDragContinue() {
        testDragStart()
        clearInvocations(mockSystemDragListener)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockContext.dragController.isDragging).thenReturn(true)

        // NOTE: Fulfillment is delegated to the system drag listener.
        assertTrue(controller.onDrag(mockDragEvent))
        verify(mockSystemDragListener).onDrag(mockDragEvent)

        whenever(mockContext.dragController.isDragging).thenReturn(false)

        // NOTE: Fulfillment is delegated to the system drag listener.
        assertTrue(controller.onDrag(mockDragEvent))
        verify(mockSystemDragListener, times(2)).onDrag(mockDragEvent)
    }

    @Test
    fun testDragStart() {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockContext.dragController.isDragging).thenReturn(false)

        // NOTE: Fulfillment is delegated to the system drag listener.
        assertTrue(controller.onDrag(mockDragEvent))
        verify(mockSystemDragListener).onDrag(mockDragEvent)
    }

    @Test
    fun testDragStartAfterCleanup() {
        testDragStart()

        val cleanupCallbackCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(mockSystemDragListener).setCleanupCallback(cleanupCallbackCaptor.capture())
        cleanupCallbackCaptor.value.run()

        val oldMockSystemDragListener = mockSystemDragListener
        mockSystemDragListener = mock<SystemDragListener>().apply(this::initMock)

        testDragStart()
        verifyNoMoreInteractions(oldMockSystemDragListener)
    }

    @Test
    fun testDragStartWhenActionIsNotStarted() {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockContext.dragController.isDragging).thenReturn(false)

        // NOTE: Fulfillment is *not* delegated to the system drag listener.
        assertFalse(controller.onDrag(mockDragEvent))
        verifyNoInteractions(mockSystemDragListener)
    }

    @Test
    fun testDragStartWhenAlreadyDragging() {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockContext.dragController.isDragging).thenReturn(true)

        // NOTE: Fulfillment is *not* delegated to the system drag listener.
        assertFalse(controller.onDrag(mockDragEvent))
        verifyNoInteractions(mockSystemDragListener)
    }

    @Test
    fun testStartDragWithStartSystemDragFailure() {
        testStartDrag(withStartSystemDragSuccess = false)
    }

    @Test
    fun testStartDragWithStartSystemDragSuccess() {
        testStartDrag(withStartSystemDragSuccess = true)
    }

    private fun testStartDrag(withStartSystemDragSuccess: Boolean) {
        val dragShadowBuilder = argumentCaptor<DragShadowBuilder>()
        val dragView = mock<DragView>()
        val onAlphaChangeListener = argumentCaptor<Consumer<Float>>()
        val systemDragListener = mock<SystemDragListener>()
        val params =
            mock<SystemDragParams>().apply {
                whenever(clipData).thenReturn(mock())
                whenever(dragImage).thenReturn(mock())
                whenever(dragOptions).thenReturn(mock())
                whenever(extraDragFlags)
                    .thenReturn(
                        DRAG_FLAG_GLOBAL or DRAG_FLAG_GLOBAL_URI_READ or DRAG_FLAG_GLOBAL_URI_WRITE
                    )
            }

        whenever(
                mockContext.dragLayer.startDragAndDrop(
                    eq(params.clipData),
                    dragShadowBuilder.capture(),
                    /*localState=*/ isNull(),
                    eq(
                        DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON or
                            DRAG_FLAG_GLOBAL or
                            DRAG_FLAG_GLOBAL_URI_READ or
                            DRAG_FLAG_GLOBAL_URI_WRITE or
                            DRAG_FLAG_OPAQUE
                    ),
                )
            )
            .thenReturn(withStartSystemDragSuccess)

        whenever(mockSystemDragListenerFactory.get(mockContext, params))
            .thenReturn(systemDragListener)
        whenever(systemDragListener.startDrag()).thenReturn(dragView)

        // NOTE: Drag view is returned when the sequence starts successfully.
        val expectedResult = dragView
        assertEquals(expectedResult, controller.startDrag(params))

        // NOTE: Drag is cancelled when the system-level sequence fails to start successfully.
        val expectedCancellation = times(if (!withStartSystemDragSuccess) 1 else 0)
        verify(mockContext.dragController, expectedCancellation).cancelDrag()

        // NOTE: System-level drag shadow opacity is synchronized with the launcher's internal drag
        // view when the sequence starts successfully.
        if (withStartSystemDragSuccess) {
            verify(dragView).addOnAlphaChangeListener(onAlphaChangeListener.capture())
            onAlphaChangeListener.firstValue.accept(0.5f)
            verify(mockContext.dragLayer).updateDragShadow(dragShadowBuilder.firstValue)
        } else {
            verify(dragView, times(0)).addOnAlphaChangeListener(anyOrNull())
            verify(mockContext.dragLayer, times(0)).updateDragShadow(anyOrNull())
        }
    }

    private fun initMock(mockContext: ActivityContext) {
        whenever(mockContext.dragController).thenReturn(mock())
        whenever(mockContext.dragLayer).thenReturn(mock())
    }

    private fun initMock(mockSystemDragListener: SystemDragListener) {
        whenever(mockSystemDragListener.onDrag(mockDragEvent)).thenReturn(true)
    }

    private fun initMock(mockSystemDragListenerFactory: SystemDragListenerFactory) {
        whenever(mockSystemDragListenerFactory.get(any(), anyOrNull())).thenAnswer {
            mockSystemDragListener
        }
    }
}
