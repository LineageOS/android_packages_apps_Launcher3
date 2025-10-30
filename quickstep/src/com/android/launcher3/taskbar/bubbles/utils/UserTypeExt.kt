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

@file:JvmName("UserTypeExt")

package com.android.launcher3.taskbar.bubbles.utils

import com.android.launcher3.util.UserIconInfo
import com.android.wm.shell.shared.bubbles.UserType

/** Converts a [UserType] to a [UserIconInfo.UserType]. */
fun UserType.toUserIconInfoType(): Int =
    when (this) {
        UserType.MAIN -> UserIconInfo.TYPE_MAIN
        UserType.CLONED -> UserIconInfo.TYPE_CLONED
        UserType.WORK -> UserIconInfo.TYPE_WORK
        UserType.PRIVATE -> UserIconInfo.TYPE_PRIVATE
    }
