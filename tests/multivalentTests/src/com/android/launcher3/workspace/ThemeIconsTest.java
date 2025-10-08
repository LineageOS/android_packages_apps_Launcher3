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
package com.android.launcher3.workspace;

import static com.android.launcher3.AbstractFloatingView.TYPE_ACTION_POPUP;
import static com.android.launcher3.Utilities.findViewByPredicate;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.platform.test.rule.LimitDevicesRule;
import android.platform.test.rule.SkipOnDeviceless;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.icons.mono.ThemedIconDelegate;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.TestUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for theme icon support in Launcher
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ThemeIconsTest extends BaseLauncherActivityTest<Launcher> {

    /**
     * This test can't run in Robolectric because APP_NAME and TEST_APP_NAME can't found when
     * running the test in Robolectric.
     */
    @Rule
    public LimitDevicesRule mlimitDevicesRule = new LimitDevicesRule();

    private static final String APP_NAME = "IconThemedActivity";
    private static final String SHORTCUT_NAME = "Shortcut 1";

    public static final String TEST_APP_NAME = "LauncherTestApp";

    @Test
    @SkipOnDeviceless
    public void testIconWithoutTheme() throws Exception {
        setThemeEnabled(false);
        ModelTestExtensions.setEmptyModelLayout(targetContext());
        loadLauncherSync();

        scrollToAppIcon(APP_NAME);
        BubbleTextView btv = getLauncherActivity().getFromLauncher(
                l -> verifyIconTheme(APP_NAME, l.getAppsView(), false));
        getLauncherTestInteractions().addToWorkspace(btv);
        getLauncherActivity().executeOnLauncher(
                l -> verifyIconTheme(APP_NAME, l.getWorkspace(), false)
        );
    }

    @Test
    @SkipOnDeviceless
    public void testShortcutIconWithoutTheme() throws Exception {
        setThemeEnabled(false);
        ModelTestExtensions.setEmptyModelLayout(targetContext());
        loadLauncherSync();

        scrollToAppIcon(TEST_APP_NAME);
        BubbleTextView btv = getLauncherActivity().getFromLauncher(
                l -> findBtv(TEST_APP_NAME, l.getAppsView()));
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, btv::performLongClick);

        BubbleTextView menuItem = getLauncherActivity().getOnceNotNull(
                "Popup menu not open",
                l -> (AbstractFloatingView.getOpenView(l, TYPE_ACTION_POPUP)
                                instanceof ArrowPopup ap)
                        ? findBtv(SHORTCUT_NAME, ap) : null
        );
        getLauncherTestInteractions().addToWorkspace(menuItem);
        getLauncherActivity().executeOnLauncher(
                l -> verifyIconTheme(SHORTCUT_NAME, l.getWorkspace(), false));
    }

    @Test
    @SkipOnDeviceless
    public void testIconWithTheme() throws Exception {
        setThemeEnabled(true);
        ModelTestExtensions.setEmptyModelLayout(targetContext());
        loadLauncherSync();

        scrollToAppIcon(APP_NAME);
        BubbleTextView btv = getLauncherActivity().getFromLauncher(
                l -> verifyIconTheme(APP_NAME, l.getAppsView(), false)
        );

        getLauncherTestInteractions().addToWorkspace(btv);
        getLauncherActivity().executeOnLauncher(
                l -> verifyIconTheme(APP_NAME, l.getWorkspace(), true));
    }

    @Test
    @SkipOnDeviceless
    public void testShortcutIconWithTheme() throws Exception {
        setThemeEnabled(true);
        loadLauncherSync();

        scrollToAppIcon(TEST_APP_NAME);
        BubbleTextView btv = getLauncherActivity().getFromLauncher(
                l -> findBtv(TEST_APP_NAME, l.getAppsView()));
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, btv::performLongClick);

        BubbleTextView menuItem = getLauncherActivity().getOnceNotNull(
                "Popup menu not open",
                l -> (AbstractFloatingView.getOpenView(l, TYPE_ACTION_POPUP)
                                instanceof ArrowPopup ap)
                        ? findBtv(SHORTCUT_NAME, ap) : null
        );
        getLauncherTestInteractions().addToWorkspace(menuItem);
        getLauncherActivity().executeOnLauncher(
                l -> verifyIconTheme(SHORTCUT_NAME, l.getWorkspace(), true));
    }

    private BubbleTextView findBtv(String title, ViewGroup parent) {
        // Wait for Launcher model to be completed
        try {
            Executors.MODEL_EXECUTOR.submit(() -> {
            }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return findViewByPredicate(parent, v ->
                v instanceof BubbleTextView btv
                        && btv.getContentDescription() != null
                        && title.equals(btv.getContentDescription().toString()));
    }

    private BubbleTextView verifyIconTheme(String title, ViewGroup parent, boolean isThemed) {
        BubbleTextView icon = findBtv(title, parent);
        assertNotNull(icon.getIcon());
        assertEquals(isThemed, icon.getIcon().getDelegate() instanceof ThemedIconDelegate);
        return icon;
    }

    private void setThemeEnabled(boolean isEnabled) {
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(targetContext().getPackageName() + ".grid_control")
                .appendPath("set_icon_themed")
                .build();
        ContentValues values = new ContentValues();
        values.put("boolean_value", isEnabled);

        int result = LauncherComponentProvider.get(targetContext()).getGridCustomizationsProxy()
                .update(uri, values, null, null);
        assertTrue(result > 0);
    }

    private void scrollToAppIcon(String appName) {
        getLauncherTestInteractions().scrollToAllAppIcon(
                info -> appName.equals(info.title.toString()));
    }
}
