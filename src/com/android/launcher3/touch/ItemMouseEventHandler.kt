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

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.TouchUtil

/** Handles mouse events for an item view, a.g. click, right-click, and drag-to-reorder. */
class ItemMouseEventHandler(private val context: Context, private val view: View) :
    View.OnTouchListener, GestureDetector.SimpleOnGestureListener() {

    // Lazily initialize GestureDetector. This allows unit tests to bypass its creation to avoid
    // testing GestureDetector directly, and avoid a RuntimeException in test environments lacking
    // Looper.prepare().
    private val gestureDetector: GestureDetector by lazy {
        GestureDetector(context, this).apply { setIsLongpressEnabled(false) }
    }

    private var isRightClickActive = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        // Do not handle non-mouse events here.
        if (!isMouseEvent(event)) {
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
        if (maybeStartDrag(event)) {
            return true
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun maybeStartDrag(event: MotionEvent): Boolean {
        if (
            event.action == MotionEvent.ACTION_MOVE && !TouchUtil.isMouseRightClickDownOrMove(event)
        ) {
            val launcher = Launcher.getLauncher(context)
            if (
                ItemLongClickListener.canStartDrag(launcher) &&
                    !Utilities.pointInView(view, event.x, event.y, 0.0f)
            ) {
                val options = DragOptions()
                options.isMouseDrag = true
                ItemLongClickListener.beginDrag(view, launcher, view.tag as ItemInfo, options)
                return true
            }
        }
        return false
    }

    override fun onDown(event: MotionEvent): Boolean {
        if (TouchUtil.isMouseRightClickDownOrMove(event)) {
            isRightClickActive = true
            // TODO: Perform right click action.
            return true
        }
        return super.onDown(event)
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        view.performClick()
        return true
    }

    private fun isMouseEvent(ev: MotionEvent): Boolean {
        return ev.isFromSource(InputDevice.SOURCE_MOUSE)
    }
}
