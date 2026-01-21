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

package com.android.launcher3.taskbar

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.touch.CustomActionsListener
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.touch.CustomActionsListener.Companion.hasFlags

/**
 * Implementation of [CustomActionsListener] for items appearing on the Taskbar or TaskbarAllApps.
 *
 * This listener tailors the response to custom actions (like right-clicks, drags, taps) to the
 * specific functionalities and context of the Taskbar, such as showing a popup menu or initiating a
 * drag from the Taskbar.
 */
class TaskbarCustomActionsListener(private val taskbarContext: BaseTaskbarContext) :
    CustomActionsListener {
    override fun performActions(view: View, actionMask: Int) {
        when {
            hasFlags(actionMask, ACTION_POPUP_MENU or ACTION_START_DRAG) -> {
                view.performLongClick()
            }
            hasFlags(actionMask, ACTION_LAUNCH) -> {
                view.performClick()
            }
            hasFlags(actionMask, ACTION_POPUP_MENU) -> {
                if (view is BubbleTextView) {
                    taskbarContext.showPopupMenuForIcon(view)
                }
            }
            hasFlags(actionMask, ACTION_START_DRAG) -> {
                if (view is BubbleTextView) {
                    (taskbarContext.dragController as? TaskbarDragController)?.startDragWithMouse(
                        view
                    )
                }
            }
        }
    }
}
