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

package com.android.quickstep.taskbar

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.BubbleTextView
import com.android.launcher3.taskbar.TaskbarAllAppsItemCustomActionsListener
import com.android.launcher3.taskbar.TaskbarBaseTestCase
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TaskbarCustomActionsListenerTest : TaskbarBaseTestCase() {

    @Mock private lateinit var taskbarOverlayContext: TaskbarOverlayContext
    @Mock private lateinit var bubbleTextView: BubbleTextView
    @Mock private lateinit var view: View

    private lateinit var listener: TaskbarAllAppsItemCustomActionsListener

    @Before
    override fun setup() {
        super.setup()

        whenever(taskbarActivityContext.controllers).thenReturn(taskbarControllers)
        whenever(taskbarOverlayContext.dragController).thenReturn(taskbarDragController)

        listener =
            TaskbarAllAppsItemCustomActionsListener(taskbarActivityContext, taskbarOverlayContext)
    }

    @Test
    fun performActions_actionLaunch_performsClick() {
        listener.performActions(view, ACTION_LAUNCH)
        verify(view).performClick()
    }

    @Test
    fun performActions_actionPopupAndDrag_performsLongClick() {
        listener.performActions(view, ACTION_POPUP_MENU or ACTION_START_DRAG)
        verify(view).performLongClick()
    }

    @Test
    fun performActions_actionPopupMenu_showsPopup() {
        listener.performActions(bubbleTextView, ACTION_POPUP_MENU)
        verify(taskbarPopupController).show(bubbleTextView)
    }

    @Test
    fun performActions_actionStartDrag_startsDrag() {
        listener.performActions(bubbleTextView, ACTION_START_DRAG)
        verify(taskbarDragController).startDragWithMouse(bubbleTextView)
    }
}
