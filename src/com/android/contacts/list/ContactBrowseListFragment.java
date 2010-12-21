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
import com.android.contacts.widget.AutoScrollListView;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        ContactEntryListFragment<ContactListAdapter> {

    private static final String TAG = "ContactList";

    private static final String KEY_SELECTED_URI = "selectedUri";
    private static final String KEY_SELECTION_VERIFIED = "selectionVerified";
    private static final String KEY_FILTER_ENABLED = "filterEnabled";
    private static final String KEY_FILTER = "filter";

    private static final String KEY_PERSISTENT_SELECTION_ENABLED = "persistentSelectionEnabled";
    private static final String PERSISTENT_SELECTION_PREFIX = "defaultContactBrowserSelection";

    /**
     * The id for a delayed message that triggers automatic selection of the first
     * found contact in search mode.
     */
    private static final int MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT = 1;

    /**
     * The delay that is used for automatically selecting the first found contact.
     */
    private static final int DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS = 500;

    /**
     * The minimum number of characters in the search query that is required
     * before we automatically select the first found contact.
     */
    private static final int AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH = 2;

    private SharedPreferences mPrefs;
    private Handler mHandler;

    private boolean mStartedLoading;
    private boolean mSelectionRequired;
    private boolean mSelectionToScreenRequested;
    private boolean mSmoothScrollRequested;
    private boolean mSelectionPersistenceRequested;
    private Uri mSelectedContactUri;
    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private boolean mSelectionVerified;
    private boolean mRefreshingContactUri;
    private boolean mFilterEnabled;
    private ContactListFilter mFilter;
    private boolean mPersistentSelectionEnabled;
    private String mPersistentSelectionPrefix = PERSISTENT_SELECTION_PREFIX;

    protected OnContactBrowserActionListener mListener;

    /**
     * Refreshes a contact URI: it may have changed as a result of aggregation
     * activity.
     */
    private class ContactUriQueryHandler extends AsyncQueryHandler {

        public ContactUriQueryHandler(ContentResolver cr) {
            super(cr);
        }

        public void runQuery() {
            startQuery(0, mSelectedContactUri, mSelectedContactUri,
                    new String[] { Contacts._ID, Contacts.LOOKUP_KEY }, null, null, null);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor data) {
            long contactId = 0;
            String lookupKey = null;
            if (data != null) {
                if (data.moveToFirst()) {
                    contactId = data.getLong(0);
                    lookupKey = data.getString(1);
                }
                data.close();
            }

            if (!cookie.equals(mSelectedContactUri)) {
                return;
            }

            onContactUriQueryFinished(Contacts.getLookupUri(contactId, lookupKey));
        }
    }

    private ContactUriQueryHandler mQueryHandler;

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT:
                            selectDefaultContact();
                            break;
                    }
                }
            };
        }
        return mHandler;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mQueryHandler = new ContactUriQueryHandler(activity.getContentResolver());
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        restoreFilter();
        restoreSelectedUri(false);
    }

    public void setPersistentSelectionEnabled(boolean flag) {
        this.mPersistentSelectionEnabled = flag;
    }

    public void setFilter(ContactListFilter filter) {
        if (mFilter == null && filter == null) {
            return;
        }

        if (mFilter != null && mFilter.equals(filter)) {
            return;
        }

        Log.v(TAG, "New filter: " + filter);

        mFilter = filter;
        saveFilter();
        mSelectedContactUri = null;
        restoreSelectedUri(true);
        reloadData();
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    public boolean isFilterEnabled() {
        return mFilterEnabled;
    }

    public void setFilterEnabled(boolean flag) {
        this.mFilterEnabled = flag;
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mPersistentSelectionEnabled = savedState.getBoolean(KEY_PERSISTENT_SELECTION_ENABLED);
        mFilterEnabled = savedState.getBoolean(KEY_FILTER_ENABLED);
        mFilter = savedState.getParcelable(KEY_FILTER);
        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
        mSelectionVerified = savedState.getBoolean(KEY_SELECTION_VERIFIED);
        parseSelectedContactUri();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PERSISTENT_SELECTION_ENABLED, mPersistentSelectionEnabled);
        outState.putBoolean(KEY_FILTER_ENABLED, mFilterEnabled);
        outState.putParcelable(KEY_FILTER, mFilter);
        outState.putParcelable(KEY_SELECTED_URI, mSelectedContactUri);
        outState.putBoolean(KEY_SELECTION_VERIFIED, mSelectionVerified);
    }

    protected void refreshSelectedContactUri() {
        if (mQueryHandler == null) {
            return;
        }

        mQueryHandler.cancelOperation(0);

        if (!isSelectionVisible()) {
            return;
        }

        mRefreshingContactUri = true;

        if (mSelectedContactUri == null) {
            onContactUriQueryFinished(null);
            return;
        }

        if (mSelectedContactDirectoryId != Directory.DEFAULT
                && mSelectedContactDirectoryId != Directory.LOCAL_INVISIBLE) {
            onContactUriQueryFinished(mSelectedContactUri);
        } else {
            mQueryHandler.runQuery();
        }
    }

    protected void onContactUriQueryFinished(Uri uri) {
        mRefreshingContactUri = false;
        mSelectedContactUri = uri;
        parseSelectedContactUri();
        checkSelection();
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

    /**
     * Sets the new selection for the list.
     */
    public void setSelectedContactUri(Uri uri) {
        setSelectedContactUri(uri, true, true, true, false);
    }

    /**
     * Sets the new contact selection.
     *
     * @param uri the new selection
     * @param required if true, we need to check if the selection is present in
     *            the list and if not notify the listener so that it can load a
     *            different list
     * @param smoothScroll if true, the UI will roll smoothly to the new
     *            selection
     * @param persistent if true, the selection will be stored in shared
     *            preferences.
     * @param willReloadData if true, the selection will be remembered but not
     *            actually shown, because we are expecting that the data will be
     *            reloaded momentarily
     */
    private void setSelectedContactUri(Uri uri, boolean required, boolean smoothScroll,
            boolean persistent, boolean willReloadData) {
        mSmoothScrollRequested = smoothScroll;
        mSelectionToScreenRequested = true;

        if ((mSelectedContactUri == null && uri != null)
                || (mSelectedContactUri != null && !mSelectedContactUri.equals(uri))) {
            mSelectionVerified = false;
            mSelectionRequired = required;
            mSelectionPersistenceRequested = persistent;
            mSelectedContactUri = uri;
            parseSelectedContactUri();

            if (!willReloadData) {
                // Configure the adapter to show the selection based on the
                // lookup key extracted from the URI
                ContactListAdapter adapter = getAdapter();
                if (adapter != null) {
                    adapter.setSelectedContact(
                            mSelectedContactDirectoryId, mSelectedContactLookupKey);
                    getListView().invalidateViews();
                }
            }

            // Also, launch a loader to pick up a new lookup URI in case it has changed
            refreshSelectedContactUri();
        }
    }

    private void parseSelectedContactUri() {
        if (mSelectedContactUri != null) {
            String directoryParam =
                    mSelectedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            mSelectedContactDirectoryId = TextUtils.isEmpty(directoryParam) ? Directory.DEFAULT
                    : Long.parseLong(directoryParam);
            if (mSelectedContactUri.toString().startsWith(Contacts.CONTENT_LOOKUP_URI.toString())) {
                List<String> pathSegments = mSelectedContactUri.getPathSegments();
                mSelectedContactLookupKey = Uri.encode(pathSegments.get(2));
            } else {
                mSelectedContactLookupKey = null;
            }

        } else {
            mSelectedContactDirectoryId = Directory.DEFAULT;
            mSelectedContactLookupKey = null;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        if (mFilterEnabled && mFilter != null) {
            adapter.setFilter(mFilter);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        mSelectionVerified = false;

        // Refresh the currently selected lookup in case it changed while we were sleeping
        refreshSelectedContactUri();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void checkSelection() {
        if (mSelectionVerified) {
            return;
        }

        if (isLoading()) {
            return;
        }

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        adapter.setSelectedContact(mSelectedContactDirectoryId, mSelectedContactLookupKey);

        int selectedPosition = adapter.getSelectedContactPosition();
        if (selectedPosition == -1) {
            if (mSelectionRequired) {
                notifyInvalidSelection();
                return;
            }

            if (isSearchMode()) {
                selectFirstFoundContactAfterDelay();
                if (mListener != null) {
                    mListener.onSelectionChange();
                }
                return;
            }

            saveSelectedUri(null);
            selectDefaultContact();
        }

        mSelectionRequired = false;
        mSelectionVerified = true;

        if (mSelectionPersistenceRequested) {
            saveSelectedUri(mSelectedContactUri);
            mSelectionPersistenceRequested = false;
        }

        if (mSelectionToScreenRequested) {
            requestSelectionToScreen();
        }

        getListView().invalidateViews();

        if (mListener != null) {
            mListener.onSelectionChange();
        }
    }

    /**
     * Automatically selects the first found contact in search mode.  The selection
     * is updated after a delay to allow the user to type without to much UI churn
     * and to save bandwidth on directory queries.
     */
    public void selectFirstFoundContactAfterDelay() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT);

        String queryString = getQueryString();
        if (queryString != null
                && queryString.length() >= AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH) {
            handler.sendEmptyMessageDelayed(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT,
                    DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS);
        } else {
            setSelectedContactUri(null, false, false, false, false);
        }
    }

    protected void selectDefaultContact() {
        Uri firstContactUri = getAdapter().getFirstContactUri();
        setSelectedContactUri(firstContactUri, false, mSmoothScrollRequested, false, false);
    }

    protected void requestSelectionToScreen() {
        int selectedPosition = getAdapter().getSelectedContactPosition();
        if (selectedPosition != -1) {
            AutoScrollListView listView = (AutoScrollListView)getListView();
            listView.requestPositionToScreen(
                    selectedPosition + listView.getHeaderViewsCount(), mSmoothScrollRequested);
            mSelectionToScreenRequested = false;
        }
    }

    @Override
    public boolean isLoading() {
        return mRefreshingContactUri || super.isLoading();
    }

    @Override
    protected void startLoading() {
        mStartedLoading = true;
        mSelectionVerified = false;
        super.startLoading();
    }

    @Override
    public void reloadData() {
        if (mStartedLoading) {
            mSelectionVerified = false;
            super.reloadData();
        }
    }

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    public void createNewContact() {
        if (mListener != null) mListener.onCreateNewContactAction();
    }

    public void viewContact(Uri contactUri) {
        setSelectedContactUri(contactUri, false, false, true, false);
        if (mListener != null) { mListener.onViewContactAction(contactUri); }
    }

    public void editContact(Uri contactUri) {
        if (mListener != null) mListener.onEditContactAction(contactUri);
    }

    public void deleteContact(Uri contactUri) {
        if (mListener != null) mListener.onDeleteContactAction(contactUri);
    }

    public void addToFavorites(Uri contactUri) {
        if (mListener != null) mListener.onAddToFavoritesAction(contactUri);
    }

    public void removeFromFavorites(Uri contactUri) {
        if (mListener != null) mListener.onRemoveFromFavoritesAction(contactUri);
    }

    public void callContact(Uri contactUri) {
        if (mListener != null) mListener.onCallContactAction(contactUri);
    }

    public void smsContact(Uri contactUri) {
        if (mListener != null) mListener.onSmsContactAction(contactUri);
    }

    private void notifyInvalidSelection() {
        if (mListener != null) mListener.onInvalidSelection();
    }

    @Override
    protected void finish() {
        super.finish();
        if (mListener != null) mListener.onFinishAction();
    }

    private void saveSelectedUri(Uri contactUri) {
        if (mFilterEnabled) {
            ContactListFilter.storeToPreferences(mPrefs, mFilter);
        }

        if (mPersistentSelectionEnabled) {
            Editor editor = mPrefs.edit();
            if (contactUri == null) {
                editor.remove(getPersistentSelectionKey());
            } else {
                editor.putString(getPersistentSelectionKey(), contactUri.toString());
            }
            editor.apply();
        }
    }

    private void restoreSelectedUri(boolean willReloadData) {
        if (!mPersistentSelectionEnabled) {
            return;
        }

        // The meaning of mSelectionRequired is that we need to show some
        // selection other than the previous selection saved in shared preferences
        if (mSelectionRequired) {
            return;
        }

        String selectedUri = mPrefs.getString(getPersistentSelectionKey(), null);
        if (selectedUri == null) {
            setSelectedContactUri(null, false, false, false, willReloadData);
        } else {
            setSelectedContactUri(Uri.parse(selectedUri), false, false, false, willReloadData);
        }
    }

    private void saveFilter() {
        if (mFilterEnabled) {
            ContactListFilter.storeToPreferences(mPrefs, mFilter);
        }
    }

    private void restoreFilter() {
        if (mFilterEnabled) {
            mFilter = ContactListFilter.restoreFromPreferences(mPrefs);
        }
    }

    private String getPersistentSelectionKey() {
        if (mFilter == null) {
            return mPersistentSelectionPrefix;
        } else {
            return mPersistentSelectionPrefix + "-" + mFilter.getId();
        }
    }

    public boolean isOptionsMenuChanged() {
        // This fragment does not have an option menu of its own
        return false;
    }
}
