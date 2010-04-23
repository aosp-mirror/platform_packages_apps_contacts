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
import com.android.contacts.widget.PinnedHeaderListView;

import android.content.Context;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Common base class for configurations of various contact-related lists, e.g.
 * contact list, phone number list etc.
 */
public abstract class ContactEntryListConfiguration {

    private final Context mContext;
    private final ContactsApplicationController mApplicationController;
    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;

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

    public abstract ListAdapter createListAdapter();
    public abstract ContactEntryListController createController();

    public void configureListView(ListView listView) {
        ListAdapter adapter = createListAdapter();
        ContactEntryListController controller = createController();
        controller.setAdapter(adapter);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(controller);
        controller.setListView(listView);

        ((ContactsListActivity)mContext).setupListView(adapter);

        configurePinnedHeader(listView, adapter);
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
}
