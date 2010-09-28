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

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryChangeListener;

import java.util.HashMap;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements TabListener, OnQueryChangeListener, OnCloseListener {

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

    private final Context mContext;

    private Listener mListener;

    private ActionBar mActionBar;
    private Tab mSearchTab;
    private Tab mContactsTab;
    private Tab mFavoritesTab;
    private SearchView mSearchView;
    private boolean mActive;
    private EditText mQueryTextView;

    public ActionBarAdapter(Context context) {
        mContext = context;
    }

    public void onCreate(Bundle savedState, ContactsRequest request, ActionBar actionBar) {
        mActionBar = actionBar;
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

        mActionBar.setTabNavigationMode();

        mContactsTab = mActionBar.newTab();
        mContactsTab.setText(mContext.getString(R.string.contactsList));
        mContactsTab.setTabListener(this);
        mActionBar.addTab(mContactsTab);

        mFavoritesTab = mActionBar.newTab();
        mFavoritesTab.setTabListener(this);
        mFavoritesTab.setText(mContext.getString(R.string.strequentList));
        mActionBar.addTab(mFavoritesTab);

        mSearchTab = mActionBar.newTab();
        mSearchTab.setTabListener(this);

        mSearchView = new SearchView(mContext);
        setSearchSelectionListener(mSearchView);
        mSearchView.setIconified(mMode != ContactBrowserMode.MODE_SEARCH);

        mSearchTab.setCustomView(mSearchView);
        mActionBar.addTab(mSearchTab);

        mActive = true;

        update();
    }

    private void setSearchSelectionListener(SearchView search) {
        mQueryTextView = (EditText) search.findViewById(com.android.internal.R.id.search_src_text);
        mQueryTextView.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    setMode(ContactBrowserMode.MODE_SEARCH);
                } else {
                    setMode(mDefaultMode);
                }
            }
        });
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        if (mMode != mode) {
            mMode = mode;
            update();
            if (mListener != null) {
                mListener.onAction();
            }
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
        setSearchViewQuery(query);
    }

    public void update() {
        if (!mActive) {
            return;
        }

        switch(mMode) {
            case ContactBrowserMode.MODE_CONTACTS:
                mActionBar.selectTab(mContactsTab);
                mSearchView.setOnCloseListener(null);
                mSearchView.setOnQueryChangeListener(null);
                mSearchView.setIconified(true);
                break;
            case ContactBrowserMode.MODE_FAVORITES:
                mActionBar.selectTab(mFavoritesTab);
                mSearchView.setOnCloseListener(null);
                mSearchView.setOnQueryChangeListener(null);
                mSearchView.setIconified(true);
                break;
            case ContactBrowserMode.MODE_SEARCH:
                setSearchViewQuery(mQueryString);
                mSearchView.setOnCloseListener(this);
                mSearchView.setOnQueryChangeListener(this);
                mActionBar.selectTab(mSearchTab);
                break;
        }
    }

    private void setSearchViewQuery(String query) {
        mSearchView.setQuery(query, false);
        // TODO Expose API on SearchView to do this
        mQueryTextView.selectAll();
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (!mActive) {
            return;
        }

        if (tab == mSearchTab) {
            setMode(ContactBrowserMode.MODE_SEARCH);
        } else if (tab == mContactsTab) {
            setMode(ContactBrowserMode.MODE_CONTACTS);
            setDefaultMode(ContactBrowserMode.MODE_CONTACTS);
        } else if (tab == mFavoritesTab) {
            setMode(ContactBrowserMode.MODE_FAVORITES);
            setDefaultMode(ContactBrowserMode.MODE_FAVORITES);
        } else {        // mCancelSearchButton
            setMode(mDefaultMode);
        }
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        // Nothing to do
    }

    @Override
    public boolean onQueryTextChanged(String queryString) {
        mQueryString = queryString;
        if (mListener != null) {
            mListener.onAction();
        }
        return true;
    }

    @Override
    public boolean onSubmitQuery(String query) {
        // Ignore submit query request
        return true;
    }

    @Override
    public boolean onClose() {
        mSearchView.setOnCloseListener(null);
        mSearchView.setOnQueryChangeListener(null);
        setMode(mDefaultMode);
        return false;  // OK to close
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
