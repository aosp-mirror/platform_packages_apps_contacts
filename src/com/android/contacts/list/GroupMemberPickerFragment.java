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
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DefaultContactListAdapter;

import java.util.ArrayList;

/**
 * Fragment containing raw contacts for a specified account that are not already in a group.
 */
public class GroupMemberPickerFragment extends
        ContactEntryListFragment<DefaultContactListAdapter> {

    public static final String TAG = "GroupMemberPicker";

    private static final String KEY_ACCOUNT_NAME = "accountName";
    private static final String KEY_ACCOUNT_TYPE = "accountType";
    private static final String KEY_ACCOUNT_DATA_SET = "accountDataSet";
    private static final String KEY_RAW_CONTACT_IDS = "rawContactIds";

    private static final String ARG_ACCOUNT_NAME = "accountName";
    private static final String ARG_ACCOUNT_TYPE = "accountType";
    private static final String ARG_ACCOUNT_DATA_SET = "accountDataSet";
    private static final String ARG_RAW_CONTACT_IDS = "rawContactIds";

    /** Callbacks for host of {@link GroupMemberPickerFragment}. */
    public interface Listener {

        /** Invoked when a potential group member is selected. */
        void onGroupMemberClicked(long contactId);
    }

    /** Filters out raw contacts that are already in the group. */
    private class FilterCursorWrapper extends CursorWrapper {

        private int[] mIndex;
        private int mCount = 0;
        private int mPos = 0;

        public FilterCursorWrapper(Cursor cursor) {
            super(cursor);

            mCount = super.getCount();
            mIndex = new int[mCount];

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "RawContacts CursorWrapper start: " + mCount);
            }

            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String contactId = getString(ContactQuery.CONTACT_ID);
                if (!mRawContactIds.contains(contactId)) {
                    mIndex[mPos++] = i;
                }
            }
            mCount = mPos;
            mPos = 0;
            super.moveToFirst();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "RawContacts CursorWrapper end: " + mCount);
            }
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

    private String mAccountName;
    private String mAccountType;
    private String mAccountDataSet;
    private ArrayList<String> mRawContactIds;
    private Listener mListener;

    public static GroupMemberPickerFragment newInstance(String accountName, String accountType,
            String accountDataSet, ArrayList<String> rawContactIds) {
        final Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_ACCOUNT_TYPE, accountType);
        args.putString(ARG_ACCOUNT_DATA_SET, accountDataSet);
        args.putStringArrayList(ARG_RAW_CONTACT_IDS, rawContactIds);

        final GroupMemberPickerFragment fragment = new GroupMemberPickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMemberPickerFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(false);
        setVisibleScrollbarEnabled(true);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState == null) {
            mAccountName = getArguments().getString(ARG_ACCOUNT_NAME);
            mAccountType = getArguments().getString(ARG_ACCOUNT_TYPE);
            mAccountDataSet = getArguments().getString(ARG_ACCOUNT_DATA_SET);
            mRawContactIds = getArguments().getStringArrayList(ARG_RAW_CONTACT_IDS);
        } else {
            mAccountName = savedState.getString(KEY_ACCOUNT_NAME);
            mAccountType = savedState.getString(KEY_ACCOUNT_TYPE);
            mAccountDataSet = savedState.getString(KEY_ACCOUNT_DATA_SET);
            mRawContactIds = savedState.getStringArrayList(KEY_RAW_CONTACT_IDS);
        }
        super.onCreate(savedState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACCOUNT_NAME, mAccountName);
        outState.putString(KEY_ACCOUNT_TYPE, mAccountType);
        outState.putString(KEY_ACCOUNT_DATA_SET, mAccountDataSet);
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
        if (data != null) {
            super.onLoadFinished(loader, new FilterCursorWrapper(data));
        }
    }

    @Override
    protected DefaultContactListAdapter createListAdapter() {
        final DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
        adapter.setFilter(ContactListFilter.createGroupMembersFilter(
                mAccountType, mAccountName, mAccountDataSet));
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (mListener != null) {
            final long contactId = getAdapter().getContactId(position);
            if (contactId > 0) {
                mListener.onGroupMemberClicked(contactId);
            }
        }
    }
}
