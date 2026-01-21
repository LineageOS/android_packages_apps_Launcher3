package com.android.launcher3.folder.largefolder;

import static com.android.launcher3.LauncherPrefs.SHOW_DESKTOP_LABELS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherState;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LargeFolderUtil;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.R;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ch.hu
 * Date: 7/3/25 09:52
 */
public class LargeFolderIcon extends ConstraintLayout implements
        DeviceProfile.OnDeviceProfileChangeListener,
        DraggableView, Reorderable {

    public static final int ADDITIONAL_INDEX = 8;
    public static final int CONTENT_GRID_SIZE_X = 3;
    private static final int CONTENT_GRID_SIZE_Y = 3;
    private static final int ON_OPEN_DELAY = 800;
    private static final String TAG = "LargeFolderIcon";
    private Context mContext;
    public int childIconSizePx;
    private ActivityContext mActivity;
    private LargeFolderAdditionalView mAdditionalView;
    Runnable mBindItemsRunnable;
    private Rect mContentBounds;
    private Rect mContentPadding;
    private DeviceProfile mDeviceProfile;
    private Folder mFolder;
    public TextView mFolderName;
    public FolderInfo mInfo;
    public CellLayout mItemContent;
    private SparseArray<View> mItemViews;
    OnAlarmListener mOnOpenListener;
    private Alarm mOpenAlarm;
    private float mScaleForReorderBounce;
    private final MultiTranslateDelegate mTranslateDelegate;
    private float scaleForItem;

    // set to true while performing a content update (to avoid reentrant UI updates)
    private boolean mSuppressContentUpdate = false;

    public LargeFolderIcon(Context context) {
        this(context, null);
    }

    public LargeFolderIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LargeFolderIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mContentBounds = new Rect();
        mContentPadding = new Rect();
        mItemViews = new SparseArray<>();
        mOpenAlarm = new Alarm(Looper.getMainLooper());
        mScaleForReorderBounce = 1.0f;
        mTranslateDelegate = new MultiTranslateDelegate(this);

        mBindItemsRunnable = () -> bindItems(mInfo == null ? null : mInfo.contents);
        mOnOpenListener = alarm -> this.mFolder.beginExternalDrag();
    }

    public static <T extends Context & ActivityContext> LargeFolderIcon inflateFolderAndIcon(
            int resId, T activity, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = Folder.fromXml(activity);
        LargeFolderIcon icon = inflateIcon(resId, activity, folderInfo);
        folder.setLargeFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        return icon;
    }

    public static <T extends Context & ActivityContext> LargeFolderIcon inflateIcon(int resId,
            T activity, FolderInfo folderInfo) {
        LargeFolderIcon icon = (LargeFolderIcon) activity.getLayoutInflater().inflate(resId,
                (ViewGroup) null, false);
        icon.setClipToPadding(false);
        activity.addOnDeviceProfileChangeListener(icon);

        icon.mActivity = activity;
        icon.mDeviceProfile = activity.getDeviceProfile();
        icon.updateIconSizeAndPadding();
        LargeFolderUtil.getInstance().filterUninstallApp(icon.getContext(), folderInfo);
        icon.setTag(folderInfo);
        icon.setOnClickListener(ItemClickHandler.INSTANCE);
        icon.mInfo = folderInfo;
        icon.setContentDescription(icon.getAccessibilityTitle(folderInfo.title));
        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());
        icon.mFolderName = (TextView) icon.findViewById(R.id.large_folder_name);
        if (SHOW_DESKTOP_LABELS.get(icon.mContext)) {
            icon.mFolderName.setText(folderInfo.getTitle());
        }
        LayoutParams lp =
                (LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = icon.mDeviceProfile.getWorkspaceIconProfile().getIconDrawablePaddingPx();
        ;
        icon.mItemContent = (CellLayout) icon.findViewById(R.id.large_folder_content);
        icon.mItemContent.setGridSize(CONTENT_GRID_SIZE_X, CONTENT_GRID_SIZE_Y);
        icon.onBackgroundColorChanged();
        // use guarded bind
        icon.bindItems(icon.mInfo == null ? null : icon.mInfo.contents);
        return icon;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        if (dp == null) {
            return;
        }
        mDeviceProfile = dp;
        updateIconSizeAndPadding();
        invalidate();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateIconSizeAndPadding();
        invalidate();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Rect iconPadding = mDeviceProfile.getIconPadding();
        setPadding(iconPadding.left, iconPadding.top, iconPadding.right, iconPadding.bottom);
        if (mItemContent != null) {
            mItemContent.setPadding(mContentPadding.left, mContentPadding.top,
                    mContentPadding.right, mContentPadding.bottom);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        folder.setMinimumWidth(Math.round(getViewHeight()));
        folder.setMinimumHeight(Math.round(getViewHeight()));
    }

    public float getViewHeight() {
        return getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    }

    public float getViewWidth() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    }

    public static int getNoticeDotOccupiedSize(int iconSizePx) {
        float dotSize = iconSizePx * 0.24f;
        int dotOccupiedSize = (int) Math.ceil(0.6f * dotSize);
        return dotOccupiedSize;
    }

    private void updateIconSizeAndPadding() {
        if (mDeviceProfile == null) {
            return;
        }
        Point cellSize = mDeviceProfile.getCellSize();
        Rect iconPadding = mDeviceProfile.getIconPadding();
        int contentWidth = ((cellSize.x * 2) - iconPadding.left) - iconPadding.right;
        int contentHeight = ((cellSize.y * 2) - iconPadding.top) - iconPadding.bottom;
        int contentSize = Math.min(contentWidth, contentHeight);
        int maxIconSize = contentSize / CONTENT_GRID_SIZE_X;
        int iconSize = maxIconSize / 2;
        while (true) {
            if (iconSize >= maxIconSize) {
                break;
            }
            int dotSize = getNoticeDotOccupiedSize(iconSize);
            int newIconSize = (int) Math.floor((contentSize - (dotSize * 8.0f)) / 3.0f);
            if (newIconSize > iconSize) {
                iconSize++;
            } else {
                childIconSizePx = newIconSize;
                break;
            }
        }
        SparseArray<View> itemViews = mItemViews.clone();
        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.valueAt(i);
            if (v instanceof AppPairIcon appPairIcon) {
                appPairIcon.setIconSize(childIconSizePx);
            }
            if (v instanceof BubbleTextView bubbleTextView) {
                bubbleTextView.setIconSize(childIconSizePx);
            }
        }
        mContentBounds.set(0, 0, contentWidth, contentHeight);
        mContentBounds.offsetTo(iconPadding.left, iconPadding.top);
        int paddingLeftRight = (contentWidth - (childIconSizePx * CONTENT_GRID_SIZE_X)) / 8;
        int paddingTopBottom = (contentHeight - (childIconSizePx * CONTENT_GRID_SIZE_Y)) / 8;
        mContentPadding.set(paddingLeftRight, paddingTopBottom, paddingLeftRight,
                paddingTopBottom);
        scaleForItem = (float) iconSize / mDeviceProfile.getWorkspaceIconProfile().getIconSizePx();
    }

    private void removeItemViews() {
        SparseArray<View> itemViews = mItemViews.clone();
        mItemViews.clear();
        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.valueAt(i);
            if (v != null) {
                v.setTag(null);
                v.setOnClickListener(null);
                v.setOnLongClickListener(null);
            }
        }
        if (mItemContent != null) {
            mItemContent.removeAllViews();
        }
    }

    /**
     * Thread-safe / atomic bind wrapper.
     * Ensures UI/content update isn't interrupted by other updates.
     */
    public void bindItems(List<ItemInfo> items) {
        // Guard for null or destroyed launcher
        // Delegate to suppressed-execution wrapper to avoid reentrancy
        executeWithContentUpdateSuppressed(() -> doBindItems(items));
    }

    /**
     * The actual bind logic. This method should assume it's called inside
     * executeWithContentUpdateSuppressed.
     */
    private void doBindItems(List<ItemInfo> items) {
        if (mItemContent == null) {
            return;
        }

        // Defensive: treat null as empty
        List<ItemInfo> safeItems = items == null ? new ArrayList<>() : items;

        mItemContent.removeAllViews();
        boolean isLayoutRightToLeft = getResources().getConfiguration().getLayoutDirection() == 1;
        mItemContent.setInvertIfRtl(isLayoutRightToLeft);

        // Build new map of views first, then swap.
        SparseArray<View> newItemViews = new SparseArray<>();
        int count = Math.min(safeItems.size(), ADDITIONAL_INDEX);
        for (int i = 0; i < count; i++) {
            ItemInfo itemInfo = safeItems.get(i);
            if (itemInfo == null) {
                continue;
            }
            View child = getOrCreateNewView(itemInfo);
            if (child == null) {
                continue;
            }
            newItemViews.put(itemInfo.id, child);
            CellLayoutLayoutParams lp = new CellLayoutLayoutParams(i % CONTENT_GRID_SIZE_X,
                    i / CONTENT_GRID_SIZE_Y, 1, 1);
            mItemContent.addViewToCellLayout(child, -1, child.getId(), lp, true);
        }

        // handle additional items
        if (safeItems.size() > ADDITIONAL_INDEX) {
            ArrayList<ItemInfo> additionalItems = new ArrayList<>();
            for (int i2 = ADDITIONAL_INDEX; i2 < safeItems.size(); i2++) {
                ItemInfo info = safeItems.get(i2);
                if (info != null) additionalItems.add(info);
            }
            if (mAdditionalView == null) {
                mAdditionalView = createAdditionalView();
            }
            mAdditionalView.bindItems(additionalItems);
            // inform additional view of the canonical contents list (for consistency)
            mAdditionalView.updateSourceData(additionalItems);

            CellLayoutLayoutParams lp2 = new CellLayoutLayoutParams(2, 2, 1, 1);
            mItemContent.addViewToCellLayout(mAdditionalView, -1,
                    mAdditionalView.getId(), lp2, true);
        } else {
            // no additional items - clear additional view reference safely
            if (mAdditionalView != null) {
                // reset its data to avoid stale references
                mAdditionalView.updateSourceData(new ArrayList<>());
            }
        }

        // Atomic swap of item views reference
        mItemViews = newItemViews;
    }

    private View getOrCreateNewView(ItemInfo itemInfo) {
        if (itemInfo == null) return null;
        if (itemInfo.id != -1 && mItemViews != null && mItemViews.contains(itemInfo.id)) {
            View existing = mItemViews.get(itemInfo.id);
            if (existing != null) {
                return existing;
            }
        }
        return createNewView(itemInfo);
    }

    private View createNewView(ItemInfo item) {
        if (item == null) {
            return null;
        }
        View icon;
        if (item instanceof AppPairInfo pairInfo) {
            AppPairIcon pairIcon = AppPairIcon.inflateIcon(R.layout.folder_app_pair, mActivity,
                    null,
                    pairInfo, BubbleTextView.DISPLAY_LARGE_FOLDER_ICON);
            pairIcon.setIconSize(childIconSizePx);
            icon = pairIcon;
        } else {
            BubbleTextView textView = (BubbleTextView) LayoutInflater.from(mContext).inflate(
                    R.layout.large_folder_internal_icon, (ViewGroup) null, false);
            textView.setIconSize(childIconSizePx);
            textView.setTextSize(0.0f);
            textView.setCompoundDrawablePadding(0);
            if (item instanceof ItemInfoWithIcon) {
                try {
                    textView.applyFromItemInfoWithIcon((ItemInfoWithIcon) item);
                } catch (Exception e) {
                    // Defensive: if icon extraction fails, do not crash; leave placeholder
                    e.printStackTrace();
                }
            }
            textView.applyDotState(item, false);
            icon = textView;
        }

        icon.setOnClickListener(
                view -> {
                    if (!((Launcher) mActivity).isInState(LauncherState.EDIT_MODE)) {
                        ItemClickHandler.INSTANCE.onClick(view);
                    }
                });
        icon.setOnLongClickListener(
                ItemLongClickListener::onWorkspaceItemLongClick);
        // tag the item for later updates
        icon.setTag(item);
        return icon;
    }

    private LargeFolderAdditionalView createAdditionalView() {
        LargeFolderAdditionalView textView = (LargeFolderAdditionalView) LayoutInflater.from(
                mContext).inflate(R.layout.large_folder_additional_icon, (ViewGroup) null,
                false);
        textView.setIconSize(childIconSizePx);
        textView.setTextSize(0.0f);
        textView.setTextVisibility(false);
        textView.setCompoundDrawablePadding(0);
        textView.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        if (mFolder != null) {
                            mFolder.animateOpen();
                        }
                    }
                });
        return textView;
    }

    public void unbindItems() {
        if (mItemContent != null) {
            mItemContent.removeAllViews();
        }
    }

    public Folder getFolder() {
        return mFolder;
    }

    public String getAccessibilityTitle(CharSequence title) {
        int size = mInfo == null ? 0 : mInfo.contents.size();
        if (size >= 4) {
            return getContext().getString(R.string.folder_name_format_overflow, title, 4);
        }
        return getContext().getString(R.string.folder_name_format_exact, title,
                size);
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect outBounds) {
        outBounds.left = mItemContent.getLeft();
        outBounds.top = mItemContent.getTop();
        outBounds.right = mItemContent.getRight();
        outBounds.bottom = mItemContent.getBottom();
    }

    public Rect getContentBoundsOnScreen(Rect outPos) {
        Rect outPos2 = outPos != null ? outPos : new Rect();
        int[] location = new int[2];
        mItemContent.getLocationOnScreen(location);
        outPos2.set(0, 0, mItemContent.getWidth(), mItemContent.getHeight());
        outPos2.offsetTo(location[0], location[1]);
        return outPos2;
    }

    public Rect getContentBounds() {
        return new Rect(mContentBounds);
    }

    public Point getItemPosition(int index) {
        int cellLayoutWidth = (mContentBounds.width() - mContentPadding.left)
                - mContentPadding.right;
        int cellLayoutHeight = (mContentBounds.height() - mContentPadding.top)
                - mContentPadding.right;
        float cellWidth = (float) cellLayoutWidth / CONTENT_GRID_SIZE_X;
        float cellHeight = (float) cellLayoutHeight / CONTENT_GRID_SIZE_Y;
        float childPaddingLeft = (cellWidth - childIconSizePx) / 2.0f;
        float childPaddingTop = (cellHeight - childIconSizePx) / 2.0f;
        int cellX = index % CONTENT_GRID_SIZE_X;
        int cellY = index / CONTENT_GRID_SIZE_Y;
        if (Utilities.isRtl(getResources())) {
            cellX = CONTENT_GRID_SIZE_X - (cellX + 1);
        }
        int left = (int) ((cellX * cellWidth) + mContentPadding.left + childPaddingLeft);
        int top = (int) ((cellY * cellHeight) + mContentPadding.top + childPaddingTop);
        return new Point(left, top);
    }

    public void removeListeners() {
        if (mFolder != null) {
            mFolder.setLargeFolderIcon(null);
        }
        if (mActivity != null) {
            mActivity.removeOnDeviceProfileChangeListener(this);
        }
        removeItemViews();
    }

    public void onTitleChange(CharSequence title) {
        if (mFolderName != null) {
            mFolderName.setText(title);
            setContentDescription(getAccessibilityTitle(title));
        }
    }

    public void onItemsChanged(boolean animate) {
        // if content update suppressed, run immediately (will be a no-op because suppressed),
        // else post delayed as before
        removeCallbacks(mBindItemsRunnable);
        // keep delayed post for UX smoothness, but ensure suppressed execution will protect
        // against races
        postDelayed(mBindItemsRunnable, 100L);
    }

    public void onBackgroundColorChanged() {
        if (mItemContent == null || mInfo == null) {
            return;
        }
        Drawable bgDrawable = new LargeFolderBgDrawable(
                Themes.getAttrColor(mContext, R.attr.folderPreviewColor), getBgRadius());
        mItemContent.setBackground(bgDrawable);
    }

    public int getBgRadius() {
        return getResources().getDimensionPixelSize(R.dimen.large_folder_content_bg_corner);
    }

    public Drawable getContentBackground() {
        if (mItemContent == null) {
            return null;
        }
        return mItemContent.getBackground();
    }

    public void updateItemDotState(boolean editMode) {
        if (mSuppressContentUpdate) {
            return;
        }
        SparseArray<View> itemViews = mItemViews == null ? new SparseArray<>() : mItemViews.clone();
        for (int i = 0; i < itemViews.size(); i++) {
            View child = itemViews.valueAt(i);
            if (child == null) continue;
            Object tag = child.getTag();
//            if (tag instanceof ItemInfo itemInfo) {
//                itemInfo.setItemSelected(false);
//            }
            child.invalidate();
        }
    }

    public void updateChildDotInfo() {
        if (mSuppressContentUpdate) {
            return;
        }
        SparseArray<View> itemViews = mItemViews == null ? new SparseArray<>() : mItemViews.clone();
        for (int i = 0; i < itemViews.size(); i++) {
            View child = itemViews.valueAt(i);
            if (child == null) continue;
            if (child instanceof BubbleTextView) {
                BubbleTextView textView = (BubbleTextView) child;
                Object tag = child.getTag();
                if (tag instanceof ItemInfo) {
                    ItemInfo itemInfo = (ItemInfo) tag;
                    textView.applyDotState(itemInfo, true);
                }
            }
            if (child instanceof AppPairIcon) {
                // nothing to do now, but keep defensive
            }
        }
        if (mAdditionalView != null) {
            // guard: mAdditionalView may itself be in flux - call defensively
            try {
                mAdditionalView.applyDotState(mAdditionalView.getInfo(), false);
            } catch (Exception e) {
                // swallow and log to avoid crash
                e.printStackTrace();
            }
        }
    }

    public void updateChildItems() {
        // Avoid running while a full bind is in progress
        if (mSuppressContentUpdate) {
            return;
        }
        SparseArray<View> itemViews = mItemViews == null ? new SparseArray<>() : mItemViews.clone();
        for (int i = 0; i < itemViews.size(); i++) {
            View icon = itemViews.valueAt(i);
            if (icon == null) continue;
            Object tag = icon.getTag();
            if (icon instanceof BubbleTextView) {
                BubbleTextView child = (BubbleTextView) icon;
                if (tag instanceof WorkspaceItemInfo itemInfo) {
                    try {
                        child.applyFromWorkspaceItem(itemInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (icon instanceof AppPairIcon) {
                AppPairIcon appPairIcon = (AppPairIcon) icon;
                if (tag instanceof AppPairInfo) {
                    // we might update app pair icon state here if necessary
                }
            }
        }
        if (mAdditionalView != null) {
            try {
                mAdditionalView.updateAdditionalIcon();
            } catch (Exception e) {
                // Log but don't crash
                e.printStackTrace();
            }
        }
    }

    public void onItemIconChanged(ComponentKey componentKey, BitmapInfo bitmapInfo) {
        if (mSuppressContentUpdate) {
            // record or queue updates if you want; for now, attempt best-effort updates afterwards
        }
        SparseArray<View> itemViews = mItemViews == null ? new SparseArray<>() : mItemViews.clone();
        for (int i = 0; i < itemViews.size(); i++) {
            View view = itemViews.valueAt(i);
            if (view instanceof BubbleTextView) {
                final BubbleTextView child = (BubbleTextView) view;
                Object tag = view.getTag();
                if (tag instanceof WorkspaceItemInfo) {
                    final WorkspaceItemInfo itemInfo = (WorkspaceItemInfo) tag;
                    if (itemInfo.getComponentKey() != null && itemInfo.getComponentKey().equals(
                            componentKey)) {
                        itemInfo.bitmap = bitmapInfo;
                        child.post(() -> {
                            try {
                                child.applyIconAndLabel(itemInfo);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }
        }
        if (mAdditionalView != null) {
            try {
                mAdditionalView.onItemIconChanged(componentKey, bitmapInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder == null || mFolder.isDestroyed() || !willAcceptItem(dragInfo)) {
            return;
        }
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if ((dragInfo instanceof WorkspaceItemFactory)
                || (dragInfo instanceof PendingAddShortcutInfo) || Folder.willAccept(
                dragInfo)) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    public void setIconVisible(boolean visible) {
        if (mItemContent != null) {
            mItemContent.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        if (mFolder == null || mFolder.isDestroyed()) {
            return false;
        }
        return willAcceptItem(dragInfo);
    }

    private boolean willAcceptItem(ItemInfo item) {
        if (item == null) return false;
        int itemType = item.itemType;
        return (itemType == ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR ||
                itemType == ITEM_TYPE_SHORTCUT ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                && item != mInfo && !mFolder.isOpen();
    }

    public void onDragExit() {
        mOpenAlarm.cancelAlarm();
    }

    public void onDrop(DropTarget.DragObject dragObject, boolean isDrag) {
        // add by ocd[2025/08/05]: Multi-select in desktop edit mode
//        onDropOthers(dragObject.getOtherDragObjects());

        executeWithContentUpdateSuppressed(() -> {
            if (dragObject.dragView != null) {
                dragObject.dragView.remove();
            }
            ItemInfo item = getItemInfo(dragObject);
            BubbleTextView child = null;
            if (dragObject.originalView != null) {
                DraggableView draggableView = dragObject.originalView;
                if (draggableView instanceof BubbleTextView) {
                    BubbleTextView textView = (BubbleTextView) draggableView;
                    child = textView;
                }
            }
            if ((dragObject.dragSource instanceof ActivityAllAppsContainerView)
                    && (item instanceof WorkspaceItemInfo)) {
                WorkspaceItemInfo itemInfo = (WorkspaceItemInfo) item;
                if (child != null) {
                    child.setVisibility(View.VISIBLE);
                }
                item = new WorkspaceItemInfo(itemInfo);
            }
            dragObject.deferDragViewCleanupPostAnimation = false;
            mInfo.add(item);
            mActivity.getModelWriter().addOrMoveItemInDatabase(item, mInfo.id,
                    item.screenId, item.cellX, item.cellY);
            bindItems(mInfo.contents);
        });
    }

    // add by ch.hu[2025/08/05]: Multi-select in desktop edit mode - start
    private void onDropOthers(ArrayList<DropTarget.DragObject> dragObjects) {
        executeWithContentUpdateSuppressed(() -> {
            for (DropTarget.DragObject d : dragObjects) {
                // Remove drag view
                if (d.dragView != null) d.dragView.remove();

                BubbleTextView child = (BubbleTextView) d.originalView;
                ItemInfo item = getItemInfo(d);

                // 如果不在当前 contents 中，则添加
                if (!mInfo.contents.contains(item)) {
                    mInfo.add(item);
                }

                // Update database
                mActivity.getModelWriter().addOrMoveItemInDatabase(
                        item, mInfo.id, item.screenId, item.cellX, item.cellY
                );

                // Remove from parent layout
                removeFromParentCell(child);
            }
        });
    }

    private void removeFromParentCell(View view) {
        CellLayout parentCell = ((Launcher) mActivity).getWorkspace().getParentCellLayoutForView(
                view);
        if (parentCell != null) {
            parentCell.removeView(view);
        }
    }
    // add by ch.hu[2025/08/05]: Multi-select in desktop edit mode - end

    public ItemInfo getItemInfo(DropTarget.DragObject d) {
        ItemInfo item = d.dragInfo;
        ItemInfo itemInfo = d.dragInfo;
        if (itemInfo instanceof WorkspaceItemFactory) {
            WorkspaceItemFactory itemFactory = (WorkspaceItemFactory) itemInfo;
            item = itemFactory.makeWorkspaceItem(getContext());
        }
        if (d.dragSource instanceof BaseItemDragListener) {
            if (item instanceof AppPairInfo) {
                AppPairInfo appPairInfo = (AppPairInfo) item;
                item = new AppPairInfo(appPairInfo);
            }
            if (item instanceof WorkspaceItemInfo) {
                WorkspaceItemInfo itemInfo2 = (WorkspaceItemInfo) item;
                item = new WorkspaceItemInfo(itemInfo2);
            }
        }

        // add by ch.hu[2025/08/05]: 桌面编辑模式多选
//        if (item != null) item.setItemSelected(false);

        return item;
    }

    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        if (success) {
            if (mInfo.contents.size() > 1) {
                bindItems(mInfo.contents);
                return;
            }
            unbindItems();
            replaceFolderWithFinalItem();
        }
    }

    public ArrayList<View> getItemViews() {
        ArrayList<View> itemViews = new ArrayList<>();
        if (mItemContent == null) return itemViews;
        for (int i = 0; i < 9; i++) {
            View icon = mItemContent.getChildAt(i % CONTENT_GRID_SIZE_X,
                    i / CONTENT_GRID_SIZE_Y);
            if (icon != null) {
                itemViews.add(icon);
            }
        }
        return itemViews;
    }

    public boolean replaceFolderWithFinalItem() {
        if (mInfo.contents.size() > 1) {
            return false;
        }
        View newIcon = null;
        ItemInfo newItemInfo = null;
        Launcher mLauncher = (Launcher) mActivity;
        if (mInfo.contents.size() == 1) {
//            CellLayout cellLayout = mLauncher.getCellLayout(mInfo.container,
//                    mInfo.screenId);
            ItemInfo newItemInfo2 = mInfo.contents.remove(0);
            newItemInfo = newItemInfo2;
            mLauncher.getModelWriter().addOrMoveItemInDatabase(newItemInfo,
                    mInfo.container, mInfo.screenId, mInfo.cellX, mInfo.cellY);
            newIcon = mLauncher.getItemInflater().inflateItem(newItemInfo);
        }
        if (mFolder != null) {
            mLauncher.removeItem(mFolder.getLargeFolderIcon(), mInfo, true,
                    "large folder removed because there's only 1 item in it");
            if (mFolder.getLargeFolderIcon() instanceof DropTarget) {
                mFolder.getDragController().removeDropTarget(
                        (DropTarget) mFolder.getLargeFolderIcon());
            }
        }
        if (newIcon != null) {
            newItemInfo.container = mInfo.container;
            newItemInfo.screenId = mInfo.screenId;
            newItemInfo.itemType = ITEM_TYPE_APPLICATION;
//            newItemInfo.setItemSelected(false);
            mLauncher.getWorkspace().addInScreen(newIcon, newItemInfo);
            newIcon.requestFocus();
        }
        mLauncher.getWorkspace().removeExtraEmptyScreen(false);
        mLauncher.getWorkspace().onDragEnd();
        return true;
    }

    public void setTextVisible(boolean visible) {
        if (visible && mFolderName != null) {
            mFolderName.setVisibility(View.VISIBLE);
        } else if (mFolderName != null) {
            mFolderName.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    public float getScaleForItem() {
        return scaleForItem;
    }

    public void onLargeFolderDestroy() {
        Launcher mLauncher = (Launcher) mActivity;
        if (mLauncher == null) {
            return;
        }
        mItemViews.clear();
        mLauncher.removeOnDeviceProfileChangeListener(
                LargeFolderIcon.this);
        mLauncher = null;
    }

    /** Executes the task while suppressing the content update for the folder */
    private void executeWithContentUpdateSuppressed(Runnable task) {
        if (mSuppressContentUpdate) {
            // if already suppressed, just run directly (we are probably nested)
            task.run();
            return;
        }
        mSuppressContentUpdate = true;
        try {
            task.run();
        } finally {
            mSuppressContentUpdate = false;
        }
    }
}
