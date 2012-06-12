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

package com.android.contacts.detail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.widget.FrameLayoutWithOverlay;

/**
 * This is a tab in the {@link ContactDetailTabCarousel}.
 */
public class CarouselTab extends FrameLayoutWithOverlay {

    private static final String TAG = CarouselTab.class.getSimpleName();

    private static final long FADE_TRANSITION_TIME = 150;

    private TextView mLabelView;
    private View mLabelBackgroundView;

    /**
     * This view adds an alpha layer over the entire tab (except for the label).
     */
    private View mAlphaLayer;

    public CarouselTab(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLabelView = (TextView) findViewById(R.id.label);
        mLabelBackgroundView = findViewById(R.id.label_background);
        mAlphaLayer = findViewById(R.id.alpha_overlay);
        setAlphaLayer(mAlphaLayer);
    }

    public void setLabel(String label) {
        mLabelView.setText(label);
    }

    public void showSelectedState() {
        mLabelView.setSelected(true);
    }

    public void showDeselectedState() {
        mLabelView.setSelected(false);
    }

    public void fadeInLabelViewAnimator(int startDelay, boolean fadeBackground) {
        final ViewPropertyAnimator labelAnimator = mLabelView.animate();
        mLabelView.setAlpha(0.0f);
        labelAnimator.alpha(1.0f);
        labelAnimator.setStartDelay(startDelay);
        labelAnimator.setDuration(FADE_TRANSITION_TIME);

        if (fadeBackground) {
            final ViewPropertyAnimator backgroundAnimator = mLabelBackgroundView.animate();
            mLabelBackgroundView.setAlpha(0.0f);
            backgroundAnimator.alpha(1.0f);
            backgroundAnimator.setStartDelay(startDelay);
            backgroundAnimator.setDuration(FADE_TRANSITION_TIME);
        }
    }
}
