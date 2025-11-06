/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.launcher3.Flags.refactorTaskbarUiState;

import android.content.Context;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.taskbar.TaskbarApiProxy;
import com.android.launcher3.taskbar.TaskbarUiState;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.wm.shell.Flags;

/**
 * Listens for touch events on the bubble bar.
 */
// TODO(b/385928447): remove debug logs with Log.d
public class BubbleBarInputConsumer implements InputConsumer {

    private static final String TAG = "BubbleBarInputConsumer";

    private final TaskbarApiProxy mTaskbarApiProxy;
    private final TaskbarUiState mTaskbarUiState;
    private final InputMonitorCompat mInputMonitorCompat;

    private volatile boolean mPilfered;
    private boolean mPassedTouchSlop;
    private boolean mStashedOrCollapsedOnDown;

    private final int mTouchSlop;
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();

    private final int mDisplayId;

    private long mDownTime;
    private final long mTimeForLongPress;
    private int mActivePointerId = INVALID_POINTER_ID;

    public BubbleBarInputConsumer(
            Context context,
            TaskbarApiProxy taskbarApiProxy,
            int displayId,
            InputMonitorCompat inputMonitorCompat) {
        mTaskbarApiProxy = taskbarApiProxy;
        mTaskbarUiState = taskbarApiProxy.getTaskbarUiState();
        mDisplayId = displayId;

        mInputMonitorCompat = inputMonitorCompat;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTimeForLongPress = ViewConfiguration.getLongPressTimeout();
    }

    @Override
    public int getType() {
        return TYPE_BUBBLE_BAR;
    }

    @Override
    public int getDisplayId() {
        return mDisplayId;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mStashedOrCollapsedOnDown = isBubbleStashed() || isCollapsed();
                Log.d(TAG,
                        "ACTION_DOWN stashedOrCollapsed=" + mStashedOrCollapsedOnDown + " downPos="
                                + mDownPos);
                mTaskbarApiProxy.startBubbleBarSwipeController();
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    Log.d(TAG, "ACTION_MOVE skip, invalid pointer id");
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                float dX = mLastPos.x - mDownPos.x;
                float dY = mLastPos.y - mDownPos.y;
                if (!mPassedTouchSlop) {
                    mPassedTouchSlop = Math.abs(dY) > mTouchSlop || Math.abs(dX) > mTouchSlop;
                    if (mPassedTouchSlop) {
                        Log.d(TAG, "ACTION_MOVE passed touch slop pos=" + mLastPos);
                    }
                }
                mTaskbarApiProxy.swipeBubbleBarTo(dY, () -> {
                    if (!mPilfered && mTaskbarApiProxy.isBubbleBarSwipeGesture()) {
                        Log.d(TAG, "ACTION_MOVE swipe gesture, pilfering");
                        mPilfered = true;
                        // Bubbles is handling the swipe so make sure no one else gets it.
                        TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
                        mInputMonitorCompat.pilferPointers();
                    }
                });
                break;
            case MotionEvent.ACTION_UP:
                long tapTime = System.currentTimeMillis() - mDownTime;
                boolean swipeUpOnBubbleHandle = mTaskbarApiProxy.isBubbleBarSwipeGesture();
                // Anything less than a long-press is a tap
                boolean isWithinTapTime = tapTime <= mTimeForLongPress;
                Log.d(TAG, "ACTION_UP swipeUp=" + swipeUpOnBubbleHandle + " isInTapTime="
                        + isWithinTapTime + " tapTime=" + tapTime + " passedTouchSlop="
                        + mPassedTouchSlop + " stashedOrCollapsedOnDown="
                        + mStashedOrCollapsedOnDown);
                if (isWithinTapTime && !swipeUpOnBubbleHandle && !mPassedTouchSlop
                        && mStashedOrCollapsedOnDown) {
                    Log.d(TAG, "ACTION_UP showing bubble bar");
                    // Taps on the handle / collapsed state should open the bar
                    mTaskbarApiProxy.showBubbleBar(
                            /* expandBubbles= */ true, /* bubbleBarGesture= */ true);
                } else {
                    Log.d(TAG, "ACTION_UP nothing to do");
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d(TAG, "ACTION_CANCEL");
                break;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cleanupAfterMotionEvent();
        }
    }

