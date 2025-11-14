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
package com.android.quickstep.sysuiconnection

import android.content.Context
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.taskbar.TaskbarManager
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.dagger.SysUIConnectionComponent
import java.util.function.Consumer

/** Utility class to simplify binding to [TouchInteractionService] */
class TISBindHelper(context: Context, private val connectionCallback: Consumer<TISBindHelper>) {

    private val pendingConnectedCallbacks = ArrayList<Runnable>()

    private val activeComponent = context.appComponent.sysUIConnectionTracker.activeComponent

    val taskbarManager: TaskbarManager?
        get() = activeComponent.value?.taskbarManager

    val overviewCommandHelper: OverviewCommandHelper?
        get() = activeComponent.value?.overviewCommandHelper?.getIfReady()

    private val cleanup = activeComponent.forEach(context.mainExecutor) { onStateChanged(it) }

    private fun onStateChanged(component: SysUIConnectionComponent?) {
        component ?: return
        connectionCallback.accept(this)
        val oldTasks = pendingConnectedCallbacks.toList()
        pendingConnectedCallbacks.clear()
        oldTasks.forEach { it.run() }
    }

    /** Runs the given {@param r} runnable when the service is connected. */
    fun runOnBindToTouchInteractionService(r: Runnable) {
        if (activeComponent.value != null) r.run() else pendingConnectedCallbacks.add(r)
    }

    /** Called when the activity is destroyed to clear the binding */
    fun onDestroy() {
        cleanup.close()
        pendingConnectedCallbacks.clear()
    }
}
