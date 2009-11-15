/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/*
 * Tab widget that can contain more tabs than can fit on screen at once and scroll over them.
 */
public class ScrollingTabWidget extends RelativeLayout
        implements OnClickListener, ViewTreeObserver.OnGlobalFocusChangeListener,
        OnFocusChangeListener {

    private static final String TAG = "ScrollingTabWidget";

    private OnTabSelectionChangedListener mSelectionChangedListener;
    private int mSelectedTab = 0;
    private ImageView mLeftArrowView;
    private ImageView mRightArrowView;
    private HorizontalScrollView mTabsScrollWrapper;
    private TabStripView mTabsView;
    private LayoutInflater mInflater;

    // Keeps track of the left most visible tab.
    private int mLeftMostVisibleTabIndex = 0;

    public ScrollingTabWidget(Context context) {
        this(context, null);
    }

    public ScrollingTabWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollingTabWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        mInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        setFocusable(true);
        setOnFocusChangeListener(this);
        if (!hasFocus()) {
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        }

        mLeftArrowView = (ImageView) mInflater.inflate(R.layout.tab_left_arrow, this, false);
        mLeftArrowView.setOnClickListener(this);
        mRightArrowView = (ImageView) mInflater.inflate(R.layout.tab_right_arrow, this, false);
        mRightArrowView.setOnClickListener(this);
        mTabsScrollWrapper = (HorizontalScrollView) mInflater.inflate(
                R.layout.tab_layout, this, false);
        mTabsView = (TabStripView) mTabsScrollWrapper.findViewById(android.R.id.tabs);
        View accountNameView = mInflater.inflate(R.layout.tab_account_name, this, false);

        mLeftArrowView.setVisibility(View.INVISIBLE);
        mRightArrowView.setVisibility(View.INVISIBLE);

        addView(mTabsScrollWrapper);
        addView(mLeftArrowView);
        addView(mRightArrowView);
        addView(accountNameView);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final ViewTreeObserver treeObserver = getViewTreeObserver();
        if (treeObserver != null) {
            treeObserver.addOnGlobalFocusChangeListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final ViewTreeObserver treeObserver = getViewTreeObserver();
        if (treeObserver != null) {
            treeObserver.removeOnGlobalFocusChangeListener(this);
        }
    }

    protected void updateArrowVisibility() {
        int scrollViewLeftEdge = mTabsScrollWrapper.getScrollX();
        int tabsViewLeftEdge = mTabsView.getLeft();
        int scrollViewRightEdge = scrollViewLeftEdge + mTabsScrollWrapper.getWidth();
        int tabsViewRightEdge = mTabsView.getRight();

        int rightArrowCurrentVisibility = mRightArrowView.getVisibility();
        if (scrollViewRightEdge == tabsViewRightEdge
                && rightArrowCurrentVisibility == View.VISIBLE) {
            mRightArrowView.setVisibility(View.INVISIBLE);
        } else if (scrollViewRightEdge < tabsViewRightEdge
                && rightArrowCurrentVisibility != View.VISIBLE) {
            mRightArrowView.setVisibility(View.VISIBLE);
        }

        int leftArrowCurrentVisibility = mLeftArrowView.getVisibility();
        if (scrollViewLeftEdge == tabsViewLeftEdge
                && leftArrowCurrentVisibility == View.VISIBLE) {
            mLeftArrowView.setVisibility(View.INVISIBLE);
        } else if (scrollViewLeftEdge > tabsViewLeftEdge
                && leftArrowCurrentVisibility != View.VISIBLE) {
            mLeftArrowView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Returns the tab indicator view at the given index.
     *
     * @param index the zero-based index of the tab indicator view to return
     * @return the tab indicator view at the given index
     */
    public View getChildTabViewAt(int index) {
        return mTabsView.getChildAt(index);
    }

    /**
     * Returns the number of tab indicator views.
     *
     * @return the number of tab indicator views.
     */
    public int getTabCount() {
        return mTabsView.getChildCount();
    }

    /**
     * Returns the {@link ViewGroup} that actually contains the tabs. This is where the tab
     * views should be attached to when being inflated.
     */
    public ViewGroup getTabParent() {
        return mTabsView;
    }

    public void removeAllTabs() {
        mTabsView.removeAllViews();
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        updateArrowVisibility();
        super.dispatchDraw(canvas);
    }

    /**
     * Sets the current tab.
     * This method is used to bring a tab to the front of the Widget,
     * and is used to post to the rest of the UI that a different tab
     * has been brought to the foreground.
     *
     * Note, this is separate from the traditional "focus" that is
     * employed from the view logic.
     *
     * For instance, if we have a list in a tabbed view, a user may be
     * navigating up and down the list, moving the UI focus (orange
     * highlighting) through the list items.  The cursor movement does
     * not effect the "selected" tab though, because what is being
     * scrolled through is all on the same tab.  The selected tab only
     * changes when we navigate between tabs (moving from the list view
     * to the next tabbed view, in this example).
     *
     * To move both the focus AND the selected tab at once, please use
     * {@link #focusCurrentTab}. Normally, the view logic takes care of
     * adjusting the focus, so unless you're circumventing the UI,
     * you'll probably just focus your interest here.
     *
     *  @param index The tab that you want to indicate as the selected
     *  tab (tab brought to the front of the widget)
     *
     *  @see #focusCurrentTab
     */
    public void setCurrentTab(int index) {
        if (index < 0 || index >= getTabCount()) {
            return;
        }

        if (mSelectedTab < getTabCount()) {
            mTabsView.setSelected(mSelectedTab, false);
        }
        mSelectedTab = index;
        mTabsView.setSelected(mSelectedTab, true);
    }

    /**
     * Return index of the currently selected tab.
     */
    public int getCurrentTab() {
        return mSelectedTab;
    }

    /**
     * Sets the current tab and focuses the UI on it.
     * This method makes sure that the focused tab matches the selected
     * tab, normally at {@link #setCurrentTab}.  Normally this would not
     * be an issue if we go through the UI, since the UI is responsible
     * for calling TabWidget.onFocusChanged(), but in the case where we
     * are selecting the tab programmatically, we'll need to make sure
     * focus keeps up.
     *
     *  @param index The tab that you want focused (highlighted in orange)
     *  and selected (tab brought to the front of the widget)
     *
     *  @see #setCurrentTab
     */
    public void focusCurrentTab(int index) {
        if (index < 0 || index >= getTabCount()) {
            return;
        }

        setCurrentTab(index);
        getChildTabViewAt(index).requestFocus();

    }

    /**
     * Adds a tab to the list of tabs. The tab's indicator view is specified
     * by a layout id. InflateException will be thrown if there is a problem
     * inflating.
     *
     * @param layoutResId The layout id to be inflated to make the tab indicator.
     */
    public void addTab(int layoutResId) {
        addTab(mInflater.inflate(layoutResId, mTabsView, false));
    }

    /**
     * Adds a tab to the list of tabs. The tab's indicator view must be provided.
     *
     * @param child
     */
    public void addTab(View child) {
        if (child == null) {
            return;
        }

        if (child.getLayoutParams() == null) {
            final LayoutParams lp = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 0);
            child.setLayoutParams(lp);
        }

        // Ensure you can navigate to the tab with the keyboard, and you can touch it
        child.setFocusable(true);
        child.setClickable(true);
        child.setOnClickListener(new TabClickListener());
        child.setOnFocusChangeListener(this);

        mTabsView.addView(child);
    }

    /**
     * Provides a way for ViewContactActivity and EditContactActivity to be notified that the
     * user clicked on a tab indicator.
     */
    public void setTabSelectionListener(OnTabSelectionChangedListener listener) {
        mSelectionChangedListener = listener;
    }

    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (isTab(oldFocus) && !isTab(newFocus)) {
            onLoseFocus();
        }
    }

    public void onFocusChange(View v, boolean hasFocus) {
        if (v == this && hasFocus) {
            onObtainFocus();
            return;
        }

        if (hasFocus) {
            for (int i = 0; i < getTabCount(); i++) {
                if (getChildTabViewAt(i) == v) {
                    setCurrentTab(i);
                    mSelectionChangedListener.onTabSelectionChanged(i, false);
                    break;
                }
            }
        }
    }

    /**
     * Called when the {@link ScrollingTabWidget} gets focus. Here the
     * widget decides which of it's tabs should have focus.
     */
    protected void onObtainFocus() {
        // Setting this flag, allows the children of this View to obtain focus.
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        // Assign focus to the last selected tab.
        focusCurrentTab(mSelectedTab);
        mSelectionChangedListener.onTabSelectionChanged(mSelectedTab, false);
    }

    /**
     * Called when the focus has left the {@link ScrollingTabWidget} or its
     * descendants. At this time we want the children of this view to be marked
     * as un-focusable, so that next time focus is moved to the widget, the widget
     * gets control, and can assign focus where it wants.
     */
    protected void onLoseFocus() {
        // Setting this flag will effectively make the tabs unfocusable. This will
        // be toggled when the widget obtains focus again.
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    public boolean isTab(View v) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getChildTabViewAt(i) == v) {
                return true;
            }
        }
        return false;
    }

    private class TabClickListener implements OnClickListener {
        public void onClick(View v) {
            for (int i = 0; i < getTabCount(); i++) {
                if (getChildTabViewAt(i) == v) {
                    setCurrentTab(i);
                    mSelectionChangedListener.onTabSelectionChanged(i, true);
                    break;
                }
            }
        }
    }

    public interface OnTabSelectionChangedListener {
        /**
         * Informs the tab widget host which tab was selected. It also indicates
         * if the tab was clicked/pressed or just focused into.
         *
         * @param tabIndex index of the tab that was selected
         * @param clicked whether the selection changed due to a touch/click
         * or due to focus entering the tab through navigation. Pass true
         * if it was due to a press/click and false otherwise.
         */
        void onTabSelectionChanged(int tabIndex, boolean clicked);
    }

    public void onClick(View v) {
        updateLeftMostVisible();
        if (v == mRightArrowView && (mLeftMostVisibleTabIndex + 1 < getTabCount())) {
            tabScroll(true /* right */);
        } else if (v == mLeftArrowView && mLeftMostVisibleTabIndex > 0) {
            tabScroll(false /* left */);
        }
    }

    /*
     * Updates our record of the left most visible tab. We keep track of this explicitly
     * on arrow clicks, but need to re-calibrate after focus navigation.
     */
    protected void updateLeftMostVisible() {
        int viewableLeftEdge = mTabsScrollWrapper.getScrollX();

        if (mLeftArrowView.getVisibility() == View.VISIBLE) {
            viewableLeftEdge += mLeftArrowView.getWidth();
        }

        for (int i = 0; i < getTabCount(); i++) {
            View tab = getChildTabViewAt(i);
            int tabLeftEdge = tab.getLeft();
            if (tabLeftEdge >= viewableLeftEdge) {
                mLeftMostVisibleTabIndex = i;
                break;
            }
        }
    }

    /**
     * Scrolls the tabs by exactly one tab width.
     *
     * @param directionRight if true, scroll to the right, if false, scroll to the left.
     */
    protected void tabScroll(boolean directionRight) {
        int scrollWidth = 0;
        View newLeftMostVisibleTab = null;
        if (directionRight) {
            newLeftMostVisibleTab = getChildTabViewAt(++mLeftMostVisibleTabIndex);
        } else {
            newLeftMostVisibleTab = getChildTabViewAt(--mLeftMostVisibleTabIndex);
        }

        scrollWidth = newLeftMostVisibleTab.getLeft() - mTabsScrollWrapper.getScrollX();
        if (mLeftMostVisibleTabIndex > 0) {
            scrollWidth -= mLeftArrowView.getWidth();
        }
        mTabsScrollWrapper.smoothScrollBy(scrollWidth, 0);
    }

}
