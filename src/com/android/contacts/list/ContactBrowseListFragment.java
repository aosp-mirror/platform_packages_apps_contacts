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

import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.widget.ListView;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        ContactEntryListFragment<ContactListAdapter> {

    private static final String KEY_SELECTED_URI = "selectedUri";

    private static final int SELECTED_ID_LOADER = -3;

    private Uri mSelectedContactUri;

    private OnContactBrowserActionListener mListener;

    private LoaderCallbacks<Cursor> mIdLoaderCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getContext(),
                    mSelectedContactUri,
                    new String[] { Contacts._ID },
                    null,
                    null,
                    null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            long selectedId = ListView.INVALID_ROW_ID;
            if (data.moveToFirst()) {
                selectedId = data.getLong(0);
            }
            getAdapter().setSelectedContactId(selectedId);
            return;
        }
    };

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_SELECTED_URI, mSelectedContactUri);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mSelectedContactUri != null && isSelectionVisible()) {
            getLoaderManager().initLoader(SELECTED_ID_LOADER, null, mIdLoaderCallbacks);
        }
   }

    @Override
    protected void prepareEmptyView() {
        if (isSearchMode()) {
            return;
        } else if (isSyncActive()) {
            if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpTextWithSync);
            } else {
                setEmptyText(R.string.noContactsNoSimHelpTextWithSync);
            }
        } else {
            if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpText);
            } else {
                setEmptyText(R.string.noContactsNoSimHelpText);
            }
        }
    }

    public Uri getSelectedContactUri() {
        return mSelectedContactUri;
    }

    public void setSelectedContactUri(Uri uri) {
        if ((mSelectedContactUri == null && uri != null)
                || (mSelectedContactUri != null && !mSelectedContactUri.equals(uri))) {
            this.mSelectedContactUri = uri;
            if (mSelectedContactUri != null) {
                getLoaderManager().restartLoader(SELECTED_ID_LOADER, null, mIdLoaderCallbacks);
            }
        }
    }

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    public void createNewContact() {
        mListener.onCreateNewContactAction();
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
