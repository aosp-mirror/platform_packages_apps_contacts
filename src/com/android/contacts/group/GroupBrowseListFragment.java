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
import com.android.contacts.widget.AutoScrollListView;

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
import android.text.TextUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private boolean mSelectionToScreenRequested;

    private static final String EXTRA_KEY_GROUP_URI = "groups.groupUri";

    /**
     * Map of account name to a list of {@link GroupMetaData} objects
     * representing groups within that account.
     * TODO: Change account name string into a wrapper object that has
     * account name, type, and authority.
     */
    private Map<String, List<GroupMetaData>> mGroupMap = new HashMap<String, List<GroupMetaData>>();

    private View mRootView;
    private AutoScrollListView mListView;
    private View mEmptyView;

    private GroupBrowseListAdapter mAdapter;
    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    private int mVerticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT;

    private OnGroupBrowserActionListener mListener;

    public GroupBrowseListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.group_browse_list_fragment, null);
        mListView = (AutoScrollListView) mRootView.findViewById(R.id.list);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mEmptyView = mRootView.findViewById(R.id.empty);

        if (savedInstanceState != null) {
            String groupUriString = savedInstanceState.getString(EXTRA_KEY_GROUP_URI);
            if (groupUriString != null) {
                mSelectedGroupUri = Uri.parse(groupUriString);
            }
        }
        return mRootView;
    }

    public void setVerticalScrollbarPosition(int position) {
        if (mVerticalScrollbarPosition != position) {
            mVerticalScrollbarPosition = position;
            configureVerticalScrollbar();
        }
    }

    private void configureVerticalScrollbar() {
        mListView.setFastScrollEnabled(true);
        mListView.setFastScrollAlwaysVisible(true);
        mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        int leftPadding = 0;
        int rightPadding = 0;
        if (mVerticalScrollbarPosition == View.SCROLLBAR_POSITION_LEFT) {
            leftPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        } else {
            rightPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        }
        mListView.setPadding(leftPadding, mListView.getPaddingTop(),
                rightPadding, mListView.getPaddingBottom());
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
        mGroupMap.clear();
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

            // Don't show the "auto-added" (i.e. My Contacts) or "favorites" groups because
            // they show up elsewhere in the app
            if (defaultGroup || favorites) {
                continue;
            }

            GroupMetaData newGroup = new GroupMetaData(accountName, accountType, groupId, title,
                    defaultGroup, favorites);

            if (mGroupMap.containsKey(accountName)) {
                List<GroupMetaData> groups = mGroupMap.get(accountName);
                groups.add(newGroup);
            } else {
                List<GroupMetaData> groups = new ArrayList<GroupMetaData>();
                groups.add(newGroup);
                mGroupMap.put(accountName, groups);
            }

        }

        mAdapter = new GroupBrowseListAdapter(mContext, mGroupMap);
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setSelectedGroup(mSelectedGroupUri);

        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GroupListItem groupListItem = (GroupListItem) view;
                viewGroup(groupListItem.getUri());
            }
        });

        if (mSelectionToScreenRequested) {
            requestSelectionToScreen();
        }

        if (mSelectionVisible && mSelectedGroupUri != null) {
            viewGroup(mSelectedGroupUri);
        }
    }

    public void setListener(OnGroupBrowserActionListener listener) {
        mListener = listener;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
    }

    private void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
        mAdapter.setSelectedGroup(groupUri);
        mListView.invalidateViews();
    }

    private void viewGroup(Uri groupUri) {
        setSelectedGroup(groupUri);
        if (mListener != null) mListener.onViewGroupAction(groupUri);
    }

    public void setSelectedUri(Uri groupUri) {
        viewGroup(groupUri);
        mSelectionToScreenRequested = true;
    }

    protected void requestSelectionToScreen() {
        int selectedPosition = mAdapter.getSelectedGroupPosition();
        if (selectedPosition != -1) {
            mListView.requestPositionToScreen(selectedPosition, true /* smooth scroll requested */);
        }
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedGroupUri != null) {
            String uriString = mSelectedGroupUri.toString();
            if (!TextUtils.isEmpty(uriString)) {
                outState.putString(EXTRA_KEY_GROUP_URI, uriString);
            }
        }
    }
}
