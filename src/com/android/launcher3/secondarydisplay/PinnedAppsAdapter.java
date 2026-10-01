/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Process;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AppInfoComparator;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter to manage pinned apps and show then in a grid.
 */
public class PinnedAppsAdapter extends BaseAdapter implements OnSharedPreferenceChangeListener {

    private static final String PINNED_APPS_KEY = "pinned_apps";
    private static final String PINNED_APPS_ORDER_KEY = "pinned_apps_order";
    private static final String PINNED_APPS_ORDER_SEPARATOR = "\n";
    private static final String PINNED_APPS_CELL_SEPARATOR = ";";

    private final SecondaryDisplayLauncher mLauncher;
    private final OnClickListener mOnClickListener;
    private final OnLongClickListener mOnLongClickListener;
    private final SharedPreferences mPrefs;
    private final AllAppsStore mAllAppsList;
    private final AppInfoComparator mAppNameComparator;
    private final int mNumColumns;
    private final int mMaxNumRows;
    private int mNumRows;

    // Pinned apps and the grid cell they are placed in
    private final LinkedHashMap<ComponentKey, Integer> mPinnedApps = new LinkedHashMap<>();
    private AppInfo[] mCells;
    // Apps pinned before they could be placed are sorted by name until they are moved
    private boolean mSortByName;

    // App being dragged over the grid, shown in mDragCell until the drag ends
    private ComponentKey mDragKey;
    private AppInfo mDragApp;
    private int mDragCell;

