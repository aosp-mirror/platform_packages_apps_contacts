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

import com.android.contacts.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * This is a tab in the {@link ContactDetailTabCarousel}.
 */
public class CarouselTab extends RelativeLayout implements ViewOverlay {

    private static final String TAG = CarouselTab.class.getSimpleName();

    private TextView mLabelView;

    /**
     * This view adds an alpha layer over the entire tab.
     */
    private View mAlphaLayer;

    /**
     * This view adds a layer over the entire tab so that when visible, it intercepts all touch
     * events on the tab.
     */
    private View mTouchInterceptLayer;

    public CarouselTab(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLabelView = (TextView) findViewById(R.id.label);
        mLabelView.setClickable(true);

        mAlphaLayer = findViewById(R.id.alpha_overlay);
        mTouchInterceptLayer = findViewById(R.id.touch_intercept_overlay);
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

    @Override
    public void disableTouchInterceptor() {
        // This shouldn't be called because there is no need to disable the touch interceptor if
        // there is no content within the tab that needs to be clicked.
    }

    @Override
    public void enableTouchInterceptor(OnClickListener clickListener) {
        if (mTouchInterceptLayer != null) {
            mTouchInterceptLayer.setVisibility(View.VISIBLE);
            mTouchInterceptLayer.setOnClickListener(clickListener);
        }
    }

    @Override
    public void setAlphaLayerValue(float alpha) {
        ContactDetailDisplayUtils.setAlphaOnViewBackground(mAlphaLayer, alpha);
    }
}
