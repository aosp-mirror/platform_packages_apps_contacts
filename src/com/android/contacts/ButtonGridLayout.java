/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.contacts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewGroup;

/**
 * Create a 4x3 grid of dial buttons.
 *
 * It was easier and more efficient to do it this way than use
 * standard layouts. It's perfectly fine (and actually encouraged) to
 * use custom layouts rather than piling up standard layouts.
 *
 * The horizontal and vertical spacings between buttons are controlled
 * by the amount of padding (attributes on the ButtonGridLayout element):
 *   - horizontal = left + right padding and
 *   - vertical = top + bottom padding.
 *
 * This class assumes that all the buttons have the same size.
 * The buttons will be bottom aligned in their view on layout.
 *
 * Invocation: onMeasure is called first by the framework to know our
 * size. Then onLayout is invoked to layout the buttons.
 */
// TODO: Blindly layout the buttons w/o checking if we overrun the
// bottom-right corner.
public class ButtonGridLayout extends ViewGroup {
    private final int COLUMNS = 3;
    private final int ROWS = 4;

    // Width and height of a button
    private int mButtonWidth;
    private int mButtonHeight;

    // Width and height of a button + padding.
    private int mWidthInc;
    private int mHeightInc;

    // Height of the dialpad. Used to align it at the bottom of the
    // view.
    private int mHeight;

    public ButtonGridLayout(Context context) {
        super(context);
    }

    public ButtonGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonGridLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int i = 0;
        // The last row is bottom aligned.
        int y = (b - t) - mHeight + mPaddingTop;
        for (int row = 0; row < ROWS; row++) {
            int x = mPaddingLeft;
            for (int col = 0; col < COLUMNS; col++) {
                View child = getChildAt(i);

                child.layout(x, y, x + mButtonWidth, y + mButtonHeight);

                x += mWidthInc;
                i++;
            }
            y += mHeightInc;
        }
      }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Measure the first child and get it's size
        View child = getChildAt(0);
        child.measure(MeasureSpec.UNSPECIFIED , MeasureSpec.UNSPECIFIED);

        // Make sure the other children are measured as well, to initialize
        for (int i = 1; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.UNSPECIFIED , MeasureSpec.UNSPECIFIED);
        }

        // Store these to be reused in onLayout.
        mButtonWidth = child.getMeasuredWidth();
        mButtonHeight = child.getMeasuredHeight();
        mWidthInc = mButtonWidth + mPaddingLeft + mPaddingRight;
        mHeightInc = mButtonHeight + mPaddingTop + mPaddingBottom;
        mHeight = ROWS * mHeightInc;

        final int width = resolveSize(COLUMNS * mWidthInc, widthMeasureSpec);
        final int height = resolveSize(mHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }
}
