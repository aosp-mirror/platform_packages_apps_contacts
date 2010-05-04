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

import android.app.patterns.CursorLoader;
import android.app.patterns.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        ContactEntryListFragment<ContactListAdapter> {

    private OnContactBrowserActionListener mListener;

    private CursorLoader mLoader;

    @Override
    protected CursorLoader onCreateLoader(int id, Bundle args) {
        mLoader = new CursorLoader(getActivity(), null, null, null, null, null);
        return mLoader;
    }

    public CursorLoader getLoader() {
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

    public void createNewContact() {
        mListener.onCreateNewContactAction();
    }

    public void searchAllContacts() {
        mListener.onSearchAllContactsAction((String)null);
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
