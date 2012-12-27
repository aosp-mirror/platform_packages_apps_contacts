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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.android.contacts.NfcHandler;
import com.android.contacts.R;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.model.Contact;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.widget.FrameLayoutWithOverlay;
import com.android.contacts.widget.TransitionAnimationView;

/**
 * Determines the layout of the contact card.
 */
public class ContactDetailLayoutController {

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String KEY_CONTACT_HAS_UPDATES = "contactHasUpdates";
    private static final String KEY_CURRENT_PAGE_INDEX = "currentPageIndex";

    private static final int TAB_INDEX_DETAIL = 0;
    private static final int TAB_INDEX_UPDATES = 1;

    private final int SINGLE_PANE_FADE_IN_DURATION = 275;

    /**
     * There are 4 possible layouts for the contact detail screen: TWO_COLUMN,
     * VIEW_PAGER_AND_TAB_CAROUSEL, FRAGMENT_CAROUSEL, and TWO_COLUMN_FRAGMENT_CAROUSEL.
     */
    private interface LayoutMode {
        /**
         * Tall and wide screen with details and updates shown side-by-side.
         */
        static final int TWO_COLUMN = 0;
        /**
         * Tall and narrow screen to allow swipe between the details and updates.
         */
        static final int VIEW_PAGER_AND_TAB_CAROUSEL = 1;
        /**
         * Short and wide screen to allow part of the other page to show.
         */
        static final int FRAGMENT_CAROUSEL = 2;
        /**
         * Same as FRAGMENT_CAROUSEL (allowing part of the other page to show) except the details
         * layout is similar to the details layout in TWO_COLUMN mode.
         */
        static final int TWO_COLUMN_FRAGMENT_CAROUSEL = 3;
    }

    private final Activity mActivity;
    private final LayoutInflater mLayoutInflater;
    private final FragmentManager mFragmentManager;

    private final View mViewContainer;
    private final TransitionAnimationView mTransitionAnimationView;
    private ContactDetailFragment mDetailFragment;
    private ContactDetailUpdatesFragment mUpdatesFragment;

    private View mDetailFragmentView;
    private View mUpdatesFragmentView;

    private final ViewPager mViewPager;
    private ContactDetailViewPagerAdapter mViewPagerAdapter;
    private int mViewPagerState;

    private final ContactDetailTabCarousel mTabCarousel;
    private final ContactDetailFragmentCarousel mFragmentCarousel;

    private final ContactDetailFragment.Listener mContactDetailFragmentListener;

    private Contact mContactData;
    private Uri mContactUri;

    private boolean mTabCarouselIsAnimating;

    private boolean mContactHasUpdates;

    private int mLayoutMode;

    public ContactDetailLayoutController(Activity activity, Bundle savedState,
            FragmentManager fragmentManager, TransitionAnimationView animationView,
            View viewContainer, ContactDetailFragment.Listener contactDetailFragmentListener) {

        if (fragmentManager == null) {
            throw new IllegalStateException("Cannot initialize a ContactDetailLayoutController "
                    + "without a non-null FragmentManager");
        }

        mActivity = activity;
        mLayoutInflater = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mFragmentManager = fragmentManager;
        mContactDetailFragmentListener = contactDetailFragmentListener;

        mTransitionAnimationView = animationView;

        // Retrieve views in case this is view pager and carousel mode
        mViewContainer = viewContainer;

        mViewPager = (ViewPager) viewContainer.findViewById(R.id.pager);
        mTabCarousel = (ContactDetailTabCarousel) viewContainer.findViewById(R.id.tab_carousel);

        // Retrieve view in case this is in fragment carousel mode
        mFragmentCarousel = (ContactDetailFragmentCarousel) viewContainer.findViewById(
                R.id.fragment_carousel);

        // Retrieve container views in case they are already in the XML layout
        mDetailFragmentView = viewContainer.findViewById(R.id.about_fragment_container);
        mUpdatesFragmentView = viewContainer.findViewById(R.id.updates_fragment_container);

        // Determine the layout mode based on the presence of certain views in the layout XML.
        if (mViewPager != null) {
            mLayoutMode = LayoutMode.VIEW_PAGER_AND_TAB_CAROUSEL;
        } else if (mFragmentCarousel != null) {
            if (PhoneCapabilityTester.isUsingTwoPanes(mActivity)) {
                mLayoutMode = LayoutMode.TWO_COLUMN_FRAGMENT_CAROUSEL;
            } else {
                mLayoutMode = LayoutMode.FRAGMENT_CAROUSEL;
            }
        } else {
            mLayoutMode = LayoutMode.TWO_COLUMN;
        }

        initialize(savedState);
    }

