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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailFragmentCarousel;
import com.android.contacts.detail.ContactDetailTabCarousel;
import com.android.contacts.detail.ContactDetailUpdatesFragment;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.PhoneCapabilityTester;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.ArrayList;

// TODO: Use {@link ContactDetailLayoutController} so there isn't duplicated code
public class ContactDetailActivity extends ContactsActivity {
    private static final String TAG = "ContactDetailActivity";

    private static final String KEY_DETAIL_FRAGMENT_TAG = "detailFragTag";
    private static final String KEY_UPDATES_FRAGMENT_TAG = "updatesFragTag";

    public static final int FRAGMENT_COUNT = 2;

    private ContactLoader.Result mContactData;
    private Uri mLookupUri;

    private ContactLoaderFragment mLoaderFragment;
    private ContactDetailFragment mDetailFragment;
    private ContactDetailUpdatesFragment mUpdatesFragment;

    private ContactDetailTabCarousel mTabCarousel;
    private ViewPager mViewPager;

    private ContactDetailFragmentCarousel mFragmentCarousel;

    private ViewGroup mRootView;
    private ViewGroup mContentView;
    private LayoutInflater mInflater;

    private Handler mHandler = new Handler();

    /**
     * Whether or not the contact has updates, which dictates whether the
     * {@link ContactDetailUpdatesFragment} will be shown.
     */
    private boolean mContactHasUpdates;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            intent.setClass(this, PeopleActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.contact_detail_activity);
        mRootView = (ViewGroup) findViewById(R.id.contact_detail_view);
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Manually remove any {@link ViewPager} fragments if there was an orientation change
        // because the {@link ViewPager} is not used in both orientations. (If we leave the
        // fragments around, they'll be around in the {@link FragmentManager} but won't be visible
        // on screen and the {@link ViewPager} won't ask to initialize them again).
        if (savedState != null) {
            String aboutFragmentTag = savedState.getString(KEY_DETAIL_FRAGMENT_TAG);
            String updatesFragmentTag = savedState.getString(KEY_UPDATES_FRAGMENT_TAG);

            FragmentManager fragmentManager = getFragmentManager();
            mDetailFragment = (ContactDetailFragment) fragmentManager.findFragmentByTag(
                    aboutFragmentTag);
            mUpdatesFragment = (ContactDetailUpdatesFragment) fragmentManager.findFragmentByTag(
                    updatesFragmentTag);

            if (mDetailFragment != null && mUpdatesFragment != null) {
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.remove(mDetailFragment);
                ft.remove(mUpdatesFragment);
                ft.commit();
            }
        }

