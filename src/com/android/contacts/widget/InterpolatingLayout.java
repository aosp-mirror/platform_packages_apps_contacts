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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.contacts.R;

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
        public int narrowMarginLeft;
        public int narrowPaddingLeft;
        public int narrowMarginRight;
        public int narrowPaddingRight;
        public int wideParentWidth;
        public int wideWidth;
        public int wideMarginLeft;
        public int widePaddingLeft;
        public int wideMarginRight;
        public int widePaddingRight;
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
            narrowMarginLeft = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowMarginLeft, -1);
            narrowPaddingLeft = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowPaddingLeft, -1);
            narrowMarginRight = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowMarginRight, -1);
            narrowPaddingRight = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_narrowPaddingRight, -1);
            wideParentWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideParentWidth, -1);
            wideWidth = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideWidth, -1);
            wideMarginLeft = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideMarginLeft, -1);
            widePaddingLeft = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_widePaddingLeft, -1);
            wideMarginRight = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_wideMarginRight, -1);
            widePaddingRight = a.getDimensionPixelSize(
                    R.styleable.InterpolatingLayout_Layout_layout_widePaddingRight, -1);

            a.recycle();

            if (narrowWidth != -1) {
                widthMultiplier = (float) (wideWidth - narrowWidth)
                        / (wideParentWidth - narrowParentWidth);
                widthConstant = (int) (narrowWidth - narrowParentWidth * widthMultiplier);
            }

            if (narrowMarginLeft != -1) {
                leftMarginMultiplier = (float) (wideMarginLeft - narrowMarginLeft)
                        / (wideParentWidth - narrowParentWidth);
                leftMarginConstant = (int) (narrowMarginLeft - narrowParentWidth
                        * leftMarginMultiplier);
            }

            if (narrowPaddingLeft != -1) {
                leftPaddingMultiplier = (float) (widePaddingLeft - narrowPaddingLeft)
                        / (wideParentWidth - narrowParentWidth);
                leftPaddingConstant = (int) (narrowPaddingLeft - narrowParentWidth
                        * leftPaddingMultiplier);
            }

            if (narrowMarginRight != -1) {
                rightMarginMultiplier = (float) (wideMarginRight - narrowMarginRight)
                        / (wideParentWidth - narrowParentWidth);
                rightMarginConstant = (int) (narrowMarginRight - narrowParentWidth
                        * rightMarginMultiplier);
            }

            if (narrowPaddingRight != -1) {
                rightPaddingMultiplier = (float) (widePaddingRight - narrowPaddingRight)
                        / (wideParentWidth - narrowParentWidth);
                rightPaddingConstant = (int) (narrowPaddingRight - narrowParentWidth
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
            if (narrowMarginLeft == -1) {
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
            if (narrowMarginRight == -1) {
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
                                parentHeight - params.topMargin - params.bottomMargin,
                                MeasureSpec.EXACTLY);
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

            if (child.getVisibility() == View.GONE) {
                continue;
            }

            LayoutParams params = (LayoutParams) child.getLayoutParams();
            int gravity = params.gravity;
            if (gravity == -1) {
                gravity = Gravity.START | Gravity.TOP;
            }

            if (params.narrowPaddingLeft != -1 || params.narrowPaddingRight != -1) {
                int leftPadding = params.narrowPaddingLeft == -1 ? child.getPaddingLeft()
                        : params.resolveLeftPadding(width);
                int rightPadding = params.narrowPaddingRight == -1 ? child.getPaddingRight()
                        : params.resolveRightPadding(width);
                child.setPadding(
                        leftPadding, child.getPaddingTop(), rightPadding, child.getPaddingBottom());
            }

            int leftMargin = params.resolveLeftMargin(width);
            int rightMargin = params.resolveRightMargin(width);

            mInRect.set(offset + leftMargin, params.topMargin,
                    right - rightMargin, bottom - params.bottomMargin);

            Gravity.apply(gravity, child.getMeasuredWidth(), child.getMeasuredHeight(),
                    mInRect, mOutRect);
            child.layout(mOutRect.left, mOutRect.top, mOutRect.right, mOutRect.bottom);

            offset = mOutRect.right + rightMargin;
        }
    }
}
