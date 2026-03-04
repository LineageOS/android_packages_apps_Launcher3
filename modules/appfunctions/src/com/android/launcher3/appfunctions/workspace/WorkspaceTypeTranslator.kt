/**
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.appfunctions.workspace

import com.android.launcher3.appfunctions.workspace.WorkspaceSpec

/**
 * A translator for converting between the flat [WorkspaceSpec] and a structured workspace type [T]
 * actually used by the launcher.
 */
interface WorkspaceTypeTranslator<T> {

    /**
     * Converts a [T] to a [WorkspaceSpec].
     *
     * @param workspace The [T] to convert.
     * @return The converted [WorkspaceSpec].
     */
    fun toSpec(workspace: T): WorkspaceSpec

    /**
     * Converts a [WorkspaceSpec] to a [T].
     *
     * @param spec The [WorkspaceSpec] to convert.
     * @return The converted [T].
     */
    fun toWorkspace(spec: WorkspaceSpec): T
}
