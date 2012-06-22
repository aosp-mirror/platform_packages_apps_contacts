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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewPropertyAnimator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.Contact;
import com.android.contacts.util.MoreMath;
import com.android.contacts.util.SchedulingUtils;

/**
 * This is a horizontally scrolling carousel with 2 tabs: one to see info about the contact and
 * one to see updates from the contact.
 */
public class ContactDetailTabCarousel extends HorizontalScrollView implements OnTouchListener {

    private static final String TAG = ContactDetailTabCarousel.class.getSimpleName();

    private static final int TRANSITION_TIME = 200;
    private static final int TRANSITION_MOVE_IN_TIME = 150;

    private static final int TAB_INDEX_ABOUT = 0;
    private static final int TAB_INDEX_UPDATES = 1;
    private static final int TAB_COUNT = 2;

    /** Tab width as defined as a fraction of the screen width */
    private float mTabWidthScreenWidthFraction;

    /** Tab height as defined as a fraction of the screen width */
    private float mTabHeightScreenWidthFraction;

    /** Height in pixels of the shadow under the tab carousel */
    private int mTabShadowHeight;

    private ImageView mPhotoView;
    private View mPhotoViewOverlay;
    private TextView mStatusView;
    private ImageView mStatusPhotoView;
    private final ContactDetailPhotoSetter mPhotoSetter = new ContactDetailPhotoSetter();

    private Listener mListener;

    private int mCurrentTab = TAB_INDEX_ABOUT;

    private View mTabAndShadowContainer;
    private View mShadow;
    private CarouselTab mAboutTab;
    private View mTabDivider;
    private CarouselTab mUpdatesTab;

    /** Last Y coordinate of the carousel when the tab at the given index was selected */
    private final float[] mYCoordinateArray = new float[TAB_COUNT];

    private int mTabDisplayLabelHeight;

    private boolean mScrollToCurrentTab = false;
    private int mLastScrollPosition = Integer.MIN_VALUE;
    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;
    private int mAllowedVerticalScrollLength = Integer.MIN_VALUE;

    /** Factor to scale scroll-amount sent to listeners. */
    private float mScrollScaleFactor = 1.0f;

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
        mTabShadowHeight = resources.getDimensionPixelSize(
                R.dimen.detail_contact_photo_shadow_height);
        mTabWidthScreenWidthFraction = resources.getFraction(
                R.fraction.tab_width_screen_width_percentage, 1, 1);
        mTabHeightScreenWidthFraction = resources.getFraction(
                R.fraction.tab_height_screen_width_percentage, 1, 1);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabAndShadowContainer = findViewById(R.id.tab_and_shadow_container);
        mAboutTab = (CarouselTab) findViewById(R.id.tab_about);
        mAboutTab.setLabel(mContext.getString(R.string.contactDetailAbout));
        mAboutTab.setOverlayOnClickListener(mAboutTabTouchInterceptListener);

        mTabDivider = findViewById(R.id.tab_divider);

        mUpdatesTab = (CarouselTab) findViewById(R.id.tab_update);
        mUpdatesTab.setLabel(mContext.getString(R.string.contactDetailUpdates));
        mUpdatesTab.setOverlayOnClickListener(mUpdatesTabTouchInterceptListener);

        mShadow = findViewById(R.id.shadow);

        // Retrieve the photo view for the "about" tab
        // TODO: This should be moved down to mAboutTab, so that it hosts its own controls
        mPhotoView = (ImageView) mAboutTab.findViewById(R.id.photo);
        mPhotoViewOverlay = mAboutTab.findViewById(R.id.photo_overlay);

        // Retrieve the social update views for the "updates" tab
        // TODO: This should be moved down to mUpdatesTab, so that it hosts its own controls
        mStatusView = (TextView) mUpdatesTab.findViewById(R.id.status);
        mStatusPhotoView = (ImageView) mUpdatesTab.findViewById(R.id.status_photo);

