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

import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.touch.CustomActionsListener.Companion.hasFlags
import com.android.launcher3.util.ShortcutUtil

/**
 * Interface for listening to custom actions performed on a view.
 *
 * This listener is used to abstract the handling of actions like launching, showing a popup menu,
 * or starting a drag operation, typically triggered by custom touch events or gestures.
 */
interface CustomActionsListener {
    /**
     * Called when custom actions should be performed on the view.
     *
     * @param view The view on which the actions are performed.
     * @param actionMask A bitmask representing the actions to be performed. See [Companion] for
     *   possible action flags.
     */
    fun performActions(view: View, actionMask: Int)

    /** Companion object containing action flags. */
    companion object {
        const val ACTION_POPUP_MENU = 1 shl 0
        const val ACTION_START_DRAG = 1 shl 1
        const val ACTION_LAUNCH = 1 shl 2

        /** Checks if the mask contains ALL of the specified flags */
        fun hasFlags(mask: Int, flags: Int): Boolean {
            return (mask and flags) == flags
        }
    }
}

/**
 * Implementation of [CustomActionsListener] for workspace items (icons, folders, app pairs, etc).
 */
object WorkspaceItemCustomActionsListener : CustomActionsListener {
    override fun performActions(view: View, actionMask: Int) {
        when {
            hasFlags(actionMask, ACTION_POPUP_MENU or ACTION_START_DRAG) -> {
                view.performLongClick()
            }
            hasFlags(actionMask, ACTION_LAUNCH) -> {
                view.performClick()
            }
            hasFlags(actionMask, ACTION_POPUP_MENU) -> {
                val launcher = Launcher.getLauncher(view.context)
                if (ShortcutUtil.supportsShortcuts(view.tag as ItemInfo)) {
                    launcher.popupControllerForAppIcons.show(view)
                } else {
                    launcher.popupControllerForHomeScreenItems.show(view)
                }
            }
            hasFlags(actionMask, ACTION_START_DRAG) -> {
                val launcher = Launcher.getLauncher(view.context)
                if (ItemLongClickListener.canStartDrag(launcher)) {
                    val options = DragOptions().apply { isMouseDrag = true }
                    // TODO: To trigger a drag and not show a popup at the same time, we currently
                    // rely on setting isMouseDrag to true. Refactor beginDrag code not to rely on
                    // ItemLongClickListener path.
                    ItemLongClickListener.beginDrag(view, launcher, view.tag as ItemInfo, options)
                }
            }
        }
    }
}
