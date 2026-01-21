package com.android.launcher3.popup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.largefolder.LargeFolderIcon;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.R;

import java.util.ArrayList;

/**
 * Created by ch.hu
 * Date: 6/30/25 11:35
 * Description:
 */
public class DismissFolderShortcut extends SystemShortcut<Launcher> {

    private View mView;

    private FolderInfo folderInfo;

    private final int mDismissDuration;

    public DismissFolderShortcut(Launcher target, ItemInfo itemInfo, View originalView) {
        super(R.drawable.dismiss_folder,
                R.string.folder_dismiss, target, itemInfo,
                originalView);
        this.mView = originalView;
        this.folderInfo = (FolderInfo) itemInfo;
        this.mDismissDuration = originalView.getContext().getResources().getInteger(
                R.integer.config_folder_dismiss_duration);
    }

    @Override
    public void onClick(View v) {
        AbstractFloatingView.closeAllOpenViews(this.mTarget);
        this.mView.postDelayed(
                () -> dismissFolder(mView, folderInfo, Launcher.getLauncher(v.getContext())), 300L);
    }

    public void dismissFolder(View folderView, FolderInfo folderInfo, Launcher launcher) {
        Workspace<?> workspace = launcher.getWorkspace();
        ModelWriter modelWriter = launcher.getModelWriter();

        int screenId = folderInfo.screenId;
        int[] folderViewPos = new int[2];
        CellLayout cellLayout = workspace.getScreenWithId(screenId);

        // Hotseat 情况
        if (folderInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            screenId = workspace.getScreenIdForPageIndex(workspace.getCurrentPage());
            cellLayout = workspace.getScreenWithId(screenId);

            folderView.getLocationOnScreen(folderViewPos);
            Rect padding = launcher.getDeviceProfile().getHotseatIconPadding(launcher);
            folderViewPos[0] += padding.left;
            folderViewPos[1] += padding.top;
        } else {
            // 普通桌面文件夹
            cellLayout.markCellsAsUnoccupiedForView(folderView);
            if (folderView instanceof FolderIcon || folderView instanceof LargeFolderIcon) {
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderView.getLayoutParams();
                folderViewPos[0] = lp.x;
                folderViewPos[1] = lp.y;
            }
        }

        // 移除文件夹
        workspace.removeWorkspaceItem(folderView);
        modelWriter.deleteItemFromDatabase(folderInfo,
                "dismiss folder, itemType:" + folderInfo.itemType);

        // 取出文件夹内容
        ArrayList<ItemInfo> contents = new ArrayList<>(folderInfo.getContents());
        folderInfo.getContents().clear();

        int[] emptyCell = new int[2];
        // 最多添加5个新屏
        int maxNewScreens = 5;
        int newScreensAdded = 0;

        while (!contents.isEmpty()) {
            ItemInfo item = contents.get(0);

            // 先尝试找空位
            if (!cellLayout.findCellForSpan(emptyCell, 1, 1)) {
                // 当前屏满，切换或新增屏
                boolean placed = false;

                while (!placed) {
                    if (workspace.mWorkspaceScreens.get(screenId + 1) != null) {
                        screenId++;
                        cellLayout = workspace.mWorkspaceScreens.get(screenId);
                        workspace.setCurrentPage(workspace.getPageIndexForScreenId(screenId));
                    } else if (newScreensAdded < maxNewScreens) {
                        int newScreenId = workspace.getNewScreenId();
                        cellLayout = workspace.insertNewWorkspaceScreen(newScreenId,
                                workspace.getChildCount());
                        screenId = newScreenId;
                        workspace.setCurrentPage(workspace.getPageIndexForScreenId(newScreenId));
                        newScreensAdded++;
                    } else {
                        Log.w("Launcher", "Not enough space to place all items from folder.");
                        Toast.makeText(launcher, R.string.dismiss_folder_error,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 新屏幕/下一屏再找一次空位
                    if (cellLayout.findCellForSpan(emptyCell, 1, 1)) {
                        placed = true;
                    }
                }
            }

            // 找到空位后才移除 item（避免丢失）
            contents.remove(0);

            // 标记格子占用
            cellLayout.markCell(emptyCell[0], emptyCell[1], true);

            // 数据库位置更新
            modelWriter.addOrMoveItemInDatabase(item,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId,
                    emptyCell[0], emptyCell[1]);

            // 创建图标
            View itemView = launcher.getItemInflater().inflateItem(item);
            workspace.addInScreen(itemView, item);
            cellLayout.getShortcutsAndWidgets().setupLp(itemView);

            // 更新高清图标
            if (itemView instanceof BubbleTextView &&
                    item.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                    item.itemType != LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR) {
                ((BubbleTextView) itemView).updateHighRes();
            }

            // 动画效果
            CellLayoutLayoutParams itemLp = (CellLayoutLayoutParams) itemView.getLayoutParams();
            int deltaX = folderViewPos[0] - itemLp.x;
            int deltaY = folderViewPos[1] - itemLp.y;

            animateIconViewIn(itemView, deltaX, deltaY);
        }

        workspace.requestLayout();
    }

    private void animateIconViewIn(View view, int deltaX, int deltaY) {
        // 初始位置和透明度
        view.setTranslationX(deltaX);
        view.setTranslationY(deltaY);
        view.setAlpha(0f);

        // 位移动画
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0);
        moveX.setInterpolator(new DecelerateInterpolator());
        moveY.setInterpolator(new DecelerateInterpolator());

        // 透明度动画
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 1f);
        fadeIn.setInterpolator(new DecelerateInterpolator());

        // 同时播放
        AnimatorSet set = new AnimatorSet();
        set.setDuration(mDismissDuration);
        set.playTogether(moveX, moveY, fadeIn);
        set.start();
    }
}