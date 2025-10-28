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

package com.android.launcher3.taskbar

import com.android.systemui.shared.Flags.cueBarAceMigration
import java.io.PrintWriter

class CuebarController (
    private val context: TaskbarActivityContext,
) : TaskbarControllers.LoggableTaskbarController {

    /**
     * Initializes the controller's logic.
     * This should be called from `TaskbarControllers.init()`.
     */
    fun init() {
        if (!cueBarAceMigration()) {
            return
        }
    }

    fun onDestroy() {
    }

    companion object {
        private const val TAG = "CuebarController"
    }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println("$prefix CuebarController:")
    }
}
