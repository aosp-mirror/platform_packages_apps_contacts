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
import com.android.contacts.widget.ListViewUtils;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.widget.ListView;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        ContactEntryListFragment<ContactListAdapter> {

    private static final String KEY_SELECTED_URI = "selectedUri";
    private static final String KEY_SELECTION_VERIFIED = "selectionVerified";

    private static final int SELECTED_ID_LOADER = -3;

    private static final int SELECTION_VISIBILITY_REQUEST_NONE = 0;
    private static final int SELECTION_VISIBILITY_REQUEST_SMOOTH = 1;
    private static final int SELECTION_VISIBILITY_REQUEST_INSTANT = 2;

    private Uri mSelectedContactUri;
    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private int mSelectionVisibilityRequest;
    private boolean mSelectionVerified;
    private boolean mLoadingLookupKey;

    protected OnContactBrowserActionListener mListener;

    private LoaderCallbacks<Cursor> mIdLoaderCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getContext(),
                    mSelectedContactUri,
                    new String[] { Contacts.LOOKUP_KEY },
                    null,
                    null,
                    null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mLoadingLookupKey = false;
            String lookupKey = null;
            if (data != null) {
                if (data.moveToFirst()) {
                    lookupKey = data.getString(0);
                }
            }
            if (!TextUtils.equals(mSelectedContactLookupKey, lookupKey)) {
                mSelectedContactLookupKey = lookupKey;
                configureContactSelection();
            }
        }
    };

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
        mSelectionVerified = savedState.getBoolean(KEY_SELECTION_VERIFIED);
        parseSelectedContactUri();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_SELECTED_URI, mSelectedContactUri);
        outState.putBoolean(KEY_SELECTION_VERIFIED, mSelectionVerified);
    }

    @Override
    public void onStart() {
        // Refresh the currently selected lookup in case it changed while we were sleeping
        startLoadingContactLookupKey();
        super.onStart();
   }

    protected void startLoadingContactLookupKey() {
        if (isSelectionVisible() && mSelectedContactUri != null &&
                (mSelectedContactDirectoryId == Directory.DEFAULT ||
                        mSelectedContactDirectoryId == Directory.LOCAL_INVISIBLE)) {
            mLoadingLookupKey = true;
            getLoaderManager().restartLoader(SELECTED_ID_LOADER, null, mIdLoaderCallbacks);
        } else {
            getLoaderManager().stopLoader(SELECTED_ID_LOADER);
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
            mSelectedContactUri = uri;

            parseSelectedContactUri();

            if (isAdded()) {
                // Configure the adapter to show the selection based on the lookup key extracted
                // from the URI
                configureAdapter();

                // Also, launch a loader to pick up a new lookup key in case it has changed
                startLoadingContactLookupKey();
            }
        }
    }

    private void parseSelectedContactUri() {
        if (mSelectedContactUri != null) {
            if (!mSelectedContactUri.toString()
                    .startsWith(Contacts.CONTENT_LOOKUP_URI.toString())) {
                throw new IllegalStateException(
                        "Contact list contains a non-lookup URI: " + mSelectedContactUri);
            }

            String directoryParam =
                    mSelectedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            mSelectedContactDirectoryId = TextUtils.isEmpty(directoryParam)
                    ? Directory.DEFAULT
                    : Long.parseLong(directoryParam);
            mSelectedContactLookupKey =
                    Uri.encode(mSelectedContactUri.getPathSegments().get(2));
        } else {
            mSelectedContactDirectoryId = Directory.DEFAULT;
            mSelectedContactLookupKey = null;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        configureContactSelection();
    }

    /**
     * Configures the adapter with the identity of the currently selected contact.
     */
    private void configureContactSelection() {
        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        adapter.setSelectedContact(mSelectedContactDirectoryId, mSelectedContactLookupKey);
        checkSelection();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        checkSelection();
    }

    private void checkSelection() {
        if (mSelectionVerified) {
            return;
        }

        if (isLoading()) {
            return;
        }

        ContactListAdapter adapter = getAdapter();
        if (adapter.hasValidSelection()) {
            mSelectionVerified = true;
            requestSelectionOnScreenIfNeeded();
            return;
        }

        notifyInvalidSelection();
    }

    @Override
    public boolean isLoading() {
        return mLoadingLookupKey || super.isLoading();
    }

    public Uri getFirstContactUri() {
        ContactListAdapter adapter = getAdapter();
        return adapter.getFirstContactUri();
    }

    @Override
    public void startLoading() {
        mSelectionVerified = false;
        super.startLoading();
    }

    @Override
    public void reloadData() {
        mSelectionVerified = false;
        super.reloadData();
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

    private void notifyInvalidSelection() {
        if (mListener != null) {
            mListener.onInvalidSelection();
        }
    }

    @Override
    protected void finish() {
        super.finish();
        mListener.onFinishAction();
    }

    public void requestSelectionOnScreen(boolean smooth) {
        mSelectionVisibilityRequest = smooth
                ? SELECTION_VISIBILITY_REQUEST_SMOOTH
                : SELECTION_VISIBILITY_REQUEST_INSTANT;
        requestSelectionOnScreenIfNeeded();
    }

    @Override
    protected void completeRestoreInstanceState() {
        super.completeRestoreInstanceState();
        requestSelectionOnScreenIfNeeded();
    }

    private void requestSelectionOnScreenIfNeeded() {
        if (mSelectionVisibilityRequest == SELECTION_VISIBILITY_REQUEST_NONE) {
            return;
        }

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        int position = adapter.getSelectedContactPosition();
        if (position != -1) {
            boolean smooth = mSelectionVisibilityRequest == SELECTION_VISIBILITY_REQUEST_SMOOTH;
            mSelectionVisibilityRequest = SELECTION_VISIBILITY_REQUEST_NONE;
            ListView listView = getListView();
            ListViewUtils.requestPositionToScreen(
                    listView, position + listView.getHeaderViewsCount(), smooth);
        }
    }

    public void saveSelectedUri(SharedPreferences preferences) {
    }

    public void restoreSelectedUri(SharedPreferences preferences) {
    }

    public void eraseSelectedUri(SharedPreferences preferences) {
    }
}
