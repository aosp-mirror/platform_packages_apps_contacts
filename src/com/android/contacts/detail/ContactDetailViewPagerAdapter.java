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

import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

/**
 * Adapter for the {@link ViewPager} for the contact detail page for a contact in 2 cases:
 * 1) without social updates, and 2) with social updates. The default initial case is for
 * the contact with social updates which uses all possible pages.
 */
public class ContactDetailViewPagerAdapter extends PagerAdapter {

    public static final String ABOUT_FRAGMENT_TAG = "view-pager-about-fragment";
    public static final String UPDATES_FRAGMENT_TAG = "view-pager-updates-fragment";

    private static final int INDEX_ABOUT_FRAGMENT = 0;
    private static final int INDEX_UPDATES_FRAGMENT = 1;

    private static final int MAX_FRAGMENT_VIEW_COUNT = 2;

    /**
     * The initial value for the view count needs to be MAX_FRAGMENT_VIEW_COUNT,
     * otherwise anything smaller would break screen rotation functionality for a user viewing
     * a contact with social updates (i.e. the user was viewing the second page, rotates the
     * device, the view pager requires the second page to exist immediately on launch).
     */
    private int mFragmentViewCount = MAX_FRAGMENT_VIEW_COUNT;

    private View mAboutFragmentView;
    private View mUpdatesFragmentView;

    public ContactDetailViewPagerAdapter() {
    }

    public void setAboutFragmentView(View view) {
        mAboutFragmentView = view;
    }

    public void setUpdatesFragmentView(View view) {
        mUpdatesFragmentView = view;
    }

    /**
     * Enable swiping if the detail and update fragments should be showing. Otherwise diable
     * swiping if only the detail fragment should be showing.
     */
    public void enableSwipe(boolean enable) {
        mFragmentViewCount = enable ? MAX_FRAGMENT_VIEW_COUNT : 1;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mFragmentViewCount;
    }

    /** Gets called when the number of items changes. */
    @Override
    public int getItemPosition(Object object) {
        // Always return a valid index for the about fragment view because it's always shown
        // whether the contact has social updates or not.
        if (object == mAboutFragmentView) {
            return INDEX_ABOUT_FRAGMENT;
        }
        // Only return a valid index for the updates fragment view if our view count > 1.
        if (object == mUpdatesFragmentView && mFragmentViewCount > 1) {
            return INDEX_UPDATES_FRAGMENT;
        }
        // Otherwise the view should have no position.
        return POSITION_NONE;
    }

    @Override
    public void startUpdate(ViewGroup container) {
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        switch (position) {
            case INDEX_ABOUT_FRAGMENT:
                mAboutFragmentView.setVisibility(View.VISIBLE);
                return mAboutFragmentView;
            case INDEX_UPDATES_FRAGMENT:
                mUpdatesFragmentView.setVisibility(View.VISIBLE);
                return mUpdatesFragmentView;
        }
        throw new IllegalArgumentException("Invalid position: " + position);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((View) object).setVisibility(View.GONE);
    }

    @Override
    public void finishUpdate(ViewGroup container) {
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((View) object) == view;
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
    }
}
