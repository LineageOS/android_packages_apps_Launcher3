package com.android.launcher3.popup;

import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.largefolder.LargeFolderIcon;
import com.android.launcher3.folder.largefolder.LargeFolderProxy;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;

/**
 * Created by ch.hu
 * Date: 6/30/25 11:35
 * Description:
 */
public class SwitchFolderShortcut extends SystemShortcut<Launcher> {
    private View mView;

    public SwitchFolderShortcut(Launcher target, ItemInfo itemInfo, View originalView) {
        super(LargeFolderProxy.getInstance().getSwitchIconResId(itemInfo),
                LargeFolderProxy.getInstance().getSwitchLabelResId(itemInfo), target, itemInfo,
                originalView);
        this.mView = originalView;
    }

    @Override
    public void onClick(View v) {
        AbstractFloatingView.closeAllOpenViews(this.mTarget);
        this.mView.postDelayed(
                () -> {
                    if (mView instanceof FolderIcon folderIcon) {
                        LargeFolderProxy.getInstance().convertToLargeFolder(
                                Launcher.getLauncher(mView.getContext()), folderIcon);
                    }
                    if (mView instanceof LargeFolderIcon largeFolderIcon) {
                        LargeFolderProxy.getInstance().convertToFolder(
                                Launcher.getLauncher(mView.getContext()), largeFolderIcon);
                    }
                }, 300L);
    }
}