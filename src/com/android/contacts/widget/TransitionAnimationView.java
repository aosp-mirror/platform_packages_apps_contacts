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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A container that places a masking view on top of all other views.  The masking view can be
 * faded in and out.  Currently, the masking view is solid color white.
 */
public class TransitionAnimationView extends FrameLayout {
    private View mMaskingView;
    private ObjectAnimator mAnimator;

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
        mMaskingView = new View(getContext());
        mMaskingView.setVisibility(View.INVISIBLE);
        mMaskingView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mMaskingView.setBackgroundColor(Color.WHITE);
        addView(mMaskingView);
    }

    public void setMaskVisibility(boolean flag) {
        if (flag) {
            mMaskingView.setAlpha(1.0f);
            mMaskingView.setVisibility(View.VISIBLE);
        } else {
            mMaskingView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Starts the transition of showing or hiding the mask. To the user, the view will appear to
     * either fade in or out of view.
     *
     * @param showMask If true, the mask the mask will be set to be invisible then fade into hide
     * the other views in this container. If false, the the mask will be set to be hide other
     * views initially.  Then, the other views in this container will be revealed.
     * @param duration The duration the animation should last for. If -1, the system default(300)
     * is used.
     */
    public void startMaskTransition(boolean showMask, int duration) {
        // Stop any animation that may still be running.
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.end();
        }
        mMaskingView.setVisibility(View.VISIBLE);
        if (showMask) {
            mAnimator = ObjectAnimator.ofFloat(mMaskingView, View.ALPHA, 0.0f, 1.0f);
        } else {
            // asked to hide the view
            mAnimator = ObjectAnimator.ofFloat(mMaskingView, View.ALPHA, 1.0f, 0.0f);
        }
        if (duration != -1) {
            mAnimator.setDuration(duration);
        }
        mAnimator.start();
    }
}
