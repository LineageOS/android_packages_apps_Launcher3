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
package com.android.launcher3.display;

import static android.content.pm.PackageManager.FEATURE_PC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static com.android.launcher3.Flags.enableScalabilityForDesktopExperience;
import static com.android.launcher3.Flags.enableTaskbarUiThread;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SimpleBroadcastReceiver.packageFilter;

import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.ListenableDiffAwareRef;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
@LauncherAppSingleton
public class DisplayController {

    private static final String TAG = "DisplayController";
    private static final boolean DEBUG = false;

    public static final DaggerSingletonObject<DisplayController> INSTANCE =
            new DaggerSingletonObject<>(LauncherAppComponent::getDisplayController);

    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final String TARGET_OVERLAY_PACKAGE = "android";

    private final WindowManagerProxy mWMProxy;

    private final @ApplicationContext Context mAppContext;

    // Will replace it with mThreadSafePerDisplayInfo.
    @Deprecated
    private final SparseArray<DisplayInfoContainer> mPerDisplayInfo = new SparseArray<>();

    private final ConcurrentHashMap<Integer, DisplayInfoContainer> mThreadSafePerDisplayInfo =
            new ConcurrentHashMap<>();

    // We will register broadcast receiver on main thread to ensure not missing changes on
    // TARGET_OVERLAY_PACKAGE and ACTION_OVERLAY_CHANGED.
    private final SimpleBroadcastReceiver mReceiver;

    private final boolean mIsDesktopFormFactor;
    private boolean mDestroyed = false;

