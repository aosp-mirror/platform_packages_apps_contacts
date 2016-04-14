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
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.Groups;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.GroupListLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.interactions.GroupDeletionDialogFragment;

/** Displays the members of a group. */
public class GroupMembersListFragment extends ContactEntryListFragment<GroupMembersListAdapter> {

    private static final String TAG = "GroupMembersList";

    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final int LOADER_GROUP_METADATA = 0;
    private static final int LOADER_GROUP_LIST_DETAILS = 1;

    private final LoaderCallbacks<Cursor> mGroupMetadataCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(getContext(), mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || cursor.isClosed()) {
                Log.e(TAG, "Failed to load group metadata");
                return;
            }
            if (cursor.moveToNext()) {
                final boolean deleted = cursor.getInt(GroupMetaDataLoader.DELETED) == 1;
                if (!deleted) {
                    mGroupMetadata = new GroupMetadata();
                    mGroupMetadata.accountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
                    mGroupMetadata.dataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
                    mGroupMetadata.groupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
                    mGroupMetadata.groupName = cursor.getString(GroupMetaDataLoader.TITLE);
                    mGroupMetadata.readOnly = cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1;

                    final AccountTypeManager accountTypeManager =
                            AccountTypeManager.getInstance(getContext());
                    final AccountType accountType = accountTypeManager.getAccountType(
                            mGroupMetadata.accountType, mGroupMetadata.dataSet);
                    mGroupMetadata.editable = accountType.isGroupMembershipEditable();

                    getLoaderManager().restartLoader(LOADER_GROUP_LIST_DETAILS, null,
                            mGroupListDetailsCallbacks);
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private final LoaderCallbacks<Cursor> mGroupListDetailsCallbacks =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final GroupListLoader groupListLoader = new GroupListLoader(getContext());

            // TODO(wjang): modify GroupListLoader to accept this selection criteria more naturally
            groupListLoader.setSelection(groupListLoader.getSelection()
                    + " AND " + Groups._ID + "=?");

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

    private static final class GroupMetadata implements Parcelable {

        public static final Creator<GroupMetadata> CREATOR = new Creator<GroupMetadata>() {

            public GroupMetadata createFromParcel(Parcel in) {
                return new GroupMetadata(in);
            }

            public GroupMetadata[] newArray(int size) {
                return new GroupMetadata[size];
            }
        };

        String accountType;
        String dataSet;
        long groupId;
        String groupName;
        boolean readOnly;
        boolean editable;
        int memberCount = -1;

        GroupMetadata() {
        }

        GroupMetadata(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(accountType);
            dest.writeString(dataSet);
            dest.writeLong(groupId);
            dest.writeString(groupName);
            dest.writeInt(readOnly ? 1 : 0);
            dest.writeInt(editable ? 1 : 0);
            dest.writeInt(memberCount);
        }

        private void readFromParcel(Parcel source) {
            accountType = source.readString();
            dataSet = source.readString();
            groupId = source.readLong();
            groupName = source.readString();
            readOnly = source.readInt() == 1;
            editable = source.readInt() == 1;
            memberCount = source.readInt();
        }

        @Override
        public String toString() {
            return "GroupMetadata[accountType=" + accountType +
                    " dataSet=" + dataSet +
                    " groupId=" + groupId +
                    " groupName=" + groupName +
                    " readOnly=" + readOnly +
                    " editable=" + editable +
                    " memberCount=" + memberCount +
                    "]";
        }
    }

    /** Callbacks for hosts of {@link GroupMembersListFragment}. */
    public interface GroupMembersListCallbacks {

        /** Invoked when the user hits back in the action bar. */
        void onHomePressed();

        /** Invoked after group metadata has been loaded. */
        void onGroupNameLoaded(String groupName);

        /** Invoked when a group member in the list is clicked. */
        void onGroupMemberClicked(Uri contactLookupUri);

        /** Invoked when a user chooses ot edit the group whose members are being displayed. */
        void onEditGroup(Uri groupUri);
    }

    private Uri mGroupUri;

    private GroupMembersListCallbacks mCallbacks;

    private GroupMetadata mGroupMetadata;

    public GroupMembersListFragment() {
        setHasOptionsMenu(true);

        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        // Don't show the scrollbar until after group members have been loaded
        setVisibleScrollbarEnabled(false);
        setQuickContactEnabled(false);
    }

    /** Sets the Uri of the group whose members will be displayed. */
    public void setGroupUri(Uri groupUri) {
        mGroupUri = groupUri;
    }

    /** Sets a listener for group member click events. */
    public void setCallbacks(GroupMembersListCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState != null) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    @Override
    protected void startLoading() {
        if (mGroupMetadata == null) {
            getLoaderManager().restartLoader(LOADER_GROUP_METADATA, null, mGroupMetadataCallbacks);
        } else {
            onGroupMetadataLoaded();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view_group, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem editMenu = menu.findItem(R.id.menu_edit_group);
        editMenu.setVisible(isGroupEditable());

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete_group);
        deleteMenu.setVisible(isGroupDeletable());
    }

    private boolean isGroupEditable() {
        return mGroupUri != null && mGroupMetadata != null && mGroupMetadata.editable;
    }

    private boolean isGroupDeletable() {
        return mGroupUri != null && mGroupMetadata != null && !mGroupMetadata.readOnly;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                if (mCallbacks != null) {
                    mCallbacks.onHomePressed();
                }
                return true;
            }
            case R.id.menu_edit_group: {
                if (mCallbacks != null) {
                    mCallbacks.onEditGroup(mGroupUri);
                }
                break;
            }
            case R.id.menu_delete_group: {
                GroupDeletionDialogFragment.show(getFragmentManager(), mGroupMetadata.groupId,
                        mGroupMetadata.groupName, /* endActivity */ true);
                return true;
            }
        }
        return false;
    }

    private void onGroupMetadataLoaded() {
        final Activity activity = getActivity();
        if (activity != null) activity.invalidateOptionsMenu();

        // Set the title
        if (mCallbacks != null) {
            mCallbacks.onGroupNameLoaded(mGroupMetadata.groupName);
        }

        // Set the header
        bindMembersCount();

        // Start loading the group members
        super.startLoading();
    }

    private void bindMembersCount() {
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
    }

    @Override
    protected GroupMembersListAdapter createListAdapter() {
        final GroupMembersListAdapter adapter = new GroupMembersListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
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
        return inflater.inflate(R.layout.contact_list_content, /* root */ null);
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (mCallbacks != null) {
            final Uri contactLookupUri = getAdapter().getContactLookupUri(position);
            mCallbacks.onGroupMemberClicked(contactLookupUri);
        }
    }
}
