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
 * limitations under the License.
 */

package com.android.contacts.group;

import com.android.contacts.GroupMetaData;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.group.GroupBrowseListAdapter.GroupListItem;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Groups;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment to display the list of groups.
 */
public class GroupBrowseListFragment extends Fragment
        implements OnFocusChangeListener, OnTouchListener {

    /**
     * Action callbacks that can be sent by a group list.
     */
    public interface OnGroupBrowserActionListener  {

        /**
         * Opens the specified group for viewing.
         *
         * @param groupUri for the group that the user wishes to view.
         */
        void onViewGroupAction(Uri groupUri);
    }

    private static final String TAG = "GroupBrowseListFragment";

    private static final int LOADER_GROUPS = 1;

    private Context mContext;
    private Cursor mGroupListCursor;
    private List<GroupMetaData> mGroupList = new ArrayList<GroupMetaData>();

    private View mRootView;
    private ListView mListView;
    private View mEmptyView;

    private OnGroupBrowserActionListener mListener;

    public GroupBrowseListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.group_browse_list_fragment, null);
        mListView = (ListView) mRootView.findViewById(R.id.list);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mEmptyView = mRootView.findViewById(R.id.empty);
        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();
    }

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, Groups.CONTENT_URI);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mGroupListCursor = data;
            bindGroupList();
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    private void bindGroupList() {
        if (mGroupListCursor == null) {
            return;
        }
        mGroupList.clear();
        mGroupListCursor.moveToPosition(-1);
        while (mGroupListCursor.moveToNext()) {
            String accountName = mGroupListCursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            String accountType = mGroupListCursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            long groupId = mGroupListCursor.getLong(GroupMetaDataLoader.GROUP_ID);
            String title = mGroupListCursor.getString(GroupMetaDataLoader.TITLE);
            boolean defaultGroup = mGroupListCursor.isNull(GroupMetaDataLoader.AUTO_ADD)
                    ? false
                    : mGroupListCursor.getInt(GroupMetaDataLoader.AUTO_ADD) != 0;
            boolean favorites = mGroupListCursor.isNull(GroupMetaDataLoader.FAVORITES)
                    ? false
                    : mGroupListCursor.getInt(GroupMetaDataLoader.FAVORITES) != 0;

            // TODO: Separate groups according to account name and type.
            mGroupList.add(new GroupMetaData(
                    accountName, accountType, groupId, title, defaultGroup, favorites));
        }

        mListView.setAdapter(new GroupBrowseListAdapter(mContext, mGroupList));
        mListView.setEmptyView(mEmptyView);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GroupListItem groupListItem = (GroupListItem) view;
                viewGroup(groupListItem.getUri());
            }
        });
    }

    public void setListener(OnGroupBrowserActionListener listener) {
        mListener = listener;
    }

    private void viewGroup(Uri groupUri) {
        if (mListener != null) mListener.onViewGroupAction(groupUri);
    }

    private void hideSoftKeyboard() {
        if (mContext == null) {
            return;
        }
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            hideSoftKeyboard();
        }
        return false;
    }
}