        // Workaround for framework issue... it shouldn't be necessary to have a
        // clickable object in the hierarchy, but if not the horizontal scroll
        // behavior doesn't work. Note: the "About" tab doesn't need this
        // because we set a real click-handler elsewhere.
        mStatusView.setClickable(true);
        mStatusPhotoView.setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        // Compute the width of a tab as a fraction of the screen width
        int tabWidth = Math.round(mTabWidthScreenWidthFraction * screenWidth);

        // Find the allowed scrolling length by subtracting the current visible screen width
        // from the total length of the tabs.
        mAllowedHorizontalScrollLength = tabWidth * TAB_COUNT - screenWidth;

        // Scrolling by mAllowedHorizontalScrollLength causes listeners to
        // scroll by the entire screen amount; compute the scale-factor
        // necessary to make this so.
        if (mAllowedHorizontalScrollLength == 0) {
            // Guard against divide-by-zero.
            // Note: this hard-coded value prevents a crash, but won't result in the
            // desired scrolling behavior.  We rely on the framework calling onMeasure()
            // again with a non-zero screen width.
            mScrollScaleFactor = 1.0f;
            Log.w(TAG, "set scale-factor to 1.0 to avoid divide-by-zero");
        } else {
            mScrollScaleFactor = screenWidth / mAllowedHorizontalScrollLength;
        }

        int tabHeight = Math.round(screenWidth * mTabHeightScreenWidthFraction) + mTabShadowHeight;
        // Set the child {@link LinearLayout} to be TAB_COUNT * the computed tab width so that the
        // {@link LinearLayout}'s children (which are the tabs) will evenly split that width.
        if (getChildCount() > 0) {
            View child = getChildAt(0);

            // add 1 dip of separation between the tabs
            final int seperatorPixels =
                    (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                    getResources().getDisplayMetrics()) + 0.5f);