    private void initialize(Bundle savedState) {
        boolean fragmentsAddedToFragmentManager = true;
        mDetailFragment = (ContactDetailFragment) mFragmentManager.findFragmentByTag(
                ContactDetailViewPagerAdapter.ABOUT_FRAGMENT_TAG);
        mUpdatesFragment = (ContactDetailUpdatesFragment) mFragmentManager.findFragmentByTag(
                ContactDetailViewPagerAdapter.UPDATES_FRAGMENT_TAG);

        // If the detail fragment was found in the {@link FragmentManager} then we don't need to add
        // it again. Otherwise, create the fragments dynamically and remember to add them to the
        // {@link FragmentManager}.
        if (mDetailFragment == null) {
            mDetailFragment = new ContactDetailFragment();
            mUpdatesFragment = new ContactDetailUpdatesFragment();
            fragmentsAddedToFragmentManager = false;
        }

        mDetailFragment.setListener(mContactDetailFragmentListener);
        NfcHandler.register(mActivity, mDetailFragment);

        // Read from savedState if possible
        int currentPageIndex = 0;
        if (savedState != null) {
            mContactUri = savedState.getParcelable(KEY_CONTACT_URI);
            mContactHasUpdates = savedState.getBoolean(KEY_CONTACT_HAS_UPDATES);
            currentPageIndex = savedState.getInt(KEY_CURRENT_PAGE_INDEX, 0);
        }

        switch (mLayoutMode) {
            case LayoutMode.VIEW_PAGER_AND_TAB_CAROUSEL: {
                // Inflate 2 view containers to pass in as children to the {@link ViewPager},
                // which will in turn be the parents to the mDetailFragment and mUpdatesFragment
                // since the fragments must have the same parent view IDs in both landscape and
                // portrait layouts.
                mDetailFragmentView = mLayoutInflater.inflate(
                        R.layout.contact_detail_about_fragment_container, mViewPager, false);
                mUpdatesFragmentView = mLayoutInflater.inflate(
                        R.layout.contact_detail_updates_fragment_container, mViewPager, false);

                mViewPagerAdapter = new ContactDetailViewPagerAdapter();
                mViewPagerAdapter.setAboutFragmentView(mDetailFragmentView);
                mViewPagerAdapter.setUpdatesFragmentView(mUpdatesFragmentView);

                mViewPager.addView(mDetailFragmentView);
                mViewPager.addView(mUpdatesFragmentView);
                mViewPager.setAdapter(mViewPagerAdapter);
                mViewPager.setOnPageChangeListener(mOnPageChangeListener);

                if (!fragmentsAddedToFragmentManager) {
                    FragmentTransaction transaction = mFragmentManager.beginTransaction();
                    transaction.add(R.id.about_fragment_container, mDetailFragment,
                            ContactDetailViewPagerAdapter.ABOUT_FRAGMENT_TAG);
                    transaction.add(R.id.updates_fragment_container, mUpdatesFragment,
                            ContactDetailViewPagerAdapter.UPDATES_FRAGMENT_TAG);
                    transaction.commitAllowingStateLoss();
                    mFragmentManager.executePendingTransactions();
                }

                mTabCarousel.setListener(mTabCarouselListener);
                mTabCarousel.restoreCurrentTab(currentPageIndex);
                mDetailFragment.setVerticalScrollListener(
                        new VerticalScrollListener(TAB_INDEX_DETAIL));
                mUpdatesFragment.setVerticalScrollListener(
                        new VerticalScrollListener(TAB_INDEX_UPDATES));
                mViewPager.setCurrentItem(currentPageIndex);
                break;
            }
            case LayoutMode.TWO_COLUMN: {
                if (!fragmentsAddedToFragmentManager) {
                    FragmentTransaction transaction = mFragmentManager.beginTransaction();
                    transaction.add(R.id.about_fragment_container, mDetailFragment,
                            ContactDetailViewPagerAdapter.ABOUT_FRAGMENT_TAG);
                    transaction.add(R.id.updates_fragment_container, mUpdatesFragment,
                            ContactDetailViewPagerAdapter.UPDATES_FRAGMENT_TAG);
                    transaction.commitAllowingStateLoss();
                    mFragmentManager.executePendingTransactions();
                }
                break;
            }
            case LayoutMode.FRAGMENT_CAROUSEL:
            case LayoutMode.TWO_COLUMN_FRAGMENT_CAROUSEL: {
                // Add the fragments to the fragment containers in the carousel using a
                // {@link FragmentTransaction} if they haven't already been added to the
                // {@link FragmentManager}.
                if (!fragmentsAddedToFragmentManager) {
                    FragmentTransaction transaction = mFragmentManager.beginTransaction();
                    transaction.add(R.id.about_fragment_container, mDetailFragment,
                            ContactDetailViewPagerAdapter.ABOUT_FRAGMENT_TAG);
                    transaction.add(R.id.updates_fragment_container, mUpdatesFragment,
                            ContactDetailViewPagerAdapter.UPDATES_FRAGMENT_TAG);
                    transaction.commitAllowingStateLoss();
                    mFragmentManager.executePendingTransactions();
                }

                mFragmentCarousel.setFragmentViews(
                        (FrameLayoutWithOverlay) mDetailFragmentView,
                        (FrameLayoutWithOverlay) mUpdatesFragmentView);
                mFragmentCarousel.setCurrentPage(currentPageIndex);

                break;
            }
        }

        // Setup the layout if we already have a saved state
        if (savedState != null) {
            if (mContactHasUpdates) {
                showContactWithUpdates(false);
            } else {
                showContactWithoutUpdates();
            }
        }
    }

