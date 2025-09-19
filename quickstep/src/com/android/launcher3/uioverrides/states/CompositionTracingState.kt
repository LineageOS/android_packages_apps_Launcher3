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

package com.android.launcher3.uioverrides.states

import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.COMPOSITION_TRACING_PREF_KEY
import com.android.launcher3.LauncherPrefs.Companion.ENABLE_COMPOSITION_TRACING
import com.android.quickstep.compose.QuickstepComposeFacade

class CompositionTracingState(private val launcherPrefs: LauncherPrefs) :
    LauncherPrefChangeListener {

    private var isCompositionTracingEnabled = false

    fun initialize() {
        isCompositionTracingEnabled = getLatestState()

        launcherPrefs.addListener(this, ENABLE_COMPOSITION_TRACING)
    }

    fun destroy() {
        launcherPrefs.removeListener(this, ENABLE_COMPOSITION_TRACING)
    }

    override fun onPrefChanged(key: String?) {
        if (key == COMPOSITION_TRACING_PREF_KEY) {
            val wasCompositionTracingEnabled = isCompositionTracingEnabled
            isCompositionTracingEnabled = getLatestState()

            when {
                isCompositionTracingEnabled && !wasCompositionTracingEnabled ->
                    QuickstepComposeFacade.enableCompositionTracing()

                !isCompositionTracingEnabled && wasCompositionTracingEnabled ->
                    QuickstepComposeFacade.disableCompositionTracing()
            }
        }
    }

    private fun getLatestState() = launcherPrefs.get(ENABLE_COMPOSITION_TRACING)
}
