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
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.widget.NotifyingSpinner;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryChangeListener;
import android.widget.TextView;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryChangeListener, OnCloseListener {

    public interface Listener {
        void onAction();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";

    private static final String KEY_MODE_DEFAULT = "mode_default";
    private static final String KEY_MODE_SEARCH = "mode_search";

    private boolean mSearchMode;
    private String mQueryString;
    private Bundle mSavedStateForSearchMode;
    private Bundle mSavedStateForDefaultMode;

    private View mNavigationBar;
    private TextView mSearchLabel;
    private SearchView mSearchView;

    private final Context mContext;

    private Listener mListener;
    private NotifyingSpinner mFilterSpinner;

    public ActionBarAdapter(Context context) {
        mContext = context;
    }

    public void onCreate(Bundle savedState, ContactsRequest request, ActionBar actionBar) {
        mQueryString = null;
        if (savedState != null) {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);
            mSavedStateForDefaultMode = savedState.getParcelable(KEY_MODE_DEFAULT);
            mSavedStateForSearchMode = savedState.getParcelable(KEY_MODE_SEARCH);
        } else {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
        }

        mNavigationBar = LayoutInflater.from(mContext).inflate(R.layout.navigation_bar, null);
        actionBar.setCustomNavigationMode(mNavigationBar);

        mFilterSpinner = (NotifyingSpinner) mNavigationBar.findViewById(R.id.filter_spinner);
        mSearchLabel = (TextView) mNavigationBar.findViewById(R.id.search_label);
        mSearchView = (SearchView) mNavigationBar.findViewById(R.id.search_view);
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setEnabled(false);
        mSearchView.setOnQueryChangeListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setQuery(mQueryString, false);

        updateVisibility();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setContactListFilterController(ContactListFilterController controller) {
        controller.setFilterSpinner(mFilterSpinner);
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            updateVisibility();
            if (mListener != null) {
                mListener.onAction();
            }
        }
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        mSearchView.setQuery(query, false);
    }

    public void updateVisibility() {
        if (mSearchMode) {
            mSearchLabel.setVisibility(View.VISIBLE);
            mFilterSpinner.setVisibility(View.GONE);
        } else {
            mSearchLabel.setVisibility(View.GONE);
            mFilterSpinner.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onQueryTextChanged(String queryString) {
        mQueryString = queryString;
        mSearchMode = !TextUtils.isEmpty(queryString);
        updateVisibility();
        if (mListener != null) {
            mListener.onAction();
        }
        return true;
    }

    @Override
    public boolean onSubmitQuery(String query) {
        return true;
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    public Bundle getSavedStateForSearchMode() {
        return mSavedStateForSearchMode;
    }

    public void setSavedStateForSearchMode(Bundle state) {
        mSavedStateForSearchMode = state;
    }

    public Bundle getSavedStateForDefaultMode() {
        return mSavedStateForDefaultMode;
    }

    public void setSavedStateForDefaultMode(Bundle state) {
        mSavedStateForDefaultMode = state;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        if (mSavedStateForDefaultMode != null) {
            outState.putParcelable(KEY_MODE_DEFAULT, mSavedStateForDefaultMode);
        }
        if (mSavedStateForSearchMode != null) {
            outState.putParcelable(KEY_MODE_SEARCH, mSavedStateForSearchMode);
        }
    }
}
