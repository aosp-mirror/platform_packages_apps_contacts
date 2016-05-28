/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.list.GroupMemberPickListAdapter.GroupMembersQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Fragment containing raw contacts for a specified account that are not already in a group.
 */
public class GroupMemberPickerFragment extends
        ContactEntryListFragment<GroupMemberPickListAdapter> {

    public static final String TAG = "GroupMemberPicker";

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_RAW_CONTACT_IDS = "rawContactIds";

    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_RAW_CONTACT_IDS = "rawContactIds";

    private static final int LOADER_CONTACT_ENTITY = 0;

    /** Callbacks for host of {@link GroupMemberPickerFragment}. */
    public interface Listener {

        /** Invoked when a potential group member is selected. */
        void onGroupMemberClicked(Uri uri);
    }

    /**
     * Filters out raw contacts that are already in the group and also handles queries for contact
     * photo IDs and lookup keys which cannot be retrieved from the raw contact table directly.
     */
    private class FilterCursorWrapper extends CursorWrapper {

        private int[] mIndex;
        private int mCount = 0;
        private int mPos = 0;

        public FilterCursorWrapper(Cursor cursor) {
            super(cursor);

            mCount = super.getCount();
            mIndex = new int[mCount];

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "FilterCursorWrapper starting size cursor=" + mCount + " photosMap="
                        + (mContactPhotosMap == null ? 0 : mContactPhotosMap.size()));
            }

            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String rawContactId = getString(GroupMembersQuery.RAW_CONTACT_ID);
                if (!mRawContactIds.contains(rawContactId)) {
                    mIndex[mPos++] = i;
                } else if (mContactPhotosMap != null) {
                    final long contactId = getLong(GroupMembersQuery.CONTACT_ID);
                    mContactPhotosMap.remove(contactId);
                }
            }
            mCount = mPos;
            mPos = 0;
            super.moveToFirst();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "FilterCursorWrapper ending  size cursor=" + mCount + " photosMap="
                        + (mContactPhotosMap == null ? 0 : mContactPhotosMap.size()));
            }
        }

        @Override
        public int getColumnIndex(String columnName) {
            final int index = getColumnIndexForContactColumn(columnName);
            return index < 0 ? super.getColumnIndex(columnName) : index;
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) {
            final int index = getColumnIndexForContactColumn(columnName);
            return index < 0 ? super.getColumnIndexOrThrow(columnName) : index;
        }

        private int getColumnIndexForContactColumn(String columnName) {
            if (Contacts.PHOTO_ID.equals(columnName)) {
                return GroupMembersQuery.CONTACT_PHOTO_ID;
            }
            if (Contacts.LOOKUP_KEY.equals(columnName)) {
                return GroupMembersQuery.CONTACT_LOOKUP_KEY;
            }
            return -1;
        }

        @Override
        public String[] getColumnNames() {
            final String displayNameColumnName =
                    getContactNameDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY
                            ? RawContacts.DISPLAY_NAME_PRIMARY
                            : RawContacts.DISPLAY_NAME_ALTERNATIVE;
            return new String[] {
                    RawContacts._ID,
                    RawContacts.CONTACT_ID,
                    displayNameColumnName,
                    Contacts.PHOTO_ID,
                    Contacts.LOOKUP_KEY,
            };
        }

        @Override
        public String getString(int columnIndex) {
            if (columnIndex == GroupMembersQuery.CONTACT_LOOKUP_KEY) {
                if (columnIndex == GroupMembersQuery.CONTACT_PHOTO_ID) {
                    final long contactId = getLong(GroupMembersQuery.CONTACT_ID);
                    final Pair<Long,String> pair = getContactPhotoPair(contactId);
                    if (pair != null) {
                        return pair.second;
                    }
                }
                return null;
            }
            return super.getString(columnIndex);
        }

        @Override
        public long getLong(int columnIndex) {
            if (columnIndex == GroupMembersQuery.CONTACT_PHOTO_ID) {
                final long contactId = getLong(GroupMembersQuery.CONTACT_ID);
                final Pair<Long,String> pair = getContactPhotoPair(contactId);
                if (pair != null) {
                    return pair.first;
                }
                return 0;
            }
            return super.getLong(columnIndex);
        }

        private Pair<Long,String> getContactPhotoPair(long contactId) {
            return mContactPhotosMap != null && mContactPhotosMap.containsKey(contactId)
                ? mContactPhotosMap.get(contactId) : null;
        }

        @Override
        public boolean move(int offset) {
            return moveToPosition(mPos + offset);
        }

        @Override
        public boolean moveToNext() {
            return moveToPosition(mPos + 1);
        }

        @Override
        public boolean moveToPrevious() {
            return moveToPosition(mPos - 1);
        }

        @Override
        public boolean moveToFirst() {
            return moveToPosition(0);
        }

        @Override
        public boolean moveToLast() {
            return moveToPosition(mCount - 1);
        }

        @Override
        public boolean moveToPosition(int position) {
            if (position >= mCount || position < 0) return false;
            return super.moveToPosition(mIndex[position]);
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public int getPosition() {
            return mPos;
        }
    }

    private final LoaderCallbacks<Cursor> mContactsEntityCallbacks = new LoaderCallbacks<Cursor>() {

        private final String[] PROJECTION = new String[] {
                Contacts._ID,
                Contacts.PHOTO_ID,
                Contacts.LOOKUP_KEY
        };

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final CursorLoader loader = new CursorLoader(getActivity());
            loader.setUri(Contacts.CONTENT_URI);
            loader.setProjection(PROJECTION);
            return loader;
        }
        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            mContactPhotosMap = new HashMap<>();
            while (cursor.moveToNext()) {
                final long contactId = cursor.getLong(0);
                final Pair<Long, String> pair =
                        new Pair(cursor.getLong(1), cursor.getString(2));
                mContactPhotosMap.put(contactId, pair);
            }
            GroupMemberPickerFragment.super.startLoading();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private AccountWithDataSet mAccount;
    private ArrayList<String> mRawContactIds;
    // Contact ID longs to Pairs of photo ID and contact lookup keys
    private Map<Long, Pair<Long,String>> mContactPhotosMap;

    private Listener mListener;

    public static GroupMemberPickerFragment newInstance(AccountWithDataSet account,
            ArrayList<String> rawContactids) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_ACCOUNT, account);
        args.putStringArrayList(ARG_RAW_CONTACT_IDS, rawContactids);

        final GroupMemberPickerFragment fragment = new GroupMemberPickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMemberPickerFragment() {
        setQuickContactEnabled(false);
        setPhotoLoaderEnabled(true);
        setVisibleScrollbarEnabled(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState == null) {
            mAccount = getArguments().getParcelable(ARG_ACCOUNT);
            mRawContactIds = getArguments().getStringArrayList(ARG_RAW_CONTACT_IDS);
        } else {
            mAccount = savedState.getParcelable(KEY_ACCOUNT);
            mRawContactIds = savedState.getStringArrayList(KEY_RAW_CONTACT_IDS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ACCOUNT, mAccount);
        outState.putStringArrayList(KEY_RAW_CONTACT_IDS, mRawContactIds);
    }

    @Override
    protected void startLoading() {
        if (mContactPhotosMap == null) {
            getLoaderManager().restartLoader(LOADER_CONTACT_ENTITY, null, mContactsEntityCallbacks);
        } else {
            super.startLoading();
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, new FilterCursorWrapper(data));
    }

    @Override
    protected GroupMemberPickListAdapter createListAdapter() {
        final GroupMemberPickListAdapter adapter = new GroupMemberPickListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        getAdapter().setAccount(mAccount);
        getAdapter().setRawContactIds(mRawContactIds);
        getAdapter().setEmptyListEnabled(true);
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (mListener != null) {
            mListener.onGroupMemberClicked(getAdapter().getRawContactUri(position));
        }
    }
}
