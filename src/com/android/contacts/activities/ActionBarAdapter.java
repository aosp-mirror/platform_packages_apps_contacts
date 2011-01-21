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
import com.android.contacts.list.ContactListFilterController.ContactListFilterListener;
import com.android.contacts.list.ContactListFilterView;
import com.android.contacts.list.ContactsRequest;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryTextListener, OnCloseListener,
        ContactListFilterListener, OnFocusChangeListener {

    public interface Listener {
        void onAction();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";

    private boolean mSearchMode;
    private String mQueryString;

    private View mNavigationBar;
    private TextView mSearchLabel;
    private SearchView mSearchView;

    private final Context mContext;

    private Listener mListener;
    private ContactListFilterView mFilterView;
    private ContactListFilterController mFilterController;

    private boolean mEnabled;

    public ActionBarAdapter(Context context) {
        mContext = context;
    }

    public void onCreate(Bundle savedState, ContactsRequest request, ActionBar actionBar) {
        mQueryString = null;
        if (savedState != null) {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);
        } else {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
        }

        if (actionBar != null) {
            actionBar.setDisplayOptions(
                    ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        mNavigationBar = LayoutInflater.from(mContext).inflate(R.layout.navigation_bar, null);
        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        if (actionBar != null) {
            actionBar.setCustomView(mNavigationBar, layoutParams);
        }

        mFilterView = (ContactListFilterView) mNavigationBar.findViewById(R.id.filter_view);
        mSearchLabel = (TextView) mNavigationBar.findViewById(R.id.search_label);
        mSearchView = (SearchView) mNavigationBar.findViewById(R.id.search_view);

        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);
        mSearchView.setQuery(mQueryString, false);
        mSearchView.setQueryHint(mContext.getString(R.string.hint_findContacts));

        update();
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        update();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setContactListFilterController(ContactListFilterController controller) {
        mFilterController = controller;
        mFilterController.setAnchor(mFilterView);
        mFilterController.addListener(this);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mSearchView && hasFocus) {
            setSearchMode(true);
        }
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            update();
            if (mSearchMode) {
                mSearchView.requestFocus();
            } else {
                mSearchView.setQuery(null, false);
            }
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

    public void update() {
        if (!mEnabled) {
            mNavigationBar.setVisibility(View.GONE);
        } else if (mSearchMode) {
            mNavigationBar.setVisibility(View.VISIBLE);
            mSearchLabel.setVisibility(View.VISIBLE);
            mFilterView.setVisibility(View.GONE);
            if (mFilterController != null) {
                mFilterController.setEnabled(false);
            }
        } else {
            mNavigationBar.setVisibility(View.VISIBLE);
            mSearchLabel.setVisibility(View.GONE);
            mFilterView.setVisibility(View.VISIBLE);
            if (mFilterController != null){
                mFilterController.setEnabled(true);
                if (mFilterController.isLoaded()) {
                    mFilterView.setContactListFilter(mFilterController.getFilter());
                    mFilterView.setSingleAccount(mFilterController.getAccountCount() == 1);
                    mFilterView.bindView(false);
                }
            }
        }
    }

    @Override
    public boolean onQueryTextChange(String queryString) {
        mQueryString = queryString;
        if (!mSearchMode) {
            if (!TextUtils.isEmpty(queryString)) {
                setSearchMode(true);
            }
        } else if (mListener != null) {
            mListener.onAction();
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
    }

    public void onRestoreInstanceState(Bundle savedState) {
        mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
        mQueryString = savedState.getString(EXTRA_KEY_QUERY);
    }

    @Override
    public void onContactListFiltersLoaded() {
        update();
    }

    @Override
    public void onContactListFilterChanged() {
        update();
    }

    @Override
    public void onContactListFilterCustomizationRequest() {
    }
}
