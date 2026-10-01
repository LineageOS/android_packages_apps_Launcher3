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

import static android.view.MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_POSITIVE;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainer;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * DragLayer for Secondary launcher
 */
public class SecondaryDragLayer extends BaseDragLayer<SecondaryDisplayLauncher> {

    private View mAllAppsButton;
    private ActivityAllAppsContainerView<SecondaryDisplayLauncher> mAppsView;
    private View mRemoveTarget;

    private GridView mWorkspace;
    private PinnedAppsAdapter mPinnedAppsAdapter;

    // Source of drags started from the pinned apps grid
    private final DragSource mWorkspaceDragSource = (target, d, success) -> { };

    public SecondaryDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        recreateControllers();
    }

    @Override
    public void recreateControllers() {
        super.recreateControllers();
        TouchController statusBarController =
            ApiWrapper.INSTANCE.get(getContext())
                .createStatusBarTouchController(mContainer, () -> true);

        if (statusBarController != null) {
            mControllers = new TouchController[]{
                new SecondaryDisplayAllAppsTouchController(),
                mContainer.getDragController(),
                statusBarController
            };
        } else {
            mControllers = new TouchController[]{
                new SecondaryDisplayAllAppsTouchController(),
                mContainer.getDragController()
            };
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAllAppsButton = findViewById(R.id.all_apps_button);

        mAppsView = findViewById(R.id.apps_view);
        mRemoveTarget = findViewById(R.id.remove_target);
        // Setup workspace
        mWorkspace = findViewById(R.id.workspace_grid);
        mPinnedAppsAdapter = new PinnedAppsAdapter(mContainer, mAppsView.getAppsStore(),
                this::onIconLongClicked, mContainer.getDeviceProfile().inv.numColumns,
                mContainer.getDeviceProfile().inv.numRows);
        mWorkspace.setAdapter(mPinnedAppsAdapter);
        mWorkspace.setNumColumns(mContainer.getDeviceProfile().inv.numColumns);
        // Fit the rows to the height of the grid once it and the apps are known
        mAppsView.getAppsStore().addUpdateListener(
                () -> mPinnedAppsAdapter.updateNumRows(mWorkspace));
        mWorkspace.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (bottom - top != oldBottom - oldTop) {
                        post(() -> mPinnedAppsAdapter.updateNumRows(mWorkspace));
                    }
                });

        RemoveDropTarget removeDropTarget = new RemoveDropTarget();
        mContainer.getDragController().addDropTarget(removeDropTarget);
        mContainer.getDragController().addDragListener(removeDropTarget);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPinnedAppsAdapter.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPinnedAppsAdapter.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        DeviceProfile grid = mContainer.getDeviceProfile();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child == mAppsView) {
                // Fill the screen, like the app drawer on the default display
                mAppsView.measure(
                        makeMeasureSpec(width - getPaddingLeft() - getPaddingRight(), EXACTLY),
                        makeMeasureSpec(height - getPaddingTop() - getPaddingBottom(), EXACTLY));
            } else if (child == mAllAppsButton) {
                int appsButtonSpec = makeMeasureSpec(
                        grid.getWorkspaceProfile().getIconSizePx(), EXACTLY
                );
                mAllAppsButton.measure(appsButtonSpec, appsButtonSpec);
            } else if (child == mWorkspace) {
                // Leave room for the all apps button below the grid
                measureChildWithMargins(mWorkspace, widthMeasureSpec, 0, heightMeasureSpec,
                        mAllAppsButton.getVisibility() == GONE ? 0
                                : grid.getWorkspaceProfile().getIconSizePx()
                                        + grid.getWorkspaceProfile().getEdgeMarginPx());
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    private class SecondaryDisplayAllAppsTouchController implements TouchController,
            SingleAxisSwipeDetector.Listener {

        private final SingleAxisSwipeDetector mSwipeDetector;
        // Opens the app drawer when swiping up on the home screen, and closes it when swiping
        // down while it is scrolled to the top
        private final SingleAxisSwipeDetector mDrawerSwipeDetector;
        private boolean mNoIntercept;

        public SecondaryDisplayAllAppsTouchController() {
            mSwipeDetector = new SingleAxisSwipeDetector(
                    getContext(),
                    new SingleAxisSwipeDetector.Listener() {
                        @Override
                        public void onDragStart(boolean start, float startDisplacement) {
                            mContainer.getSecondaryDisplayDelegate().openAllAppsForDisplay(
                                            mContainer.getAppsView().getDisplay().getDisplayId());
                        }

                        @Override
                        public boolean onDrag(float displacement) {
                            return false;
                        }

                        @Override
                        public void onDragEnd(float velocity) {}
                    },
                    SingleAxisSwipeDetector.VERTICAL
            );
            mSwipeDetector.setDetectableScrollConditions(
                    SingleAxisSwipeDetector.DIRECTION_POSITIVE, false /* ignoreSlop */);
            mDrawerSwipeDetector = new SingleAxisSwipeDetector(
                    getContext(), this, SingleAxisSwipeDetector.VERTICAL);
        }

        @Override
        public boolean onControllerTouchEvent(MotionEvent ev) {
            if (!usingTwoFingerSwipeOnConnectedDisplay(ev)) {
                // Consume the rest of the gesture that closed the app drawer on touch down
                return !mDrawerSwipeDetector.isDraggingOrSettling()
                        || mDrawerSwipeDetector.onTouchEvent(ev);
            }
            return mSwipeDetector.onTouchEvent(ev);
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            if (usingTwoFingerSwipeOnConnectedDisplay(ev)) {
                return true;
            }

            if (AbstractFloatingView.getTopOpenView(mContainer) != null
                    || mContainer.getDragController().isDragging()) {
                return false;
            }

            boolean drawerShown = mContainer.isAppDrawerShown();
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                if (drawerShown && !isEventOverView(mContainer.getAppsView(), ev)) {
                    mContainer.showAppDrawer(false);
                    return true;
                }
                mNoIntercept = drawerShown
                        && !mContainer.getAppsView().shouldContainerScroll(ev);
                mDrawerSwipeDetector.setDetectableScrollConditions(
                        drawerShown ? DIRECTION_NEGATIVE : DIRECTION_POSITIVE,
                        false /* ignoreSlop */);
            }
            if (mNoIntercept) {
                return false;
            }
            mDrawerSwipeDetector.onTouchEvent(ev);
            return mDrawerSwipeDetector.isDraggingOrSettling();
        }

        @Override
        public void onDragStart(boolean start, float startDisplacement) {
            mContainer.showAppDrawer(!mContainer.isAppDrawerShown());
        }

        @Override
        public boolean onDrag(float displacement) {
            return true;
        }

        @Override
        public void onDragEnd(float velocity) {
            mDrawerSwipeDetector.finishedScrolling();
        }

        private boolean usingTwoFingerSwipeOnConnectedDisplay(MotionEvent ev) {
            return ev.getClassification() == CLASSIFICATION_TWO_FINGER_SWIPE
                    && mContainer.getSecondaryDisplayDelegate().enableTaskbarConnectedDisplays();
        }
    }

    /**
     * Drop target to unpin apps dragged from the pinned apps grid
     */
    private class RemoveDropTarget implements DropTarget, DragController.DragListener {

        @Override
        public boolean isDropEnabled() {
            return mRemoveTarget.getVisibility() == VISIBLE;
        }

        @Override
        public void onDrop(DragObject dragObject, DragOptions options) {
            mPinnedAppsAdapter.unpinApp(dragObject.dragInfo);
            // Let the drag end right away, there is no drop animation
            dragObject.deferDragViewCleanupPostAnimation = false;
        }

        @Override
        public void onDragEnter(DragObject dragObject) {
            mRemoveTarget.setSelected(true);
        }

        @Override
        public void onDragOver(DragObject dragObject) { }

        @Override
        public void onDragExit(DragObject dragObject) {
            mRemoveTarget.setSelected(false);
        }

        @Override
        public boolean acceptDrop(DragObject dragObject) {
            return true;
        }

        @Override
        public void prepareAccessibilityDrop() { }

        @Override
        public void getHitRectRelativeToDragLayer(Rect outRect) {
            // Accept drops across the whole width above the bottom of the target
            outRect.set(0, 0, getWidth(), mRemoveTarget.getBottom());
        }

        @Override
        public View getDropView() {
            return null;
        }

        @Override
        public void onDragStart(DragObject dragObject, DragOptions options) {
            if (dragObject.dragSource == mWorkspaceDragSource) {
                mRemoveTarget.setVisibility(VISIBLE);
            }
        }

        @Override
        public void onDragEnd() {
            mRemoveTarget.setVisibility(INVISIBLE);
            mRemoveTarget.setSelected(false);
            mPinnedAppsAdapter.clearDragPreview();
        }
    }

    public PinnedAppsAdapter getPinnedAppsAdapter() {
        return mPinnedAppsAdapter;
    }

    /**
     * Shows the dragged app in the cell of the pinned apps grid it is being dragged over
     */
    void onDragOverWorkspace(DropTarget.DragObject dragObject) {
        float[] point = new float[]{dragObject.x, dragObject.y};
        mapCoordInSelfToDescendant(mWorkspace, point);
        // Empty cells are invisible, so pointToPosition() would skip them
        Rect cellRect = new Rect();
        for (int i = 0; i < mWorkspace.getChildCount(); i++) {
            mWorkspace.getChildAt(i).getHitRect(cellRect);
            if (cellRect.contains((int) point[0], (int) point[1])) {
                mPinnedAppsAdapter.setDragPreview(dragObject.dragInfo,
                        mWorkspace.getFirstVisiblePosition() + i);
                return;
            }
        }
    }

    boolean onIconLongClicked(View v) {
        if (!(v instanceof BubbleTextView)) {
            return false;
        }
        if (PopupContainer.getOpen(mContainer) != null) {
            // There is already an items container open, so don't open this one.
            v.clearFocus();
            return false;
        }
        ItemInfo item = (ItemInfo) v.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return false;
        }
        PopupDataProvider popupDataProvider =
                mContainer.getActivityComponent().getPopupDataProvider();

        // order of this list will reflect in the popup
        List<SystemShortcut<?>> systemShortcuts = new ArrayList<>();
        systemShortcuts.add(APP_INFO.getShortcut(mContainer, item, v));
        // App drawer icons are pinned by dragging them to the home screen
        boolean fromAppDrawer = mContainer.isAppDrawerShown();
        if (!fromAppDrawer) {
            systemShortcuts.add(mPinnedAppsAdapter.getSystemShortcut(item, v));
        }
        int deepShortcutCount = popupDataProvider.getShortcutCountForItem(item);
        final PopupContainerWithArrow<SecondaryDisplayLauncher> container =
                PopupContainerWithArrow.create(
                        /* context */ mContainer,
                        /* originalView */ v,
                        /* itemInfo */ item,
                        /* updateIconUi */ false
                );
        container.populateAndShowRows(deepShortcutCount,
                systemShortcuts);
        container.requestFocus();

        DragOptions options = new DragOptions();
        DragSource source;
        if (fromAppDrawer) {
            DeviceProfile grid = mContainer.getDeviceProfile();
            options.intrinsicIconScaleFactor = (float) grid.getAllAppsProfile().getIconSizePx()
                    / grid.getWorkspaceProfile().getIconSizePx();
            source = mContainer.getAppsView();
        } else {
            source = mWorkspaceDragSource;
        }
        options.preDragCondition = container.createPreDragCondition();
        if (options.preDragCondition == null) {
            options.preDragCondition = new DragOptions.PreDragCondition() {
                private DragView mDragView;

                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return mDragView != null && mDragView.isScaleAnimationFinished();
                }

                @Override
                public void onPreDragStart(DropTarget.DragObject dragObject) {
                    mDragView = dragObject.dragView;
                    if (!shouldStartDrag(0)) {
                        mDragView.setOnScaleAnimEndCallback(() ->
                                mContainer.beginDragShared(v, source, options));
                    }
                }

                @Override
                public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                    mDragView = null;
                }
            };
        }
        mContainer.beginDragShared(v, source, options);
        return true;
    }
}
