/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.DEEP_SHORTCUTS;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;

import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LogUtils;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.logging.EntryPoint;

import java.util.List;

/**
 * Accessibility delegate for the Taskbar. This provides an accessible interface for taskbar
 * features.
 */
public class TaskbarShortcutMenuAccessibilityDelegate
        extends BaseAccessibilityDelegate<TaskbarActivityContext> {

    public static final int MOVE_TO_TOP_OR_LEFT = R.id.action_move_to_top_or_left;
    public static final int MOVE_TO_BOTTOM_OR_RIGHT = R.id.action_move_to_bottom_or_right;
    public static final int CREATE_APPLICATION_BUBBLE = R.id.action_create_application_bubble;

    private final LauncherApps mLauncherApps;
    private final StatsLogManager mStatsLogManager;

    public TaskbarShortcutMenuAccessibilityDelegate(TaskbarActivityContext context) {
        super(context);
        mLauncherApps = context.getSystemService(LauncherApps.class);
        mStatsLogManager = context.getStatsLogManager();

        mActions.put(DEEP_SHORTCUTS, new LauncherAction(DEEP_SHORTCUTS,
                R.string.action_deep_shortcut, KeyEvent.KEYCODE_S));
        mActions.put(MOVE_TO_TOP_OR_LEFT, new LauncherAction(
                MOVE_TO_TOP_OR_LEFT, R.string.move_drop_target_top_or_left, KeyEvent.KEYCODE_L));
        mActions.put(MOVE_TO_BOTTOM_OR_RIGHT, new LauncherAction(
                MOVE_TO_BOTTOM_OR_RIGHT,
                R.string.move_drop_target_bottom_or_right,
                KeyEvent.KEYCODE_R));
        mActions.put(CREATE_APPLICATION_BUBBLE, new LauncherAction(
                CREATE_APPLICATION_BUBBLE, R.string.open_app_as_a_bubble,
                KeyEvent.KEYCODE_L));
    }

    @Override
    protected void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out) {
        if (ShortcutUtil.supportsShortcuts(item)) {
            out.add(mActions.get(DEEP_SHORTCUTS));
        }
        out.add(mActions.get(MOVE_TO_TOP_OR_LEFT));
        out.add(mActions.get(MOVE_TO_BOTTOM_OR_RIGHT));
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            out.add(mActions.get(CREATE_APPLICATION_BUBBLE));
        }
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if (host.getTag() instanceof GroupTask && action == ACTION_LONG_CLICK) {
            return performLongClick(host);
        }
        return super.performAccessibilityAction(host, action, args);
    }

    @Override
    protected boolean performAction(View host, ItemInfo item, int action, boolean fromKeyboard) {
        if (action == ACTION_LONG_CLICK) {
            return performLongClick(host);
        } else if (action == DEEP_SHORTCUTS) {
            mContext.showPopupMenuForIcon((BubbleTextView) host);
            return true;
        } else if (action == CREATE_APPLICATION_BUBBLE) {
            EntryPoint entryPoint = EntryPoint.TASKBAR_ICON_MENU;
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                    && item instanceof WorkspaceItemInfo) {
                ShortcutInfo shortcutInfo = ((WorkspaceItemInfo) item).getDeepShortcutInfo();
                BubbleActivityStarter.INSTANCE.get(mContext)
                        .showShortcutBubble(shortcutInfo, entryPoint);
                return true;
            } else if (item.getIntent() != null && item.getIntent().getPackage() != null) {
                BubbleActivityStarter.INSTANCE.get(mContext).showAppBubble(item.getIntent(),
                        item.user, entryPoint);
                return true;
            }
        } else if (item instanceof ItemInfoWithIcon
                && (action == MOVE_TO_TOP_OR_LEFT || action == MOVE_TO_BOTTOM_OR_RIGHT)) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) item;
            int side = action == MOVE_TO_TOP_OR_LEFT
                    ? STAGE_POSITION_TOP_OR_LEFT : STAGE_POSITION_BOTTOM_OR_RIGHT;

            Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                    LogUtils.getShellShareableInstanceId();
            mStatsLogManager.logger()
                    .withItemInfo(item)
                    .withInstanceId(instanceIds.second)
                    .log(getLogEventForPosition(side));

            if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                    && item instanceof WorkspaceItemInfo) {
                SystemUiProxy.INSTANCE.get(mContext).startShortcut(
                        info.getIntent().getPackage(),
                        ((WorkspaceItemInfo) info).getDeepShortcutId(),
                        side,
                        /* bundleOpts= */ null,
                        info.user,
                        instanceIds.first);
            } else {
                SystemUiProxy.INSTANCE.get(mContext).startIntent(
                        mLauncherApps.getMainActivityLaunchIntent(
                                item.getIntent().getComponent(),
                                /* startActivityOptions= */null,
                                item.user),
                        item.user.getIdentifier(), new Intent(), side, null,
                        instanceIds.first);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean beginAccessibleDrag(View item, ItemInfo info, boolean fromKeyboard) {
        return false;
    }

    private boolean performLongClick(View host) {
        // Long press should be consumed for workspace items, and it should invoke the
        // Shortcuts / Notifications / Actions pop-up menu, and not start a drag as the
        // standard long press path does.
        if (host instanceof BubbleTextView bubbleTextView) {
            mContext.showPopupMenuForIcon(bubbleTextView);
            return true;
        }

        if (host instanceof BubbleTextHolder holder) {
            if (holder.getBubbleText() != null) {
                mContext.showPopupMenuForIcon(holder.getBubbleText());
                return true;
            }
        }
        return false;
    }
}
