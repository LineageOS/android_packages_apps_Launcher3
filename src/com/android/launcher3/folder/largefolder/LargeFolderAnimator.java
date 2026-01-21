package com.android.launcher3.folder.largefolder;

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;

import static com.android.launcher3.folder.largefolder.LargeFolderProxy.EXPAND_LB;
import static com.android.launcher3.folder.largefolder.LargeFolderProxy.EXPAND_LT;
import static com.android.launcher3.folder.largefolder.LargeFolderProxy.EXPAND_RB;
import static com.android.launcher3.folder.largefolder.LargeFolderProxy.EXPAND_RT;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Property;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.ClippedFolderIconLayoutRule;
import com.android.launcher3.folder.PreviewItemDrawingParams;
import com.android.launcher3.util.RectProperty;
import com.android.quickstep.util.AnimUtils;

import java.util.List;

/**
 * Created by ch.hu
 * Date: 7/3/25 12:22
 * Description:
 */

public class LargeFolderAnimator {

    private final LargeFolderIcon mLargeFolderIcon;
    private final int mContentHeight;
    private final int mContentWidth;
    private final ClippedFolderIconLayoutRule mFolderIconLayoutRule;
    private final int mIconSize;
    private final PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0f, 0f, 0f);

    public LargeFolderAnimator(Launcher launcher, LargeFolderIcon largeFolderIcon) {
        mLargeFolderIcon = largeFolderIcon;
        DeviceProfile dp = launcher.getDeviceProfile();
        mFolderIconLayoutRule = new ClippedFolderIconLayoutRule();
        mFolderIconLayoutRule.init(dp.getWorkspaceIconProfile().getIconSizePx(), dp.getFolderProfile().getChildIconSizePx(),
                Utilities.isRtl(launcher.getResources()), 3);
        mIconSize = dp.getWorkspaceIconProfile().getIconSizePx();
        Rect contentBounds = largeFolderIcon.getContentBounds();
        mContentWidth = contentBounds.width();
        mContentHeight = contentBounds.height();
    }

    public AnimatorSet getAnimator(int expandType, boolean expand) {
        AnimatorSet animatorSet = new AnimatorSet();

        float itemIconSize0 = getItemIconSize(LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
        float itemIconSize1 = getItemIconSize(LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER);
        float scale = itemIconSize0 / itemIconSize1;

        Point pivot = getContentPivotPoint(expandType);
        mLargeFolderIcon.mItemContent.setPivotX(pivot.x);
        mLargeFolderIcon.mItemContent.setPivotY(pivot.y);

        animatorSet.play(getAnimator(
                mLargeFolderIcon.mItemContent,
                LauncherAnimUtils.SCALE_PROPERTY,
                scale, 1f, expand));

        Object contentBg = mLargeFolderIcon.getContentBackground();
        if (contentBg instanceof RectProperty rectProp) {
            Rect startRect = getContentClipRect(expandType, scale);
            Rect endRect = new Rect(0, 0, mContentWidth, mContentHeight);
            animatorSet.play(getContentClipAnimator(rectProp, startRect, endRect, expand));
        }

        Point nameTranslation = getFolderNameTranslations(expandType);
        animatorSet.play(
                getAnimator(mLargeFolderIcon.mFolderName, View.TRANSLATION_X, nameTranslation.x, 0f,
                        expand));
        animatorSet.play(
                getAnimator(mLargeFolderIcon.mFolderName, View.TRANSLATION_Y, nameTranslation.y, 0f,
                        expand));

        addItemAnimators(animatorSet, scale, expandType, expand);

        return animatorSet;
    }

    private void addItemAnimators(AnimatorSet animatorSet, float scale, int expandType,
            boolean expand) {
        List<View> items = mLargeFolderIcon.getItemViews();
        ShortcutAndWidgetContainer cwc = mLargeFolderIcon.mItemContent.getShortcutsAndWidgets();

        for (int i = 0; i < items.size(); i++) {
            View child = items.get(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.isLockedToGrid = true;
            cwc.setupLp(child);

            int previewIndex = Math.min(i, MAX_NUM_ITEMS_IN_PREVIEW - 1);
            PointF folded = getPreviewItemLocation(LauncherSettings.Favorites.ITEM_TYPE_FOLDER,
                    previewIndex);
            PointF expanded = getPreviewItemLocation(
                    LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER, i);

            float dx, dy;

            switch (expandType) {
                case EXPAND_LT: // 右下角
                    dx = (((-(mIconSize - folded.x)) / scale) + mContentWidth) - expanded.x;
                    dy = (((-(mIconSize - folded.y)) / scale) + mContentHeight) - expanded.y;
                    break;
                case EXPAND_RT: // 左下角
                    dx = (folded.x / scale) - expanded.x;
                    dy = (((-(mIconSize - folded.y)) / scale) + mContentHeight) - expanded.y;
                    break;
                case EXPAND_LB: // 右上角
                    dx = (((-(mIconSize - folded.x)) / scale) + mContentWidth) - expanded.x;
                    dy = (folded.y / scale) - expanded.y;
                    break;
                case EXPAND_RB:
                default: // 左上角
                    dx = (folded.x / scale) - expanded.x;
                    dy = (folded.y / scale) - expanded.y;
                    break;
            }

            animatorSet.play(getAnimator(child, View.TRANSLATION_X, dx, 0f, expand));
            animatorSet.play(getAnimator(child, View.TRANSLATION_Y, dy, 0f, expand));

            // 淡入后 x 个图标
            if (i >= MAX_NUM_ITEMS_IN_PREVIEW - 1) {
                animatorSet.play(getAnimator(child, View.ALPHA, 0f, 1f, expand));
            }
        }
    }

    private float getItemIconSize(int itemType) {
        return switch (itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                float previewScale = mFolderIconLayoutRule.scaleForItemLargeFolder(0)
                        * mFolderIconLayoutRule.getBaselineIconScale();
                yield mFolderIconLayoutRule.getIconSize() * previewScale;
            }
            case LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER ->
                    mLargeFolderIcon.childIconSizePx;
            default -> 1f;
        };
    }

    private Point getContentPivotPoint(int expandType) {
        return switch (expandType) {
            case EXPAND_LT -> new Point(mContentWidth, mContentHeight);
            case EXPAND_RT -> new Point(0, mContentHeight);
            case EXPAND_LB -> new Point(mContentWidth, 0);
            default -> new Point(0, 0);
        };
    }

    private Rect getContentClipRect(int expandType, float scale) {
        int prevSize = (int) (mIconSize / scale);
        int left, top;

        switch (expandType) {
            case EXPAND_LT -> {
                left = mContentWidth - prevSize;
                top = mContentHeight - prevSize;
            }
            case EXPAND_RT -> {
                left = 0;
                top = mContentHeight - prevSize;
            }
            case EXPAND_LB -> {
                left = mContentWidth - prevSize;
                top = 0;
            }
            default -> {
                left = 0;
                top = 0;
            }
        }

        return new Rect(left, top, left + prevSize, top + prevSize);
    }

    private Point getFolderNameTranslations(int expandType) {
        int offsetX = (mContentWidth / 2) - (mIconSize / 2);
        int offsetY = mContentHeight - mIconSize;

        return switch (expandType) {
            case EXPAND_LT -> new Point(offsetX, 0);
            case EXPAND_RT -> new Point(-offsetX, 0);
            case EXPAND_LB -> new Point(offsetX, -offsetY);
            default -> new Point(-offsetX, -offsetY);
        };
    }

    private PointF getPreviewItemLocation(int itemType, int index) {
        PointF fallback = new PointF(1f, 1f);
        switch (itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                mFolderIconLayoutRule.computePreviewItemDrawingParams(index, mTmpParams);
                return new PointF(mTmpParams.getTransX(), mTmpParams.getTransY());
            }
            case LauncherSettings.Favorites.ITEM_TYPE_LARGE_FOLDER -> {
                List<View> items = mLargeFolderIcon.getItemViews();
                if (index >= items.size()) {
                    index = items.size() - 1;
                }
                return new PointF(mLargeFolderIcon.getItemPosition(index));
            }
            default -> {
                return fallback;
            }
        }
    }

    private Animator getAnimator(View view, Property<View, Float> property, float v1, float v2,
            boolean expand) {
        return ObjectAnimator.ofFloat(view, property, expand ? v1 : v2, expand ? v2 : v1);
    }

    private Animator getContentClipAnimator(RectProperty target, Rect start, Rect end,
            boolean expand) {
        return ObjectAnimator.ofObject(
                target,
                AnimUtils.RECT_PROPERTY,
                new RectEvaluator(),
                expand ? start : end,
                expand ? end : start
        );
    }
}