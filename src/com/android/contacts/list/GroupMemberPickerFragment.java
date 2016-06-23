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

import android.app.Activity;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.activities.ContactSelectionActivity;
import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactsSectionIndexer;
import com.android.contacts.common.list.DefaultContactListAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fragment containing raw contacts for a specified account that are not already in a group.
 */
public class GroupMemberPickerFragment extends
        MultiSelectContactsListFragment<DefaultContactListAdapter> {

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

        /** Invoked when multiple potential group members are selected. */
        void onGroupMembersSelected(long[] contactIds);

        /** Invoked when user has initiated multiple selection mode. */
        void onSelectGroupMembers();
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

            final Bundle bundle = cursor.getExtras();
            boolean hasSections = bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)
                    && bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            boolean needsTrimming = false;

            String sections[] = new String[]{};
            int counts[] = new int[]{};
            ContactsSectionIndexer indexer = null;
            if (hasSections) {
                sections = bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
                counts = bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);

                indexer = new ContactsSectionIndexer(sections, counts);
                final int positions[] = indexer.getPositions();

                // The sum of the last element in counts[] and the last element in positions[] is
                // the total number of remaining elements in cursor. If mCount is more than
                // what's in the indexer now, then we don't need to trim.
                needsTrimming =
                        mCount <= (counts[counts.length - 1] + positions[positions.length - 1]);

                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "sections before: " + Arrays.toString(sections));
                    Log.v(TAG, "counts before: " + Arrays.toString(counts));
                    Log.v(TAG, "positions: " + Arrays.toString(positions));
                    Log.v(TAG, "mCount: " + mCount);
                }
            }

            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String contactId = getString(ContactQuery.CONTACT_ID);
                if (!mRawContactIds.contains(contactId)) {
                    mIndex[mPos++] = i;
                } else if (needsTrimming) {
                    int filteredContact = indexer.getSectionForPosition(i);
                    if (filteredContact < counts.length && filteredContact >= 0) {
                        counts[filteredContact]--;
                        if (counts[filteredContact] == 0) {
                            sections[filteredContact] = "";
                        }
                    }
                }
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "sections  after: " + Arrays.toString(sections));
                Log.v(TAG, "counts  after: " + Arrays.toString(counts));
                Log.v(TAG, "mIndex: " + Arrays.toString(mIndex));
            }

            if (needsTrimming) {
                final String[] newSections = clearEmptyString(sections);
                bundle.putStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES, newSections);
                final int[] newCounts = clearZeros(counts);
                bundle.putIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS, newCounts);
            }

            mCount = mPos;
            mPos = 0;
            super.moveToFirst();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "RawContacts CursorWrapper end: " + mCount);
            }
        }

        private String[] clearEmptyString(String[] strings) {
            final List<String> list = new ArrayList<>();
            for (String s : strings) {
                if (!TextUtils.isEmpty(s)) {
                    list.add(s);
                }
            }
            return list.toArray(new String[list.size()]);
        }

        private int[] clearZeros(int[] numbers) {
            final List<Integer> list = new ArrayList<>();
            for (int n : numbers) {
                if (n > 0) {
                    list.add(n);
                }
            }
            final int[] array = new int[list.size()];
            for(int i = 0; i < list.size(); i++) {
                array[i] = list.get(i);
            }
            return array;
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
        setSectionHeaderDisplayEnabled(true);
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
            // Wait until contacts are loaded before showing the scrollbar
            setVisibleScrollbarEnabled(true);

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
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        }
        if (mListener != null) {
            final long contactId = getAdapter().getContactId(position);
            if (contactId > 0) {
                mListener.onGroupMemberClicked(contactId);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.group_member_picker, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final ContactSelectionActivity activity = getContactSelectionActivity();
        final boolean isSearchMode = activity == null ? false : activity.isSearchMode();
        final boolean isSelectionMode = activity == null ? false : activity.isSelectionMode();

        // Added in ContactSelectionActivity but we must account for selection mode
        setVisible(menu, R.id.menu_search, !isSearchMode && !isSelectionMode);

        setVisible(menu, R.id.menu_done, !isSearchMode && isSelectionMode &&
                getAdapter().getSelectedContactIds().size() > 0);
        setVisible(menu, R.id.menu_select, !isSearchMode && !isSelectionMode);
    }

    private ContactSelectionActivity getContactSelectionActivity() {
        final Activity activity = getActivity();
        if (activity != null && activity instanceof ContactSelectionActivity) {
            return (ContactSelectionActivity) activity;
        }
        return null;
    }

    private static void setVisible(Menu menu, int id, boolean visible) {
        final MenuItem menuItem = menu.findItem(id);
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.onBackPressed();
                }
                return true;
            }
            case R.id.menu_done: {
                if (mListener != null) {
                    mListener.onGroupMembersSelected(getAdapter().getSelectedContactIdsArray());
                }
                return true;
            }
            case R.id.menu_select: {
                if (mListener != null) {
                    mListener.onSelectGroupMembers();
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
