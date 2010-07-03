/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnFilterTextListener;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ToggleButton;

/**
 * Navigation bar at the top of the Contacts activity.
 */
public class NavigationBar implements OnFilterTextListener, OnClickListener {

    public interface Listener {
        void onNavigationBarChange();
    }

    private static final String EXTRA_KEY_DEFAULT_MODE = "navBar.defaultMode";
    private static final String EXTRA_KEY_MODE = "navBar.mode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";

    public static final int MODE_CONTACTS = 0;
    public static final int MODE_FAVORITES = 1;
    public static final int MODE_SEARCH = 2;

    private int mMode = MODE_CONTACTS;
    private int mDefaultMode = MODE_CONTACTS;
    private String mQueryString;
    private SearchEditText mSearchEditText;
    private View mNavigationBar;

    private final Context mContext;

    private Listener mListener;

    private ToggleButton mContactsButton;
    private ToggleButton mFavoritesButton;
    private ToggleButton mSearchButton;
    private ImageView mCancelSearchButton;

    public NavigationBar(Context context) {
        mContext = context;
    }

    public void onCreate(Bundle savedState, ContactsRequest request) {
        mDefaultMode = -1;
        mMode = -1;
        mQueryString = null;
        if (savedState != null) {
            mDefaultMode = savedState.getInt(EXTRA_KEY_DEFAULT_MODE, -1);
            mMode = savedState.getInt(EXTRA_KEY_MODE, -1);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);
        }

        int actionCode = request.getActionCode();
        if (mDefaultMode == -1) {
            mDefaultMode = actionCode == ContactsRequest.ACTION_DEFAULT
                    ? NavigationBar.MODE_CONTACTS
                    : NavigationBar.MODE_FAVORITES;
        }
        if (mMode == -1) {
            mMode = request.isSearchMode() ? NavigationBar.MODE_SEARCH : mDefaultMode;
        }
        if (mQueryString == null) {
            mQueryString = request.getQueryString();
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public View onCreateView(LayoutInflater inflater) {
        mNavigationBar = inflater.inflate(R.layout.navigation_bar, null);
        mSearchEditText = (SearchEditText)mNavigationBar.findViewById(R.id.search_src_text);
        mSearchEditText.setMaginfyingGlassEnabled(false);
        mSearchEditText.setOnFilterTextListener(this);
        mSearchEditText.setText(mQueryString);
        mContactsButton = (ToggleButton)mNavigationBar.findViewById(R.id.nav_contacts);
        mContactsButton.setOnClickListener(this);
        mFavoritesButton = (ToggleButton)mNavigationBar.findViewById(R.id.nav_favorites);
        mFavoritesButton.setOnClickListener(this);
        mSearchButton = (ToggleButton)mNavigationBar.findViewById(R.id.nav_search);
        mSearchButton.setOnClickListener(this);
        mCancelSearchButton = (ImageView)mNavigationBar.findViewById(R.id.nav_cancel_search);
        mCancelSearchButton.setOnClickListener(this);
        update();

        return mNavigationBar;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        mMode = mode;
        update();
        if (mListener != null) {
            mListener.onNavigationBarChange();
        }
    }

    public int getDefaultMode() {
        return mDefaultMode;
    }

    public void setDefaultMode(int defaultMode) {
        mDefaultMode = defaultMode;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        mSearchEditText.setText(query);
    }

    public void update() {
        switch(mMode) {
            case MODE_CONTACTS:
                mContactsButton.setChecked(true);
                mFavoritesButton.setChecked(false);
                mSearchButton.setChecked(false);
                mSearchButton.setVisibility(View.VISIBLE);
                mSearchEditText.setVisibility(View.GONE);
                mCancelSearchButton.setVisibility(View.GONE);
                break;
            case MODE_FAVORITES:
                mContactsButton.setChecked(false);
                mFavoritesButton.setChecked(true);
                mSearchButton.setChecked(false);
                mSearchButton.setVisibility(View.VISIBLE);
                mSearchEditText.setVisibility(View.GONE);
                mCancelSearchButton.setVisibility(View.GONE);
                break;
            case MODE_SEARCH:
                mContactsButton.setChecked(false);
                mFavoritesButton.setChecked(false);
                mSearchButton.setVisibility(View.GONE);
                mSearchEditText.setVisibility(View.VISIBLE);
                mSearchEditText.requestFocus();
                InputMethodManager inputMethodManager =
                    (InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(mSearchEditText, 0);
                mCancelSearchButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void toggleSearchMode() {
        setMode(mMode == MODE_SEARCH ? mDefaultMode : MODE_SEARCH);
    }

    @Override
    public void onClick(View view) {
        if (view == mSearchButton) {
            setMode(MODE_SEARCH);
        } else if (view == mContactsButton) {
            setMode(MODE_CONTACTS);
            setDefaultMode(MODE_CONTACTS);
        } else if (view == mFavoritesButton) {
            setMode(MODE_FAVORITES);
            setDefaultMode(MODE_FAVORITES);
        } else {        // mCancelSearchButton
            setMode(mDefaultMode);
        }
    }

    @Override
    public void onFilterChange(String queryString) {
        mQueryString = queryString;
        if (mListener != null) {
            mListener.onNavigationBarChange();
        }
    }

    @Override
    public void onCancelSearch() {
        setMode(mDefaultMode);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(EXTRA_KEY_DEFAULT_MODE, mDefaultMode);
        outState.putInt(EXTRA_KEY_MODE, mMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
    }
}
