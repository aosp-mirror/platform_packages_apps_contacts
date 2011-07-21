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
import com.android.contacts.activities.PeopleActivity.ContactDetailFragmentListener;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

/**
 * Determines the layout of the contact card.
 */
public class ContactDetailLayoutController {

    public static final int FRAGMENT_COUNT = 2;

    private static final String KEY_DETAIL_FRAGMENT_TAG = "detailFragTag";
    private static final String KEY_UPDATES_FRAGMENT_TAG = "updatesFragTag";

    private String mDetailFragmentTag;
    private String mUpdatesFragmentTag;

    private enum LayoutMode {
        TWO_COLUMN, VIEW_PAGER_AND_CAROUSEL,
    }

    private final FragmentManager mFragmentManager;

    private ContactDetailFragment mContactDetailFragment;
    private ContactDetailUpdatesFragment mContactDetailUpdatesFragment;

    private final ViewPager mViewPager;
    private final ContactDetailTabCarousel mTabCarousel;
    private ContactDetailFragment mPagerContactDetailFragment;
    private ContactDetailUpdatesFragment mPagerContactDetailUpdatesFragment;

    private ContactDetailFragmentListener mContactDetailFragmentListener;

    private ContactLoader.Result mContactData;

    private boolean mIsInitialized;

    private LayoutMode mLayoutMode;

    public ContactDetailLayoutController(FragmentManager fragmentManager, ViewPager viewPager,
            ContactDetailTabCarousel tabCarousel, ContactDetailFragmentListener
            contactDetailFragmentListener) {
        if (fragmentManager == null) {
            throw new IllegalStateException("Cannot initialize a ContactDetailLayoutController "
                    + "without a non-null FragmentManager");
        }

        mFragmentManager = fragmentManager;
        mViewPager = viewPager;
        mTabCarousel = tabCarousel;
        mContactDetailFragmentListener = contactDetailFragmentListener;

        // Determine the layout based on whether the {@link ViewPager} is null or not. If the
        // {@link ViewPager} is null, then this is a wide screen and the content can be displayed
        // in 2 columns side by side. If the {@link ViewPager} is non-null, then this is a narrow
        // screen and the user will need to swipe to see all the data.
        mLayoutMode = (mViewPager == null) ? LayoutMode.TWO_COLUMN :
                LayoutMode.VIEW_PAGER_AND_CAROUSEL;

    }

    public boolean isInitialized() {
        return mIsInitialized;
    }

    public void initialize() {
        mIsInitialized = true;
        if (mDetailFragmentTag != null || mUpdatesFragmentTag != null) {
            // Manually remove any {@link ViewPager} fragments if there was an orientation change
            ContactDetailFragment oldDetailFragment = (ContactDetailFragment) mFragmentManager.
                    findFragmentByTag(mDetailFragmentTag);
            ContactDetailUpdatesFragment oldUpdatesFragment = (ContactDetailUpdatesFragment)
                    mFragmentManager.findFragmentByTag(mUpdatesFragmentTag);

            if (oldDetailFragment != null && oldUpdatesFragment != null) {
                FragmentTransaction ft = mFragmentManager.beginTransaction();
                ft.remove(oldDetailFragment);
                ft.remove(oldUpdatesFragment);
                ft.commit();
            }
        }
        if (mViewPager != null) {
            mViewPager.setAdapter(new ViewPagerAdapter(mFragmentManager));
            mViewPager.setOnPageChangeListener(mOnPageChangeListener);
            mTabCarousel.setListener(mTabCarouselListener);
        }
    }

    public void setContactDetailFragment(ContactDetailFragment contactDetailFragment) {
        mContactDetailFragment = contactDetailFragment;
    }

    public void setContactDetailUpdatesFragment(ContactDetailUpdatesFragment updatesFragment) {
        mContactDetailUpdatesFragment = updatesFragment;
    }

    public void setContactData(ContactLoader.Result data) {
        mContactData = data;
        if (mContactData.getSocialSnippet() != null) {
            showContactWithUpdates();
        } else {
            showContactWithoutUpdates();
        }
    }

    private void showContactWithUpdates() {
        FragmentTransaction ft = mFragmentManager.beginTransaction();

        switch (mLayoutMode) {
            case TWO_COLUMN: {
                // Set the contact data
                mContactDetailFragment.setData(mContactData.getLookupUri(), mContactData);
                mContactDetailUpdatesFragment.setData(mContactData.getLookupUri(), mContactData);

                // Update fragment visibility
                ft.show(mContactDetailUpdatesFragment);
                break;
            }
            case VIEW_PAGER_AND_CAROUSEL: {
                // Set the contact data
                mTabCarousel.loadData(mContactData);
                mPagerContactDetailFragment.setData(mContactData.getLookupUri(), mContactData);
                mPagerContactDetailUpdatesFragment.setData(mContactData.getLookupUri(),
                        mContactData);

                // Update fragment and view visibility
                mViewPager.setVisibility(View.VISIBLE);
                mTabCarousel.setVisibility(View.VISIBLE);
                ft.hide(mContactDetailFragment);
                break;
            }
            default:
                throw new IllegalStateException("Invalid LayoutMode " + mLayoutMode);
        }

        // If the activity has already saved its state, then allow this fragment
        // transaction to be dropped because there's nothing else we can do to update the UI.
        // The fact that the contact URI has already been saved by the activity means we can
        // restore this later.
        // TODO: Figure out if this is really the solution we want.
        ft.commitAllowingStateLoss();
    }