    public void setContactData(Contact data) {
        final boolean contactWasLoaded;
        final boolean contactHadUpdates;
        final boolean isDifferentContact;
        if (mContactData == null) {
            contactHadUpdates = false;
            contactWasLoaded = false;
            isDifferentContact = true;
        } else {
            contactHadUpdates = mContactHasUpdates;
            contactWasLoaded = true;
            isDifferentContact =
                    !UriUtils.areEqual(mContactData.getLookupUri(), data.getLookupUri());
        }
        mContactData = data;
        mContactHasUpdates = !data.getStreamItems().isEmpty();

        if (PhoneCapabilityTester.isUsingTwoPanes(mActivity)) {
            // Tablet: If we already showed data before, we want to cross-fade from screen to screen
            if (contactWasLoaded && mTransitionAnimationView != null && isDifferentContact) {
                mTransitionAnimationView.startMaskTransition(mContactData == null, -1);
            }
        } else {
            // Small screen: We are on our own screen. Fade the data in, but only the first time
            if (!contactWasLoaded) {
                mViewContainer.setAlpha(0.0f);
                final ViewPropertyAnimator animator = mViewContainer.animate();
                animator.alpha(1.0f);
                animator.setDuration(SINGLE_PANE_FADE_IN_DURATION);
            }
        }

        if (mContactHasUpdates) {
            showContactWithUpdates(
                    contactWasLoaded && contactHadUpdates == false);
        } else {
            showContactWithoutUpdates();
        }
    }

