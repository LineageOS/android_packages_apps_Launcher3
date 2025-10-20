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

package com.android.launcher3.qsb

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_PACKAGE_ADDED
import android.content.Intent.ACTION_PACKAGE_CHANGED
import android.content.Intent.ACTION_PACKAGE_REMOVED
import android.content.Intent.ACTION_SEARCH
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.InstallSessionTracker
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SecureStringObserver
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import javax.inject.Inject

/**
 * Manager to handle when OnDevice Search Engine selection changes.
 *
 * Listens to Settings.Secure and PackageManager.
 */
@LauncherAppSingleton
class OSEManager(
    val context: Context,
    private val settingsObserver: SecureStringObserver,
    private val installhelper: InstallSessionHelper,
    private val executor: LooperExecutor = OSE_LOOPER,
) {

    private val packageAvailableReceiver =
        SimpleBroadcastReceiver(context, executor) { reloadOse() }
    @VisibleForTesting var tracker: InstallSessionTracker? = null
    private val mutableOSEInfoRef = MutableListenableRef(OSEInfo())

    /**
     * Represents the current OSE Info and this should be used by consumers and listen to the value
     * changes
     */
    val oseInfo = mutableOSEInfoRef.asListenable()
    private val defaultSearchPackage =
        context.getSystemService(SearchManager::class.java)?.globalSearchActivity?.packageName
            ?: context.resources.getString(R.string.fallback_search_package_name)

    @Inject
    constructor(
        @ApplicationContext context: Context,
        tracker: DaggerSingletonTracker,
        installhelper: InstallSessionHelper,
    ) : this(
        context,
        SecureStringObserver(context, OSE_LOOPER.handler, SEARCH_ENGINE_SETTINGS_KEY),
        installhelper,
    ) {
        settingsObserver.callback = Runnable { reloadOse() }
        executor.execute { reloadOse() }
        tracker.addCloseable(this::close)
    }

    @WorkerThread
    @VisibleForTesting
    fun reloadOse() {
        Preconditions.assertNonUiThread()
        val oseSettingsValue = settingsObserver.getValue()
        val appInfoWrapper =
            oseSettingsValue?.let { ApplicationInfoWrapper(context, it, myUserHandle()) }
        val oseApkInstalled = appInfoWrapper?.run { isInstalled() && !isArchived() } ?: false
        val activeInstallSession =
            oseSettingsValue?.let {
                installhelper.getActiveSessionInfo(myUserHandle(), oseSettingsValue) != null
            } ?: false

        // Check if package is being installed or is already installed
        val osePkg: String? =
            when {
                oseApkInstalled || activeInstallSession -> oseSettingsValue
                // No install session available, so fallback to defaultSearchPackage
                isDefaultSearchPackageEnabled() -> defaultSearchPackage
                else -> null
            }

        val oseApkInstallPending =
            when {
                oseApkInstalled -> false
                activeInstallSession -> true
                // No install session available, so apk install is not pending
                else -> false
            }

        val oseConfigured = oseSettingsValue != null && (oseApkInstalled || activeInstallSession)

        unregisterInstallSessionTracker()
        if (!oseApkInstalled) {
            // Register to track ose package being installed.
            // Continue tracking in case the user manually installs again.
            tracker =
                oseSettingsValue?.let {
                    installhelper.registerInstallTracker(SessionTrackerCallback(it))
                }
        }

        val overlayAppsList =
            context.resources.getStringArray(R.array.supported_overlay_apps).asList()
        // Look into the "supported_overlay_apps" Array based on OsePackage and fallback to first
        // entry in overlay or null
        val overlayPkg: String? =
            if (overlayAppsList.contains(osePkg)) osePkg
            else if (overlayAppsList.isNotEmpty()) overlayAppsList[0] else null

        val overlayTarget =
            overlayPkg
                ?.runCatching {
                    context.packageManager
                        .resolveActivity(Intent(OVERLAY_ACTION).setPackage(overlayPkg), 0)
                        ?.activityInfo
                }
                ?.getOrNull()

        val supportsSearchIntent =
            osePkg
                ?.runCatching {
                    context.packageManager.resolveActivity(
                        Intent(ACTION_SEARCH).setPackage(osePkg),
                        0,
                    )
                }
                ?.getOrNull() != null

        val oldOseInfo = mutableOSEInfoRef.value
        val newOseInfo =
            OSEInfo(
                osePkg,
                overlayTarget,
                oseApkInstallPending,
                oseConfigured,
                supportsSearchIntent,
            )
        Log.i(TAG, "reloadOse oldOseInfo= " + oldOseInfo + "\nnewOseInfo= " + newOseInfo)
        if (osePkg == null && oldOseInfo.pkg == newOseInfo.pkg) {
            // osePkg can be null only when defaultSearchPackage is disabled.
            // So register to defaultSearchPackage changes.
            // This condition can only happen with launcher force-stop or reboot of the device when
            // defaultSearchPackage is disabled
            packageAvailableReceiver.register(
                packageFilter(
                    defaultSearchPackage,
                    ACTION_PACKAGE_ADDED,
                    ACTION_PACKAGE_CHANGED,
                    ACTION_PACKAGE_REMOVED,
                )
            )
        }

        if (
            oldOseInfo.pkg != newOseInfo.pkg ||
                oldOseInfo.overlayPackage != newOseInfo.overlayPackage ||
                oldOseInfo.installPending != newOseInfo.installPending ||
                oldOseInfo.isOseConfigured != newOseInfo.isOseConfigured
        ) {
            packageAvailableReceiver.close()
            // Listen for ose changes
            if (osePkg != null) {
                packageAvailableReceiver.register(
                    packageFilter(
                        osePkg,
                        ACTION_PACKAGE_ADDED,
                        ACTION_PACKAGE_CHANGED,
                        ACTION_PACKAGE_REMOVED,
                    )
                )
                // Listen to the OseSettingValue package as well if it's installed little later or
                // if the app gets archived/restored.
                if (oseSettingsValue != null && oseSettingsValue != osePkg) {
                    packageAvailableReceiver.register(
                        packageFilter(
                            oseSettingsValue,
                            ACTION_PACKAGE_ADDED,
                            ACTION_PACKAGE_CHANGED,
                            ACTION_PACKAGE_REMOVED,
                        )
                    )
                }
            }

            // Listen for overlay changes
            if (overlayPkg != null && osePkg != overlayPkg) {
                packageAvailableReceiver.register(
                    packageFilter(
                        overlayPkg,
                        ACTION_PACKAGE_ADDED,
                        ACTION_PACKAGE_CHANGED,
                        ACTION_PACKAGE_REMOVED,
                    )
                )
            }

            mutableOSEInfoRef.dispatchValue(newOseInfo)
        }
    }

    private fun isDefaultSearchPackageEnabled(): Boolean {
        try {
            return defaultSearchPackage?.let {
                context
                    .getSystemService(LauncherApps::class.java)
                    ?.getApplicationInfo(
                        it,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES,
                        myUserHandle(),
                    )
                    ?.enabled
            } ?: false
        } catch (e: NameNotFoundException) {
            return false
        }
    }

    private fun unregisterInstallSessionTracker() {
        tracker?.close()
        tracker = null
    }

    @VisibleForTesting
    fun close() {
        settingsObserver.close()
        packageAvailableReceiver.close()
        executor.execute { unregisterInstallSessionTracker() }
    }

    /** Object representing properties of the on-device search engine */
    class OSEInfo(
        val pkg: String? = null,
        val overlayTarget: ActivityInfo? = null,
        val installPending: Boolean = false,
        val isOseConfigured: Boolean = false,
        val supportsSearchIntent: Boolean = false,
    ) {
        val overlayPackage: String?
            get() = overlayTarget?.packageName ?: pkg

        override fun toString(): String {
            return "pkg=" +
                pkg +
                " overlayPackage=" +
                overlayPackage +
                " installPending=" +
                installPending +
                " isOseConfigured=" +
                isOseConfigured +
                " supportsSearchIntent=" +
                supportsSearchIntent
        }
    }

    companion object {
        const val TAG = "OSEManager"
        const val SEARCH_ENGINE_SETTINGS_KEY = "selected_search_engine"

        val OSE_LOOPER = LooperExecutor("OSEManager")

        const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
    }

    inner class SessionTrackerCallback(val osePackage: String) : InstallSessionTracker.Callback {

        override fun onSessionFailure(packageName: String, user: UserHandle) {
            if (packageName == osePackage) {
                // Session failed - fallback to defaultSearchPackage
                postInstallSessionUpdate()
            }
        }

        override fun onUpdateSessionDisplay(
            key: PackageUserKey,
            info: PackageInstaller.SessionInfo,
        ) {
            // Do nothing
        }

        override fun onPackageStateChanged(info: PackageInstallInfo) {
            if (
                info.packageName == osePackage && info.state == PackageInstallInfo.STATUS_INSTALLED
            ) {
                // OsePkg installation is successful, reloadOse to update oseApkInstallPending value
                postInstallSessionUpdate()
            }
        }

        override fun onInstallSessionCreated(info: PackageInstallInfo) {
            if (info.packageName == osePackage) {
                // If the oseSettingsValue is still the same and install session got created for the
                // same package then reloadOse to  update oseApkInstallPending value
                postInstallSessionUpdate()
            }
        }

        private fun postInstallSessionUpdate() {
            executor.execute { reloadOse() }
        }
    }
}
