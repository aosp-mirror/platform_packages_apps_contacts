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

package com.android.contacts.list;

import com.android.contacts.ContactsApplicationController;
import com.android.contacts.ContactsListActivity;
import com.android.contacts.R;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.PinnedHeaderListView;

import android.app.Fragment;
import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ContactEntryListFragment extends Fragment
        implements AdapterView.OnItemClickListener {

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private String mQueryString;

    private ContactsApplicationController mAppController;
    private ContactEntryListAdapter mAdapter;
    private ListView mListView;

    private boolean mLegacyCompatibility;
    private int mDisplayOrder;
    private ContextMenuAdapter mContextMenuAdapter;

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract ContactEntryListAdapter createListAdapter();
    protected abstract void onItemClick(int position, long id);

    public ContactEntryListAdapter getAdapter() {
        return mAdapter;
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        mSectionHeaderDisplayEnabled = flag;
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchResultsMode(boolean flag) {
        mSearchResultsMode = flag;
    }

    public boolean isSearchResultsMode() {
        return mSearchResultsMode;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
    }

    public boolean isLegacyCompatibility() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibility(boolean flag) {
        mLegacyCompatibility = flag;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        if (mAdapter != null) {
            mAdapter.setContactNameDisplayOrder(displayOrder);
        }
    }


    @Deprecated
    public void setContactsApplicationController(ContactsApplicationController controller) {
        mAppController = controller;
    }

    @Deprecated
    public ContactsApplicationController getContactsApplicationController() {
        return mAppController;
    }

    public void setContextMenuAdapter(ContextMenuAdapter adapter) {
        mContextMenuAdapter = adapter;
        if (mListView != null) {
            mListView.setOnCreateContextMenuListener(adapter);
        }
    }

    public ContextMenuAdapter getContextMenuAdapter() {
        return mContextMenuAdapter;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        View view = inflateView(inflater, container);
        mAdapter = createListAdapter();
        configureView(view);
        return view;
    }

    protected void configureView(View view) {
        mListView = (ListView)view.findViewById(android.R.id.list);
        if (mListView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }

        View emptyView = view.findViewById(com.android.internal.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }

        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        mListView.setDividerHeight(0);

        if (mContextMenuAdapter != null) {
            mListView.setOnCreateContextMenuListener(mContextMenuAdapter);
        }

        mAdapter.setContactNameDisplayOrder(mDisplayOrder);

        ((ContactsListActivity)getActivity()).setupListView(mAdapter, mListView);

        configurePinnedHeader();

        if (isSearchResultsMode()) {
            TextView titleText = (TextView)view.findViewById(R.id.search_results_for);
            if (titleText != null) {
                titleText.setText(Html.fromHtml(getActivity().getString(R.string.search_results_for,
                        "<b>" + getQueryString() + "</b>")));
            }
        }
    }

    private void configurePinnedHeader() {
        if (!mSectionHeaderDisplayEnabled) {
            return;
        }

        if (mListView instanceof PinnedHeaderListView) {
            PinnedHeaderListView pinnedHeaderList = (PinnedHeaderListView)mListView;
            View headerView = mAdapter.createPinnedHeaderView(pinnedHeaderList);
            pinnedHeaderList.setPinnedHeaderView(headerView);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideSoftKeyboard();

        onItemClick(position, id);
    }


    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }
}
