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

import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.GroupListLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.list.MultiSelectContactsListFragment;

/** Displays the members of a group. */
public class GroupMembersListFragment extends MultiSelectContactsListFragment {

    private static final String TAG = "GroupMembers";

    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final String ARG_GROUP_URI = "groupUri";

    private static final int LOADER_GROUP_METADATA = 0;
    private static final int LOADER_GROUP_LIST_DETAILS = 1;

    /** Callbacks for hosts of {@link GroupMembersListFragment}. */
    public interface GroupMembersListListener {

        /** Invoked after group metadata for the passed in group URI has loaded. */
        void onGroupMetadataLoaded(GroupMetadata groupMetadata);

        /** Invoked if group metadata can't be loaded for the passed in group URI. */
        void onGroupMetadataLoadFailed();

        /** Invoked when a group member in the list is clicked. */
        void onGroupMemberListItemClicked(Uri contactLookupUri);
    }

    /** Step 1 of loading group metadata. */
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
            // TODO(wjang): how should we handle deleted groups
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

            getLoaderManager().restartLoader(LOADER_GROUP_LIST_DETAILS, null, mGroupListCallbacks);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /** Step 2 of loading group metadata. */
    private final LoaderCallbacks<Cursor> mGroupListCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final GroupListLoader groupListLoader = new GroupListLoader(getActivity());

            // TODO(wjang): modify GroupListLoader to accept this selection criteria more naturally
            groupListLoader.setSelection(groupListLoader.getSelection()
                    + " AND " + ContactsContract.Groups._ID + "=?");

            final String[] selectionArgs = new String[1];
            selectionArgs[0] = Long.toString(mGroupMetadata.groupId);
            groupListLoader.setSelectionArgs(selectionArgs);

            return groupListLoader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || cursor.isClosed()) {
                Log.e(TAG, "Failed to load group list details");
                return;
            }
            if (cursor.moveToNext()) {
                mGroupMetadata.memberCount = cursor.getInt(GroupListLoader.MEMBER_COUNT);
            }
            onGroupMetadataLoaded();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private Uri mGroupUri;

    private GroupMembersListListener mListener;

    private GroupMetadata mGroupMetadata;

    public static GroupMembersListFragment newInstance(Uri groupUri) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_GROUP_URI, groupUri);

        final GroupMembersListFragment fragment = new GroupMembersListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMembersListFragment() {
        setHasOptionsMenu(true);

        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        // Don't show the scrollbar until after group members have been loaded
        setVisibleScrollbarEnabled(false);
        setQuickContactEnabled(false);
    }

    public void setListener(GroupMembersListListener listener) {
        mListener = listener;
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    private void onGroupMetadataLoaded() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Loaded " + mGroupMetadata);

        maybeAttachCheckBoxListener();

        // Bind the members count
        final View accountFilterContainer = getView().findViewById(
                R.id.account_filter_header_container);
        if (mGroupMetadata.memberCount >= 0) {
            accountFilterContainer.setVisibility(View.VISIBLE);

            final TextView accountFilterHeader = (TextView) accountFilterContainer.findViewById(
                    R.id.account_filter_header);
            accountFilterHeader.setText(getResources().getQuantityString(
                    R.plurals.group_members_count, mGroupMetadata.memberCount,
                    mGroupMetadata.memberCount));
        } else {
            accountFilterContainer.setVisibility(View.GONE);
        }

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
    protected GroupMembersListAdapter createListAdapter() {
        final GroupMembersListAdapter adapter = new GroupMembersListAdapter(getContext());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    public GroupMembersListAdapter getAdapter() {
        return (GroupMembersListAdapter) super.getAdapter();
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
        return inflater.inflate(R.layout.contact_list_content, /* root */ null);
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
            final Uri contactLookupUri = getAdapter().getContactLookupUri(position);
            mListener.onGroupMemberListItemClicked(contactLookupUri);
        }
    }
}
