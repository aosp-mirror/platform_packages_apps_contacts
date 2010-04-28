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

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class ContactBrowseListFragment extends ContactEntryListFragment {

    private OnContactBrowserActionListener mListener;
    private boolean mEditMode;
    private boolean mCreateContactEnabled;

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
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
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                mListener.onCreateNewContactAction();
            } else {
                ContactEntryListAdapter adapter = getAdapter();
                adapter.moveToPosition(position);
                mListener.onEditContactAction(adapter.getContactUri());
            }
        } else {
            ContactEntryListAdapter adapter = getAdapter();
            adapter.moveToPosition(position);
            mListener.onViewContactAction(adapter.getContactUri());
        }
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
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

    public void viewContact(Uri contactUri) {
        mListener.onViewContactAction(contactUri);
    }

    public void editContact(Uri contactUri) {
        mListener.onEditContactAction(contactUri);
    }

    public void deleteContact(Uri contactUri) {
        mListener.onDeleteContactAction(contactUri);
    }

    public void addToFavorites(Uri contactUri) {
        mListener.onAddToFavoritesAction(contactUri);
    }

    public void removeFromFavorites(Uri contactUri) {
        mListener.onRemoveFromFavoritesAction(contactUri);
    }

    public void callContact(Uri contactUri) {
        mListener.onCallContactAction(contactUri);
    }

    public void smsContact(Uri contactUri) {
        mListener.onSmsContactAction(contactUri);
    }
}
