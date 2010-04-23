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
import com.android.contacts.widget.PinnedHeaderListView;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Common base class for configurations of various contact-related lists, e.g.
 * contact list, phone number list etc.
 */
public abstract class ContactEntryListConfiguration {

    private final Context mContext;
    private final ContactsApplicationController mApplicationController;
    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private String mQueryString;

    public ContactEntryListConfiguration(Context context,
            ContactsApplicationController applicationController) {
        this.mContext = context;
        this.mApplicationController = applicationController;
    }

    public Context getContext() {
        return mContext;
    }

    public ContactsApplicationController getApplicationController() {
        return mApplicationController;
    }

    protected abstract View inflateView();
    protected abstract ListAdapter createListAdapter();
    protected abstract ContactEntryListController createController();

    public View createView() {
        View view = inflateView();
        ListAdapter adapter = createListAdapter();
        ContactEntryListController controller = createController();
        configureView(view, adapter, controller);
        return view;
    }

    protected void configureView(View view, ListAdapter adapter,
            ContactEntryListController controller) {
        ListView listView = (ListView)view.findViewById(android.R.id.list);
        if (listView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }

        View emptyView = view.findViewById(com.android.internal.R.id.empty);
        if (emptyView != null) {
            listView.setEmptyView(emptyView);
        }

        controller.setAdapter(adapter);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(controller);
        controller.setListView(listView);

        ((ContactsListActivity)mContext).setupListView(adapter, listView);

        configurePinnedHeader(listView, adapter);

        if (isSearchResultsMode()) {
            TextView titleText = (TextView)view.findViewById(R.id.search_results_for);
            if (titleText != null) {
                titleText.setText(Html.fromHtml(getContext().getString(R.string.search_results_for,
                        "<b>" + getQueryString() + "</b>")));
            }
        }
    }

    private void configurePinnedHeader(ListView listView, ListAdapter adapter) {
        if (!mSectionHeaderDisplayEnabled) {
            return;
        }

        if (listView instanceof PinnedHeaderListView
                && adapter instanceof PinnedHeaderListAdapter) {
            PinnedHeaderListView pinnedHeaderList = (PinnedHeaderListView)listView;
            PinnedHeaderListAdapter pinnedHeaderListAdapter = (PinnedHeaderListAdapter)adapter;
            View headerView = pinnedHeaderListAdapter.createPinnedHeaderView(pinnedHeaderList);
            pinnedHeaderList.setPinnedHeaderView(headerView);
        }
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
}
