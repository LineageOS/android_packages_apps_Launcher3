package com.android.launcher3.popup;

import android.view.View;

import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.dragndrop.DragOptions;

/**
 * Created by ch.hu
 * Date: 6/30/25 14:04
 * Description:
 */
public class ShortcutsProxy {

    public static DragOptions.PreDragCondition startLongPressActionFolder(View view) {
        Popup popup = PopupContainerWithArrow.showForFolder(view);
        return popup != null ? popup.createPreDragCondition() : null;
    }

    public static DragOptions.PreDragCondition startLongPressActionAppPairIcon(AppPairIcon view) {
        Popup popup = PopupContainerWithArrow.showForAppPairIcon(view);
        return popup != null ? popup.createPreDragCondition() : null;
    }
}