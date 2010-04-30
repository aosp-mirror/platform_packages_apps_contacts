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

import android.app.patterns.CursorLoader;
import android.app.patterns.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class ContactBrowseListFragment extends ContactEntryListFragment<ContactListAdapter> {

    private OnContactBrowserActionListener mListener;
    private boolean mEditMode;
    private boolean mCreateContactEnabled;

    private CursorLoader mLoader;

    @Override
    protected CursorLoader onCreateLoader(int id, Bundle args) {
        mLoader = new CursorLoader(getActivity(), null, null, null, null, null);
        return mLoader;
    }

    @Override
    protected void reloadData() {
        getAdapter().configureLoader(mLoader);
        mLoader.forceLoad();
    }

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    @Override
    protected void onInitializeLoaders() {
        startLoading(0, null);
    }

    @Override
    protected void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        getAdapter().changeCursor(data);
    }

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        if (adapter.isSearchAllContactsItemPosition(position)) {
            mListener.onSearchAllContactsAction((String)null);
        } else if (isEditMode()) {
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                mListener.onCreateNewContactAction();
            } else {
                adapter.moveToPosition(position);
                mListener.onEditContactAction(adapter.getContactUri());
            }
        } else {
            adapter.moveToPosition(position);
            mListener.onViewContactAction(adapter.getContactUri());
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        ContactListAdapter adapter = new ContactListAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());

        adapter.setSearchMode(isSearchMode());
        adapter.setSearchResultsMode(isSearchResultsMode());
        adapter.setQueryString(getQueryString());

        adapter.setContactNameDisplayOrder(getContactNameDisplayOrder());
        adapter.setSortOrder(getSortOrder());

        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(true);

        adapter.configureLoader(mLoader);
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

    @Override
    protected void finish() {
        super.finish();
        mListener.onFinishAction();
    }

}
