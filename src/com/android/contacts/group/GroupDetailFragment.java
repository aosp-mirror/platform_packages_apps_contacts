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
import com.android.contacts.R;
import com.android.contacts.activities.GroupDetailActivity;
import com.android.contacts.list.ContactListAdapter;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.DefaultContactListAdapter;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
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

/**
 * Displays the details of a group and shows a list of actions possible for the group.
 */
public class GroupDetailFragment extends Fragment implements OnScrollListener {

    private static final String TAG = "GroupDetailFragment";

    private static final int LOADER_MEMBERS = 0;

    private Context mContext;

    private View mRootView;
    private ListView mMemberListView;

    private ContactListAdapter mAdapter;
    private ContactPhotoManager mPhotoManager;

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
        mMemberListView = (ListView) mRootView.findViewById(R.id.member_list);
        mMemberListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO: Open contact detail for this person
            }
        });
        return mRootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Intent intent = getActivity().getIntent();
        String accountType = intent.getStringExtra(GroupDetailActivity.KEY_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(GroupDetailActivity.KEY_ACCOUNT_NAME);
        long groupId = intent.getLongExtra(GroupDetailActivity.KEY_GROUP_ID, -1);
        String groupTitle = intent.getStringExtra(GroupDetailActivity.KEY_GROUP_TITLE);

        configureAdapter(accountType, accountName, groupId, groupTitle);
        startGroupMembersLoader();
    }

    private void configureAdapter(String accountType, String accountName,
                long groupId, String groupTitle) {
        mAdapter = new DefaultContactListAdapter(getActivity());
        mAdapter.setSectionHeaderDisplayEnabled(false);
        mAdapter.setDisplayPhotos(true);
        mAdapter.setHasHeader(0, false);
        mAdapter.setQuickContactEnabled(false);
        mAdapter.setPinnedPartitionHeadersEnabled(false);
        mAdapter.setContactNameDisplayOrder(ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY);
        mAdapter.setSortOrder(ContactsContract.Preferences.SORT_ORDER_PRIMARY);
        mAdapter.setPhotoLoader(mPhotoManager);
        mAdapter.setFilter(new ContactListFilter(accountType, accountName, groupId, "", false,
                groupTitle));
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

    /**
     * Start the loader to retrieve the list of group members.
     */
    private void startGroupMembersLoader() {
        getLoaderManager().destroyLoader(LOADER_MEMBERS);
        getLoaderManager().restartLoader(LOADER_MEMBERS, null, mGroupMemberListLoaderListener);
    }

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
            mAdapter.changeCursor(loader.getId(), data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

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
