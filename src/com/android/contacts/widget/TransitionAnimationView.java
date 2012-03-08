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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A container for a view that needs to have exit/enter animations when rebinding data.
 * After rebinding the contents, the following call should be made (where child is the only visible)
 * child
 * <pre>
 *   TransitionAnimationView.startAnimation(child);
 * </pre>
 */
public class TransitionAnimationView extends FrameLayout implements AnimatorListener {
    private View mPreviousStateView;
    private Bitmap mPreviousStateBitmap;
    private ObjectAnimator mPreviousAnimator;

    public TransitionAnimationView(Context context) {
        this(context, null, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPreviousStateView = new View(getContext());
        mPreviousStateView.setVisibility(View.INVISIBLE);
        mPreviousStateView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        addView(mPreviousStateView);
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

    public void startTransition(View view, boolean closing) {
        if (mPreviousAnimator != null && mPreviousAnimator.isRunning()) {
            mPreviousAnimator.end();
        }
        if (view.getVisibility() != View.VISIBLE) {
            if (!closing) {
                mPreviousAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
                mPreviousAnimator.start();
            }
        } else if (closing) {
            mPreviousAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f);
            mPreviousAnimator.start();
        } else {
            if (view.getWidth() > 0 && view.getHeight() > 0) {
                // Take a "screenshot" of the current state of the screen and show that on top
                // of the real content. Then, fade that out.
                mPreviousStateBitmap = Bitmap.createBitmap(
                        view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                mPreviousStateView.setBackgroundDrawable(
                        new BitmapDrawable(getContext().getResources(), mPreviousStateBitmap));
                mPreviousStateView.setLayoutParams(view.getLayoutParams());
                mPreviousStateBitmap.eraseColor(Color.WHITE);
                Canvas canvas = new Canvas(mPreviousStateBitmap);
                view.draw(canvas);
                canvas.setBitmap(null);
                mPreviousStateView.setVisibility(View.VISIBLE);

                mPreviousAnimator =
                        ObjectAnimator.ofFloat(mPreviousStateView, View.ALPHA, 1.0f, 0.0f);
                mPreviousAnimator.start();
            }
        }
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        mPreviousStateView.setVisibility(View.INVISIBLE);
        mPreviousStateView.setBackgroundDrawable(null);
        mPreviousStateBitmap.recycle();
        mPreviousStateBitmap = null;
        mPreviousAnimator = null;
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
