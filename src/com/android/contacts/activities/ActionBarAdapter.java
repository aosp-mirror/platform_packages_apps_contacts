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
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryTextListener, OnCloseListener {

    public interface Listener {
        public enum Action {
            CHANGE_SEARCH_QUERY, START_SEARCH_MODE, STOP_SEARCH_MODE
        }

        void onAction(Action action);
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

    private ActionBar mActionBar;


    public ActionBarAdapter(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        mSearchLabelText = mContext.getString(R.string.search_label);
        mAlwaysShowSearchView = mContext.getResources().getBoolean(R.bool.always_show_search_view);
    }

    public void onCreate(Bundle savedState, ContactsRequest request, ActionBar actionBar) {
        mActionBar = actionBar;
        mQueryString = null;

        if (savedState != null) {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);
        } else {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
        }

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

        update();
    }

    public void setListener(Listener listener) {
        mListener = listener;
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

    public void update() {
        if (mSearchMode) {
            mActionBar.setDisplayShowCustomEnabled(true);
            if (mAlwaysShowSearchView) {
                // Tablet -- change the app title for the search mode
                mActionBar.setTitle(mSearchLabelText);
            } else {
                // Phone -- search view gets focus
                setFocusOnSearchView();
            }
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            if (mListener != null) {
                mListener.onAction(Action.START_SEARCH_MODE);
            }
        } else {
            mActionBar.setDisplayShowCustomEnabled(mAlwaysShowSearchView);
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            mActionBar.setTitle(null);
            if (mListener != null) {
                mListener.onAction(Action.STOP_SEARCH_MODE);
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
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mActionBar.getSelectedNavigationIndex());
    }

    public void onRestoreInstanceState(Bundle savedState) {
        mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
        mQueryString = savedState.getString(EXTRA_KEY_QUERY);
        mActionBar.setSelectedNavigationItem(savedState.getInt(EXTRA_KEY_SELECTED_TAB));
    }

    private void setFocusOnSearchView() {
        mSearchView.requestFocus();
        mSearchView.setIconified(false); // Workaround for the "IME not popping up" issue.
    }
}
