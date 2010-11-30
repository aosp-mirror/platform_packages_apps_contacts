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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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

    private Rect mInRect = new Rect();
    private Rect mOutRect = new Rect();

    public InterpolatingLayout(Context context) {
        super(context);
    }

    public InterpolatingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InterpolatingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public final static class LayoutParams extends LinearLayout.LayoutParams {

        public int narrowParentWidth;
        public int narrowWidth;
        public int narrowLeftMargin;
        public int narrowLeftPadding;
        public int narrowRightMargin;
        public int narrowRightPadding;
        public int wideParentWidth;
        public int wideWidth;
        public int wideLeftMargin;
        public int wideLeftPadding;
        public int wideRightMargin;
        public int wideRightPadding;
        private float widthMultiplier;
        private int widthConstant;
        private float leftMarginMultiplier;
        private int leftMarginConstant;
        private float leftPaddingMultiplier;
        private int leftPaddingConstant;
        private float rightMarginMultiplier;
        private int rightMarginConstant;
        private float rightPaddingMultiplier;
        private int rightPaddingConstant;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.InterpolatingLayout_Layout);

            narrowParentWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowParentWidth, -1);
            narrowWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowWidth, -1);
            narrowLeftMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowLeftMargin, -1);
            narrowLeftPadding = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowLeftPadding, -1);
            narrowRightMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowRightMargin, -1);
            narrowRightPadding = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowRightPadding, -1);
            wideParentWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideParentWidth, -1);
            wideWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideWidth, -1);
            wideLeftMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideLeftMargin, -1);
            wideLeftPadding = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideLeftPadding, -1);
            wideRightMargin = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideRightMargin, -1);
            wideRightPadding = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideRightPadding, -1);

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

            if (narrowLeftPadding != -1) {
                leftPaddingMultiplier = (float) (wideLeftPadding - narrowLeftPadding)
                        / (wideParentWidth - narrowParentWidth);
                leftPaddingConstant = (int) (narrowLeftPadding - narrowParentWidth
                        * leftPaddingMultiplier);
            }

            if (narrowRightMargin != -1) {
                rightMarginMultiplier = (float) (wideRightMargin - narrowRightMargin)
                        / (wideParentWidth - narrowParentWidth);
                rightMarginConstant = (int) (narrowRightMargin - narrowParentWidth
                        * rightMarginMultiplier);
            }

            if (narrowRightPadding != -1) {
                rightPaddingMultiplier = (float) (wideRightPadding - narrowRightPadding)
                        / (wideParentWidth - narrowParentWidth);
                rightPaddingConstant = (int) (narrowRightPadding - narrowParentWidth
                        * rightPaddingMultiplier);
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

        public int resolveLeftPadding(int parentSize) {
            int w = (int) (parentSize * leftPaddingMultiplier) + leftPaddingConstant;
            return w < 0 ? 0 : w;
        }

        public int resolveRightMargin(int parentSize) {
            if (narrowRightMargin == -1) {
                return rightMargin;
            } else {
                int w = (int) (parentSize * rightMarginMultiplier) + rightMarginConstant;
                return w < 0 ? 0 : w;
            }
        }

        public int resolveRightPadding(int parentSize) {
            int w = (int) (parentSize * rightPaddingMultiplier) + rightPaddingConstant;
            return w < 0 ? 0 : w;
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
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

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
                int childWidth = params.resolveWidth(parentWidth);
                int childWidthMeasureSpec;
                switch (childWidth) {
                    case LayoutParams.WRAP_CONTENT:
                        childWidthMeasureSpec = MeasureSpec.UNSPECIFIED;
                        break;
                    default:
                        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                                childWidth, MeasureSpec.EXACTLY);
                        break;
                }

                int childHeightMeasureSpec;
                switch (params.height) {
                    case LayoutParams.WRAP_CONTENT:
                        childHeightMeasureSpec = MeasureSpec.UNSPECIFIED;
                        break;
                    case LayoutParams.MATCH_PARENT:
                        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                                parentHeight, MeasureSpec.EXACTLY);
                        break;
                    default:
                        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                                params.height, MeasureSpec.EXACTLY);
                        break;
                }

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                width += child.getMeasuredWidth();
                height = Math.max(child.getMeasuredHeight(), height);
            }

            width += params.resolveLeftMargin(parentWidth) + params.resolveRightMargin(parentWidth);
        }

        if (fillChild != null) {
            int remainder = parentWidth - width;
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
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);

            LayoutParams params = (LayoutParams) child.getLayoutParams();
            int gravity = params.gravity;
            if (gravity == -1) {
                gravity = Gravity.LEFT | Gravity.TOP;
            }

            if (params.narrowLeftPadding != -1 || params.narrowRightPadding != -1) {
                int leftPadding = params.narrowLeftPadding == -1 ? child.getPaddingLeft()
                        : params.resolveLeftPadding(width);
                int rightPadding = params.narrowRightPadding == -1 ? child.getPaddingRight()
                        : params.resolveRightPadding(width);
                child.setPadding(
                        leftPadding, child.getPaddingTop(), rightPadding, child.getPaddingBottom());
            }

            int leftMargin = params.resolveLeftMargin(width);
            int rightMargin = params.resolveRightMargin(width);

            mInRect.set(offset + leftMargin, params.topMargin,
                    right - left - offset - rightMargin,
                    bottom - top - params.bottomMargin);

            Gravity.apply(gravity, child.getMeasuredWidth(), child.getMeasuredHeight(),
                    mInRect, mOutRect);
            child.layout(mOutRect.left, mOutRect.top, mOutRect.right, mOutRect.bottom);

            offset = mOutRect.right + rightMargin;
        }
    }
}
