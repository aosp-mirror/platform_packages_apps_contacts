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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
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
    private int mFixedTopPosition;
    private View mChild;
    private Rect mTargetScreen = new Rect();
    private final int mAnimationDuration;

    public FloatingChildLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Resources resources = getResources();
        mFixedTopPosition =
                resources.getDimensionPixelOffset(R.dimen.quick_contact_top_position);
        mAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime);
    }

    @Override
    protected void onFinishInflate() {
        mChild = findViewById(android.R.id.content);
        mChild.setDuplicateParentStateEnabled(true);

        // this will be expanded in showChild()
        mChild.setScaleX(0.0f);
        mChild.setScaleY(0.0f);
        mChild.setAlpha(0.0f);
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        final View child = mChild;
        final Rect target = getTargetInWindow();

        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        if (mFixedTopPosition != -1) {
            // Horizontally centered, vertically fixed position
            final int childLeft = (getWidth() - childWidth) / 2;
            final int childTop = mFixedTopPosition;
            layoutChild(child, childLeft, childTop);
        } else {
            // default is centered horizontally around target...
            final int childLeft = target.centerX() - (childWidth / 2);
            // ... and vertically aligned a bit below centered
            final int childTop = target.centerY() - Math.round(childHeight * 0.35f);

            // when child is outside bounds, nudge back inside
            final int clampedChildLeft = clampDimension(childLeft, childWidth, getWidth());
            final int clampedChildTop = clampDimension(childTop, childHeight, getHeight());

            layoutChild(child, clampedChildLeft, clampedChildTop);
        }
    }

    private static int clampDimension(int value, int size, int max) {
        // when larger than bounds, just center
        if (size > max) {
            return (max - size) / 2;
        }

        // clamp to bounds
        return Math.min(Math.max(value, 0), max - size);
    }

    private static void layoutChild(View child, int left, int top) {
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    /** Begin animating {@link #getChild()} visible. */
    public void showChild(Runnable onAnimationEndRunnable) {
        animateScale(false, onAnimationEndRunnable);
    }

    /** Begin animating {@link #getChild()} invisible. */
    public void hideChild(Runnable onAnimationEndRunnable) {
        animateScale(true, onAnimationEndRunnable);
    }

    /** Creates the open/close animation */
    private void animateScale(boolean isExitAnimation, final Runnable onAnimationEndRunnable) {
        mChild.setPivotX(mTargetScreen.centerX() - mChild.getLeft());
        mChild.setPivotY(mTargetScreen.centerY() - mChild.getTop());
        ViewPropertyAnimator animator = mChild.animate();
        animator.setDuration(mAnimationDuration);
        final int scaleInterpolator = isExitAnimation ? android.R.interpolator.accelerate_quint
                : android.R.interpolator.decelerate_quint;
        animator.setInterpolator(AnimationUtils.loadInterpolator(getContext(), scaleInterpolator));
        final float scaleTarget = isExitAnimation ? 0.5f : 1.0f;
        animator.scaleX(scaleTarget);
        animator.scaleY(scaleTarget);
        animator.alpha(isExitAnimation ? 0.0f : 1.0f);

        if (onAnimationEndRunnable != null) {
            animator.setListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    onAnimationEndRunnable.run();
                }
            });
        }
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
        }
        return false;
    }
}
