package com.android.launcher3.folder.largefolder;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.apppairs.AppPairIconDrawingParams;
import com.android.launcher3.apppairs.AppPairIconGraphic;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LargeFolderUtil;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ch.hu
 * Date: 7/3/25 10:13
 * Description:
 */
public class LargeFolderAdditionalView extends DoubleShadowBubbleTextView {

    private final String TAG = "LargeFolderAdditionalView";

    private final ArrayList<DotInfo> mDotInfos = new ArrayList<>();
    private final ArrayList<ItemInfo> mItemInfos = new ArrayList<>();

    private final AtomicReference<ItemInfo> mFrontIconRef = new AtomicReference<>();
    private final AtomicReference<ItemInfo> mSecondIconRef = new AtomicReference<>();
    private final AtomicReference<ItemInfo> mThirdIconRef = new AtomicReference<>();

    // Virtual item for Workspace display
    public final ItemInfoWithIcon mInfo = new WorkspaceItemInfo();

    protected final ActivityContext mActivity;

    public Launcher mLauncher;

    public DeviceProfile mDeviceProfile;

    public LargeFolderAdditionalView(Context context) {
        this(context, null);
    }

    public LargeFolderAdditionalView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LargeFolderAdditionalView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);
        try {
            mLauncher = Launcher.getLauncher(context);
        } catch (Exception ignore) {
        }
        mDeviceProfile = mActivity.getDeviceProfile();
        setTag(mInfo);
    }

    public static LargeFolderAdditionalView inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group) {
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        LargeFolderAdditionalView icon = (LargeFolderAdditionalView) inflater.inflate(resId, group,
                false);
        if (activity instanceof Launcher) {
            icon.mLauncher = (Launcher) activity;
        }
        icon.mDeviceProfile = activity.getDeviceProfile();
        icon.setTag(icon.mInfo);
        return icon;
    }

    public synchronized void bindItems(ArrayList<ItemInfo> items) {
        if (items == null || items.isEmpty()) {
            mItemInfos.clear();
            mFrontIconRef.set(null);
            mSecondIconRef.set(null);
            mThirdIconRef.set(null);
            return;
        }

        mItemInfos.clear();
        mItemInfos.addAll(items);

        ItemInfo newFront = !items.isEmpty() ? items.get(0) : null;
        ItemInfo oldFront = mFrontIconRef.get();

        ItemInfo newSecond = items.size() > 1 ? items.get(1) : null;
        ItemInfo oldSecond = mFrontIconRef.get();

        ItemInfo newThird = items.size() > 2 ? items.get(2) : null;
        ItemInfo oldThird = mFrontIconRef.get();

        boolean frontChanged = (oldFront == null || newFront == null || oldFront.id != newFront.id);
        boolean secondChanged = (oldSecond == null || newSecond == null || oldSecond.id != newSecond.id);
        boolean thirdChanged = (oldThird == null || newThird == null || oldThird.id != newThird.id);

        if (frontChanged || secondChanged || thirdChanged) {
            mFrontIconRef.set(newFront);
            mSecondIconRef.set(newSecond);
            mThirdIconRef.set(newThird);
            mInfo.bitmap = null;
            updateAdditionalIcon();
        }

        applyDotState(mInfo, false);
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        onItemIconChanged(info.getComponentKey(), info.bitmap);
    }

    /******************************************************
     *
     *  Icon change listener —— only update front icon
     *
     ******************************************************/
    public void onItemIconChanged(ComponentKey key, BitmapInfo newBitmap) {
        ItemInfo front = mFrontIconRef.get();
        if (front == null || key == null) return;
        if (front.getComponentKey() == null) return;

        if (!front.getComponentKey().equals(key)) return;

        if (front instanceof WorkspaceItemInfo wii) {
            wii.bitmap = newBitmap;
            updateAdditionalIcon();
        }
    }

    /**
     * Called externally when items are updated to synchronize data references.
     * Triggers re-rendering only when the front/second/third icon changes.
     */
    public synchronized void updateSourceData(List<ItemInfo> items) {

        if (items == null || items.isEmpty()) {
            mItemInfos.clear();
            mFrontIconRef.set(null);
            mSecondIconRef.set(null);
            mThirdIconRef.set(null);
            return;
        }

        // Refresh item list
        mItemInfos.clear();
        mItemInfos.addAll(items);

        ItemInfo newFront = !items.isEmpty() ? items.get(0) : null;
        ItemInfo oldFront = mFrontIconRef.get();

        ItemInfo newSecond = items.size() > 1 ? items.get(1) : null;
        ItemInfo oldSecond = mFrontIconRef.get();

        ItemInfo newThird = items.size() > 2 ? items.get(2) : null;
        ItemInfo oldThird = mFrontIconRef.get();

        // icon update data and redraw
        mFrontIconRef.set(newFront);
        mSecondIconRef.set(newSecond);
        mThirdIconRef.set(newThird);
        mInfo.bitmap = null; // clear the cache icon

        // If the icon remains unchanged → Only refresh the dot; no need to redraw the icon.
        boolean frontChanged = (oldFront == null || newFront == null || oldFront.id != newFront.id);
        boolean secondChanged = (oldSecond == null || newSecond == null || oldSecond.id != newSecond.id);
        boolean thirdChanged = (oldThird == null || newThird == null || oldThird.id != newThird.id);

        // If any icon changed，update all icons
        if (!frontChanged && !secondChanged && !thirdChanged) {
            applyDotState(mInfo, false);
            return;
        }

        // icon changed → update data & redraw
        mFrontIconRef.set(newFront);
        mSecondIconRef.set(newSecond);
        mThirdIconRef.set(newThird);
        mInfo.bitmap = null; // invalidate cached icon

        updateAdditionalIcon();
    }

    /******************************************************
     *
     *  updateAdditionalIcon
     *
     ******************************************************/
    public void updateAdditionalIcon() {
        ItemInfo front = mFrontIconRef.get();
        if (front == null) return;

        new Thread(() -> {

            Bitmap icon = createAdditionalIcon(front);

            if (icon == null) return;

            BitmapInfo newInfo = BitmapInfo.fromBitmap(icon);
            mInfo.bitmap = newInfo;

            MAIN_EXECUTOR.execute(() -> {
                applyIconAndLabel(mInfo);
                applyDotState(mInfo, false);
            });

        }).start();
    }

    public Bitmap createAdditionalIcon(ItemInfo itemInfo) {

        if (itemInfo == null) return null;

        int iconSize = mDeviceProfile.getWorkspaceIconProfile().getIconSizePx();
        Path path = LargeFolderUtil.getInstance().getIconShapePath(getContext(), iconSize);

        Bitmap centerBitmap = getBitmapFromItemInfo(itemInfo, iconSize, path);
        if (centerBitmap == null) return null;

        Bitmap layer1Bitmap = getLayerBitmap(null, iconSize, path, 1);
        Bitmap layer2Bitmap = getLayerBitmap(null, iconSize, path, 2);

        int layerCount = 0;
        if (layer1Bitmap != null) layerCount++;
        if (layer2Bitmap != null) layerCount++;

        int offset = calculateOffset(iconSize, layerCount);
        int scaled = (int) (offset * mDeviceProfile.iconloaderlibScale);

        int newSize = offset * 2 + iconSize;

        Bitmap result = Bitmap.createBitmap(newSize, newSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        if (layerCount == 2) {
            canvas.drawBitmap(layer2Bitmap, 0, 0, paint);
            canvas.drawBitmap(layer1Bitmap, offset, offset, paint);
        } else if (layerCount == 1) {
            canvas.drawBitmap(layer1Bitmap, offset, offset, paint);
        }

        canvas.drawBitmap(centerBitmap, scaled * 2, scaled * 2, paint);

        return result;
    }

    private int calculateOffset(int iconSize, int layerCount) {
        // Calculate the offset by dynamically adjusting it based on the icon size ratio
        float offsetFactor = 0.15f; // The offset as a percentage of the icon size, 0.15 means 15%

        // Calculate the offset
        int offset = (int) (iconSize * offsetFactor);

        // Adjust the offset based on the number of icons
        return switch (layerCount) {
            case 1 ->
                // If there is only 1 icon, the offset is smaller for centering
                    offset;
            case 2 ->
                // If there are 2 icons, the offset is moderate to keep a symmetrical distribution
                    offset;
            case 3 ->
                // If there are 3 icons, the offset is larger for an even distribution
                    offset * 2;
            default -> offset;
        };
    }


    private Bitmap getLayerBitmap(ItemInfo item, int iconSize, Path path, int layerIndex) {
        // Get different icons based on the provided layer index
        Bitmap layerBitmap = null;

        if (mItemInfos.size() >= layerIndex + 1) {
            item = mItemInfos.get(layerIndex);
        }

        if (item instanceof WorkspaceItemInfo wii) {
            // For WorkspaceItemInfo, fetch the real icon
            layerBitmap = getBitmapFromItemInfo(wii, iconSize, path);
        } else if (item instanceof AppPairInfo api) {
            // For AppPairInfo, it may require fetching a different type of icon
            AppPairIconDrawingParams params = new AppPairIconDrawingParams(getContext(),
                    DISPLAY_FOLDER);
            Drawable drawable = AppPairIconGraphic.composeDrawable(api, params);
            layerBitmap = getBitmapFromDrawable(drawable, iconSize);
        }

        // Return the icon bitmap
        return layerBitmap;
    }

    private Bitmap getBitmapFromDrawable(Drawable drawable, int size) {
        if (drawable == null) return null;

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) return null;

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bmp;
    }

    private Bitmap getBitmapFromItemInfo(ItemInfo item, int iconSize, Path path) {

        if (item == null) return null;

        Drawable iconDrawable = null;

        if (item instanceof WorkspaceItemInfo wii) {
            iconDrawable = wii.newIcon(getContext(), BitmapInfo.FLAG_THEMED);
        } else if (item instanceof AppPairInfo api) {
            AppPairIconDrawingParams params = new AppPairIconDrawingParams(getContext(),
                    DISPLAY_FOLDER);
            iconDrawable = AppPairIconGraphic.composeDrawable(api, params);
        }

        if (iconDrawable == null) return null;

        int width = iconDrawable.getIntrinsicWidth();
        int height = iconDrawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) return null;

        Picture pic = new Picture();
        Canvas canvas = pic.beginRecording(width, height);
        iconDrawable.setBounds(0, 0, width, height);
        iconDrawable.draw(canvas);
        pic.endRecording();

        return Bitmap.createBitmap(pic, iconSize, iconSize, Bitmap.Config.ARGB_8888);
    }

    @Override
    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon == null) return;

        updateDotInfos();

        boolean needDot = !mDotInfos.isEmpty();
        float newScale = needDot ? 1f : 0f;

        if (mDisplay == DISPLAY_ALL_APPS) {
            mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;
        } else {
            mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
        }

        if (!animate || !isShown()) {
            cancelDotScaleAnim();
            mDotParams.scale = newScale;
            invalidate();
            return;
        }
        animateDotScale(newScale);
    }

    private void updateDotInfos() {
        if (mLauncher == null) return;
        ArrayList<DotInfo> infos = new ArrayList<>();
        if (mItemInfos.isEmpty()) {
            mDotInfos.clear();
            return;
        }
        mItemInfos.forEach(item -> {
            DotInfo di = mLauncher.getDotInfoForItem(item);
            if (di != null) {
                infos.add(di);
            }
        });
        mDotInfos.clear();
        mDotInfos.addAll(infos);
    }

    @Override
    public boolean hasDot() {
        return !mDotInfos.isEmpty();
    }

    public ItemInfoWithIcon getInfo() {
        return mInfo;
    }
}
