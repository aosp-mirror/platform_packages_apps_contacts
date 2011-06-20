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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.HorizontalScrollView;

/**
 * This is a horizontally scrolling carousel with 2 fragments: one to see info about the contact and
 * one to see updates from the contact. Depending on the scroll position and user selection of which
 * fragment to currently view, the alpha values and touch interceptors over each fragment are
 * configured accordingly.
 */
public class ContactDetailFragmentCarousel extends HorizontalScrollView implements OnTouchListener {

    private static final String TAG = ContactDetailFragmentCarousel.class.getSimpleName();

    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;
    private int mLowerThreshold = Integer.MIN_VALUE;
    private int mUpperThreshold = Integer.MIN_VALUE;

    private static final int ABOUT_PAGE = 0;
    private static final int UPDATES_PAGE = 1;

    private int mCurrentPage = ABOUT_PAGE;
    private int mLastScrollPosition;

    private FragmentOverlay mAboutFragment;
    private FragmentOverlay mUpdatesFragment;

    private static final float MAX_ALPHA = 0.5f;

    public ContactDetailFragmentCarousel(Context context) {
        this(context, null);
    }

    public ContactDetailFragmentCarousel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactDetailFragmentCarousel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.contact_detail_fragment_carousel, this);

        setOnTouchListener(this);
    }

    public void setAboutFragment(FragmentOverlay fragment) {
        // TODO: We can't always assume the "about" page will be the current page.
        mAboutFragment = fragment;
        mAboutFragment.enableAlphaLayer();
        mAboutFragment.setAlphaLayerValue(0);
        mAboutFragment.disableTouchInterceptor();
    }

    public void setUpdatesFragment(FragmentOverlay fragment) {
        mUpdatesFragment = fragment;
        mUpdatesFragment.enableAlphaLayer();
        mUpdatesFragment.setAlphaLayerValue(MAX_ALPHA);
        mUpdatesFragment.enableTouchInterceptor(mUpdatesFragTouchInterceptListener);
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    private final OnClickListener mAboutFragTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mCurrentPage = ABOUT_PAGE;
            snapToEdge();
        }
    };

    private final OnClickListener mUpdatesFragTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mCurrentPage = UPDATES_PAGE;
            snapToEdge();
        }
    };

    private void updateTouchInterceptors() {
        switch (mCurrentPage) {
            case ABOUT_PAGE:
                // The "about this contact" page has been selected, so disable the touch interceptor
                // on this page and enable it for the "updates" page.
                mAboutFragment.disableTouchInterceptor();
                mUpdatesFragment.enableTouchInterceptor(mUpdatesFragTouchInterceptListener);
                break;
            case UPDATES_PAGE:
                mUpdatesFragment.disableTouchInterceptor();
                mAboutFragment.enableTouchInterceptor(mAboutFragTouchInterceptListener);
                break;
        }
    }

    private void updateAlphaLayers() {
        mAboutFragment.setAlphaLayerValue(mLastScrollPosition * MAX_ALPHA /
                getAllowedHorizontalScrollLength());
        mUpdatesFragment.setAlphaLayerValue(MAX_ALPHA - mLastScrollPosition * MAX_ALPHA /
                getAllowedHorizontalScrollLength());
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mLastScrollPosition= l;
        updateAlphaLayers();
    }

    private void snapToEdge() {
        switch (mCurrentPage) {
            case ABOUT_PAGE:
                smoothScrollTo(0, 0);
                break;
            case UPDATES_PAGE:
                smoothScrollTo(getAllowedHorizontalScrollLength(), 0);
                break;
        }
        updateTouchInterceptors();
    }

    /**
     * Returns the desired page we should scroll to based on the current X scroll position and the
     * current page.
     */
    private int getDesiredPage() {
        switch (mCurrentPage) {
            case ABOUT_PAGE:
                // If the user is on the "about" page, and the scroll position exceeds the lower
                // threshold, then we should switch to the "updates" page.
                return (mLastScrollPosition > getLowerThreshold()) ? UPDATES_PAGE : ABOUT_PAGE;
            case UPDATES_PAGE:
                // If the user is on the "updates" page, and the scroll position goes below the
                // upper threshold, then we should switch to the "about" page.
                return (mLastScrollPosition < getUpperThreshold()) ? ABOUT_PAGE : UPDATES_PAGE;
        }
        throw new IllegalStateException("Invalid current page " + mCurrentPage);
    }

    /**
     * Returns the number of pixels that this view can be scrolled horizontally.
     */
    private int getAllowedHorizontalScrollLength() {
        if (mAllowedHorizontalScrollLength == Integer.MIN_VALUE) {
            computeThresholds();
        }
        return mAllowedHorizontalScrollLength;
    }

    /**
     * Returns the minimum X scroll position that must be surpassed (if the user is on the "about"
     * page of the contact card), in order for this view to automatically snap to the "updates"
     * page.
     */
    private int getLowerThreshold() {
        if (mLowerThreshold == Integer.MIN_VALUE) {
            computeThresholds();
        }
        return mLowerThreshold;
    }

    /**
     * Returns the maximum X scroll position (if the user is on the "updates" page of the contact
     * card), below which this view will automatically snap to the "about" page.
     */
    private int getUpperThreshold() {
        if (mLowerThreshold == Integer.MIN_VALUE) {
            computeThresholds();
        }
        return mUpperThreshold;
    }

    // TODO: Move this to a Fragment override method (i.e. onActivityCreated or some method where
    // we can be sure the width of the views are non-zero) instead of doing it on the fly when the
    // values are requested for the first time.
    private void computeThresholds() {
        int screenWidth = getWidth();
        int fragmentWidth = findViewById(R.id.about_fragment).getWidth();
        mAllowedHorizontalScrollLength = (2 * fragmentWidth) - screenWidth;
        mLowerThreshold = (screenWidth - fragmentWidth) / 2;
        mUpperThreshold = mAllowedHorizontalScrollLength - mLowerThreshold;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mCurrentPage = getDesiredPage();
            snapToEdge();
            return true;
        }
        return false;
    }
}
