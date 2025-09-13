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

package com.android.launcher3.util

import android.view.View

/** Extension functions for [View] and its subclasses */
object ViewEx {

    /** Goes up the view hierarchy until a view (inclusive) matching [predicate] is found. */
    inline fun View.findInParentTree(predicate: (View) -> Boolean): View? {
        var current: View = this
        while (!predicate(current)) {
            val parent = current.parent
            if (parent is View) current = parent else return null
        }
        return current
    }
}
