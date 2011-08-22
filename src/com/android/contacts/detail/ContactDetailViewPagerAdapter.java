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
 * Adapter for the {@link ViewPager} for the contact detail page for a contact with social updates.
 */
public class ContactDetailViewPagerAdapter extends PagerAdapter {

    public static final String ABOUT_FRAGMENT_TAG = "view-pager-about-fragment";
    public static final String UPDTES_FRAGMENT_TAG = "view-pager-updates-fragment";

    private static final int INDEX_ABOUT_FRAGMENT = 0;
    private static final int INDEX_UPDATES_FRAGMENT = 1;

    private static final int MAX_FRAGMENT_VIEW_COUNT = 2;

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
        if (object == mAboutFragmentView) {
            return INDEX_ABOUT_FRAGMENT;
        }
        if (object == mUpdatesFragmentView) {
            return INDEX_UPDATES_FRAGMENT;
        }
        return POSITION_NONE;
    }

    @Override
    public void startUpdate(View container) {
    }

    @Override
    public Object instantiateItem(View container, int position) {
        switch (position) {
            case INDEX_ABOUT_FRAGMENT:
                return mAboutFragmentView;
            case INDEX_UPDATES_FRAGMENT:
                return mUpdatesFragmentView;
        }
        throw new IllegalArgumentException("Invalid position: " + position);
    }

    @Override
    public void destroyItem(View container, int position, Object object) {
    }

    @Override
    public void finishUpdate(View container) {
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
