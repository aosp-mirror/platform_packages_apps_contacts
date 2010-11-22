/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.quickcontact;

import com.android.contacts.R;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Drawable that draws three pictures for the QuickContact-Background. ColorFilter is ignored
 */
public class QuickContactBackgroundDrawable extends Drawable {
    private Drawable mLeftDrawable;
    private Drawable mMiddleDrawable;
    private Drawable mRightDrawable;
    private int mRequestedX = Integer.MIN_VALUE;
    private boolean mBoundsSet = false;
    private int mAlpha = -1;
    private int mBottomOverride = Integer.MIN_VALUE;

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        setChildAlpha();
    }

    /**
     * Overrides the bottom bounds. This is used for the animation when the QuickContact
     * expands/collapses options
     */
    public void setBottomOverride(int value) {
        mBottomOverride = value;
        setChildBounds();
        invalidateSelf();
    }

    public void clearBottomOverride() {
        mBottomOverride = Integer.MIN_VALUE;
        invalidateSelf();
        setChildBounds();
    }

    public float getBottomOverride() {
        return mBottomOverride;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void configure(Resources resources, boolean arrowUp, int requestedX) {
        mLeftDrawable = resources.getDrawable(arrowUp
                ? R.drawable.quickactions_arrowup_left_holo_light
                : R.drawable.quickactions_arrowdown_left_holo_light);
        mMiddleDrawable = resources.getDrawable(arrowUp
                ? R.drawable.quickactions_arrowup_middle_holo_light
                : R.drawable.quickactions_arrowdown_middle_holo_light);
        mRightDrawable = resources.getDrawable(arrowUp
                ? R.drawable.quickactions_arrowup_right_holo_light
                : R.drawable.quickactions_arrowdown_right_holo_light);

        mRequestedX = requestedX;

        setChildAlpha();
        setChildBounds();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mBoundsSet = true;
        setChildBounds();
    }

    private void setChildAlpha() {
        if (mAlpha == -1) return;

        if (mLeftDrawable != null) mLeftDrawable.setAlpha(mAlpha);
        if (mMiddleDrawable != null) mMiddleDrawable.setAlpha(mAlpha);
        if (mRightDrawable != null) mRightDrawable.setAlpha(mAlpha);
    }

    private void setChildBounds() {
        if (mRequestedX == Integer.MIN_VALUE) return;
        if (!mBoundsSet) return;

        final Rect bounds = getBounds();
        int middleLeft = mRequestedX - mMiddleDrawable.getIntrinsicWidth() / 2;
        int middleRight = mRequestedX + mMiddleDrawable.getIntrinsicWidth() / 2;

        // ensure left drawable is not smaller than its Intrinsic Width
        final int leftExtra =  (middleLeft - bounds.left) - mLeftDrawable.getIntrinsicWidth();
        if (leftExtra < 0) {
            middleLeft -= leftExtra;
            middleRight -= leftExtra;
        }

        // ensure right drawable is not smaller than its Intrinsic Width
        final int rightExtra =  (bounds.right - middleRight) - mRightDrawable.getIntrinsicWidth();
        if (rightExtra < 0) {
            middleLeft += rightExtra;
            middleRight += rightExtra;
        }

        final int bottom = mBottomOverride == Integer.MIN_VALUE ? bounds.bottom : mBottomOverride;
        mLeftDrawable.setBounds(bounds.left, bounds.top, middleLeft, bottom);
        mMiddleDrawable.setBounds(middleLeft, bounds.top, middleRight, bottom);
        mRightDrawable.setBounds(middleRight, bounds.top, bounds.right, bottom);
    }

    @Override
    public void draw(Canvas canvas) {
        mLeftDrawable.draw(canvas);
        mMiddleDrawable.draw(canvas);
        mRightDrawable.draw(canvas);
    }
}
