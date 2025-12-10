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

import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.util.TouchUtil
import kotlin.math.abs

/**
 * Handles touch events to trigger custom actions on a view, based on gestures like taps, long
 * presses, and mouse-specific interactions like right clicks and drags.
 *
 * This class uses a [GestureDetector] to interpret touch events and delegates the actual action
 * handling to a [CustomActionsListener] attached to the view.
 *
 * @property view The view to which this touch handler is attached.
 */
class CustomEventsTouchHandler(private val view: View) :
    GestureDetector.SimpleOnGestureListener(), CustomTouchDelegate {

    private var downX = 0f
    private var downY = 0f

    private val gestureDetector: GestureDetector =
        GestureDetector(view.context, this, Handler(Looper.getMainLooper()))

    private var isRightClickActive = false

    /**
     * Processes a touch event.
     *
     * This method detects single-tap, long-press, right-click, and mouse-drag to trigger the
     * appropriate custom actions.
     *
     * @param event The MotionEvent to handle.
     * @return True if the event is handled by this handler, false otherwise.
     */
    override fun onDelegateTouchEvent(event: MotionEvent): Boolean {
        if (!Flags.enableCursorDrivenWorkflows() || getCustomActionsListener() == null) {
            return false
        }

        // If a right click has been triggered onDown(), then consume all events.
        if (isRightClickActive) {
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isRightClickActive = false
            }
            return true
        }

        if (shouldStartMouseDrag(event)) {
            performActions(ACTION_START_DRAG)
            return true
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDown(event: MotionEvent): Boolean {
        downX = event.x
        downY = event.y
        if (TouchUtil.isMouseRightClickDownOrMove(event)) {
            isRightClickActive = true
            performActions(ACTION_POPUP_MENU)
        }
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        if (!isMouseEvent(event)) {
            performActions(ACTION_POPUP_MENU or ACTION_START_DRAG)
        }
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        performActions(ACTION_LAUNCH)
        return true
    }

    private fun getCustomActionsListener(): CustomActionsListener? {
        return view.getTag(R.id.custom_actions_listener) as? CustomActionsListener
    }

    private fun performActions(actionMask: Int) {
        getCustomActionsListener()?.performActions(view, actionMask)
    }

    private fun shouldStartMouseDrag(event: MotionEvent): Boolean {
        if (
            !isMouseEvent(event) ||
                event.action != MotionEvent.ACTION_MOVE ||
                TouchUtil.isMouseRightClickDownOrMove(event)
        ) {
            return false
        }
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        return abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
    }

    private fun isMouseEvent(event: MotionEvent): Boolean {
        return event.isFromSource(InputDevice.SOURCE_MOUSE)
    }
}