        // We want the UP affordance but no app icon.
        // Setting HOME_AS_UP, SHOW_TITLE and clearing SHOW_HOME does the trick.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setTitle("");
        }

        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactDetailFragment) {
            mDetailFragment = (ContactDetailFragment) fragment;
            mDetailFragment.setListener(mFragmentListener);
            mDetailFragment.setVerticalScrollListener(mVerticalScrollListener);
            mDetailFragment.setData(mLookupUri, mContactData);
            // If the contact has social updates, then the photo should be shown in the tab
            // carousel, so don't show the photo again in the scrolling list of contact details.
            // We also don't want to show the photo if there is a fragment carousel because then
            // the picture will already be on the left of the list of contact details.
            mDetailFragment.setShowPhotoInHeader(!mContactHasUpdates && mFragmentCarousel == null);
        } else if (fragment instanceof ContactDetailUpdatesFragment) {
            mUpdatesFragment = (ContactDetailUpdatesFragment) fragment;
            mUpdatesFragment.setData(mLookupUri, mContactData);
        } else if (fragment instanceof ContactLoaderFragment) {
            mLoaderFragment = (ContactLoaderFragment) fragment;
            mLoaderFragment.setListener(mLoaderFragmentListener);
            mLoaderFragment.loadUri(getIntent().getData());
        }
    }

    @Override
    public boolean onSearchRequested() {
        return true; // Don't respond to the search key.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.star, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
        ViewGroup starredContainer = (ViewGroup) getLayoutInflater().inflate(
                R.layout.favorites_star, null, false);
        final CheckBox starredView = (CheckBox) starredContainer.findViewById(R.id.star);
        starredView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mLookupUri != null) {
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            ContactDetailActivity.this, mLookupUri, starredView.isChecked());
                    ContactDetailActivity.this.startService(intent);
                }
            }
        });
        // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.setStarred(mContactData, starredView);
        }
        starredMenuItem.setActionView(starredContainer);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // First check if the {@link ContactLoaderFragment} can handle the key
        if (mLoaderFragment != null && mLoaderFragment.handleKeyDown(keyCode)) return true;

        // Otherwise find the correct fragment to handle the event
        FragmentKeyListener mCurrentFragment;
        switch (getCurrentPage()) {
            case 0:
                mCurrentFragment = mDetailFragment;
                break;
            case 1:
                mCurrentFragment = mUpdatesFragment;
                break;
            default:
                throw new IllegalStateException("Invalid current item for ViewPager");
        }
        if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return true;

        // In the last case, give the key event to the superclass.
        return super.onKeyDown(keyCode, event);
    }

    private int getCurrentPage() {
        // If the contact doesn't have any social updates, there is only 1 page (detail fragment).
        if (!mContactHasUpdates) {
            return 0;
        }
        // Otherwise find the current page based on the {@link ViewPager} or fragment carousel.
        if (mViewPager != null) {
            return mViewPager.getCurrentItem();
        } else if (mFragmentCarousel != null) {
            return mFragmentCarousel.getCurrentPage();
        }
        throw new IllegalStateException("Can't figure out the currently selected page. If the " +
                "contact has social updates, there must be a ViewPager or fragment carousel");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mViewPager != null) {
            outState.putString(KEY_DETAIL_FRAGMENT_TAG, mDetailFragment.getTag());
            outState.putString(KEY_UPDATES_FRAGMENT_TAG, mUpdatesFragment.getTag());
            return;
        }
    }

    private final ContactLoaderFragmentListener mLoaderFragmentListener =
            new ContactLoaderFragmentListener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onDetailsLoaded(final ContactLoader.Result result) {
            if (result == null) {
                return;
            }
            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mContactData = result;
                    mLookupUri = result.getLookupUri();
                    mContactHasUpdates = result.getSocialSnippet() != null;
                    invalidateOptionsMenu();
                    setupTitle();
                    if (mContactHasUpdates) {
                        setupContactWithUpdates();
                    } else {
                        setupContactWithoutUpdates();
                    }
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            startActivity(new Intent(Intent.ACTION_EDIT, contactLookupUri));
            finish();
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactDetailActivity.this, contactUri, true);
        }
    };

    /**
     * Setup the activity title and subtitle with contact name and company.
     */
    private void setupTitle() {
        CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(this, mContactData);
        String company =  ContactDetailDisplayUtils.getCompany(this, mContactData);

        ActionBar actionBar = getActionBar();
        actionBar.setTitle(displayName);
        actionBar.setSubtitle(company);
    }

    private void setupContactWithUpdates() {
        if (mContentView == null) {
            mContentView = (ViewGroup) mInflater.inflate(
                    R.layout.contact_detail_container_with_updates, mRootView, false);
            mRootView.addView(mContentView);

            // Make sure all needed views are retrieved. Note that narrow width screens have a
            // {@link ViewPager} and {@link ContactDetailTabCarousel}, while wide width screens have
            // a {@link ContactDetailFragmentCarousel}.
            mViewPager = (ViewPager) findViewById(R.id.pager);
            if (mViewPager != null) {
                mViewPager.removeAllViews();
                mViewPager.setAdapter(new ViewPagerAdapter(getFragmentManager()));
                mViewPager.setOnPageChangeListener(mOnPageChangeListener);
            }

            mTabCarousel = (ContactDetailTabCarousel) findViewById(R.id.tab_carousel);
            if (mTabCarousel != null) {
                mTabCarousel.setListener(mTabCarouselListener);
            }

            mFragmentCarousel = (ContactDetailFragmentCarousel)
                    findViewById(R.id.fragment_carousel);
        }

        // Then reset the contact data to the appropriate views
        if (mTabCarousel != null) {
            mTabCarousel.loadData(mContactData);
        }
        if (mFragmentCarousel != null) {
            if (mDetailFragment != null) mFragmentCarousel.setAboutFragment(mDetailFragment);
            if (mUpdatesFragment != null) mFragmentCarousel.setUpdatesFragment(mUpdatesFragment);
        }
        if (mDetailFragment != null) {
            mDetailFragment.setData(mLookupUri, mContactData);
        }
        if (mUpdatesFragment != null) {
            mUpdatesFragment.setData(mLookupUri, mContactData);
        }
    }

    private void setupContactWithoutUpdates() {
        if (mContentView == null) {
            mContentView = (ViewGroup) mInflater.inflate(
                    R.layout.contact_detail_container_without_updates, mRootView, false);
            mRootView.addView(mContentView);
        }
        // Reset contact data
        if (mDetailFragment != null) {
            mDetailFragment.setData(mLookupUri, mContactData);
        }
    }

    private final ContactDetailFragment.Listener mFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onItemClicked(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, Account account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };

    public class ViewPagerAdapter extends FragmentPagerAdapter{

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new ContactDetailFragment();
                case 1:
                    return new ContactDetailUpdatesFragment();
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
                    - mTabCarousel.getAllowedVerticalScrollLength());
            mTabCarousel.setY(amtToScroll);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

    };

    /**
     * This interface should be implemented by {@link Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
