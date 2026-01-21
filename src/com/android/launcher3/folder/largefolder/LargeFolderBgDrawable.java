package com.android.launcher3.folder.largefolder;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;

import com.android.launcher3.util.RectProperty;

/**
 * Created by ch.hu
 * Date: 7/3/25 11:29
 * Description:
 */
public class LargeFolderBgDrawable extends ColorDrawable implements RectProperty {

    private RectF mCanvasBounds;
    private final Paint mPaint;
    private int mRadius;
    private Rect mRect;

    public LargeFolderBgDrawable(int color, int radius) {
        super(color);
        mRect = new Rect();
        mCanvasBounds = new RectF(0.0f, 0.0f, 0.0f, 0.0f);
        mRadius = radius;
        mPaint = new Paint(1);
        mPaint.setColor(getColor());
    }

    @Override
    public void setColor(int color) {
        super.setColor(color);
        if (mPaint != null) {
            mPaint.setColor(getColor());
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mCanvasBounds.width() == 0.0f && mCanvasBounds.height() == 0.0f) {
            mCanvasBounds.set(getBounds());
        }
        mPaint.setAlpha(getAlpha());
        canvas.drawRoundRect(mCanvasBounds, mRadius, mRadius, mPaint);
    }

    @Override
    public void setRect(Rect rect) {
        mRect.set(rect);
        mCanvasBounds.set(rect);
        invalidateSelf();
    }

    @Override
    public Rect getRect() {
        return new Rect(mRect);
    }
}