    public PinnedAppsAdapter(
            SecondaryDisplayLauncher launcher,
            AllAppsStore allAppsStore,
            OnLongClickListener onLongClickListener,
            int numColumns,
            int numRows) {
        mLauncher = launcher;
        mOnClickListener = launcher.getItemOnClickListener();
        mOnLongClickListener = onLongClickListener;
        mAllAppsList = allAppsStore;
        mPrefs = launcher.getSharedPreferences(PINNED_APPS_KEY, MODE_PRIVATE);
        mAppNameComparator = new AppInfoComparator(launcher);
        mNumColumns = numColumns;
        mMaxNumRows = numRows;
        mNumRows = numRows;
        mCells = new AppInfo[numColumns * numRows];

        mAllAppsList.addUpdateListener(this::createFilteredAppsList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PINNED_APPS_KEY.equals(key) || PINNED_APPS_ORDER_KEY.equals(key)) {
            Executors.MODEL_EXECUTOR.submit(() -> {
                String order = prefs.getString(PINNED_APPS_ORDER_KEY, null);
                boolean sortByName = order == null;
                List<String> entries = sortByName
                        ? new ArrayList<>(prefs.getStringSet(PINNED_APPS_KEY,
                                Collections.emptySet()))
                        : Arrays.asList(order.split(PINNED_APPS_ORDER_SEPARATOR));
                LinkedHashMap<ComponentKey, Integer> apps = new LinkedHashMap<>();
                for (String entry : entries) {
                    String[] parts = entry.split(PINNED_APPS_CELL_SEPARATOR);
                    ComponentKey app = parseComponentKey(parts[0]);
                    if (app != null) {
                        apps.putIfAbsent(app, parts.length > 1 ? parseCell(parts[1]) : -1);
                    }
                }
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mPinnedApps.clear();
                    mPinnedApps.putAll(apps);
                    mSortByName = sortByName;
                    createFilteredAppsList();
                });
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mCells.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppInfo getItem(int position) {
        return mCells[position];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        BubbleTextView icon;
        if (view instanceof BubbleTextView) {
            icon = (BubbleTextView) view;
        } else {
            icon = (BubbleTextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_icon, parent, false);
            icon.setOnClickListener(mOnClickListener);
            icon.setOnLongClickListener(mOnLongClickListener);
            icon.setLongPressTimeoutFactor(1f);
            int padding = mLauncher.getDeviceProfile().getWorkspaceProfile().getEdgeMarginPx();
            icon.setPadding(padding, padding, padding, padding);
        }
        // Spread the rows over the height of the grid
        int rowHeight = parent.getHeight() / mNumRows;
        if (rowHeight > 0) {
            icon.getLayoutParams().height = rowHeight;
        }

        AppInfo item = mCells[position];
        if (item != null) {
            icon.applyFromApplicationInfo(item);
        }
        // Keep empty cells in the layout so that apps can be placed in them
        icon.setVisibility(item == null || item == mDragApp ? View.INVISIBLE : View.VISIBLE);
        return icon;
    }

    private void createFilteredAppsList() {
        Arrays.fill(mCells, null);
        List<AppInfo> unplaced = new ArrayList<>();
        mPinnedApps.forEach((key, cell) -> {
            AppInfo app = mAllAppsList.getApp(key);
            if (app == null || key.equals(mDragKey)) {
                return;
            }
            if (mSortByName || cell < 0 || cell >= mCells.length || mCells[cell] != null) {
                unplaced.add(app);
            } else {
                mCells[cell] = app;
            }
        });
        if (mSortByName) {
            unplaced.sort(mAppNameComparator);
        }
        if (mDragApp != null) {
            AppInfo displaced = mCells[mDragCell];
            mCells[mDragCell] = mDragApp;
            if (displaced != null) {
                // Swap with the dragged app if it was pinned already
                Integer dragOrigin = mPinnedApps.get(mDragKey);
                if (dragOrigin != null && dragOrigin >= 0 && dragOrigin < mCells.length
                        && mCells[dragOrigin] == null) {
                    mCells[dragOrigin] = displaced;
                } else {
                    unplaced.add(0, displaced);
                }
            }
        }
        for (AppInfo app : unplaced) {
            int cell = getFirstEmptyCell();
            if (cell < 0) {
                break;
            }
            mCells[cell] = app;
        }
        notifyDataSetChanged();
    }

    /**
     * Fits as many rows into the grid as possible without cutting off app labels
     */
    public void updateNumRows(GridView grid) {
        AppInfo[] apps = mAllAppsList.getApps();
        if (grid.getHeight() == 0 || apps.length == 0) {
            return;
        }
        BubbleTextView icon = (BubbleTextView) getView(0, null, grid);
        icon.applyFromApplicationInfo(apps[0]);
        icon.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        icon.measure(View.MeasureSpec.makeMeasureSpec(
                        grid.getWidth() / mNumColumns, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int numRows = Math.max(1, Math.min(mMaxNumRows,
                grid.getHeight() / Math.max(1, icon.getMeasuredHeight())));
        if (numRows != mNumRows) {
            mNumRows = numRows;
            mCells = new AppInfo[mNumColumns * numRows];
            createFilteredAppsList();
        } else {
            notifyDataSetChanged();
        }
    }

    private int getFirstEmptyCell() {
        for (int i = 0; i < mCells.length; i++) {
            if (mCells[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Initialized the pinned apps list and starts listening for changes
     */
    public void init() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mPrefs, PINNED_APPS_KEY);
    }

    /**
     * Stops listening for any pinned apps changes
     */
    public void destroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Pins the app in the given cell of the grid, or moves it there if it is already pinned. A
     * negative cell places it in the first empty cell.
     */
    public void pinApp(ItemInfo info, int cell) {
        ComponentKey key = getComponentKey(info);
        AppInfo app = mAllAppsList.getApp(key);
        if (app == null) {
            return;
        }
        clearDragPreview();
        if (cell < 0 || cell >= mCells.length) {
            Integer currentCell = mPinnedApps.get(key);
            cell = currentCell != null && currentCell >= 0 && currentCell < mCells.length
                    && mCells[currentCell] == app ? currentCell : getFirstEmptyCell();
            if (cell < 0) {
                // The grid is full
                return;
            }
        }
        setDragPreview(info, cell);

        LinkedHashMap<ComponentKey, Integer> apps = new LinkedHashMap<>();
        for (int i = 0; i < mCells.length; i++) {
            if (mCells[i] != null) {
                apps.put(getComponentKey(mCells[i]), i);
            }
        }
        // Keep pinned apps that aren't currently available, e.g. on a paused work profile
        mPinnedApps.forEach(apps::putIfAbsent);
        mDragKey = null;
        mDragApp = null;
        setPinnedApps(apps);
    }

    /**
     * Unpins the app from the grid
     */
    public void unpinApp(ItemInfo info) {
        // Keep the other apps in the cells they were in before the drag
        clearDragPreview();
        LinkedHashMap<ComponentKey, Integer> apps = new LinkedHashMap<>();
        for (int i = 0; i < mCells.length; i++) {
            if (mCells[i] != null) {
                apps.put(getComponentKey(mCells[i]), i);
            }
        }
        mPinnedApps.forEach(apps::putIfAbsent);
        if (apps.remove(getComponentKey(info)) != null) {
            setPinnedApps(apps);
        }
    }

    /**
     * Shows the given app in the given cell of the grid while it is being dragged
     */
    public void setDragPreview(ItemInfo info, int cell) {
        ComponentKey key = getComponentKey(info);
        AppInfo app = mAllAppsList.getApp(key);
        if (app == null || cell < 0 || cell >= mCells.length
                || (key.equals(mDragKey) && cell == mDragCell)) {
            return;
        }
        mDragKey = key;
        mDragApp = app;
        mDragCell = cell;
        createFilteredAppsList();
    }

    /**
     * Returns the cell of the grid the dragged app is shown in, or -1 if there is none
     */
    public int getDragPreviewCell() {
        return mDragApp == null ? -1 : mDragCell;
    }

    /**
     * Stops showing the dragged app in the grid
     */
    public void clearDragPreview() {
        if (mDragKey != null) {
            mDragKey = null;
            mDragApp = null;
            createFilteredAppsList();
        }
    }

    private void setPinnedApps(Map<ComponentKey, Integer> apps) {
        mPinnedApps.clear();
        mPinnedApps.putAll(apps);
        mSortByName = false;
        createFilteredAppsList();
        String order = apps.entrySet().stream()
                .map(e -> encode(e.getKey()) + PINNED_APPS_CELL_SEPARATOR + e.getValue())
                .collect(Collectors.joining(PINNED_APPS_ORDER_SEPARATOR));
        Executors.MODEL_EXECUTOR.submit(() -> mPrefs.edit()
                .putString(PINNED_APPS_ORDER_KEY, order)
                .remove(PINNED_APPS_KEY)
                .apply());
    }

    private ComponentKey getComponentKey(ItemInfo info) {
        return new ComponentKey(info.getTargetComponent(), info.user);
    }

    private int parseCell(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ComponentKey parseComponentKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user;
            if (parts.length > 2) {
                user = UserCache.INSTANCE.get(mLauncher)
                        .getUserForSerialNumber(Long.parseLong(parts[2]));
            } else {
                user = Process.myUserHandle();
            }
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            return new ComponentKey(cn, user);
        } catch (Exception e) {
            return null;
        }
    }

    private String encode(ComponentKey key) {
        return key.componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(mLauncher).getSerialNumberForUser(key.user);
    }

    /**
     * Returns a system shortcut to pin/unpin a shortcut
     */
    public SystemShortcut getSystemShortcut(ItemInfo info, View originalView) {
        return new PinUnPinShortcut(mLauncher, info, originalView,
                mPinnedApps.containsKey(new ComponentKey(info.getTargetComponent(), info.user)));
    }

    private class PinUnPinShortcut extends SystemShortcut<SecondaryDisplayLauncher> {

        private final boolean mIsPinned;

        PinUnPinShortcut(SecondaryDisplayLauncher target, ItemInfo info, View originalView,
                boolean isPinned) {
            super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                    isPinned ? R.string.remove_drop_target_label : R.string.action_add_to_workspace,
                    target, info, originalView);
            mIsPinned = isPinned;
        }

        @Override
        public void onClick(View view) {
            if (mIsPinned) {
                unpinApp(mItemInfo);
            } else {
                pinApp(mItemInfo, -1);
            }
            AbstractFloatingView.closeAllOpenViews(mLauncher);
        }
    }
}
