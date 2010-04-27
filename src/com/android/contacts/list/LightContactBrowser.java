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

import com.android.contacts.ContactsListActivity;
import com.android.contacts.R;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Fragment for the light-weight contact list.
 */
public class LightContactBrowser extends ContactEntryListFragment {

    // TODO move these constants to the "loader"
    public static final int SUMMARY_ID_COLUMN_INDEX = 0;
    public static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;

    private OnContactBrowserActionListener mListener;
    private boolean mEditMode;
    private boolean mCreateContactEnabled;

    public void setOnContactBrowserActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    public boolean isSearchAllContactsItemPosition(int position) {
        return isSearchMode() && position == getAdapter().getCount() - 1;
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (isSearchAllContactsItemPosition(position)) {
            mListener.onSearchAllContactsAction((String)null);
        } else if (isEditMode()) {
            Intent intent;
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                mListener.onCreateNewContactAction();
            } else {
                mListener.onEditContactAction(getContactUri(position));
            }
        } else {
            mListener.onViewContactAction(getContactUri(position));
        }
    }

    @Override
    protected ListAdapter createListAdapter() {
        ContactItemListAdapter adapter =
                new ContactItemListAdapter((ContactsListActivity)getActivity());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(isPhotoLoaderEnabled());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        if (isSearchMode()) {
            return inflater.inflate(R.layout.contacts_search_content, null);
        } else if (isSearchResultsMode()) {
            return inflater.inflate(R.layout.contacts_list_search_results, null);
        } else {
            return inflater.inflate(R.layout.contacts_list_content, null);
        }
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    /**
     * Build the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position.
     */
    private Uri getContactUri(int position) {
        final Cursor cursor = (Cursor)getAdapter().getItem(position);
        if (cursor == null) {
            return null;
        }

        // Build and return soft, lookup reference
        long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
        String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
        return Contacts.getLookupUri(contactId, lookupKey);
    }
}
