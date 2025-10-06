/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.tasks.AddWorkspaceItemsTask;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.PersistedItemArray;
import com.android.launcher3.util.Preconditions;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Class to maintain a queue of pending items to be added to the workspace.
 */
@LauncherAppSingleton
public class ItemInstallQueue {

    private static final String LOG = "ItemInstallQueue";

    public static final int FLAG_ACTIVITY_PAUSED = 1;
    public static final int FLAG_LOADER_RUNNING = 2;
    public static final int FLAG_DRAG_AND_DROP = 4;

    // The set of shortcuts that are pending install
    private static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 85;

    public static DaggerSingletonObject<ItemInstallQueue> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getItemInstallQueue);
    private final PersistedItemArray<SerializedItemItem> mStorage =
            new PersistedItemArray<>(APPS_PENDING_INSTALL);

    private final Context mContext;
    private final WorkspaceItemSerializer mPendingItemParser;
    private final Provider<WorkspaceItemSpaceFinder> mSpaceFinderProvider;

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private int mInstallQueueDisabledFlags = 0;

    // Only accessed on worker thread
    private List<SerializedItemItem> mItems;

    @Inject
    public ItemInstallQueue(@ApplicationContext Context context,
            Provider<WorkspaceItemSpaceFinder> spaceFinderProvider,
            WorkspaceItemSerializer pendingItemParser) {
        mContext = context;
        mSpaceFinderProvider = spaceFinderProvider;
        mPendingItemParser = pendingItemParser;
    }

    @WorkerThread
    private void ensureQueueLoaded() {
        Preconditions.assertWorkerThread();
        if (mItems == null) {
            mItems = mStorage.read(mContext, SerializedItemItem::new);
        }
    }

    @WorkerThread
    private void addToQueue(SerializedItemItem info) {
        ensureQueueLoaded();
        if (!mItems.contains(info)) {
            mItems.add(info);
            mStorage.write(mContext, mItems);
        }
    }

    @WorkerThread
    private void flushQueueInBackground() {
        Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext();
        if (launcher == null) {
            // Launcher not loaded
            return;
        }
        ensureQueueLoaded();
        if (mItems.isEmpty()) {
            return;
        }

        List<ItemInfo> installQueue = mItems.stream()
                .map(mPendingItemParser::decode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // Add the items and clear queue
        if (!installQueue.isEmpty()) {
            MAIN_EXECUTOR.execute(() -> commitInstallQueue(launcher, installQueue));
        }
        mItems.clear();
        mStorage.getFile(mContext).delete();
    }

    @UiThread
    private void commitInstallQueue(Launcher launcher, List<ItemInfo> itemList) {
        // If there's an undo snackbar, force it to complete to ensure empty screens are
        // removed before trying to add new items.
        launcher.getModelWriter().commitDelete();
        AbstractFloatingView snackbar = AbstractFloatingView.getOpenView(
                launcher,
                AbstractFloatingView.TYPE_SNACKBAR
        );
        if (snackbar != null) {
            snackbar.close(true);
        }
        launcher.getModel().enqueueModelUpdateTask(
                new AddWorkspaceItemsTask(itemList, mSpaceFinderProvider.get()));
    }

    /**
     * Removes previously added items from the queue.
     */
    @WorkerThread
    public void removeFromInstallQueue(Set<String> packageNames, UserHandle user) {
        if (packageNames.isEmpty()) {
            return;
        }
        ensureQueueLoaded();
        if (mItems.removeIf(item ->
                item.user.equals(user) && packageNames.contains(item.getTargetPackage()))) {
            mStorage.write(mContext, mItems);
        }
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(ShortcutInfo info) {
        queuePendingShortcutInfo(new SerializedItemItem(info));
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(AppWidgetProviderInfo info, int widgetId) {
        queuePendingShortcutInfo(new SerializedItemItem(info, widgetId));
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(String packageName, UserHandle userHandle) {
        queuePendingShortcutInfo(new SerializedItemItem(packageName, userHandle));
    }

    /**
     * Returns a stream of all pending shortcuts in the queue
     */
    @WorkerThread
    public Stream<ShortcutKey> getPendingShortcuts(UserHandle user) {
        ensureQueueLoaded();
        return mItems.stream()
                .filter(item -> item.itemType == ITEM_TYPE_DEEP_SHORTCUT && user.equals(item.user))
                .map(item -> ShortcutKey.fromIntent(item.getIntent(), user));
    }

    private void queuePendingShortcutInfo(SerializedItemItem info) {
        // Queue the item up for adding if launcher has not loaded properly yet
        MODEL_EXECUTOR.post(() -> {
            ItemInfo itemInfo = mPendingItemParser.decode(info);
            if (itemInfo == null) {
                FileLog.d(LOG,
                        "Adding PendingInstallShortcutInfo with no attached info to queue.");
            } else {
                FileLog.d(LOG,
                        "Adding PendingInstallShortcutInfo to queue."
                                + " Attached info: " + itemInfo);
            }
            addToQueue(info);
        });
        flushInstallQueue();
    }

    /**
     * Pauses the push-to-model flow until unpaused. All items are held in the queue and
     * not added to the model.
     */
    public void pauseModelPush(int flag) {
        mInstallQueueDisabledFlags |= flag;
    }

    /**
     * Adds all the queue items to the model if the use is completely resumed.
     */
    public void resumeModelPush(int flag) {
        mInstallQueueDisabledFlags &= ~flag;
        flushInstallQueue();
    }

    private void flushInstallQueue() {
        if (mInstallQueueDisabledFlags != 0) {
            return;
        }
        MODEL_EXECUTOR.post(this::flushQueueInBackground);
    }
}
