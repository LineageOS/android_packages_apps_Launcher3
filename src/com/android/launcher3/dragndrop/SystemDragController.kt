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

import android.view.DragEvent
import com.android.launcher3.dragndrop.DragController.SystemDragHandler

/** Controller for system-level drag-and-drop. */
sealed class SystemDragController : SystemDragHandler {

    /**
     * Returns whether a drop of the specified item info should be accepted.
     *
     * @param itemInfo The item info for which to determine acceptability.
     * @return Whether a drop should be accepted.
     */
    open fun acceptDrop(itemInfo: SystemDragItemInfo) = false

    /** Return [false] to ignore all system-level drag events. */
    override fun onDrag(event: DragEvent): Boolean = false

    /**
     * Starts a system-level drag-and-drop sequence.
     *
     * @param params The parameters to use for the sequence.
     * @return The drag view for the sequence if started successfully.
     */
    open fun startDrag(params: SystemDragParams): DragView? = null
}

/**
 * Stub implementation of the controller for system-level drag-and-drop. Injected when {@link
 * com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG} is disabled.
 */
class SystemDragControllerStub : SystemDragController()
