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

import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
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

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_RAW_CONTACT_IDS = "rawContactIds";

    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_RAW_CONTACT_IDS = "rawContactIds";

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
            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String rawContactId = getString(GroupMembersQuery.RAW_CONTACT_ID);
                if (!mRawContactIds.contains(rawContactId)) {
                    mIndex[mPos++] = i;
                }
            }
            mCount = mPos;
            mPos = 0;
            super.moveToFirst();
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

    private AccountWithDataSet mAccount;
    private ArrayList<String> mRawContactIds;
    private Map<Long, Pair<Long,String>> mContactPhotoMap = new HashMap();

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
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (mListener != null) {
            mListener.onGroupMemberClicked(getAdapter().getRawContactUri(position));
        }
    }

    // TODO(wjang): unacceptable scrolling performance for big groups
    private Pair<Long,String> getContactPhotoPair(long contactId) {
        if (mContactPhotoMap.containsKey(contactId)) {
            return mContactPhotoMap.get(contactId);
        }
        final Uri uri  = Data.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();
        final String[] projection = new String[] { Data.PHOTO_ID, Data.LOOKUP_KEY };
        final String selection = Data.CONTACT_ID + "=?";
        final String[] selectionArgs = new String[] { Long.toString(contactId) };
        Cursor cursor = null;
        try {
            cursor = getActivity().getContentResolver().query(
                    uri, projection, selection, selectionArgs, /* sortOrder */ null);
            if (cursor != null && cursor.moveToFirst()) {
                final Pair<Long, String> pair = new Pair(cursor.getLong(0), cursor.getString(1));
                mContactPhotoMap.put(contactId, pair);
                return pair;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
