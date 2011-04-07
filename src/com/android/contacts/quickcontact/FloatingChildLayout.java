/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

/**
 * Layout containing single child {@link View} which it attempts to center
 * around {@link #setChildTargetScreen(Rect)}.
 * <p>
 * Updates drawable state to be {@link android.R.attr#state_first} when child is
 * above target, and {@link android.R.attr#state_last} when child is below
 * target. Also updates {@link Drawable#setLevel(int)} on child
 * {@link View#getBackground()} to reflect horizontal center of target.
 * <p>
 * The reason for this approach is because target {@link Rect} is in screen
 * coordinates disregarding decor insets; otherwise something like
 * {@link PopupWindow} might work better.
 */
public class FloatingChildLayout extends FrameLayout {
    private static final String TAG = "FloatingChild";

    public FloatingChildLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private View mChild;

    private Rect mTargetScreen = new Rect();

    private int mCalloutState = 0;
    private int mCalloutLeft;

    @Override
    protected void onFinishInflate() {
        mChild = findViewById(android.R.id.content);
        mChild.setDuplicateParentStateEnabled(true);
    }

    public View getChild() {
        return mChild;
    }

    /**
     * Set {@link Rect} in screen coordinates that {@link #getChild()} should be
     * centered around.
     */
    public void setChildTargetScreen(Rect targetScreen) {
        mTargetScreen = targetScreen;
        requestLayout();
    }

    /**
     * Return {@link #mTargetScreen} in local window coordinates, taking any
     * decor insets into account.
     */
    private Rect getTargetInWindow() {
        final Rect windowScreen = new Rect();
        getWindowVisibleDisplayFrame(windowScreen);

        final Rect target = new Rect(mTargetScreen);
        target.offset(-windowScreen.left, -windowScreen.top);
        return target;
    }

    private void updateCallout(int calloutState, int calloutLeft) {
        if (mCalloutState != calloutState) {
            mCalloutState = calloutState;
            mChild.refreshDrawableState();
        }

        final Drawable background = mChild.getBackground();
        if (background != null && mCalloutLeft != calloutLeft) {
            mCalloutLeft = calloutLeft;
            background.setLevel(calloutLeft);
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        mergeDrawableStates(drawableState, new int[] { mCalloutState });
        return drawableState;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        final View child = mChild;
        final Rect target = getTargetInWindow();

        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        // default is no callout, left-aligned, and vertically centered
        int calloutState = 0;
        int childLeft = target.left;
        int childTop = target.centerY() - (childHeight / 2);

        // when target is wide, horizontally center instead of left-align
        if (target.width() > childWidth / 2) {
            childLeft = target.centerX() - (childWidth / 2);
        }

        final int areaAboveTarget = target.top;
        final int areaBelowTarget = getHeight() - target.bottom;

        if (areaAboveTarget >= childHeight) {
            // enough room above target, place above and callout down
            calloutState = android.R.attr.state_first;
            childTop = target.top - childHeight;

        } else if (areaBelowTarget >= childHeight) {
            // enough room below target, place below and callout up
            calloutState = android.R.attr.state_last;
            childTop = target.bottom;
        }

        // when child is outside bounds, nudge back inside
        childLeft = clampDimension(childLeft, childWidth, getWidth());
        childTop = clampDimension(childTop, childHeight, getHeight());

        final int calloutLeft = target.centerX() - childLeft;
        updateCallout(calloutState, calloutLeft);
        layoutChild(child, childLeft, childTop);

    }

    private static int clampDimension(int value, int size, int max) {
        // when larger than bounds, just center
        if (size > max) {
            return (max - size) / 2;
        }

        // clamp to lower bound
        value = Math.max(value, 0);
        // clamp to higher bound
        value = Math.min(value, max - size);

        return value;
    }

    private static void layoutChild(View child, int left, int top) {
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    /**
     * Begin animating {@link #getChild()} visible.
     */
    public void showChild() {
        final boolean calloutAbove = mCalloutState == android.R.attr.state_first;
        final Animation anim = AnimationUtils.loadAnimation(getContext(),
                calloutAbove ? R.anim.quickcontact_above_enter : R.anim.quickcontact_below_enter);
        mChild.startAnimation(anim);
        mChild.setVisibility(View.VISIBLE);
    }

    /**
     * Begin animating {@link #getChild()} invisible.
     */
    public void hideChild(final Runnable onAnimationEnd) {
        final boolean calloutAbove = mCalloutState == android.R.attr.state_first;
        final Animation anim = AnimationUtils.loadAnimation(getContext(),
                calloutAbove ? R.anim.quickcontact_above_exit : R.anim.quickcontact_below_exit);

        if (onAnimationEnd != null) {
            anim.setAnimationListener(new AnimationListener() {
                /** {@inheritDoc} */
                public void onAnimationStart(Animation animation) {
                    // ignored
                }

                /** {@inheritDoc} */
                public void onAnimationRepeat(Animation animation) {
                    // ignored
                }

                /** {@inheritDoc} */
                public void onAnimationEnd(Animation animation) {
                    onAnimationEnd.run();
                }
            });
        }

        mChild.startAnimation(anim);
        mChild.setVisibility(View.INVISIBLE);
    }

    private View.OnTouchListener mOutsideTouchListener;

    public void setOnOutsideTouchListener(View.OnTouchListener listener) {
        mOutsideTouchListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // at this point, touch wasn't handled by child view; assume outside
        if (mOutsideTouchListener != null) {
            return mOutsideTouchListener.onTouch(this, event);
        } else {
            return false;
        }
    }
}
