/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.GroupMetaData;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.GroupDetailActivity;
import com.android.contacts.list.ContactListAdapter;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.DefaultContactListAdapter;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Displays the details of a group and shows a list of actions possible for the group.
 */
public class GroupDetailFragment extends Fragment implements OnScrollListener {

    public static interface Listener {
        /**
         * The group title has been loaded
         */
        public void onGroupTitleUpdated(String title);

        /**
         * The number of group members has been determined
         */
        public void onGroupSizeUpdated(String size);
    }

    private static final String TAG = "GroupDetailFragment";

    private static final int LOADER_METADATA = 0;
    private static final int LOADER_MEMBERS = 1;

    private Context mContext;

    private View mRootView;
    private TextView mGroupTitle;
    private TextView mGroupSize;
    private ListView mMemberListView;

    private Listener mListener;

    private ContactListAdapter mAdapter;
    private ContactPhotoManager mPhotoManager;

    private Uri mGroupUri;
    private long mGroupId;

    public GroupDetailFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        configurePhotoLoader();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mRootView = inflater.inflate(R.layout.group_detail_fragment, container, false);
        mGroupTitle = (TextView) mRootView.findViewById(R.id.group_title);
        mGroupSize = (TextView) mRootView.findViewById(R.id.group_size);
        mMemberListView = (ListView) mRootView.findViewById(android.R.id.list);
        mMemberListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO: Open contact detail for this person
            }
        });
        return mRootView;
    }

    public void loadGroup(Uri groupUri) {
        mGroupUri= groupUri;
        startGroupMetadataLoader();
    }

    private void configureAdapter(long groupId) {
        mAdapter = new DefaultContactListAdapter(getActivity());
        mAdapter.setSectionHeaderDisplayEnabled(false);
        mAdapter.setDisplayPhotos(true);
        mAdapter.setHasHeader(0, false);
        mAdapter.setQuickContactEnabled(false);
        mAdapter.setPinnedPartitionHeadersEnabled(false);
        mAdapter.setContactNameDisplayOrder(ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY);
        mAdapter.setSortOrder(ContactsContract.Preferences.SORT_ORDER_PRIMARY);
        mAdapter.setPhotoLoader(mPhotoManager);
        mAdapter.setFilter(ContactListFilter.createGroupFilter(groupId));
        mMemberListView.setAdapter(mAdapter);
    }

    private void configurePhotoLoader() {
        if (mContext != null) {
            if (mPhotoManager == null) {
                mPhotoManager = ContactPhotoManager.getInstance(mContext);
            }
            if (mMemberListView != null) {
                mMemberListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoManager);
            }
        }
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    /**
     * Start the loader to retrieve the metadata for this group.
     */
    private void startGroupMetadataLoader() {
        getLoaderManager().destroyLoader(LOADER_METADATA);
        getLoaderManager().restartLoader(LOADER_METADATA, null, mGroupMetadataLoaderListener);
    }

    /**
     * Start the loader to retrieve the list of group members.
     */
    private void startGroupMembersLoader() {
        getLoaderManager().destroyLoader(LOADER_MEMBERS);
        getLoaderManager().restartLoader(LOADER_MEMBERS, null, mGroupMemberListLoaderListener);
    }

    /**
     * The listener for the group metadata loader.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMetadataLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            bindGroupMetaData(data);

            // Retrieve the list of members
            configureAdapter(mGroupId);
            startGroupMembersLoader();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /**
     * The listener for the group members list loader
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            CursorLoader loader = new CursorLoader(mContext, null, null, null, null, null);
            mAdapter.configureLoader(loader, Directory.DEFAULT);
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            updateSize(Integer.toString(data.getCount()));
            mAdapter.changeCursor(0, data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private void bindGroupMetaData(Cursor cursor) {
        cursor.moveToPosition(-1);
        if (cursor.moveToNext()) {
            mGroupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
            updateTitle(cursor.getString(GroupMetaDataLoader.TITLE));
        }
    }

    private void updateTitle(String title) {
        if (mGroupTitle != null) {
            mGroupTitle.setText(title);
        } else {
            mListener.onGroupTitleUpdated(title);
        }
    }

    private void updateSize(String size) {
        if (mGroupSize != null) {
            mGroupSize.setText(size);
        } else {
            mListener.onGroupSizeUpdated(size);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else {
            mPhotoManager.resume();
        }
    }
}
