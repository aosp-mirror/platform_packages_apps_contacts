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
 * Background {@link Drawable} for {@link QuickContactWindow} that draws arrow
 * centered around requested position.
 */
public class QuickContactBackgroundDrawable extends Drawable {
    private Drawable mLeftDrawable;
    private Drawable mMiddleDrawable;
    private Drawable mRightDrawable;

    private int mBottomOverride = Integer.MIN_VALUE;

    public QuickContactBackgroundDrawable(Resources res) {
        mLeftDrawable = res.getDrawable(R.drawable.quickactions_arrow_left_holo_light);
        mMiddleDrawable = res.getDrawable(R.drawable.quickactions_arrow_middle_holo_light);
        mRightDrawable = res.getDrawable(R.drawable.quickactions_arrow_right_holo_light);
    }

    @Override
    public void setAlpha(int alpha) {
        mLeftDrawable.setAlpha(alpha);
        mMiddleDrawable.setAlpha(alpha);
        mRightDrawable.setAlpha(alpha);
    }

    /**
     * Overrides the bottom bounds. This is used for the animation when the
     * QuickContact expands/collapses options
     */
    public void setBottomOverride(int value) {
        mBottomOverride = value;
        onBoundsChange(getBounds());
        invalidateSelf();
    }

    public void clearBottomOverride() {
        mBottomOverride = Integer.MIN_VALUE;
        onBoundsChange(getBounds());
        invalidateSelf();
    }

    public float getBottomOverride() {
        return mBottomOverride;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        super.onStateChange(state);
        mLeftDrawable.setState(state);
        mMiddleDrawable.setState(state);
        mRightDrawable.setState(state);
        return true;
    }

    @Override
    protected boolean onLevelChange(int level) {
        return true;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mLeftDrawable.setColorFilter(cf);
        mMiddleDrawable.setColorFilter(cf);
        mRightDrawable.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        final int requestedX = getLevel();

        int middleLeft = requestedX - mMiddleDrawable.getIntrinsicWidth() / 2;
        int middleRight = requestedX + mMiddleDrawable.getIntrinsicWidth() / 2;

        // ensure left drawable is not smaller than its Intrinsic Width
        final int leftExtra = (middleLeft - bounds.left) - mLeftDrawable.getIntrinsicWidth();
        if (leftExtra < 0) {
            middleLeft -= leftExtra;
            middleRight -= leftExtra;
        }

        // ensure right drawable is not smaller than its Intrinsic Width
        final int rightExtra = (bounds.right - middleRight) - mRightDrawable.getIntrinsicWidth();
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
