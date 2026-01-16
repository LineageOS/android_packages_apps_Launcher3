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
import android.view.DragAndDropPermissions
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SYSTEM_DRAG
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Item info used for system-level drag-and-drop. It is replaced with N-many items of more
 * appropriate types during drop handling.
 */
class SystemDragItemInfo : WorkspaceItemInfo() {

    /** The payload that was dropped in a system-level drag-and-drop sequence. */
    var payload: Payload = EmptyPayload

    init {
        itemType = ITEM_TYPE_SYSTEM_DRAG
    }

    /** Represents a payload that was dropped in a system-level drag-and-drop sequence. */
    sealed class Payload {
        /** Returns whether a payload is suitable to be accepted during drop handling. */
        abstract fun isAcceptable(): Boolean
    }

    /** Represents an empty payload that was dropped in a system-level drag-and-drop sequence. */
    data object EmptyPayload : Payload() {
        override fun isAcceptable(): Boolean = false
    }

    /**
     * Represents a payload of URIs that were dropped in a system-level drag-and-drop sequence.
     *
     * @param permissions The permissions for the URIs that were dropped.
     * @param uriList The list of URIs that were dropped.
     */
    data class UriListPayload(val permissions: DragAndDropPermissions?, val uriList: List<Uri>?) :
        Payload() {
        // NOTE: Permissions must be obtained in order to accept a system-level drop of URIs. If
        // permissions are not checked, a bad actor could piggy-back on the permissions that
        // Launcher already has.
        override fun isAcceptable(): Boolean = permissions != null && uriList?.isNotEmpty() == true
    }
}
