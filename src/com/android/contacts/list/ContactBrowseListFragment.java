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
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
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

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        ContactEntryListFragment<ContactListAdapter> {

    private static final String KEY_SELECTED_URI = "selectedUri";
    private static final String KEY_SELECTION_VERIFIED = "selectionVerified";
    private static final String KEY_FILTER_ENABLED = "filterEnabled";

    private static final String KEY_PERSISTENT_SELECTION_ENABLED = "persistenSelectionEnabled";
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

    private static final int SELECTED_ID_LOADER = -3;
    private static final String ARG_CONTACT_URI = "uri";

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
    private boolean mLoadingLookupKey;
    private boolean mFilterEnabled;
    private ContactListFilter mFilter;
    private boolean mPersistentSelectionEnabled;
    private String mPersistentSelectionPrefix = PERSISTENT_SELECTION_PREFIX;

    protected OnContactBrowserActionListener mListener;

    private LoaderCallbacks<Cursor> mIdLoaderCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
            return new CursorLoader(getContext(), contactUri, new String[] { Contacts.LOOKUP_KEY },
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            String lookupKey = null;
            if (data != null) {
                if (data.moveToFirst()) {
                    lookupKey = data.getString(0);
                }
            }
            onLookupKeyLoadFinished(lookupKey);
        }
    };

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
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        restoreSelectedUri();
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

        mFilter = filter;
        restoreSelectedUri();
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
        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
        mSelectionVerified = savedState.getBoolean(KEY_SELECTION_VERIFIED);
        parseSelectedContactUri();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FILTER_ENABLED, mFilterEnabled);
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
        getLoaderManager().stopLoader(SELECTED_ID_LOADER);

        if (!isSelectionVisible()) {
            return;
        }

        mLoadingLookupKey = true;

        if (mSelectedContactUri == null) {
            onLookupKeyLoadFinished(null);
            return;
        }

        if (mSelectedContactDirectoryId != Directory.DEFAULT
                && mSelectedContactDirectoryId != Directory.LOCAL_INVISIBLE) {
            onLookupKeyLoadFinished(mSelectedContactLookupKey);
        } else {
            Bundle bundle = new Bundle();
            bundle.putParcelable(ARG_CONTACT_URI, mSelectedContactUri);
            getLoaderManager().restartLoader(SELECTED_ID_LOADER, bundle, mIdLoaderCallbacks);
      }
    }

    protected void onLookupKeyLoadFinished(String lookupKey) {
        mLoadingLookupKey = false;
        if (!TextUtils.equals(mSelectedContactLookupKey, lookupKey)) {
            mSelectedContactLookupKey = lookupKey;
            getAdapter().setSelectedContact(mSelectedContactDirectoryId, mSelectedContactLookupKey);
        }
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
        setSelectedContactUri(uri, true, true, true);
    }

    private void setSelectedContactUri(
            Uri uri, boolean required, boolean smoothScroll, boolean persistent) {
        mSmoothScrollRequested = smoothScroll;
        mSelectionToScreenRequested = true;

        if ((mSelectedContactUri == null && uri != null)
                || (mSelectedContactUri != null && !mSelectedContactUri.equals(uri))) {
            mSelectionVerified = false;
            mSelectionRequired = required;
            mSelectionPersistenceRequested = persistent;
            mSelectedContactUri = uri;
            parseSelectedContactUri();

            if (mStartedLoading) {
                // Configure the adapter to show the selection based on the lookup key extracted
                // from the URI
                getAdapter().setSelectedContact(
                        mSelectedContactDirectoryId, mSelectedContactLookupKey);

                // Also, launch a loader to pick up a new lookup key in case it has changed
                startLoadingContactLookupKey();
            }
        }
    }

    private void parseSelectedContactUri() {
        if (mSelectedContactUri != null) {
            String directoryParam =
                mSelectedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            mSelectedContactDirectoryId = TextUtils.isEmpty(directoryParam) ? Directory.DEFAULT
                    : Long.parseLong(directoryParam);
            if (mSelectedContactUri.toString().startsWith(Contacts.CONTENT_LOOKUP_URI.toString())) {
                mSelectedContactLookupKey = Uri.encode(
                        mSelectedContactUri.getPathSegments().get(2));
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

        adapter.setSelectedContact(mSelectedContactDirectoryId, mSelectedContactLookupKey);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        mSelectionVerified = false;
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

        int selectedPosition = adapter.getSelectedContactPosition();
        if (mSelectionRequired && selectedPosition == -1) {
            notifyInvalidSelection();
            return;
        }

        if (selectedPosition == -1) {
            saveSelectedUri(null);

            if (isSearchMode()) {
                selectFirstFoundContactAfterDelay();
            } else {
                selectDefaultContact();
            }

            if (mSelectedContactUri != null) {
                // The default selection is now loading and this method will be called again
                return;
            }
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
            setSelectedContactUri(null, false, false, false);
        }
    }

    protected void selectDefaultContact() {
        Uri firstContactUri = getAdapter().getFirstContactUri();
        setSelectedContactUri(firstContactUri, false, true, false);
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
        return mLoadingLookupKey || super.isLoading();
    }

    @Override
    public void startLoading() {
        if (!mFilterEnabled || mFilter != null) {
            mStartedLoading = true;
            mSelectionVerified = false;
            super.startLoading();
        }
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
        setSelectedContactUri(contactUri, false, false, true);
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
        if (!mPersistentSelectionEnabled) {
            return;
        }

        Editor editor = mPrefs.edit();
        if (contactUri == null) {
            editor.remove(getPersistentSelectionKey());
        } else {
            editor.putString(getPersistentSelectionKey(), contactUri.toString());
        }
        editor.apply();
    }

    private void restoreSelectedUri() {
        if (!mPersistentSelectionEnabled || mPrefs == null || mSelectionRequired) {
            return;
        }

        String selectedUri = mPrefs.getString(getPersistentSelectionKey(), null);
        if (selectedUri == null) {
            setSelectedContactUri(null, false, false, false);
        } else {
            setSelectedContactUri(Uri.parse(selectedUri), false, false, false);
        }
    }

    private String getPersistentSelectionKey() {
        if (mFilter == null) {
            return mPersistentSelectionPrefix;
        } else {
            return mPersistentSelectionPrefix + "-" + mFilter.getId();
        }
    }
}
