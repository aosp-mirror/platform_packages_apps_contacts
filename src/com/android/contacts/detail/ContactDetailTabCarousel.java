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

import com.android.contacts.ContactLoader;
import com.android.contacts.R;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This is a horizontally scrolling carousel with 2 tabs: one to see info about the contact and
 * one to see updates from the contact.
 */
public class ContactDetailTabCarousel extends HorizontalScrollView implements OnTouchListener {

    private static final String TAG = ContactDetailTabCarousel.class.getSimpleName();

    private static final int TAB_INDEX_ABOUT = 0;
    private static final int TAB_INDEX_UPDATES = 1;
    private static final int TAB_COUNT = 2;

    /** Tab width as defined as a fraction of the screen width */
    private float mTabWidthScreenWidthFraction;

    /** Tab height as defined as a fraction of the screen width */
    private float mTabHeightScreenWidthFraction;

    private ImageView mPhotoView;
    private TextView mStatusView;
    private ImageView mStatusPhotoView;

    private Listener mListener;

    private int mCurrentTab = TAB_INDEX_ABOUT;

    private CarouselTab mAboutTab;
    private CarouselTab mUpdatesTab;

    private int mTabDisplayLabelHeight;

    private int mLastScrollPosition;

    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;
    private int mAllowedVerticalScrollLength = Integer.MIN_VALUE;

    private static final float MAX_ALPHA = 0.5f;

    /**
     * Interface for callbacks invoked when the user interacts with the carousel.
     */
    public interface Listener {
        public void onTouchDown();
        public void onTouchUp();
        public void onScrollChanged(int l, int t, int oldl, int oldt);
        public void onTabSelected(int position);
    }

    public ContactDetailTabCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);

        Resources resources = mContext.getResources();
        mTabDisplayLabelHeight = resources.getDimensionPixelSize(
                R.dimen.detail_tab_carousel_tab_label_height);
        mTabWidthScreenWidthFraction = resources.getFraction(
                R.fraction.tab_width_screen_width_percentage, 1, 1);
        mTabHeightScreenWidthFraction = resources.getFraction(
                R.fraction.tab_height_screen_width_percentage, 1, 1);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAboutTab = (CarouselTab) findViewById(R.id.tab_about);
        mAboutTab.setLabel(mContext.getString(R.string.contactDetailAbout));

        mUpdatesTab = (CarouselTab) findViewById(R.id.tab_update);
        mUpdatesTab.setLabel(mContext.getString(R.string.contactDetailUpdates));

        // TODO: We can't always assume the "about" page will be the current page.
        mAboutTab.showSelectedState();
        mAboutTab.enableAlphaLayer();
        mAboutTab.setAlphaLayerValue(0);
        mAboutTab.enableTouchInterceptor(mAboutTabTouchInterceptListener);

        mUpdatesTab.enableAlphaLayer();
        mUpdatesTab.setAlphaLayerValue(MAX_ALPHA);
        mUpdatesTab.enableTouchInterceptor(mUpdatesTabTouchInterceptListener);

        // Retrieve the photo view for the "about" tab
        mPhotoView = (ImageView) mAboutTab.findViewById(R.id.photo);

        // Retrieve the social update views for the "updates" tab
        mStatusView = (TextView) mUpdatesTab.findViewById(R.id.status);
        mStatusPhotoView = (ImageView) mUpdatesTab.findViewById(R.id.status_photo);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        // Compute the width of a tab as a fraction of the screen width
        int tabWidth = (int) (mTabWidthScreenWidthFraction * screenWidth);

        // Find the allowed scrolling length by subtracting the current visible screen width
        // from the total length of the tabs.
        mAllowedHorizontalScrollLength = tabWidth * TAB_COUNT - screenWidth;

        int tabHeight = (int) (screenWidth * mTabHeightScreenWidthFraction);
        // Set the child {@link LinearLayout} to be TAB_COUNT * the computed tab width so that the
        // {@link LinearLayout}'s children (which are the tabs) will evenly split that width.
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            child.measure(MeasureSpec.makeMeasureSpec(TAB_COUNT * tabWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        mAllowedVerticalScrollLength = tabHeight - mTabDisplayLabelHeight;
        setMeasuredDimension(
                resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(tabHeight, heightMeasureSpec));
    }

    private final OnClickListener mAboutTabTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mListener.onTabSelected(TAB_INDEX_ABOUT);
        }
    };

    private final OnClickListener mUpdatesTabTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mListener.onTabSelected(TAB_INDEX_UPDATES);
        }
    };

    private void updateAlphaLayers() {
        mAboutTab.setAlphaLayerValue(mLastScrollPosition * MAX_ALPHA /
                mAllowedHorizontalScrollLength);
        mUpdatesTab.setAlphaLayerValue(MAX_ALPHA - mLastScrollPosition * MAX_ALPHA /
                mAllowedHorizontalScrollLength);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mListener.onScrollChanged(l, t, oldl, oldt);
        mLastScrollPosition = l;
        updateAlphaLayers();
    }

    /**
     * Returns the number of pixels that this view can be scrolled horizontally.
     */
    public int getAllowedHorizontalScrollLength() {
        return mAllowedHorizontalScrollLength;
    }

    /**
     * Returns the number of pixels that this view can be scrolled vertically while still allowing
     * the tab labels to still show.
     */
    public int getAllowedVerticalScrollLength() {
        return mAllowedVerticalScrollLength;
    }

    /**
     * Updates the tab selection.
     */
    public void setCurrentTab(int position) {
        // TODO: Handle device rotation (saving and restoring state of the selected tab)
        // This will take more work because there is no tab carousel in phone landscape
        switch (position) {
            case TAB_INDEX_ABOUT:
                mAboutTab.showSelectedState();
                mUpdatesTab.showDeselectedState();
                break;
            case TAB_INDEX_UPDATES:
                mUpdatesTab.showSelectedState();
                mAboutTab.showDeselectedState();
                break;
            default:
                throw new IllegalStateException("Invalid tab position " + position);
        }
        mCurrentTab = position;
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(ContactLoader.Result contactData) {
        if (contactData == null) {
            return;
        }

        // TODO: Move this into the {@link CarouselTab} class when the updates fragment code is more
        // finalized
        ContactDetailDisplayUtils.setPhoto(mContext, contactData, mPhotoView);
        ContactDetailDisplayUtils.setSocialSnippet(mContext, contactData, mStatusView,
                mStatusPhotoView);
    }

    /**
     * Set the given {@link Listener} to handle carousel events.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mListener.onTouchDown();
                return false;
            case MotionEvent.ACTION_UP:
                mListener.onTouchUp();
                return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean interceptTouch = super.onInterceptTouchEvent(ev);
        if (interceptTouch) {
            mListener.onTouchDown();
        }
        return interceptTouch;
    }
}