    public void showEmptyState() {
        switch (mLayoutMode) {
            case LayoutMode.FRAGMENT_CAROUSEL: {
                mFragmentCarousel.setCurrentPage(0);
                mFragmentCarousel.enableSwipe(false);
                mDetailFragment.showEmptyState();
                break;
            }
            case LayoutMode.TWO_COLUMN: {
                mDetailFragment.setShowStaticPhoto(false);
                mUpdatesFragmentView.setVisibility(View.GONE);
                mDetailFragment.showEmptyState();
                break;
            }
            case LayoutMode.TWO_COLUMN_FRAGMENT_CAROUSEL: {
                mFragmentCarousel.setCurrentPage(0);
                mFragmentCarousel.enableSwipe(false);
                mDetailFragment.setShowStaticPhoto(false);
                mUpdatesFragmentView.setVisibility(View.GONE);
                mDetailFragment.showEmptyState();
                break;
            }
            case LayoutMode.VIEW_PAGER_AND_TAB_CAROUSEL: {
                mDetailFragment.setShowStaticPhoto(false);
                mDetailFragment.showEmptyState();
                mTabCarousel.loadData(null);
                mTabCarousel.setVisibility(View.GONE);
                mViewPagerAdapter.enableSwipe(false);
                mViewPager.setCurrentItem(0);
                break;
            }
            default:
                throw new IllegalStateException("Invalid LayoutMode " + mLayoutMode);
        }
    }

    /**
     * Setup the layout for the contact with updates.
     * TODO: Clean up this method so it's easier to understand.
     */
    private void showContactWithUpdates(boolean animateStateChange) {
        if (mContactData == null) {
            return;
        }

        Uri previousContactUri = mContactUri;
        mContactUri = mContactData.getLookupUri();
        boolean isDifferentContact = !UriUtils.areEqual(previousContactUri, mContactUri);

        switch (mLayoutMode) {
            case LayoutMode.TWO_COLUMN: {
                if (!isDifferentContact && animateStateChange) {
                    // This is screen is very hard to animate properly, because there is such a hard
                    // cut from the regular version. A proper animation would have to reflow text
                    // and move things around. Doing a simple cross-fade instead.
                    mTransitionAnimationView.startMaskTransition(false, -1);
                }

                // Set the contact data (hide the static photo because the photo will already be in
                // the header that scrolls with contact details).
                mDetailFragment.setShowStaticPhoto(false);
                // Show the updates fragment
                mUpdatesFragmentView.setVisibility(View.VISIBLE);
                break;
            }
            case LayoutMode.VIEW_PAGER_AND_TAB_CAROUSEL: {
                // Update and show the tab carousel (also restore its last saved position)
                mTabCarousel.loadData(mContactData);
                mTabCarousel.restoreYCoordinate();
                mTabCarousel.setVisibility(View.VISIBLE);
                // Update ViewPager to allow swipe between all the fragments (to see updates)
                mViewPagerAdapter.enableSwipe(true);
                // If this is a different contact than before, then reset some views.
                if (isDifferentContact) {
                    resetViewPager();
                    resetTabCarousel();
                }
                if (!isDifferentContact && animateStateChange) {
                    mTabCarousel.animateAppear(mViewContainer.getWidth(),
                            mDetailFragment.getFirstListItemOffset());
                }
                break;
            }
            case LayoutMode.FRAGMENT_CAROUSEL: {
                // Allow swiping between all fragments
                mFragmentCarousel.enableSwipe(true);
                if (!isDifferentContact && animateStateChange) {
                    mFragmentCarousel.animateAppear();
                }
                break;
            }
            case LayoutMode.TWO_COLUMN_FRAGMENT_CAROUSEL: {
                // Allow swiping between all fragments
                mFragmentCarousel.enableSwipe(true);
                if (isDifferentContact) {
                    mFragmentCarousel.reset();
                }
                if (!isDifferentContact && animateStateChange) {
                    mFragmentCarousel.animateAppear();
                }
                mDetailFragment.setShowStaticPhoto(false);
                break;
            }
            default:
                throw new IllegalStateException("Invalid LayoutMode " + mLayoutMode);
        }

        if (isDifferentContact) {
            resetFragments();
        }

        mDetailFragment.setData(mContactUri, mContactData);
        mUpdatesFragment.setData(mContactUri, mContactData);
    }

