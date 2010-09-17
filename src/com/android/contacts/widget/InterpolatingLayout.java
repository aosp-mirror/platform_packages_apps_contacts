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

package com.android.contacts.widget;

import com.android.contacts.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Layout similar to LinearLayout that allows a child to specify examples of
 * desired size depending on the parent size. For example if the widget wants to
 * be 100dip when parent is 200dip and 110dip when parent is 400dip, the layout
 * will ensure these requirements and interpolate for other parent sizes.
 * You can also specify minWidth for each child.  You can have at most one
 * child with layout_width="match_parent" - it will take the entire remaining
 * space.
 */
public class InterpolatingLayout extends ViewGroup {

    public InterpolatingLayout(Context context) {
        super(context);
    }

    public InterpolatingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InterpolatingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public final static class LayoutParams extends ViewGroup.MarginLayoutParams {

        public int narrowParentWidth;
        public int narrowWidth;
        public int narrowLeftMargin;
        public int narrowRightMargin;
        public int wideParentWidth;
        public int wideWidth;
        public int wideLeftMargin;
        public int wideRightMargin;
        private float widthMultiplier;
        private int widthConstant;
        private float leftMarginMultiplier;
        private int leftMarginConstant;
        private float rightMarginMultiplier;
        private int rightMarginConstant;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.InterpolatingLayout_Layout);

            narrowParentWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowParentWidth, -1);
            narrowWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowWidth, -1);
            narrowLeftMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowLeftMargin, -1);
            narrowRightMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowRightMargin, -1);
            wideParentWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideParentWidth, -1);
            wideWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideWidth, -1);
            wideLeftMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideLeftMargin, -1);
            wideRightMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideRightMargin, -1);

            a.recycle();

            if (narrowWidth != -1) {
                widthMultiplier = (float) (wideWidth - narrowWidth)
                        / (wideParentWidth - narrowParentWidth);
                widthConstant = (int) (narrowWidth - narrowParentWidth * widthMultiplier);
            }

            if (narrowLeftMargin != -1) {
                leftMarginMultiplier = (float) (wideLeftMargin - narrowLeftMargin)
                        / (wideParentWidth - narrowParentWidth);
                leftMarginConstant = (int) (narrowLeftMargin - narrowParentWidth
                        * leftMarginMultiplier);
            }

            if (narrowRightMargin != -1) {
                rightMarginMultiplier = (float) (wideRightMargin - narrowRightMargin)
                        / (wideParentWidth - narrowParentWidth);
                rightMarginConstant = (int) (narrowRightMargin - narrowParentWidth
                        * rightMarginMultiplier);
            }
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public int resolveWidth(int parentSize) {
            if (narrowWidth == -1) {
                return width;
            } else {
                int w = (int) (parentSize * widthMultiplier) + widthConstant;
                return w <= 0 ? WRAP_CONTENT : w;
            }
        }

        public int resolveLeftMargin(int parentSize) {
            if (narrowLeftMargin == -1) {
                return leftMargin;
            } else {
                int w = (int) (parentSize * leftMarginMultiplier) + leftMarginConstant;
                return w < 0 ? 0 : w;
            }
        }

        public int resolveRightMargin(int parentSize) {
            if (narrowRightMargin == -1) {
                return rightMargin;
            } else {
                int w = (int) (parentSize * rightMarginMultiplier) + rightMarginConstant;
                return w < 0 ? 0 : w;
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int parentSize = MeasureSpec.getSize(widthMeasureSpec);

        int width = 0;
        int height = 0;

        View fillChild = null;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }

            LayoutParams params = (LayoutParams) child.getLayoutParams();

            if (params.width == LayoutParams.MATCH_PARENT) {
                if (fillChild != null) {
                    throw new RuntimeException(
                            "Interpolating layout allows at most one child"
                            + " with layout_width='match_parent'");
                }
                fillChild = child;
            } else {
                int childWidth = params.resolveWidth(parentSize);
                int childMeasureSpec = (childWidth == LayoutParams.WRAP_CONTENT)
                        ? MeasureSpec.UNSPECIFIED
                        : MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                child.measure(childMeasureSpec, heightMeasureSpec);
                width += child.getMeasuredWidth();
                height = Math.max(child.getMeasuredHeight(), height);
            }

            width += params.resolveLeftMargin(parentSize) + params.resolveRightMargin(parentSize);
        }

        if (fillChild != null) {
            int remainder = parentSize - width;
            int childMeasureSpec = remainder > 0
                    ? MeasureSpec.makeMeasureSpec(remainder, MeasureSpec.EXACTLY)
                    : MeasureSpec.UNSPECIFIED;
            fillChild.measure(childMeasureSpec, heightMeasureSpec);
            width += fillChild.getMeasuredWidth();
            height = Math.max(fillChild.getMeasuredHeight(), height);
        }

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int offset = 0;
        int width = right - left;
        int height = bottom - top;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);

            LayoutParams params = (LayoutParams) child.getLayoutParams();
            offset += params.resolveLeftMargin(width);
            int childWidth = child.getMeasuredWidth();
            int childTop = params.topMargin;
            child.layout(offset, params.topMargin, offset + childWidth,
                    params.topMargin + child.getMeasuredHeight());
            offset += childWidth + params.resolveRightMargin(width);
        }
    }
}
