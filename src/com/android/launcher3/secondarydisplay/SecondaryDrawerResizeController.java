/*
 * Copyright (C) 2026 The LineageOS Project
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

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Touch resize controller for the native secondary all-apps panel. */
final class SecondaryDrawerResizeController {
    private final State mState;

    SecondaryDrawerResizeController(ViewGroup parent, View drawer) {
        mState = new State(parent, drawer);
    }

    void attach() {
        mState.loadPreferences();
        mState.parent.getOverlay().add(mState.grip);
        mState.parent.requestLayout();
    }
    void detach() { mState.parent.getOverlay().remove(mState.grip); }
    float getWidthRatio() { return mState.widthRatio; }
    float getHeightRatio() { return mState.heightRatio; }
    void layout(Rect availableBounds) {
        mState.availableBounds.set(availableBounds);
        mState.positionGrip();
    }
    boolean touch(MotionEvent event) {
        return mState.drawer.isShown() && mState.touch(event);
    }
    void resizeBy(float delta) {
        mState.widthRatio = clamp(mState.widthRatio + delta, .50f, 1f);
        mState.heightRatio = clamp(mState.heightRatio + delta, .45f, 1f);
        mState.save();
    }
    void reset() {
        mState.widthRatio = .94f;
        mState.heightRatio = .88f;
        mState.save();
    }

    private static final class State {
        final ViewGroup parent;
        final View drawer;
        SharedPreferences preferences;
        final Grip grip = new Grip();
        final float density;
        final Rect availableBounds = new Rect();
        float widthRatio = .94f, heightRatio = .88f;
        boolean dragging;
        float startX, startY, startWidth, startHeight;
        float originalWidthRatio, originalHeightRatio;

        State(ViewGroup parent, View drawer) {
            this.parent = parent;
            this.drawer = drawer;
            density = parent.getResources().getDisplayMetrics().density;
        }

        void loadPreferences() {
            // The activity context may still report the default display during
            // inflation. Read the attached view's display before selecting a key.
            android.view.Display display = parent.getDisplay();
            if (display == null) {
                return;
            }
            preferences = parent.getContext().getSharedPreferences(
                    "secondary_drawer_display_" + display.getDisplayId(), 0);
            widthRatio = clamp(preferences.getFloat("width_ratio", .94f), .50f, 1f);
            heightRatio = clamp(preferences.getFloat("height_ratio", .88f), .45f, 1f);
        }

        void positionGrip() {
            grip.setVisible(drawer.isShown(), false);
            int size = Math.round(38 * density);
            int inset = Math.round(2 * density);
            grip.setBounds(drawer.getLeft() + inset, drawer.getTop() + inset,
                    drawer.getLeft() + inset + size, drawer.getTop() + inset + size);
            parent.invalidate();
        }

        boolean touch(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                Rect bounds = new Rect(grip.getBounds());
                int extra = Math.round(8 * density);
                bounds.inset(-extra, -extra);
                if (!bounds.contains(Math.round(event.getX()), Math.round(event.getY()))) return false;
                dragging = true;
                startX = event.getX(); startY = event.getY();
                startWidth = drawer.getWidth(); startHeight = drawer.getHeight();
                originalWidthRatio = widthRatio;
                originalHeightRatio = heightRatio;
                if (parent.getParent() != null) {
                    parent.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            if (!dragging) return false;
            if (action == MotionEvent.ACTION_MOVE) {
                float availableWidth = Math.max(1, availableBounds.width());
                float availableHeight = Math.max(1, availableBounds.height());
                widthRatio = clamp((startWidth - event.getX() + startX) / availableWidth, .50f, 1f);
                heightRatio = clamp((startHeight - event.getY() + startY) / availableHeight, .45f, 1f);
                parent.requestLayout();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                dragging = false;
                if (parent.getParent() != null) {
                    parent.getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    widthRatio = originalWidthRatio;
                    heightRatio = originalHeightRatio;
                    parent.requestLayout();
                } else {
                    save();
                }
                return true;
            }
            return true;
        }

        void save() {
            if (preferences != null) {
                preferences.edit().putFloat("width_ratio", widthRatio)
                        .putFloat("height_ratio", heightRatio).apply();
            }
            parent.requestLayout();
        }
    }

    private static float clamp(float value, float low, float high) {
        if (!Float.isFinite(value)) return high;
        return Math.max(low, Math.min(high, value));
    }

    /** Subtle diagonal corner grip; the native panel remains responsible for its appearance. */
    private static final class Grip extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float unit = bounds.width() / 38f;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2 * unit);
            paint.setColor(0xffaeb9bb);
            canvas.drawLine(bounds.left + 10 * unit, bounds.top + 25 * unit,
                    bounds.left + 25 * unit, bounds.top + 10 * unit, paint);
            canvas.drawLine(bounds.left + 10 * unit, bounds.top + 17 * unit,
                    bounds.left + 10 * unit, bounds.top + 10 * unit, paint);
            canvas.drawLine(bounds.left + 10 * unit, bounds.top + 10 * unit,
                    bounds.left + 17 * unit, bounds.top + 10 * unit, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
