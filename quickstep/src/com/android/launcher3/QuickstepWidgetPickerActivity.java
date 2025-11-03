/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.launcher3.Utilities.shouldEnableCursorDrivenWorkflows;
import static com.android.launcher3.Utilities.shouldEnableMouseInteractionChanges;

import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.widgetpicker.WidgetPickerConfig;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.TISBindHelper;
import com.android.systemui.animation.back.FlingOnBackAnimationCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** An Activity that can host Launcher's widget picker for additional surfaces. */
public class QuickstepWidgetPickerActivity extends
        com.android.launcher3.widgetpicker.WidgetPickerActivity {
    private static final String TAG = "WidgetPickerActivity";
    // Unlike the AppWidgetManager.EXTRA_CATEGORY_FILTER, this filter removes certain categories.
    // Filter is ignore if it is not a negative value.
    // Example usage: WIDGET_CATEGORY_HOME_SCREEN.inv() and WIDGET_CATEGORY_NOT_KEYGUARD.inv()
    private static final String EXTRA_CATEGORY_EXCLUSION_FILTER = "category_exclusion_filter";
    /**
     * Intent extra for the string representing the title displayed within the picker header.
     */
    private static final String EXTRA_PICKER_TITLE = "picker_title";
    /**
     * Intent extra for the string representing the description displayed within the picker header.
     */
    private static final String EXTRA_PICKER_DESCRIPTION = "picker_description";

    /**
     * A unique identifier of the surface hosting the widgets;
     * <p>"widgets" is reserved for home screen surface.</p>
     * <p>"widgets_hub" is reserved for lockscreen hub surface.</p>
     */
    private static final String EXTRA_UI_SURFACE = "ui_surface";
    private static final String LOCKSCREEN_WIDGETS_HUB_UI_SURFACE = "widgets_hub";
    private static final Pattern UI_SURFACE_PATTERN =
            Pattern.compile("^(widgets|widgets_hub)$");
    /**
     * User ids that should be filtered out of the widget lists created by this activity.
     */
    private static final String EXTRA_USER_ID_FILTER = "filtered_user_ids";

    private TISBindHelper mTISBindHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);

        setWidgetPickerConfig(parseIntentExtras());
        super.onCreate(savedInstanceState);

        if (getWidgetPickerConfig().getUiSurface().equals(LOCKSCREEN_WIDGETS_HUB_UI_SURFACE)) {
            getWindow().getDecorView().getWindowInsetsController().hide(
                    navigationBars() + statusBars());
        }
    }

    @Override
    protected void registerBackDispatcher() {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new BackAnimationCallback());
    }

    @NonNull
    protected WidgetPickerConfig parseIntentExtras() {
        String title = getIntent().getStringExtra(EXTRA_PICKER_TITLE);
        String description = getIntent().getStringExtra(EXTRA_PICKER_DESCRIPTION);

        // Defaults to '0' to indicate that there isn't a category filter.
        // Negative value indicates it's an exclusion filter (e.g. NOT_KEYGUARD_CATEGORY.inv())
        // Positive value indicates it's inclusion filter (e.g. HOME_SCREEN or KEYGUARD)
        // Note: A filter can either be inclusion or exclusion filter; not both.
        int inclusionFilter = getIntent().getIntExtra(AppWidgetManager.EXTRA_CATEGORY_FILTER, 0);
        if (inclusionFilter < 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_FILTER: " + inclusionFilter);
        }
        int exclusionFilter = getIntent().getIntExtra(EXTRA_CATEGORY_EXCLUSION_FILTER, 0);
        if (exclusionFilter > 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_EXCLUSION_FILTER: " + exclusionFilter);
        }

        String uiSurfaceParam = getIntent().getStringExtra(EXTRA_UI_SURFACE);
        String uiSurface = WidgetPickerConfig.HOMESCREEN_WIDGETS_UI_SURFACE;
        if (uiSurfaceParam != null && UI_SURFACE_PATTERN.matcher(uiSurfaceParam).matches()) {
            uiSurface = uiSurfaceParam;
        }

        List<UserHandle> filteredUsers = List.of();
        ArrayList<Integer> filteredUserIds = getIntent().getIntegerArrayListExtra(
                EXTRA_USER_ID_FILTER);
        if (filteredUserIds != null) {
            filteredUsers = filteredUserIds.stream().map(UserHandle::of).toList();
        }

        DeviceProfile deviceProfile = LauncherComponentProvider.get(this)
                .getIDP()
                .getDeviceProfile(this);

        return new WidgetPickerConfig(
                /*uiSurface=*/ uiSurface,
                /*title=*/ title,
                /*description=*/ description,
                /*categoryInclusionFilter=*/ inclusionFilter,
                /*categoryExclusionFilter=*/ exclusionFilter,
                /*filteredUsers=*/ filteredUsers,
                /*handleSwipeUpGesture=*/ deviceProfile.getDeviceProperties().isGestureMode(),
                /*isDesktopFormFactor=*/ shouldEnableMouseInteractionChanges(
                        getApplicationContext()),
                /*enableCursorDrivenWorkflows=*/ shouldEnableCursorDrivenWorkflows(
                        getApplicationContext()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceState(true);
    }

    private void onTISConnected(TouchInteractionService.TISBinder binder) {
        updateServiceState(isResumed());
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateServiceState(false);
    }

    private void updateServiceState(boolean isEnabled) {
        if (DisplayController.showDesktopTaskbarForFreeformDisplay(this)) {
            // Avoid blocking gestures when taskbar is always shown. Gestures should still allow
            // user to return home in this case.
            return;
        }
        TouchInteractionService.TISBinder binder = mTISBindHelper.getBinder();
        if (binder != null) {
            binder.setGestureBlockedTaskId(isEnabled ? getTaskId() : -1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTISBindHelper.onDestroy();
        updateServiceState(false);
    }

    /**
     * Animation callback for different predictive back animation states for the widget picker.
     */
    private static class BackAnimationCallback extends FlingOnBackAnimationCallback {
        @Nullable
        OnBackAnimationCallback mActiveOnBackAnimationCallback;

        @Override
        public void onBackStartedCompat(@NonNull BackEvent backEvent) {
            if (mActiveOnBackAnimationCallback != null) {
                mActiveOnBackAnimationCallback.onBackCancelled();
            }
        }

        @Override
        public void onBackInvokedCompat() {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackInvoked();
            mActiveOnBackAnimationCallback = null;
        }

        @Override
        public void onBackProgressedCompat(@NonNull BackEvent backEvent) {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackProgressed(backEvent);
        }

        @Override
        public void onBackCancelledCompat() {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackCancelled();
            mActiveOnBackAnimationCallback = null;
        }
    }
}
