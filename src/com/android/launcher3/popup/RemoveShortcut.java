package com.android.launcher3.popup;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.Launcher;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * Created by ch.hu
 * Date: 6/30/25 12:12
 * Description:
 */
public class RemoveShortcut extends SystemShortcut<Launcher> {

    public static final SystemShortcut.Factory<Launcher> REMOVE =
            new SystemShortcut.Factory() {
                @Override
                public final SystemShortcut getShortcut(ActivityContext activityContext,
                        ItemInfo itemInfo, View view) {
                    return new RemoveShortcut((Launcher) activityContext, itemInfo, view);
                }
            };
    private static final String TAG = "RemoveShortcut";
    private Launcher mLauncher;
    private View mView;

    public RemoveShortcut(Launcher target, ItemInfo itemInfo, View originalView) {
        super(R.drawable.ic_remove_no_shadow_newer, R.string.remove_drop_target_label, target,
                itemInfo, originalView);
        this.mLauncher = target;
        this.mView = originalView;
    }

    @Override
    public void onClick(View v) {
        dismissTaskMenuView();
        try {
            removeShortcut(this.mView, this.mLauncher, (ItemInfo) this.mView.getTag());
            final DragLayer p = this.mLauncher.getDragLayer();
            p.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            mLauncher.runOnUiThread(
                                    () -> {
                                        for (int i = 0; i < p.getChildCount(); i++) {
                                            View child = p.getChildAt(i);
                                            if (child instanceof AppWidgetResizeFrame) {
                                                AppWidgetResizeFrame wid =
                                                        (AppWidgetResizeFrame) child;
                                                p.removeView(wid);
                                            }
                                        }
                                    });
                        }
                    }, 500L);
        } catch (Exception e) {
        }
    }

    public static void removeShortcut(View v, Launcher launcher, ItemInfo item) {
        if (canRemove(item)) {
            long start = System.currentTimeMillis();
            launcher.removeItem(v, item, true);
            long end = System.currentTimeMillis();
            Log.i(TAG, "removeShortcut:  time " + (end - start));
            launcher.getWorkspace().stripEmptyScreens();
            launcher.getDragLayer().announceForAccessibility(
                    v.getContext().getString(R.string.item_removed));
        }
    }

    private static boolean canRemove(ItemInfo item) {
        return item.id != -1;
    }

    public static void addShortcut(Launcher launcher, List<SystemShortcut> systemShortcuts,
            BubbleTextView originalIcon, ItemInfo info) {
        ViewParent parent = originalIcon.getParent();
        if (parent instanceof PredictionRowView<?>) {
            return;
        }
        Log.d(TAG, "isRemoveShortcut: container=" + info.container + ", itemType=" + info.itemType);
        if ((info.itemType == ITEM_TYPE_DEEP_SHORTCUT || info.itemType == ITEM_TYPE_SHORTCUT
                || info.getTargetPackage().equals(
                BuildConfig.APPLICATION_ID) || (info.container != CONTAINER_ALL_APPS
                && info.getTargetPackage() != null)) && !info.isPredictedItem()) {
            systemShortcuts.add(REMOVE.getShortcut(launcher, info, originalIcon));
        }
    }

    public static boolean isRemoveShortcut(BubbleTextView view, ItemInfo info) {
        ViewParent parent = view.getParent();
        if (parent instanceof PredictionRowView) {
            return false;
        }
        return info.itemType == ITEM_TYPE_DEEP_SHORTCUT || info.itemType == ITEM_TYPE_SHORTCUT;
    }
}