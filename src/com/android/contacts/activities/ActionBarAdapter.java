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
 * limitations under the License.
 */

package com.android.contacts.activities;

import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.Listener.Action;
import com.android.contacts.list.ContactsRequest;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TabHost.OnTabChangeListener;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryTextListener, OnCloseListener {

    public interface Listener {
        public enum Action {
            CHANGE_SEARCH_QUERY, START_SEARCH_MODE, STOP_SEARCH_MODE
        }

        void onAction(Action action);

        /**
         * Called when the user selects a tab.  The new tab can be obtained using
         * {@link #getCurrentTab}.
         */
        void onSelectedTabChanged();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";
    private static final String EXTRA_KEY_SELECTED_TAB = "navBar.selectedTab";

    private boolean mSearchMode;
    private String mQueryString;

    private String mSearchLabelText;
    private SearchView mSearchView;

    private final Context mContext;
    private final boolean mAlwaysShowSearchView;

    private Listener mListener;

    private final ActionBar mActionBar;
    private final MyTabListener mTabListener = new MyTabListener();

    public enum TabState {
        FAVORITES, ALL, GROUPS;

        public static TabState fromInt(int value) {
            switch (value) {
                case 0:
                    return FAVORITES;
                case 1:
                    return ALL;
                case 2:
                    return GROUPS;
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    private TabState mCurrentTab = TabState.FAVORITES;

    public ActionBarAdapter(Context context, Listener listener, ActionBar actionBar) {
        mContext = context;
        mListener = listener;
        mActionBar = actionBar;
        mSearchLabelText = mContext.getString(R.string.search_label);
        mAlwaysShowSearchView = mContext.getResources().getBoolean(R.bool.always_show_search_view);

        // Set up search view.
        View customSearchView = LayoutInflater.from(mContext).inflate(R.layout.custom_action_bar,
                null);
        int searchViewWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.search_view_width);
        if (searchViewWidth == 0) {
            searchViewWidth = LayoutParams.MATCH_PARENT;
        }
        LayoutParams layoutParams = new LayoutParams(searchViewWidth, LayoutParams.WRAP_CONTENT);
        mSearchView = (SearchView) customSearchView.findViewById(R.id.search_view);
        mSearchView.setQueryHint(mContext.getString(R.string.hint_findContacts));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setQuery(mQueryString, false);
        mActionBar.setCustomView(customSearchView, layoutParams);

        mActionBar.setDisplayShowTitleEnabled(true);

        // TODO Just use a boolean resource instead of styles.
        TypedArray array = mContext.obtainStyledAttributes(null, R.styleable.ActionBarHomeIcon);
        boolean showHomeIcon = array.getBoolean(R.styleable.ActionBarHomeIcon_show_home_icon, true);
        array.recycle();
        mActionBar.setDisplayShowHomeEnabled(showHomeIcon);

        addTab(TabState.FAVORITES, mContext.getString(R.string.contactsFavoritesLabel));
        addTab(TabState.ALL, mContext.getString(R.string.contactsAllLabel));
        addTab(TabState.GROUPS, mContext.getString(R.string.contactsGroupsLabel));
    }

    public void initialize(Bundle savedState, ContactsRequest request) {
        if (savedState == null) {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
        } else {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);

            // Just set to the field here.  The listener will be notified by update().
            mCurrentTab = TabState.fromInt(savedState.getInt(EXTRA_KEY_SELECTED_TAB));
        }
        update();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void addTab(TabState tabState, String text) {
        final Tab tab = mActionBar.newTab();
        tab.setTag(tabState);
        tab.setText(text);
        tab.setTabListener(mTabListener);
        mActionBar.addTab(tab);
    }

    private class MyTabListener implements ActionBar.TabListener {
        /**
         * If true, it won't call {@link #setCurrentTab} in {@link #onTabSelected}.
         * This flag is used when we want to programmatically update the current tab without
         * {@link #onTabSelected} getting called.
         */
        public boolean mIgnoreTabSelected;

        @Override public void onTabReselected(Tab tab, FragmentTransaction ft) { }
        @Override public void onTabUnselected(Tab tab, FragmentTransaction ft) { }

        @Override public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (!mIgnoreTabSelected) {
                setCurrentTab((TabState)tab.getTag());
            }
        }
    }

    /**
     * Change the current tab, and notify the listener.
     */
    public void setCurrentTab(TabState tab) {
        if (tab == null) throw new NullPointerException();
        if (tab == mCurrentTab) {
            return;
        }
        mCurrentTab = tab;

        int index = mCurrentTab.ordinal();
        if ((mActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_TABS)
                && (index != mActionBar.getSelectedNavigationIndex())) {
            mActionBar.setSelectedNavigationItem(index);
        }

        if (mListener != null) mListener.onSelectedTabChanged();
    }

    public TabState getCurrentTab() {
        return mCurrentTab;
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            update();
            if (mSearchView == null) {
                return;
            }
            if (mSearchMode) {
                setFocusOnSearchView();
            } else {
                mSearchView.setQuery(null, false);
            }
        }
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        if (mSearchView != null) {
            mSearchView.setQuery(query, false);
        }
    }

    private void update() {
        if (mSearchMode) {
            mActionBar.setDisplayShowCustomEnabled(true);
            if (mAlwaysShowSearchView) {
                // Tablet -- change the app title for the search mode
                mActionBar.setTitle(mSearchLabelText);
            } else {
                // Phone -- search view gets focus
                setFocusOnSearchView();
            }
            if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_STANDARD) {
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            }
            if (mListener != null) {
                mListener.onAction(Action.START_SEARCH_MODE);
            }
        } else {
            mActionBar.setDisplayShowCustomEnabled(mAlwaysShowSearchView);
            if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_TABS) {
                // setNavigationMode will trigger onTabSelected() with the tab which was previously
                // selected.
                // The issue is that when we're first switching to the tab navigation mode after
                // screen orientation changes, onTabSelected() will get called with the first tab
                // (i.e. favorite), which would results in mCurrentTab getting set to FAVORITES and
                // we'd lose restored tab.
                // So let's just disable the callback here temporarily.  We'll notify the listener
                // after this anyway.
                mTabListener.mIgnoreTabSelected = true;
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                mActionBar.setSelectedNavigationItem(mCurrentTab.ordinal());
                mTabListener.mIgnoreTabSelected = false;
            }
            mActionBar.setTitle(null);
            if (mListener != null) {
                mListener.onAction(Action.STOP_SEARCH_MODE);
                mListener.onSelectedTabChanged();
            }
        }
    }

    @Override
    public boolean onQueryTextChange(String queryString) {
        // TODO: Clean up SearchView code because it keeps setting the SearchView query,
        // invoking onQueryChanged, setting up the fragment again, invalidating the options menu,
        // storing the SearchView again, and etc... unless we add in the early return statements.
        if (queryString.equals(mQueryString)) {
            return false;
        }
        mQueryString = queryString;
        if (!mSearchMode) {
            if (!TextUtils.isEmpty(queryString)) {
                setSearchMode(true);
            }
        } else if (mListener != null) {
            mListener.onAction(Action.CHANGE_SEARCH_QUERY);
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mCurrentTab.ordinal());
    }

    private void setFocusOnSearchView() {
        mSearchView.requestFocus();
        mSearchView.setIconified(false); // Workaround for the "IME not popping up" issue.
    }
}
