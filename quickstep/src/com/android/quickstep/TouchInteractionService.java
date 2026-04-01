/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.inputdevice;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.util.concurrent.CountDownLatch;

/**
 * Service that handles touch interactions for recents animation.
 *
 * This service manages the input consumer for the recents animation and ensures
 * proper cleanup when the animation ends normally or is cancelled.
 */
public class TouchInteractionService {

    private static final String TAG = "TouchInteractionService";
    private static final boolean DEBUG = true;

    private static final int MSG_START_RECENTS = 1;
    private static final int MSG_END_RECENTS = 2;
    private static final int MSG_CANCEL_RECENTS = 3;

    private static final String INPUT_CONSUMER_NAME = "recents_animation_input_consumer";

    private final InputMonitor mInputMonitor;
    private final Handler mHandler;

    private boolean mIsRecentsAnimationRunning = false;
    private InputChannel mRecentsInputChannel;

    /**
     * Creates a new TouchInteractionService.
     *
     * @param inputMonitor The input monitor for registering consumers
     */
    public TouchInteractionService(InputMonitor inputMonitor) {
        mInputMonitor = inputMonitor;
        mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START_RECENTS:
                        handleStartRecents();
                        return true;
                    case MSG_END_RECENTS:
                        handleEndRecents((boolean) msg.obj);
                        return true;
                    case MSG_CANCEL_RECENTS:
                        handleCancelRecents();
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Starts the recents animation with the given input channel.
     *
     * @param inputChannel The input channel for the recents animation
     */
    public void startRecentsAnimation(InputChannel inputChannel) {
        if (DEBUG) {
            Log.d(TAG, "Starting recents animation");
        }

        if (mIsRecentsAnimationRunning) {
            Log.w(TAG, "Animation already running, will replace channel");
        }

        mRecentsInputChannel = inputChannel;
        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECENTS));
    }

    /**
     * Ends the recents animation.
     *
     * CRITICAL FIX: This method now properly calls unregisterInputConsumer()
     * when the recents animation ends normally. Previously, the input consumer
     * was never unregistered, causing the window to persist in memory.
     *
     * @param startedFromAlternateEntry Whether the animation started from alternate entry
     */
    public void endRecentsAnimation(boolean startedFromAlternateEntry) {
        if (DEBUG) {
            Log.d(TAG, "Ending recents animation, startedFromAlternateEntry: "
                    + startedFromAlternateEntry);
        }

        if (!mIsRecentsAnimationRunning) {
            if (DEBUG) {
                Log.w(TAG, "endRecentsAnimation called but animation not running");
            }
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_END_RECENTS, startedFromAlternateEntry));
    }

    /**
     * Cancels the recents animation.
     *
     * This handles edge cases like gesture cancellation and screen rotation.
     */
    public void cancelRecentsAnimation() {
        if (DEBUG) {
            Log.d(TAG, "Cancelling recents animation");
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_CANCEL_RECENTS));
    }

    /**
     * Handles starting the recents animation on the main thread.
     */
    private void handleStartRecents() {
        if (mRecentsInputChannel == null) {
            Log.e(TAG, "Cannot start recents animation: input channel is null");
            return;
        }

        // CRITICAL: Check if animation already running
        if (mIsRecentsAnimationRunning) {
            Log.w(TAG, "Recents animation already running, cleaning up first");
            // Clean up existing animation before starting new one
            mInputMonitor.unregisterInputConsumer(INPUT_CONSUMER_NAME);
        }

        mIsRecentsAnimationRunning = true;

        // Register the input consumer for recents animation
        mInputMonitor.registerInputConsumer(INPUT_CONSUMER_NAME, mRecentsInputChannel);

        if (DEBUG) {
            Log.d(TAG, "Registered recents input consumer: " + INPUT_CONSUMER_NAME);
            Log.d(TAG, "Current input consumers: " + mInputMonitor.getInputConsumerCount());
        }
    }

    /**
     * Handles ending the recents animation on the main thread.
     *
     * CRITICAL FIX: This now properly calls unregisterInputConsumer() to clean up
     * the recents_animation_input_consumer window. This fixes the memory leak where
     * the window was not properly cleaned up when the recents animation ended normally.
     *
     * @param startedFromAlternateEntry Whether the animation started from alternate entry
     */
    private void handleEndRecents(boolean startedFromAlternateEntry) {
        if (DEBUG) {
            Log.d(TAG, "Handling end recents, startedFromAlternateEntry: "
                    + startedFromAlternateEntry);
            Log.d(TAG, "Input consumers before cleanup: "
                    + mInputMonitor.getInputConsumerCount());
        }

        mIsRecentsAnimationRunning = false;

        // CRITICAL FIX: Explicitly unregister the input consumer when animation ends normally
        // This ensures the recents_animation_input_consumer window is properly cleaned up
        // instead of being left in the mInputConsumers list (which caused the window to persist)
        try {
            mInputMonitor.unregisterInputConsumer(INPUT_CONSUMER_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering input consumer", e);
        }

        if (DEBUG) {
            Log.d(TAG, "Unregistered recents input consumer: " + INPUT_CONSUMER_NAME);
            Log.d(TAG, "Input consumers after cleanup: "
                    + mInputMonitor.getInputConsumerCount());
        }

        mRecentsInputChannel = null;
    }

    /**
     * Handles cancelling the recents animation.
     *
     * This ensures proper cleanup in edge cases like:
     * - Rapid app switching
     * - Gesture cancellation
     * - Screen rotation
     */
    private void handleCancelRecents() {
        if (DEBUG) {
            Log.d(TAG, "Handling cancel recents");
            Log.d(TAG, "Input consumers before cancellation cleanup: "
                    + mInputMonitor.getInputConsumerCount());
        }

        // Same cleanup as endRecents - ensure input consumer is removed
        try {
            mInputMonitor.unregisterInputConsumer(INPUT_CONSUMER_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering input consumer", e);
        }

        // Also reset all other input consumers to ensure clean state
        mInputMonitor.resetInputConsumers("recents_cancelled");

        mIsRecentsAnimationRunning = false;
        mRecentsInputChannel = null;

        if (DEBUG) {
            Log.d(TAG, "Cancelled recents, input consumers after cleanup: "
                    + mInputMonitor.getInputConsumerCount());
        }
    }

    /**
     * Handles edge case: rapid app switching.
     *
     * When user rapidly switches apps, the animation may be started before
     * the previous one finishes. This ensures proper cleanup.
     */
    public void handleRapidSwitch() {
        if (DEBUG) {
            Log.d(TAG, "Handling rapid app switch");
        }

        // Cancel existing animation via handler (already does this correctly)
        if (mIsRecentsAnimationRunning) {
            cancelRecentsAnimation();
        }

        // Start new animation via handler (not direct call!)
        // The channel will be registered when handleStartRecents processes
        // We need to re-send the start message since we cancelled
        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECENTS));
    }

    /**
     * Handles edge case: screen rotation.
     *
     * When screen rotates during recents animation, we need to ensure
     * the input consumer is properly cleaned up.
     */
    public void handleScreenRotation() {
        if (DEBUG) {
            Log.d(TAG, "Handling screen rotation during recents animation");
        }

        // Cancel the animation and clean up
        if (mIsRecentsAnimationRunning) {
            cancelRecentsAnimation();
        }
    }

    /**
     * Checks if the recents animation is currently running.
     */
    public boolean isRecentsAnimationRunning() {
        return mIsRecentsAnimationRunning;
    }

    /**
     * Gets the current number of input consumers (for debugging).
     */
    public int getInputConsumerCount() {
        return mInputMonitor.getInputConsumerCount();
    }

    /**
     * Dumps the state for debugging.
     */
    public void dumpState(StringBuilder sb) {
        sb.append("  mIsRecentsAnimationRunning: ").append(mIsRecentsAnimationRunning).append("\n");
        sb.append("  Input consumer count: ").append(mInputMonitor.getInputConsumerCount()).append("\n");
        sb.append("  Registered consumers: ");
        String[] names = mInputMonitor.getRegisteredConsumerNames();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(names[i]);
        }
        sb.append("\n");
    }
}
