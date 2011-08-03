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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * A container for a view that needs to have exit/enter animations when rebinding data.
 * This layout should have a single child.  Just before rebinding data that child
 * should make this call:
 * <pre>
 *   TransitionAnimationView.startAnimation(this);
 * </pre>
 */
public class TransitionAnimationView extends FrameLayout implements AnimatorListener {

    private View mPreviousStateView;
    private Bitmap mPreviousStateBitmap;
    private int mEnterAnimationId;
    private int mExitAnimationId;
    private int mAnimationDuration;
    private Rect mClipMargins = new Rect();
    private Rect mClipRect = new Rect();
    private Animator mEnterAnimation;
    private Animator mExitAnimation;

    public TransitionAnimationView(Context context) {
        this(context, null, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TransitionAnimationView);

        mEnterAnimationId = a.getResourceId(R.styleable.TransitionAnimationView_enterAnimation,
                android.R.animator.fade_in);
        mExitAnimationId = a.getResourceId(R.styleable.TransitionAnimationView_exitAnimation,
                android.R.animator.fade_out);
        mClipMargins.left = a.getDimensionPixelOffset(
                R.styleable.TransitionAnimationView_clipMarginLeft, 0);
        mClipMargins.top = a.getDimensionPixelOffset(
                R.styleable.TransitionAnimationView_clipMarginTop, 0);
        mClipMargins.right = a.getDimensionPixelOffset(
                R.styleable.TransitionAnimationView_clipMarginRight, 0);
        mClipMargins.bottom = a.getDimensionPixelOffset(
                R.styleable.TransitionAnimationView_clipMarginBottom, 0);
        mAnimationDuration = a.getInt(
                R.styleable.TransitionAnimationView_animationDuration, 100);

        a.recycle();

        mPreviousStateView = new View(context);
        mPreviousStateView.setVisibility(View.INVISIBLE);
        addView(mPreviousStateView);

        mEnterAnimation = AnimatorInflater.loadAnimator(getContext(), mEnterAnimationId);
        if (mEnterAnimation == null) {
            throw new IllegalArgumentException("Invalid enter animation: " + mEnterAnimationId);
        }
        mEnterAnimation.addListener(this);
        mEnterAnimation.setDuration(mAnimationDuration);

        mExitAnimation = AnimatorInflater.loadAnimator(getContext(), mExitAnimationId);
        if (mExitAnimation == null) {
            throw new IllegalArgumentException("Invalid exit animation: " + mExitAnimationId);
        }

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || mPreviousStateBitmap == null) {
            if (mPreviousStateBitmap != null) {
                mPreviousStateBitmap.recycle();
                mPreviousStateBitmap = null;
            }
            int width = right - left;
            int height = bottom - top;
            if (width > 0 && height > 0) {
                mPreviousStateBitmap = Bitmap.createBitmap(
                        width, height, Bitmap.Config.ARGB_8888);
                mPreviousStateView.setBackgroundDrawable(
                        new BitmapDrawable(getContext().getResources(), mPreviousStateBitmap));
                mClipRect.set(mClipMargins.left, mClipMargins.top,
                        width - mClipMargins.right, height - mClipMargins.bottom);
            } else {
                mPreviousStateBitmap = null;
                mPreviousStateView.setBackgroundDrawable(null);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPreviousStateView.setBackgroundDrawable(null);
        if (mPreviousStateBitmap != null) {
            mPreviousStateBitmap.recycle();
            mPreviousStateBitmap = null;
        }
    }

    public static void startAnimation(View view, boolean closing) {
        TransitionAnimationView container = null;
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof TransitionAnimationView) {
                container = (TransitionAnimationView) parent;
                break;
            }
            parent = parent.getParent();
        }

        if (container != null) {
            container.start(view, closing);
        }
    }

    private void start(View view, boolean closing) {
        if (mEnterAnimation.isRunning()) {
            mEnterAnimation.end();
        }
        if (mExitAnimation.isRunning()) {
            mExitAnimation.end();
        }
        if (view.getVisibility() != View.VISIBLE) {
            if (!closing) {
                mEnterAnimation.setTarget(view);
                mEnterAnimation.start();
            }
        } else if (closing) {
            mExitAnimation.setTarget(view);
            mExitAnimation.start();
        } else {
            if (mPreviousStateBitmap == null) {
                return;
            }

            Canvas canvas = new Canvas(mPreviousStateBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.TRANSPARENT);
            canvas.drawRect(0, 0, mPreviousStateBitmap.getWidth(), mPreviousStateBitmap.getHeight(),
                    paint);
            canvas.clipRect(mClipRect);
            view.draw(canvas);
            canvas.setBitmap(null);
            mPreviousStateView.setVisibility(View.VISIBLE);

            mEnterAnimation.setTarget(view);
            mEnterAnimation.start();
        }
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        mPreviousStateView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationStart(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
}