            child.measure(
                    MeasureSpec.makeMeasureSpec(
                            TAB_COUNT * tabWidth +
                            (TAB_COUNT - 1) * seperatorPixels, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        mAllowedVerticalScrollLength = tabHeight - mTabDisplayLabelHeight - mTabShadowHeight;
        setMeasuredDimension(
                resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(tabHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // Defer this stuff until after the layout has finished.  This is because
        // updateAlphaLayers() ultimately results in another layout request, and
        // the framework currently can't handle this safely.
        if (!mScrollToCurrentTab) return;
        mScrollToCurrentTab = false;
        SchedulingUtils.doAfterLayout(this, new Runnable() {
            @Override
            public void run() {
                scrollTo(mCurrentTab == TAB_INDEX_ABOUT ? 0 : mAllowedHorizontalScrollLength, 0);
                updateAlphaLayers();
            }
        });
    }

    /** When clicked, selects the corresponding tab. */
    private class TabClickListener implements OnClickListener {
        private final int mTab;

        public TabClickListener(int tab) {
            super();
            mTab = tab;
        }

        @Override
        public void onClick(View v) {
            mListener.onTabSelected(mTab);
        }
    }

    private final TabClickListener mAboutTabTouchInterceptListener =
            new TabClickListener(TAB_INDEX_ABOUT);

    private final TabClickListener mUpdatesTabTouchInterceptListener =
            new TabClickListener(TAB_INDEX_UPDATES);

    /**
     * Does in "appear" animation to allow a seamless transition from
     * the "No updates" mode.
     * @param width Width of the container. As we haven't been layed out yet, we can't know
     * @param scrollOffset The offset by how far we scrolled, where 0=not scrolled, -x=scrolled by
     * x pixels, Integer.MIN_VALUE=scrolled so far that the image is not visible in "no updates"
     * mode of this screen
     */
    public void animateAppear(int width, int scrollOffset) {
        final float photoHeight = mTabHeightScreenWidthFraction * width;
        final boolean animateZoomAndFade;
        int pixelsToScrollVertically = 0;

        // Depending on how far we are scrolled down, there is one of three animations:
        //   - Zoom and fade the picture (if it is still visible)
        //   - Scroll, zoom and fade (if the picture is mostly invisible and we now have a
        //     bigger visible region due to the pinning)
        //   - Just scroll if the picture is completely invisible. This time, no zoom is needed
        if (scrollOffset == Integer.MIN_VALUE) {
            // animate in completely by scrolling. no need for zooming here
            pixelsToScrollVertically = mTabDisplayLabelHeight;
            animateZoomAndFade = false;
        } else {
            final int pixelsOfPhotoLeft = Math.round(photoHeight) + scrollOffset;
            if (pixelsOfPhotoLeft > mTabDisplayLabelHeight) {
                // nothing to scroll
                pixelsToScrollVertically = 0;
            } else {
                pixelsToScrollVertically = mTabDisplayLabelHeight - pixelsOfPhotoLeft;
            }
            animateZoomAndFade = true;
        }

        if (pixelsToScrollVertically != 0) {
            // We can't animate ourselves here, because our own translation is needed for the user's
            // scrolling. Instead, we use our only child. As we are transparent, that is just as
            // good
            mTabAndShadowContainer.setTranslationY(-pixelsToScrollVertically);
            final ViewPropertyAnimator animator = mTabAndShadowContainer.animate();
            animator.translationY(0.0f);
            animator.setDuration(TRANSITION_MOVE_IN_TIME);
        }

        if (animateZoomAndFade) {
            // Hack: We have two types of possible layouts:
            //   If the picture is square, it is square in both "with updates" and "without updates"
            //     --> no need for scale animation here
            //     example: 10inch tablet portrait
            //   If the picture is non-square, it is full-width in "without updates" and something
            //     arbitrary in "with updates"
            //     --> do animation with container
            //     example: 4.6inch phone portrait
            final boolean squarePicture =
                    mTabWidthScreenWidthFraction == mTabHeightScreenWidthFraction;
            final int firstTransitionTime;
            if (squarePicture) {
                firstTransitionTime = 0;
            } else {
                // For x, we need to scale our container so we'll animate the whole tab
                // (unfortunately, we need to have the text invisible during this transition as it
                // would also be stretched)
                float revScale = 1.0f/mTabWidthScreenWidthFraction;
                mAboutTab.setScaleX(revScale);
                mAboutTab.setPivotX(0.0f);
                final ViewPropertyAnimator aboutAnimator = mAboutTab.animate();
                aboutAnimator.setDuration(TRANSITION_TIME);
                aboutAnimator.scaleX(1.0f);

                // For y, we need to scale only the picture itself because we want it to be cropped
                mPhotoView.setScaleY(revScale);
                mPhotoView.setPivotY(photoHeight * 0.5f);
                final ViewPropertyAnimator photoAnimator = mPhotoView.animate();
                photoAnimator.setDuration(TRANSITION_TIME);
                photoAnimator.scaleY(1.0f);
                firstTransitionTime = TRANSITION_TIME;
            }

            // Animate in the labels after the above transition is finished
            mAboutTab.fadeInLabelViewAnimator(firstTransitionTime, true);
            mUpdatesTab.fadeInLabelViewAnimator(firstTransitionTime, false);

            final float pixelsToTranslate = (1.0f - mTabWidthScreenWidthFraction) * width;
            // Views to translate
            for (View view : new View[] { mUpdatesTab, mTabDivider }) {
                view.setTranslationX(pixelsToTranslate);
                final ViewPropertyAnimator translateAnimator = view.animate();
                translateAnimator.translationX(0.0f);
                translateAnimator.setDuration(TRANSITION_TIME);
            }

            // Another hack: If the picture is square, there is no shadow in "Without updates"
            //    --> fade it in after the translations are done
            if (squarePicture) {
                mShadow.setAlpha(0.0f);
                mShadow.animate().setStartDelay(TRANSITION_TIME).alpha(1.0f);
            }
        }
    }

    private void updateAlphaLayers() {
        float alpha = mLastScrollPosition * MAX_ALPHA / mAllowedHorizontalScrollLength;
        alpha = MoreMath.clamp(alpha, 0.0f, 1.0f);
        mAboutTab.setAlphaLayerValue(alpha);
        mUpdatesTab.setAlphaLayerValue(MAX_ALPHA - alpha);
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);

        // Guard against framework issue where onScrollChanged() is called twice
        // for each touch-move event.  This wreaked havoc on the tab-carousel: the
        // view-pager moved twice as fast as it should because we called fakeDragBy()
        // twice with the same value.
        if (mLastScrollPosition == x) return;

        // Since we never completely scroll the about/updates tabs off-screen,
        // the draggable range is less than the width of the carousel. Our
        // listeners don't care about this... if we scroll 75% percent of our
        // draggable range, they want to scroll 75% of the entire carousel
        // width, not the same number of pixels that we scrolled.
        int scaledL = (int) (x * mScrollScaleFactor);
        int oldScaledL = (int) (oldX * mScrollScaleFactor);
        mListener.onScrollChanged(scaledL, y, oldScaledL, oldY);

        mLastScrollPosition = x;
        updateAlphaLayers();
    }

    /**
     * Reset the carousel to the start position (i.e. because new data will be loaded in for a
     * different contact).
     */
    public void reset() {
        scrollTo(0, 0);
        setCurrentTab(0);
        moveToYCoordinate(0, 0);
    }

    /**
     * Set the current tab that should be restored when the view is first laid out.
     */
    public void restoreCurrentTab(int position) {
        setCurrentTab(position);
        // It is only possible to scroll the view after onMeasure() has been called (where the
        // allowed horizontal scroll length is determined). Hence, set a flag that will be read
        // in onLayout() after the children and this view have finished being laid out.
        mScrollToCurrentTab = true;
    }

    /**
     * Restore the Y position of this view to the last manually requested value. This can be done
     * after the parent has been re-laid out again, where this view's position could have been
     * lost if the view laid outside its parent's bounds.
     */
    public void restoreYCoordinate() {
        setY(getStoredYCoordinateForTab(mCurrentTab));
    }

    /**
     * Request that the view move to the given Y coordinate. Also store the Y coordinate as the
     * last requested Y coordinate for the given tabIndex.
     */
    public void moveToYCoordinate(int tabIndex, float y) {
        setY(y);
        storeYCoordinate(tabIndex, y);
    }

    /**
     * Store this information as the last requested Y coordinate for the given tabIndex.
     */
    public void storeYCoordinate(int tabIndex, float y) {
        mYCoordinateArray[tabIndex] = y;
    }

    /**
     * Returns the stored Y coordinate of this view the last time the user was on the selected
     * tab given by tabIndex.
     */
    public float getStoredYCoordinateForTab(int tabIndex) {
        return mYCoordinateArray[tabIndex];
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
        final CarouselTab selected, deselected;

        switch (position) {
            case TAB_INDEX_ABOUT:
                selected = mAboutTab;
                deselected = mUpdatesTab;
                break;
            case TAB_INDEX_UPDATES:
                selected = mUpdatesTab;
                deselected = mAboutTab;
                break;
            default:
                throw new IllegalStateException("Invalid tab position " + position);
        }
        selected.showSelectedState();
        selected.setOverlayClickable(false);
        deselected.showDeselectedState();
        deselected.setOverlayClickable(true);
        mCurrentTab = position;
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(Contact contactData) {
        if (contactData == null) return;

        // TODO: Move this into the {@link CarouselTab} class when the updates
        // fragment code is more finalized.
        final boolean expandOnClick = contactData.getPhotoUri() != null;
        final OnClickListener listener = mPhotoSetter.setupContactPhotoForClick(
                mContext, contactData, mPhotoView, expandOnClick);

        if (expandOnClick || contactData.isWritableContact(mContext)) {
            mPhotoViewOverlay.setOnClickListener(listener);
        } else {
            // Work around framework issue... if we instead use
            // setClickable(false), then we can't swipe horizontally.
            mPhotoViewOverlay.setOnClickListener(null);
        }

        ContactDetailDisplayUtils.setSocialSnippet(
                mContext, contactData, mStatusView, mStatusPhotoView);
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
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onTouchUp();
                return true;
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
