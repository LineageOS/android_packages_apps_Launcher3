/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.recents.domain.model

import android.graphics.Rect

/**
 * Represents the layout data for a desktop task.
 *
 * @property taskId The ID of the task.
 * @property bounds The bounds of the task in fullscreen.
 * @property isObscured Whether the task is completely obscured by other tasks in fullscreen.
 */
data class FullscreenPosition(val taskId: TaskId, val bounds: Rect, val isObscured: Boolean)
