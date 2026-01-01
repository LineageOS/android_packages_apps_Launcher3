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
package com.android.launcher3.display

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.util.Log
import android.util.SparseArray
import android.view.Display
import android.view.WindowManager.LayoutParams
import androidx.annotation.AnyThread
import androidx.core.util.forEach
import com.android.launcher3.Flags
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import com.android.launcher3.util.window.WindowManagerProxy
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Utility class to cache properties of default display to avoid a system RPC on every call. */
@LauncherAppSingleton
class DisplayController
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val wmProxy: WindowManagerProxy,
    lifecycle: DaggerSingletonTracker,
) {
    @Deprecated("Will replace it with threadSafePerDisplayInfo")
    private val perDisplayInfo = SparseArray<DisplayInfoContainer>()

    private val threadSafePerDisplayInfo = ConcurrentHashMap<Int, DisplayInfoContainer>()

    private val isDesktopFormFactor =
        Flags.enableScalabilityForDesktopExperience() &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)

    init {
        // Initialize navigation mode change listener. We will register broadcast receiver on main
        // thread to ensure not missing changes on TARGET_OVERLAY_PACKAGE and ACTION_OVERLAY_CHANGED
        var destroyed = false
        SimpleBroadcastReceiver(appContext, Executors.MAIN_EXECUTOR) {
                Log.d(TAG, "Overlay changed, destroyed=$destroyed")
                if (!destroyed) notifyConfigChange(Display.DEFAULT_DISPLAY)
            }
            .let {
                it.register(packageFilter(TARGET_OVERLAY_PACKAGE, ACTION_OVERLAY_CHANGED))
                lifecycle.addCloseable(it)
                lifecycle.addCloseable { destroyed = true }
            }

        // Initialize display containers
        val displayManager = appContext.getSystemService(DisplayManager::class.java)!!
        val displayListener: DisplayListener =
            object : DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    displayManager.getDisplay(displayId)?.let { getOrCreatePerDisplayInfo(it) }
                }

                override fun onDisplayChanged(displayId: Int) {}

                override fun onDisplayRemoved(displayId: Int) {
                    removePerDisplayInfoById(displayId)?.cleanup()
                }
            }
        displayManager.registerDisplayListener(displayListener, Executors.MAIN_EXECUTOR.handler)
        // Add any PerDisplayInfos for already-connected displays.
        displayManager.displays.forEach { getOrCreatePerDisplayInfo(it) }
        getOrCreatePerDisplayInfo(displayManager.getDisplay(Display.DEFAULT_DISPLAY))

        lifecycle.addCloseable {
            displayManager.unregisterDisplayListener(displayListener)
            if (Flags.enableTaskbarUiThread()) {
                threadSafePerDisplayInfo.forEach { (_, container) -> container.cleanup() }
            } else {
                perDisplayInfo.forEach { _, container -> container.cleanup() }
            }
        }
    }

    @get:AnyThread
    val listenable: ListenableDiffAwareRef<LauncherDisplayInfo, Int>?
        get() = getListenable(Display.DEFAULT_DISPLAY)

    @AnyThread
    fun getListenable(displayId: Int): ListenableDiffAwareRef<LauncherDisplayInfo, Int>? {
        val displayInfoContainer = getPerDisplayInfoById(displayId)
        return displayInfoContainer?.info
    }

    @get:AnyThread
    val info: LauncherDisplayInfo
        get() = requireNotNull(getInfoForDisplay(Display.DEFAULT_DISPLAY))

    @AnyThread
    fun getInfoForDisplay(displayId: Int): LauncherDisplayInfo? =
        getPerDisplayInfoById(displayId)?.info?.value

    @AnyThread
    fun notifyConfigChange(displayId: Int) = getPerDisplayInfoById(displayId)?.notifyConfigChange()

    private fun getOrCreatePerDisplayInfo(display: Display): DisplayInfoContainer {
        val displayId = display.displayId
        return if (Flags.enableTaskbarUiThread()) {
            threadSafePerDisplayInfo.getOrPut(displayId) {
                createDisplayInfoContainer(display, displayId)
            }
        } else {
            perDisplayInfo[displayId]
                ?: createDisplayInfoContainer(display, displayId).apply {
                    perDisplayInfo[displayId] = this
                }
        }
    }

    private fun createDisplayInfoContainer(display: Display, displayId: Int): DisplayInfoContainer {
        if (DEBUG) {
            Log.d(TAG, "getOrCreatePerDisplayInfo - no cached value found for $displayId")
        }
        return DisplayInfoContainer(
            displayId,
            appContext.createWindowContext(display, LayoutParams.TYPE_APPLICATION, null),
            wmProxy,
            isDesktopFormFactor,
        )
    }

    @AnyThread
    private fun getPerDisplayInfoById(displayId: Int): DisplayInfoContainer? {
        return if (Flags.enableTaskbarUiThread()) threadSafePerDisplayInfo[displayId]
        else perDisplayInfo[displayId]
    }

    @AnyThread
    private fun removePerDisplayInfoById(displayId: Int): DisplayInfoContainer? =
        if (Flags.enableTaskbarUiThread()) {
            threadSafePerDisplayInfo.remove(displayId)
        } else {
            perDisplayInfo[displayId].also { perDisplayInfo.remove(displayId) }
        }

    /** Dumps the current state information */
    fun dump(pw: PrintWriter) {
        if (Flags.enableTaskbarUiThread()) {
            threadSafePerDisplayInfo.forEach { (displayId, container) ->
                dumpInternal(pw, displayId, container.info.value)
            }
        } else {
            perDisplayInfo.forEach { displayId, container ->
                dumpInternal(pw, displayId, container.info.value)
            }
        }
    }

    private fun dumpInternal(pw: PrintWriter, displayId: Int, info: LauncherDisplayInfo) {
        pw.println("DisplayController.Info (displayId=$displayId):")
        info.dump(pw)
    }

    companion object {
        private const val TAG = "DisplayController"
        private const val DEBUG = false

        @JvmField val INSTANCE = DaggerSingletonObject { it.displayController }

        private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
        private const val TARGET_OVERLAY_PACKAGE = "android"

        /** Returns the current navigation mode */
        @JvmStatic
        fun getNavigationMode(context: Context): NavigationMode = getInfo(context).navigationMode

        /**
         * Gets the info for whatever display the context is associated with or the default display
         * if it is not associated with a display.
         */
        @JvmStatic
        fun getInfo(context: Context): LauncherDisplayInfo {
            val controller = INSTANCE[context]
            val display = controller.wmProxy.getDisplay(context)
            val displayId = display.displayId
            val info = controller.getInfoForDisplay(displayId)
            if (info != null) {
                return info
            }
            return controller.info
        }
    }
}
