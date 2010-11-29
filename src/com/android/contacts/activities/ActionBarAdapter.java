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
import android.widget.SearchView.OnQueryChangeListener;
import android.widget.TextView;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryChangeListener, OnCloseListener,
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
    private View mFilterIndicator;
    private ContactListFilterController mFilterController;
    private View mFilterContainer;

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

        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);

        mNavigationBar = LayoutInflater.from(mContext).inflate(R.layout.navigation_bar, null);
        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        actionBar.setCustomView(mNavigationBar, layoutParams);

        mFilterContainer = mNavigationBar.findViewById(R.id.filter_container);
        mFilterView = (ContactListFilterView) mNavigationBar.findViewById(R.id.filter_view);
        mSearchLabel = (TextView) mNavigationBar.findViewById(R.id.search_label);
        mFilterIndicator = mNavigationBar.findViewById(R.id.filter_indicator);
        mSearchView = (SearchView) mNavigationBar.findViewById(R.id.search_view);

        mSearchView.setOnQueryChangeListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);
        mSearchView.setQuery(mQueryString, false);

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
        mFilterController.setAnchor(mFilterContainer);
        mFilterController.addListener(this);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v != mSearchView) {
            return;
        }

        // When we switch search mode on/off, the activity may need to change
        // fragments, which may lead to focus temporarily leaving the search
        // view or coming back to it, which could lead to an infinite loop.
        // Postponing the change breaks that loop.
        mNavigationBar.post(new Runnable() {

            @Override
            public void run() {
                setSearchMode(mSearchView.hasFocus());
            }
        });
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
            mFilterIndicator.setVisibility(View.INVISIBLE);
            if (mFilterController != null) {
                mFilterController.setEnabled(false);
            }
        } else {
            mNavigationBar.setVisibility(View.VISIBLE);
            mSearchLabel.setVisibility(View.GONE);
            mFilterView.setVisibility(View.VISIBLE);
            boolean showIndicator = false;
            if (mFilterController != null){
                mFilterController.setEnabled(true);
                if (mFilterController.isLoaded()) {
                    mFilterView.setContactListFilter(mFilterController.getFilter());
                    mFilterView.setSingleAccount(mFilterController.getAccountCount() == 1);
                    mFilterView.bindView(false);
                    showIndicator = mFilterController.getFilterCount() > 1;
                }
            }
            mFilterIndicator.setVisibility(showIndicator ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public boolean onQueryTextChanged(String queryString) {
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
    public boolean onSubmitQuery(String query) {
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