    /**
     * Setup the layout for the contact without updates.
     * TODO: Clean up this method so it's easier to understand.
     */
    private void showContactWithoutUpdates() {
        if (mContactData == null) {
            return;
        }

        Uri previousContactUri = mContactUri;
        mContactUri = mContactData.getLookupUri();
        boolean isDifferentContact = !UriUtils.areEqual(previousContactUri, mContactUri);

        switch (mLayoutMode) {
            case LayoutMode.TWO_COLUMN:
                // Show the static photo which is next to the list of scrolling contact details
                mDetailFragment.setShowStaticPhoto(true);
                // Hide the updates fragment
                mUpdatesFragmentView.setVisibility(View.GONE);
                break;
            case LayoutMode.VIEW_PAGER_AND_TAB_CAROUSEL:
                // Hide the tab carousel
                mTabCarousel.setVisibility(View.GONE);
                // Update ViewPager to disable swipe so that it only shows the detail fragment
                // and switch to the detail fragment
                mViewPagerAdapter.enableSwipe(false);
                mViewPager.setCurrentItem(0, false /* smooth transition */);
                break;
            case LayoutMode.FRAGMENT_CAROUSEL:
                // Disable swipe so only the detail fragment shows
                mFragmentCarousel.setCurrentPage(0);
                mFragmentCarousel.enableSwipe(false);
                break;
            case LayoutMode.TWO_COLUMN_FRAGMENT_CAROUSEL:
                mFragmentCarousel.setCurrentPage(0);
                mFragmentCarousel.enableSwipe(false);
                mDetailFragment.setShowStaticPhoto(true);
                break;
            default:
                throw new IllegalStateException("Invalid LayoutMode " + mLayoutMode);
        }

        if (isDifferentContact) {
            resetFragments();
        }

        mDetailFragment.setData(mContactUri, mContactData);
    }

    private void resetTabCarousel() {
        mTabCarousel.reset();
    }

    private void resetViewPager() {
        mViewPager.setCurrentItem(0, false /* smooth transition */);
    }

    private void resetFragments() {
        mDetailFragment.resetAdapter();
        mUpdatesFragment.resetAdapter();
    }

    public FragmentKeyListener getCurrentPage() {
        switch (getCurrentPageIndex()) {
            case 0:
                return mDetailFragment;
            case 1:
                return mUpdatesFragment;
            default:
                throw new IllegalStateException("Invalid current item for ViewPager");
        }
    }

    private int getCurrentPageIndex() {
        // If the contact has social updates, then retrieve the current page based on the
        // {@link ViewPager} or fragment carousel.
        if (mContactHasUpdates) {
            if (mViewPager != null) {
                return mViewPager.getCurrentItem();
            } else if (mFragmentCarousel != null) {
                return mFragmentCarousel.getCurrentPage();
            }
        }
        // Otherwise return the default page (detail fragment).
        return 0;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
        outState.putBoolean(KEY_CONTACT_HAS_UPDATES, mContactHasUpdates);
        outState.putInt(KEY_CURRENT_PAGE_INDEX, getCurrentPageIndex());
    }

    private final OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        private ObjectAnimator mTabCarouselAnimator;

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // The user is horizontally dragging the {@link ViewPager}, so send
            // these scroll changes to the tab carousel. Ignore these events though if the carousel
            // is actually controlling the {@link ViewPager} scrolls because it will already be
            // in the correct position.
            if (mViewPager.isFakeDragging()) return;

