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

import com.android.contacts.R;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment for the contact list used for browsing contacts (as compared to
 * picking a contact with one of the PICK or SHORTCUT intents).
 */
public class ContactPickerFragment extends ContactEntryListFragment<ContactListAdapter> {

    private OnContactPickerActionListener mListener;
    private boolean mCreateContactEnabled;

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    public boolean isSearchAllContactsItemPosition(int position) {
        return isSearchMode() && position == getAdapter().getCount() - 1;
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (isSearchAllContactsItemPosition(position)) {
            mListener.onSearchAllContactsAction((String)null);
        } else {
            Intent intent;
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                mListener.onCreateNewContactAction();
            } else {
                ContactListAdapter adapter = getAdapter();
                adapter.moveToPosition(position);
                mListener.onPickContactAction(adapter.getContactUri());
            }
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        ContactListAdapter adapter = new ContactListAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(isPhotoLoaderEnabled());

        // TODO more settings

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

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }
}