    private void showContactWithoutUpdates() {
        FragmentTransaction ft = mFragmentManager.beginTransaction();

        switch (mLayoutMode) {
            case TWO_COLUMN:
                mContactDetailFragment.setData(mContactData.getLookupUri(), mContactData);
                ft.hide(mContactDetailUpdatesFragment);
                break;
            case VIEW_PAGER_AND_CAROUSEL:
                mContactDetailFragment.setData(mContactData.getLookupUri(), mContactData);
                ft.show(mContactDetailFragment);
                mViewPager.setVisibility(View.GONE);
                mTabCarousel.setVisibility(View.GONE);
                break;
            default:
                throw new IllegalStateException("Invalid LayoutMode " + mLayoutMode);
        }

        // If the activity has already saved its state, then allow this fragment
        // transaction to be dropped because there's nothing else we can do to update the UI.
        // The fact that the contact URI has already been saved by the activity means we can
        // restore this later.
        // TODO: Figure out if this is really the solution we want.
        ft.commitAllowingStateLoss();
    }

    public void onSaveInstanceState(Bundle outState) {
        if (mPagerContactDetailFragment != null) {
            outState.putString(KEY_DETAIL_FRAGMENT_TAG,
                    mPagerContactDetailFragment.getTag());
            outState.putString(KEY_UPDATES_FRAGMENT_TAG,
                    mPagerContactDetailUpdatesFragment.getTag());
        }
    }

    public void onRestoreInstanceState(Bundle savedState) {
        mDetailFragmentTag = savedState.getString(KEY_DETAIL_FRAGMENT_TAG);
        mUpdatesFragmentTag = savedState.getString(KEY_UPDATES_FRAGMENT_TAG);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter{

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    mPagerContactDetailFragment = new ContactDetailFragment();
                    if (mContactData != null) {
                        mPagerContactDetailFragment.setData(mContactData.getLookupUri(),
                                mContactData);
                    }
                    mPagerContactDetailFragment.setListener(mContactDetailFragmentListener);
                    mPagerContactDetailFragment.setVerticalScrollListener(mVerticalScrollListener);
                    mPagerContactDetailFragment.setShowPhotoInHeader(false);
                    return mPagerContactDetailFragment;
                case 1:
                    mPagerContactDetailUpdatesFragment = new ContactDetailUpdatesFragment();
                    if (mContactData != null) {
                        mPagerContactDetailUpdatesFragment.setData(mContactData.getLookupUri(),
                                mContactData);
                    }
                    return mPagerContactDetailUpdatesFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return FRAGMENT_COUNT;
        }
    }

    private OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // The user is horizontally dragging the {@link ViewPager}, so send
            // these scroll changes to the tab carousel. Ignore these events though if the carousel
            // is actually controlling the {@link ViewPager} scrolls because it will already be
            // in the correct position.
            if (mViewPager.isFakeDragging()) {
                return;
            }
            int x = (int) ((position + positionOffset) *
                    mTabCarousel.getAllowedHorizontalScrollLength());
            mTabCarousel.scrollTo(x, 0);
        }

        @Override
        public void onPageSelected(int position) {
            // Since a new page has been selected by the {@link ViewPager},
            // update the tab selection in the carousel.
            mTabCarousel.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {}

    };

    private ContactDetailTabCarousel.Listener mTabCarouselListener =
            new ContactDetailTabCarousel.Listener() {

        @Override
        public void onTouchDown() {
            // The user just started scrolling the carousel, so begin "fake dragging" the
            // {@link ViewPager} if it's not already doing so.
            if (mViewPager.isFakeDragging()) {
                return;
            }
            mViewPager.beginFakeDrag();
        }

        @Override
        public void onTouchUp() {
            // The user just stopped scrolling the carousel, so stop "fake dragging" the
            // {@link ViewPager} if was doing so before.
            if (mViewPager.isFakeDragging()) {
                mViewPager.endFakeDrag();
            }
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            // The user is scrolling the carousel, so send the scroll deltas to the
            // {@link ViewPager} so it can move in sync.
            if (mViewPager.isFakeDragging()) {
                mViewPager.fakeDragBy(oldl-l);
            }
        }

        @Override
        public void onTabSelected(int position) {
            // The user selected a tab, so update the {@link ViewPager}
            mViewPager.setCurrentItem(position);
        }
    };

    private OnScrollListener mVerticalScrollListener = new OnScrollListener() {

        @Override
        public void onScroll(
                AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mTabCarousel == null) {
                return;
            }
            // If the FIRST item is not visible on the screen, then the carousel must be pinned
            // at the top of the screen.
            if (firstVisibleItem != 0) {
                mTabCarousel.setY(-mTabCarousel.getAllowedVerticalScrollLength());
                return;
            }
            View topView = view.getChildAt(firstVisibleItem);
            if (topView == null) {
                return;
            }
            int amtToScroll = Math.max((int) view.getChildAt(firstVisibleItem).getY(),
                    -mTabCarousel.getAllowedVerticalScrollLength());
            mTabCarousel.setY(amtToScroll);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

    };
}
