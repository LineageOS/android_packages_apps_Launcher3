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

package com.android.launcher3.organizer.creation.screen.ui

import kotlinx.coroutines.flow.StateFlow

/** Used for showing the */
interface PageUI

/**
 * Repository used for showing the workspace preview.
 *
 * It holds the UI elements needed to show a preview of the workspace and manages it's lifecycle and
 * cache.
 */
interface WorkspacePreviewRepository {

    fun getPages(): StateFlow<List<PageUI>>

    fun refreshPages()
}
