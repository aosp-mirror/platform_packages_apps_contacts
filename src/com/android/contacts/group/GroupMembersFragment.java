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
 * limitations under the License
 */
package com.android.contacts.group;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.GroupMembersActivity;
import com.android.contacts.common.list.ContactsSectionIndexer;
import com.android.contacts.common.list.MultiSelectEntryContactListAdapter;
import com.android.contacts.common.logging.ListEvent.ListType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.group.GroupMembersAdapter.GroupMembersQuery;
import com.android.contacts.list.MultiSelectContactsListFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Displays the members of a group. */
public class GroupMembersFragment extends MultiSelectContactsListFragment<GroupMembersAdapter>
        implements MultiSelectEntryContactListAdapter.DeleteContactListener {

    private static final String TAG = "GroupMembers";

    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final String ARG_GROUP_URI = "groupUri";

    private static final int LOADER_GROUP_METADATA = 0;

    /** Callbacks for hosts of {@link GroupMembersFragment}. */
    public interface GroupMembersListener {

        /** Invoked after group metadata for the passed in group URI has loaded. */
        void onGroupMetadataLoaded(GroupMetadata groupMetadata);

        /** Invoked if group metadata can't be loaded for the passed in group URI. */
        void onGroupMetadataLoadFailed();

        /** Invoked when a group member in the list is clicked. */
        void onGroupMemberListItemClicked(int position, Uri contactLookupUri);

        /** Invoked when a the delete button for a group member in the list is clicked. */
        void onGroupMemberListItemDeleted(int position, long contactId);
    }

    /** Filters out duplicate contacts. */
    private class FilterCursorWrapper extends CursorWrapper {

        private int[] mIndex;
        private int mCount = 0;
        private int mPos = 0;

        public FilterCursorWrapper(Cursor cursor) {
            super(cursor);

            mCount = super.getCount();
            mIndex = new int[mCount];

            final List<Integer> indicesToFilter = new ArrayList<>();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Group members CursorWrapper start: " + mCount);
            }

            final Bundle bundle = cursor.getExtras();
            final String sections[] = bundle.getStringArray(Contacts
                    .EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            final int counts[] = bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            final ContactsSectionIndexer indexer = (sections == null || counts == null)
                    ? null : new ContactsSectionIndexer(sections, counts);

            mGroupMemberContactIds.clear();
            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String contactId = getString(GroupMembersQuery.CONTACT_ID);
                if (!mGroupMemberContactIds.contains(contactId)) {
                    mIndex[mPos++] = i;
                    mGroupMemberContactIds.add(contactId);
                } else {
                    indicesToFilter.add(i);
                }
            }

            if (indexer != null && GroupUtil.needTrimming(mCount, counts, indexer.getPositions())) {
                GroupUtil.updateBundle(bundle, indexer, indicesToFilter, sections, counts);
            }

            mCount = mPos;
            mPos = 0;
            super.moveToFirst();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Group members CursorWrapper end: " + mCount);
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
            if (position >= mCount) {
                mPos = mCount;
                return false;
            } else if (position < 0) {
                mPos = -1;
                return false;
            }
            mPos = mIndex[position];
            return super.moveToPosition(mPos);
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

    private final LoaderCallbacks<Cursor> mGroupMetadataCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(getActivity(), mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || cursor.isClosed() || !cursor.moveToNext()) {
                Log.e(TAG, "Failed to load group metadata for " + mGroupUri);
                if (mListener != null) {
                    mListener.onGroupMetadataLoadFailed();
                }
                return;
            }
            mGroupMetadata = new GroupMetadata();
            mGroupMetadata.uri = mGroupUri;
            mGroupMetadata.accountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            mGroupMetadata.accountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            mGroupMetadata.dataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
            mGroupMetadata.groupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
            mGroupMetadata.groupName = cursor.getString(GroupMetaDataLoader.TITLE);
            mGroupMetadata.readOnly = cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1;

            final AccountTypeManager accountTypeManager =
                    AccountTypeManager.getInstance(getActivity());
            final AccountType accountType = accountTypeManager.getAccountType(
                    mGroupMetadata.accountType, mGroupMetadata.dataSet);
            mGroupMetadata.editable = accountType.isGroupMembershipEditable();

            onGroupMetadataLoaded();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private Uri mGroupUri;

    private GroupMembersListener mListener;

    private GroupMetadata mGroupMetadata;

    private Set<String> mGroupMemberContactIds = new HashSet();

    public static GroupMembersFragment newInstance(Uri groupUri) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_GROUP_URI, groupUri);

        final GroupMembersFragment fragment = new GroupMembersFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMembersFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setHasOptionsMenu(true);

        setListType(ListType.GROUP);
    }

    public void setListener(GroupMembersListener listener) {
        mListener = listener;
    }

    public void displayDeleteButtons(boolean displayDeleteButtons) {
        getAdapter().setDisplayDeleteButtons(displayDeleteButtons);
    }

    public ArrayList<String> getMemberContactIds() {
        return new ArrayList<>(mGroupMemberContactIds);
    }

    public int getMemberCount() {
        return mGroupMemberContactIds.size();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState == null) {
            mGroupUri = getArguments().getParcelable(ARG_GROUP_URI);
        } else {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        }
    }

    @Override
    protected void startLoading() {
        if (mGroupMetadata == null || !mGroupMetadata.isValid()) {
            getLoaderManager().restartLoader(LOADER_GROUP_METADATA, null, mGroupMetadataCallbacks);
        } else {
            onGroupMetadataLoaded();
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null) {
            // Wait until contacts are loaded before showing the scrollbar
            setVisibleScrollbarEnabled(true);

            final FilterCursorWrapper cursorWrapper = new FilterCursorWrapper(data);
            bindMembersCount(cursorWrapper.getCount());
            super.onLoadFinished(loader, cursorWrapper);
            // Update state of menu items (e.g. "Remove contacts") based on number of group members.
            getActivity().invalidateOptionsMenu();
        }
    }

    private void bindMembersCount(int memberCount) {
        final View accountFilterContainer = getView().findViewById(
                R.id.account_filter_header_container);
        final View emptyGroupView = getView().findViewById(R.id.empty_group);
        if (memberCount > 0) {
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    mGroupMetadata.accountName, mGroupMetadata.accountType, mGroupMetadata.dataSet);
            bindListHeader(getContext(), getListView(), accountFilterContainer,
                    accountWithDataSet, memberCount);
            emptyGroupView.setVisibility(View.GONE);
        } else {
            hideHeaderAndAddPadding(getContext(), getListView(), accountFilterContainer);
            emptyGroupView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    private void onGroupMetadataLoaded() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Loaded " + mGroupMetadata);

        maybeAttachCheckBoxListener();

        if (mListener != null) {
            mListener.onGroupMetadataLoaded(mGroupMetadata);
        }

        // Start loading the group members
        super.startLoading();
    }

    private void maybeAttachCheckBoxListener() {
        // Don't attach the multi select check box listener if we can't edit the group
        if (mGroupMetadata != null && mGroupMetadata.editable) {
            try {
                setCheckBoxListListener((OnCheckBoxListActionListener) getActivity());
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity() + " must implement " +
                        OnCheckBoxListActionListener.class.getSimpleName());
            }
        }
    }

    @Override
    protected GroupMembersAdapter createListAdapter() {
        final GroupMembersAdapter adapter = new GroupMembersAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        adapter.setDeleteContactListener(this);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        if (mGroupMetadata != null) {
            getAdapter().setGroupId(mGroupMetadata.groupId);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        final View view = inflater.inflate(R.layout.contact_list_content, /* root */ null);
        final View emptyGroupView = inflater.inflate(R.layout.empty_group_view, null);

        final ImageView image = (ImageView) emptyGroupView.findViewById(R.id.empty_group_image);
        final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) image.getLayoutParams();
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        params.setMargins(0, screenHeight /
                getResources().getInteger(R.integer.empty_group_view_image_margin_divisor), 0, 0);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(params);

        final FrameLayout contactListLayout = (FrameLayout) view.findViewById(R.id.contact_list);
        contactListLayout.addView(emptyGroupView);

        final Button addContactsButton =
                (Button) emptyGroupView.findViewById(R.id.add_member_button);
        addContactsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((GroupMembersActivity) getActivity()).startGroupAddMemberActivity();
            }
        });
        return view;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        }
        if (mListener != null) {
            mListener.onGroupMemberListItemClicked(position, uri);
        }
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        final Activity activity = getActivity();
        if (activity != null && activity instanceof GroupMembersActivity) {
            if (((GroupMembersActivity) activity).isEditMode()) {
                return true;
            }
        }
        return super.onItemLongClick(position, id);
    }

    @Override
    public void onContactDeleteClicked(int position) {
        final long contactId = getAdapter().getContactId(position);
        mListener.onGroupMemberListItemDeleted(position, contactId);
    }
}
