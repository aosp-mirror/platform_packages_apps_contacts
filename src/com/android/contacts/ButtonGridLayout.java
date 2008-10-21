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
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;

public class ButtonGridLayout extends ViewGroup {

    private final int mColumns = 3;
    
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
        int y = mPaddingTop;
        final int rows = getRows();
        final View child0 = getChildAt(0);
        final int yInc = (getHeight() - mPaddingTop - mPaddingBottom) / rows;
        final int xInc = (getWidth() - mPaddingLeft - mPaddingRight) / mColumns;
        final int childWidth = child0.getMeasuredWidth();
        final int childHeight = child0.getMeasuredHeight();
        final int xOffset = (xInc - childWidth) / 2;
        final int yOffset = (yInc - childHeight) / 2;
        
        for (int row = 0; row < rows; row++) {
            int x = mPaddingLeft;
            for (int col = 0; col < mColumns; col++) {
                int cell = row * mColumns + col;
                if (cell >= getChildCount()) {
                    break;
                }
                View child = getChildAt(cell);
                child.layout(x + xOffset, y + yOffset, 
                        x + xOffset + childWidth, 
                        y + yOffset + childHeight);
                x += xInc;
            }
            y += yInc;
        }
    }

    private int getRows() {
        return (getChildCount() + mColumns - 1) / mColumns; 
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = mPaddingLeft + mPaddingRight;
        int height = mPaddingTop + mPaddingBottom;
        
        // Measure the first child and get it's size
        View child = getChildAt(0);
        child.measure(MeasureSpec.UNSPECIFIED , MeasureSpec.UNSPECIFIED);
        int childWidth = child.getMeasuredWidth();
        int childHeight = child.getMeasuredHeight();
        // Make sure the other children are measured as well, to initialize
        for (int i = 1; i < getChildCount(); i++) {
            getChildAt(0).measure(MeasureSpec.UNSPECIFIED , MeasureSpec.UNSPECIFIED);
        }
        // All cells are going to be the size of the first child
        width += mColumns * childWidth;
        height += getRows() * childHeight;
        
        width = resolveSize(width, widthMeasureSpec);
        height = resolveSize(height, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

}
