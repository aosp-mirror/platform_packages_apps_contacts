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

    /**
     * Number of pixels that this view can be scrolled horizontally.
     */
    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;

    /**
     * Minimum X scroll position that must be surpassed (if the user is on the "about" page of the
     * contact card), in order for this view to automatically snap to the "updates" page.
     */
    private int mLowerThreshold = Integer.MIN_VALUE;

    /**
     * Maximum X scroll position (if the user is on the "updates" page of the contact card), below
     * which this view will automatically snap to the "about" page.
     */
    private int mUpperThreshold = Integer.MIN_VALUE;

    /**
     * Minimum width of a fragment (if there is more than 1 fragment in the carousel, then this is
     * the width of one of the fragments).
     */
    private int mMinFragmentWidth = Integer.MIN_VALUE;

    /**
     * Maximum alpha value of the overlay on the fragment that is not currently selected
     * (if there are 1+ fragments in the carousel).
     */
    private static final float MAX_ALPHA = 0.5f;

    /**
     * Fragment width (if there are 1+ fragments in the carousel) as defined as a fraction of the
     * screen width.
     */
    private static final float FRAGMENT_WIDTH_SCREEN_WIDTH_FRACTION = 0.85f;

    private static final int ABOUT_PAGE = 0;
    private static final int UPDATES_PAGE = 1;

    private static final int MAX_FRAGMENT_VIEW_COUNT = 2;

    private boolean mEnableSwipe;

    private int mCurrentPage = ABOUT_PAGE;
    private int mLastScrollPosition;

    private ViewOverlay mAboutFragment;
    private ViewOverlay mUpdatesFragment;

    private View mDetailFragmentView;
    private View mUpdatesFragmentView;

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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        int screenHeight = MeasureSpec.getSize(heightMeasureSpec);

        // Take the width of this view as the width of the screen and compute necessary thresholds.
        // Only do this computation 1x.
        if (mAllowedHorizontalScrollLength == Integer.MIN_VALUE) {
            mMinFragmentWidth = (int) (FRAGMENT_WIDTH_SCREEN_WIDTH_FRACTION * screenWidth);
            mAllowedHorizontalScrollLength = (MAX_FRAGMENT_VIEW_COUNT * mMinFragmentWidth) -
                    screenWidth;
            mLowerThreshold = (screenWidth - mMinFragmentWidth) / MAX_FRAGMENT_VIEW_COUNT;
            mUpperThreshold = mAllowedHorizontalScrollLength - mLowerThreshold;
        }

        if (getChildCount() > 0) {
            View child = getChildAt(0);
            // If we enable swipe, then the {@link LinearLayout} child width must be the sum of the
            // width of all its children fragments.
            if (mEnableSwipe) {
                child.measure(MeasureSpec.makeMeasureSpec(
                        mMinFragmentWidth * MAX_FRAGMENT_VIEW_COUNT, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(screenHeight, MeasureSpec.EXACTLY));
            } else {
                // Otherwise, the {@link LinearLayout} child width will just be the screen width
                // because it will only have 1 child fragment.
                child.measure(MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(screenHeight, MeasureSpec.EXACTLY));
            }
        }

        setMeasuredDimension(
                resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(screenHeight, heightMeasureSpec));
    }

    /**
     * Set the current page. This dims out the non-selected page but doesn't do any scrolling of
     * the carousel.
     */
    public void setCurrentPage(int pageIndex) {
        mCurrentPage = pageIndex;

        if (mAboutFragment != null && mUpdatesFragment != null) {
            mAboutFragment.setAlphaLayerValue(mCurrentPage == ABOUT_PAGE ? 0 : MAX_ALPHA);
            mUpdatesFragment.setAlphaLayerValue(mCurrentPage == UPDATES_PAGE ? 0 : MAX_ALPHA);
        }
    }

    /**
     * Set the view containers for the detail and updates fragment.
     */
    public void setFragmentViews(View detailFragmentView, View updatesFragmentView) {
        mDetailFragmentView = detailFragmentView;
        mUpdatesFragmentView = updatesFragmentView;
    }

    /**
     * Set the detail and updates fragment.
     */
    public void setFragments(ViewOverlay aboutFragment, ViewOverlay updatesFragment) {
        mAboutFragment = aboutFragment;
        mUpdatesFragment = updatesFragment;
    }

    /**
     * Enable swiping if the detail and update fragments should be showing. Otherwise disable
     * swiping if only the detail fragment should be showing.
     */
    public void enableSwipe(boolean enable) {
        if (mEnableSwipe != enable) {
            mEnableSwipe = enable;
            if (mUpdatesFragmentView != null) {
                mUpdatesFragmentView.setVisibility(enable ? View.VISIBLE : View.GONE);
                if (mCurrentPage == ABOUT_PAGE) {
                    mDetailFragmentView.requestFocus();
                } else {
                    mUpdatesFragmentView.requestFocus();
                }
                updateTouchInterceptors();
            }
        }
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
                mAllowedHorizontalScrollLength);
        mUpdatesFragment.setAlphaLayerValue(MAX_ALPHA - mLastScrollPosition * MAX_ALPHA /
                mAllowedHorizontalScrollLength);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!mEnableSwipe) {
            return;
        }
        mLastScrollPosition= l;
        updateAlphaLayers();
    }

    private void snapToEdge() {
        switch (mCurrentPage) {
            case ABOUT_PAGE:
                smoothScrollTo(0, 0);
                break;
            case UPDATES_PAGE:
                smoothScrollTo(mAllowedHorizontalScrollLength, 0);
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
                return (mLastScrollPosition > mLowerThreshold) ? UPDATES_PAGE : ABOUT_PAGE;
            case UPDATES_PAGE:
                // If the user is on the "updates" page, and the scroll position goes below the
                // upper threshold, then we should switch to the "about" page.
                return (mLastScrollPosition < mUpperThreshold) ? ABOUT_PAGE : UPDATES_PAGE;
        }
        throw new IllegalStateException("Invalid current page " + mCurrentPage);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!mEnableSwipe) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mCurrentPage = getDesiredPage();
            snapToEdge();
            return true;
        }
        return false;
    }
}