    private void cleanupAfterMotionEvent() {
        Log.d(TAG, "cleaning up passedSlop=" + mPassedTouchSlop + " pilfered=" + mPilfered);
        mTaskbarApiProxy.finishBubbleBarSwipeController();
        mPassedTouchSlop = false;
        mPilfered = false;
        mDownTime = 0;
    }

    private boolean isCollapsed() {
        return isBubbleBarVisible() && !isBubbleBarExpanded();
    }

    /**
     * Returns whether the event is occurring on a visible bubble bar or the bar handle.
     */
    public static boolean isEventOnBubbles(TaskbarApiProxy taskbarApiProxy, MotionEvent ev) {
        if (taskbarApiProxy == null || !taskbarApiProxy.isBubbleBarEnabled()) {
            return false;
        }
        TaskbarUiState taskbarUiState = taskbarApiProxy.getTaskbarUiState();
        if (!taskbarUiState.getHasBubbles()) {
            return false;
        }
        if (taskbarUiState.isBubbleStashed()
                && taskbarApiProxy.isBubbleStashedHandleViewControllerPresent()) {
            return taskbarUiState.isEventOverBubbleBarStashedHandle(ev);
        } else if (taskbarUiState.isBubbleBarViewVisible()) {
            if (Flags.bugRotationButtonCoverBubble()) {
                return !taskbarUiState.isEventOnNavbar(ev)
                        && taskbarUiState.isEventOverBubbleBarView(ev);
            } else {
                return taskbarUiState.isEventOverBubbleBarView(ev);
            }
        }
        return false;
    }

    private boolean isBubbleStashed() {
        if (refactorTaskbarUiState()) {
            boolean ret = mTaskbarUiState.isBubbleStashed();
            if (BuildConfig.IS_STUDIO_BUILD && ret != legacyIsBubbleStashed()) {
                throw new IllegalStateException("isBubbleStashed() doesn't match ret=" + ret);
            }
            return ret;
        } else {
            return legacyIsBubbleStashed();
        }
    }

    @Deprecated
    private boolean legacyIsBubbleStashed() {
        return mTaskbarApiProxy.isBubbleBarStashed();
    }

    private boolean isBubbleBarVisible() {
        if (refactorTaskbarUiState()) {
            boolean ret = mTaskbarUiState.getHasBubbles() && !mTaskbarUiState.isBubbleStashed();
            if (BuildConfig.IS_STUDIO_BUILD && ret != legacyIsBubbleBarViewVisible()) {
                throw new IllegalStateException("isBubbleBarViewVisible() doesn't match ret="
                        + ret);
            }
            return ret;
        } else {
            return legacyIsBubbleBarViewVisible();
        }
    }

    @Deprecated
    private boolean legacyIsBubbleBarViewVisible() {
        return mTaskbarApiProxy.isBubbleBarVisible();
    }

    private boolean isBubbleBarExpanded() {
        if (refactorTaskbarUiState()) {
            boolean ret = mTaskbarUiState.isBubbleBarExpanded();
            if (BuildConfig.IS_STUDIO_BUILD && ret != legacyIsBubbleBarExpanded()) {
                throw new IllegalStateException("isBubbleBarExpanded() doesn't match ret=" + ret);
            }
            return ret;
        } else {
            return legacyIsBubbleBarExpanded();
        }
    }

    @Deprecated
    private boolean legacyIsBubbleBarExpanded() {
        return mTaskbarApiProxy.isBubbleBarExpanded();
    }
}