    @Inject
    protected DisplayController(@ApplicationContext Context context,
            WindowManagerProxy wmProxy,
            DaggerSingletonTracker lifecycle) {
        mAppContext = context;
        mWMProxy = wmProxy;

        mIsDesktopFormFactor = enableScalabilityForDesktopExperience()
                && mAppContext.getPackageManager().hasSystemFeature(FEATURE_PC);

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(DEFAULT_DISPLAY);
        DisplayInfoContainer defaultDisplayInfoContainer =
                getOrCreatePerDisplayInfo(defaultDisplay);

        // Initialize navigation mode change listener
        mReceiver = new SimpleBroadcastReceiver(context, MAIN_EXECUTOR, this::onIntent);
        mReceiver.register(packageFilter(TARGET_OVERLAY_PACKAGE, ACTION_OVERLAY_CHANGED));

        final DisplayManager.DisplayListener displayListener =
                new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                        Display display = displayManager.getDisplay(displayId);
                        if (display != null) {
                            getOrCreatePerDisplayInfo(display);
                        }
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                        removePerDisplayInfo(displayId);
                    }
                };
        displayManager.registerDisplayListener(displayListener, MAIN_EXECUTOR.getHandler());
        lifecycle.addCloseable(() -> {
            displayManager.unregisterDisplayListener(displayListener);
        });
        // Add any PerDisplayInfos for already-connected displays.
        Arrays.stream(displayManager.getDisplays())
                .forEach((it) ->
                        getOrCreatePerDisplayInfo(
                                displayManager.getDisplay(it.getDisplayId())));

        lifecycle.addCloseable(() -> {
            mDestroyed = true;
            defaultDisplayInfoContainer.cleanup();
            mReceiver.close();
        });
    }

    /**
     * Returns the current navigation mode
     */
    public static NavigationMode getNavigationMode(Context context) {
        return getInfo(context).getNavigationMode();
    }

    /**
     * Returns whether the display is in desktop-first mode.
     */
    public static boolean isInDesktopFirstMode(Context context) {
        return getInfo(context).isInDesktopFirstMode;
    }

    /**
     * Returns whether the taskbar is forced to be pinned when home is visible on the display
     * associated with the context.
     */
    public static boolean showLockedTaskbarOnHome(Context context) {
        return getInfo(context).showLockedTaskbarOnHome;
    }

    /**
     * Returns whether desktop taskbar (pinned taskbar that shows desktop tasks) is to be used
     * on the display because the display is a freeform display.
     */
    public static boolean showDesktopTaskbarForFreeformDisplay(Context context) {
        return getInfo(context).getShowDesktopTaskbarForFreeformDisplay();
    }

    // Gets the info for whatever display the context is associated with or the default display
    // if it is not associated with a display.
    private static LauncherDisplayInfo getInfo(Context context) {
        DisplayController controller = INSTANCE.get(context);
        Display display = controller.mWMProxy.getDisplay(context);
        int displayId = display.getDisplayId();
        LauncherDisplayInfo info = controller.getInfoForDisplay(displayId);
        if (info != null) {
            return info;
        }
        return controller.getInfo();
    }

    private void onIntent(Intent intent) {
        if (mDestroyed) {
            return;
        }
        if (ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
            Log.d(TAG, "Overlay changed, notifying listeners");
            notifyConfigChange(DEFAULT_DISPLAY);
        }
    }

    @VisibleForTesting
    public void onConfigurationChanged(Configuration config) {
        requireNonNull(getPerDisplayInfoById(DEFAULT_DISPLAY)).onConfigurationChanged(config);
    }

    @Nullable
    @AnyThread
    public ListenableDiffAwareRef<LauncherDisplayInfo, Integer> getListenable() {
        return getListenable(DEFAULT_DISPLAY);
    }

    @Nullable
    @AnyThread
    public ListenableDiffAwareRef<LauncherDisplayInfo, Integer> getListenable(int displayId) {
        DisplayInfoContainer displayInfoContainer = getPerDisplayInfoById(displayId);
        return displayInfoContainer != null ? displayInfoContainer.getInfo() : null;
    }

    @AnyThread
    public LauncherDisplayInfo getInfo() {
        return requireNonNull(getInfoForDisplay(DEFAULT_DISPLAY));
    }

    @AnyThread
    public @Nullable LauncherDisplayInfo getInfoForDisplay(int displayId) {
        DisplayInfoContainer displayInfoContainer = getPerDisplayInfoById(displayId);
        if (displayInfoContainer != null) {
            return displayInfoContainer.getInfo().getValue();
        } else {
            return null;
        }
    }

    @AnyThread
    public void notifyConfigChange(int displayId) {
        DisplayInfoContainer displayInfoContainer = getPerDisplayInfoById(displayId);
        if (displayInfoContainer != null) displayInfoContainer.notifyConfigChange();
    }

    @VisibleForTesting
    protected DisplayInfoContainer getOrCreatePerDisplayInfo(Display display) {
        int displayId = display.getDisplayId();
        DisplayInfoContainer displayInfoContainer = getPerDisplayInfoById(displayId);
        if (displayInfoContainer != null) {
            return displayInfoContainer;
        }
        if (DEBUG) {
            Log.d(TAG,
                    String.format("getOrCreatePerDisplayInfo - no cached value found for %d",
                            displayId));
        }
        Context windowContext = mAppContext.createWindowContext(display, TYPE_APPLICATION, null);
        displayInfoContainer = new DisplayInfoContainer(
                displayId, windowContext, mWMProxy, mIsDesktopFormFactor);
        putPerDisplayInfoById(displayId, displayInfoContainer);
        return displayInfoContainer;
    }

    /**
     * Clean up resources for the given display id.
     * @param displayId The display id
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    protected void removePerDisplayInfo(int displayId) {
        DisplayInfoContainer info = removePerDisplayInfoById(displayId);
        if (info != null) {
            info.cleanup();
        }
    }

    @Nullable
    @AnyThread
    private DisplayInfoContainer getPerDisplayInfoById(int displayId) {
        return enableTaskbarUiThread()
                ? mThreadSafePerDisplayInfo.get(displayId) : mPerDisplayInfo.get(displayId);
    }

    @AnyThread
    private void putPerDisplayInfoById(int displayId, DisplayInfoContainer info) {
        if (enableTaskbarUiThread()) {
            mThreadSafePerDisplayInfo.put(displayId, info);
        } else {
            mPerDisplayInfo.put(displayId, info);
        }
    }

    @AnyThread
    @Nullable
    private DisplayInfoContainer removePerDisplayInfoById(int displayId) {
        if (enableTaskbarUiThread()) {
            return mThreadSafePerDisplayInfo.remove(displayId);
        } else {
            DisplayInfoContainer ret = mPerDisplayInfo.get(displayId);
            mPerDisplayInfo.remove(displayId);
            return ret;
        }
    }

    /**
     * Dumps the current state information
     */
    public void dump(PrintWriter pw) {
        if (enableTaskbarUiThread()) {
            for (int displayId: mThreadSafePerDisplayInfo.keySet()) {
                dumpInternal(pw, displayId);
            }
        } else {
            int count = mPerDisplayInfo.size();
            for (int i = 0; i < count; ++i) {
                int displayId = mPerDisplayInfo.keyAt(i);
                dumpInternal(pw, displayId);
            }
        }
    }

    private void dumpInternal(PrintWriter pw, int displayId) {
        LauncherDisplayInfo info = getInfoForDisplay(displayId);
        if (info == null) {
            return;
        }
        pw.println(String.format(Locale.ENGLISH, "DisplayController.Info (displayId=%d):",
                displayId));
        pw.println("  normalizedDisplayInfo=" + info.normalizedDisplayInfo);
        pw.println("  rotation=" + info.rotation);
        pw.println("  fontScale=" + info.fontScale);
        pw.println("  densityDpi=" + info.getDensityDpi());
        pw.println("  navigationMode=" + info.getNavigationMode().name());
        pw.println("  isInDesktopFirstMode=" + info.isInDesktopFirstMode);
        pw.println("  showLockedTaskbarOnHome=" + info.showLockedTaskbarOnHome);
        pw.println("  currentSize=" + info.currentSize);
        info.getPerDisplayBounds().forEach((key, value) -> pw.println(
                "  perDisplayBounds - " + key + ": " + value));
    }

}