            int x = (int) ((position + positionOffset) *
                    mTabCarousel.getAllowedHorizontalScrollLength());
            mTabCarousel.scrollTo(x, 0);
        }

        @Override
        public void onPageSelected(int position) {
            // Since the {@link ViewPager} has committed to a new page now (but may not have
            // finished scrolling yet), update the tab selection in the carousel.
            mTabCarousel.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (mViewPagerState == ViewPager.SCROLL_STATE_IDLE) {

                // If we are leaving the IDLE state, we are starting a swipe.
                // First cancel any pending animations on the tab carousel.
                cancelTabCarouselAnimator();

                // Sync the two lists because the list on the other page will start to show as
                // we swipe over more.
                syncScrollStateBetweenLists(mViewPager.getCurrentItem());

            } else if (state == ViewPager.SCROLL_STATE_IDLE) {

                // Otherwise if the {@link ViewPager} is idle now, a page has been selected and
                // scrolled into place. Perform an animation of the tab carousel is needed.
                int currentPageIndex = mViewPager.getCurrentItem();
                int tabCarouselOffset = (int) mTabCarousel.getY();
                boolean shouldAnimateTabCarousel;

                // Find the offset position of the first item in the list of the current page.
                int listOffset = getOffsetOfFirstItemInList(currentPageIndex);

                // If the list was able to successfully offset by the tab carousel amount, then
                // log this as the new Y coordinate for that page, and no animation is needed.
                if (listOffset == tabCarouselOffset) {
                    mTabCarousel.storeYCoordinate(currentPageIndex, tabCarouselOffset);
                    shouldAnimateTabCarousel = false;
                } else if (listOffset == Integer.MIN_VALUE) {
                    // If the offset of the first item in the list is unknown (i.e. the item
                    // is no longer visible on screen) then just animate the tab carousel to the
                    // previously logged position.
                    shouldAnimateTabCarousel = true;
                } else if (Math.abs(listOffset) < Math.abs(tabCarouselOffset)) {
                    // If the list could not offset the full amount of the tab carousel offset (i.e.
                    // the list can only be scrolled a tiny amount), then animate the carousel down
                    // to compensate.
                    mTabCarousel.storeYCoordinate(currentPageIndex, listOffset);
                    shouldAnimateTabCarousel = true;
                } else {
                    // By default, animate back to the Y coordinate of the tab carousel the last
                    // time the other page was selected.
                    shouldAnimateTabCarousel = true;
                }

                if (shouldAnimateTabCarousel) {
                    float desiredOffset = mTabCarousel.getStoredYCoordinateForTab(currentPageIndex);
                    if (desiredOffset != tabCarouselOffset) {
                        createTabCarouselAnimator(desiredOffset);
                        mTabCarouselAnimator.start();
                    }
                }
            }
            mViewPagerState = state;
        }

        private void createTabCarouselAnimator(float desiredValue) {
            mTabCarouselAnimator = ObjectAnimator.ofFloat(
                    mTabCarousel, "y", desiredValue).setDuration(75);
            mTabCarouselAnimator.setInterpolator(AnimationUtils.loadInterpolator(
                    mActivity, android.R.anim.accelerate_decelerate_interpolator));
            mTabCarouselAnimator.addListener(mTabCarouselAnimatorListener);
        }

        private void cancelTabCarouselAnimator() {
            if (mTabCarouselAnimator != null) {
                mTabCarouselAnimator.cancel();
                mTabCarouselAnimator = null;
                mTabCarouselIsAnimating = false;
            }
        }
    };

    private void syncScrollStateBetweenLists(int currentPageIndex) {
        // Since the user interacted with the currently visible page, we need to sync the
        // list on the other page (i.e. if the updates page is the current page, modify the
        // list in the details page).
        if (currentPageIndex == TAB_INDEX_UPDATES) {
            mDetailFragment.requestToMoveToOffset((int) mTabCarousel.getY());
        } else {
            mUpdatesFragment.requestToMoveToOffset((int) mTabCarousel.getY());
        }
    }

    private int getOffsetOfFirstItemInList(int currentPageIndex) {
        if (currentPageIndex == TAB_INDEX_DETAIL) {
            return mDetailFragment.getFirstListItemOffset();
        } else {
            return mUpdatesFragment.getFirstListItemOffset();
        }
    }

    /**
     * This listener keeps track of whether the tab carousel animation is currently going on or not,
     * in order to prevent other simultaneous changes to the Y position of the tab carousel which
     * can cause flicker.
     */
    private final AnimatorListener mTabCarouselAnimatorListener = new AnimatorListener() {

        @Override
        public void onAnimationCancel(Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            mTabCarouselIsAnimating = true;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mTabCarouselIsAnimating = true;
        }
    };

    private final ContactDetailTabCarousel.Listener mTabCarouselListener
            = new ContactDetailTabCarousel.Listener() {

        @Override
        public void onTouchDown() {
            // The user just started scrolling the carousel, so begin
            // "fake dragging" the {@link ViewPager} if it's not already
            // doing so.
            if (!mViewPager.isFakeDragging()) mViewPager.beginFakeDrag();
        }

        @Override
        public void onTouchUp() {
            // The user just stopped scrolling the carousel, so stop
            // "fake dragging" the {@link ViewPager} if it was doing so
            // before.
            if (mViewPager.isFakeDragging()) mViewPager.endFakeDrag();
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            // The user is scrolling the carousel, so send the scroll
            // deltas to the {@link ViewPager} so it can move in sync.
            if (mViewPager.isFakeDragging()) {
                mViewPager.fakeDragBy(oldl - l);
            }
        }

        @Override
        public void onTabSelected(int position) {
            // The user selected a tab, so update the {@link ViewPager}
            mViewPager.setCurrentItem(position);
        }
    };

    private final class VerticalScrollListener implements OnScrollListener {

        private final int mPageIndex;

        public VerticalScrollListener(int pageIndex) {
            mPageIndex = pageIndex;
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            int currentPageIndex = mViewPager.getCurrentItem();
            // Don't move the carousel if: 1) the contact does not have social updates because then
            // tab carousel must not be visible, 2) if the view pager is still being scrolled,
            // 3) if the current page being viewed is not this one, or 4) if the tab carousel
            // is already being animated vertically.
            if (!mContactHasUpdates || mViewPagerState != ViewPager.SCROLL_STATE_IDLE ||
                    mPageIndex != currentPageIndex || mTabCarouselIsAnimating) {
                return;
            }
            // If the FIRST item is not visible on the screen, then the carousel must be pinned
            // at the top of the screen.
            if (firstVisibleItem != 0) {
                mTabCarousel.moveToYCoordinate(mPageIndex,
                        -mTabCarousel.getAllowedVerticalScrollLength());
                return;
            }
            View topView = view.getChildAt(firstVisibleItem);
            if (topView == null) {
                return;
            }
            int amtToScroll = Math.max((int) view.getChildAt(firstVisibleItem).getY(),
                    -mTabCarousel.getAllowedVerticalScrollLength());
            mTabCarousel.moveToYCoordinate(mPageIndex, amtToScroll);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            // Once the list has become IDLE, check if we need to sync the scroll position of
            // the other list now. This will make swiping faster by doing the re-layout now
            // (instead of at the start of a swipe). However, there will still be another check
            // when we start swiping if the scroll positions are correct (to catch the edge case
            // where the user flings and immediately starts a swipe so we never get the idle state).
            if (scrollState == SCROLL_STATE_IDLE) {
                syncScrollStateBetweenLists(mPageIndex);
            }
        }
    }
}
