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

import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.DragEvent
import android.view.View
import android.view.View.DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON
import android.view.View.DRAG_FLAG_GLOBAL
import android.view.View.DRAG_FLAG_GLOBAL_URI_READ
import android.view.View.DRAG_FLAG_GLOBAL_URI_WRITE
import android.view.View.DRAG_FLAG_OPAQUE
import com.android.launcher3.Launcher
import kotlin.math.roundToInt

/**
 * Production implementation of the controller for system-level drag-and-drop. Injected when {@link
 * com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG} is enabled.
 *
 * @param systemDragListenerFactory The factory used to create listeners for system-level
 *   drag-and-drop. A unique listener instance is created per handled drag-and-drop sequence.
 */
class SystemDragControllerImpl(private val systemDragListenerFactory: SystemDragListenerFactory) :
    SystemDragController(), DragController.SystemDragHandler {

    private var launcher: Launcher? = null
    private var systemDragListener: SystemDragListener? = null

    // NOTE: Permissions must be obtained in order to accept a system-level drop. If permissions are
    // not checked, a bad actor could piggy-back on the permissions that Launcher already has.
    override fun acceptDrop(itemInfo: SystemDragItemInfo) =
        itemInfo.permissions != null && itemInfo.uriList?.isEmpty() == false

    override fun onDrag(event: DragEvent): Boolean =
        continueDrag(event) ?: startDrag(event) ?: false

    override fun setLauncher(launcher: Launcher) {
        if (this.launcher != launcher) {
            this.launcher?.dragController?.removeSystemDragHandler(this)
            this.launcher = launcher.also { it.dragController?.addSystemDragHandler(this) }
        }
    }

    override fun startDrag(params: SystemDragParams): DragView? {
        val dragController = launcher?.dragController ?: return null
        params.dragOptions.simulatedDndStartPoint = dragController.downPoint
        return createSystemDragListener(params)?.startDrag()?.also { dragView ->
            if (!startSystemDrag(dragView, params)) {
                dragController.cancelDrag()
            }
        }
    }

    private fun continueDrag(event: DragEvent): Boolean? = systemDragListener?.onDrag(event)

    private fun createSystemDragListener(params: SystemDragParams? = null): SystemDragListener? =
        launcher?.run {
            systemDragListenerFactory(this, params).also { listener ->
                systemDragListener = listener
                listener.setCleanupCallback {
                    if (systemDragListener == listener) {
                        systemDragListener = null
                    }
                }
            }
        }

    private fun startDrag(event: DragEvent): Boolean? =
        launcher?.run {
            dragController?.isDragging == false &&
                event.action == DragEvent.ACTION_DRAG_STARTED &&
                createSystemDragListener()?.onDrag(event) == true
        }

    private fun startSystemDrag(dragView: DragView, params: SystemDragParams): Boolean =
        launcher
            ?.dragLayer
            ?.startDragAndDrop(
                params.clipData,
                object : View.DragShadowBuilder() {
                    val h = (params.dragImage.intrinsicHeight * params.initialDragViewScale).toInt()
                    val w = (params.dragImage.intrinsicWidth * params.initialDragViewScale).toInt()

                    val touch =
                        Point(dragView.registrationX, dragView.registrationY).apply {
                            val offsetX = (w - params.dragImage.intrinsicWidth) / 2.0f
                            val offsetY = (h - params.dragImage.intrinsicHeight) / 2.0f
                            offset(offsetX.roundToInt(), offsetY.roundToInt())
                        }

                    override fun onDrawShadow(canvas: Canvas) {
                        val oldBounds = params.dragImage.copyBounds()
                        params.dragImage.bounds = Rect(0, 0, w, h)
                        params.dragImage.draw(canvas)
                        params.dragImage.bounds = oldBounds
                    }

                    override fun onProvideShadowMetrics(
                        outShadowSize: Point,
                        outShadowTouchPoint: Point,
                    ) {
                        outShadowSize.set(w, h)
                        outShadowTouchPoint.set(touch.x, touch.y)
                    }
                },
                /*localState=*/ null,
                /*flags=*/ DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON or
                    DRAG_FLAG_GLOBAL or
                    DRAG_FLAG_GLOBAL_URI_READ or
                    DRAG_FLAG_GLOBAL_URI_WRITE or
                    DRAG_FLAG_OPAQUE,
            ) == true
}
