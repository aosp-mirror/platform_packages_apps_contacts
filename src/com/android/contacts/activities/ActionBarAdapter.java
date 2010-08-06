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

import java.util.HashMap;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnFilterTextListener, OnClickListener {

    public interface Listener {
        void onAction();
    }

    private static final String EXTRA_KEY_DEFAULT_MODE = "navBar.defaultMode";
    private static final String EXTRA_KEY_MODE = "navBar.mode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";

    private static final String KEY_MODE_CONTACTS = "mode_contacts";
    private static final String KEY_MODE_FAVORITES = "mode_favorites";
    private static final String KEY_MODE_SEARCH = "mode_search";

    private int mMode = ContactBrowserMode.MODE_CONTACTS;
    private int mDefaultMode = ContactBrowserMode.MODE_CONTACTS;
    private String mQueryString;
    private HashMap<Integer, Bundle> mSavedStateByMode = new HashMap<Integer, Bundle>();


    private SearchEditText mSearchEditText;
    private View mNavigationBar;

    private final Context mContext;

    private Listener mListener;

    private ToggleButton mContactsButton;
    private ToggleButton mFavoritesButton;
    private ToggleButton mSearchButton;
    private ImageView mCancelSearchButton;

    public ActionBarAdapter(Context context) {
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
            restoreSavedState(savedState, ContactBrowserMode.MODE_CONTACTS, KEY_MODE_CONTACTS);
            restoreSavedState(savedState, ContactBrowserMode.MODE_FAVORITES, KEY_MODE_FAVORITES);
            restoreSavedState(savedState, ContactBrowserMode.MODE_SEARCH, KEY_MODE_SEARCH);
        }

        int actionCode = request.getActionCode();
        if (mDefaultMode == -1) {
            mDefaultMode = actionCode == ContactsRequest.ACTION_DEFAULT
                    ? ContactBrowserMode.MODE_CONTACTS
                    : ContactBrowserMode.MODE_FAVORITES;
        }
        if (mMode == -1) {
            mMode = request.isSearchMode() ? ContactBrowserMode.MODE_SEARCH : mDefaultMode;
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
            mListener.onAction();
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
            case ContactBrowserMode.MODE_CONTACTS:
                mContactsButton.setChecked(true);
                mFavoritesButton.setChecked(false);
                mSearchButton.setChecked(false);
                mSearchButton.setVisibility(View.VISIBLE);
                mSearchEditText.setVisibility(View.GONE);
                mCancelSearchButton.setVisibility(View.GONE);
                break;
            case ContactBrowserMode.MODE_FAVORITES:
                mContactsButton.setChecked(false);
                mFavoritesButton.setChecked(true);
                mSearchButton.setChecked(false);
                mSearchButton.setVisibility(View.VISIBLE);
                mSearchEditText.setVisibility(View.GONE);
                mCancelSearchButton.setVisibility(View.GONE);
                break;
            case ContactBrowserMode.MODE_SEARCH:
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
        setMode(mMode == ContactBrowserMode.MODE_SEARCH
                ? mDefaultMode
                : ContactBrowserMode.MODE_SEARCH);
    }

    @Override
    public void onClick(View view) {
        if (view == mSearchButton) {
            setMode(ContactBrowserMode.MODE_SEARCH);
        } else if (view == mContactsButton) {
            setMode(ContactBrowserMode.MODE_CONTACTS);
            setDefaultMode(ContactBrowserMode.MODE_CONTACTS);
        } else if (view == mFavoritesButton) {
            setMode(ContactBrowserMode.MODE_FAVORITES);
            setDefaultMode(ContactBrowserMode.MODE_FAVORITES);
        } else {        // mCancelSearchButton
            setMode(mDefaultMode);
        }
    }

    @Override
    public void onFilterChange(String queryString) {
        mQueryString = queryString;
        if (mListener != null) {
            mListener.onAction();
        }
    }

    @Override
    public void onCancelSearch() {
        setMode(mDefaultMode);
    }

    public void saveStateForMode(int mode, Bundle state) {
        mSavedStateByMode.put(mode, state);
    }

    public Bundle getSavedStateForMode(int mode) {
        return mSavedStateByMode.get(mode);
    }

    public void clearSavedState(int mode) {
        mSavedStateByMode.remove(mode);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(EXTRA_KEY_DEFAULT_MODE, mDefaultMode);
        outState.putInt(EXTRA_KEY_MODE, mMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        saveInstanceState(outState, ContactBrowserMode.MODE_CONTACTS, KEY_MODE_CONTACTS);
        saveInstanceState(outState, ContactBrowserMode.MODE_FAVORITES, KEY_MODE_FAVORITES);
        saveInstanceState(outState, ContactBrowserMode.MODE_SEARCH, KEY_MODE_SEARCH);
    }

    private void saveInstanceState(Bundle outState, int mode, String key) {
        Bundle state = mSavedStateByMode.get(mode);
        if (state != null) {
            outState.putParcelable(key, state);
        }
    }

    private void restoreSavedState(Bundle savedState, int mode, String key) {
        Bundle bundle = savedState.getParcelable(key);
        if (bundle == null) {
            mSavedStateByMode.remove(mode);
        } else {
            mSavedStateByMode.put(mode, bundle);
        }
    }
}
