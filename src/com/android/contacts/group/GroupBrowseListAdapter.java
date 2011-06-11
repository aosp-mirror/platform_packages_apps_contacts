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
import com.android.contacts.R;
import com.android.contacts.list.ContactListPinnedHeaderView;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.Groups;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter to populate the list of groups.
 */
public class GroupBrowseListAdapter extends BaseAdapter {

    private Context mContext;
    private LayoutInflater mLayoutInflater;

    private List<GroupListEntry> mGroupList = new ArrayList<GroupListEntry>();
    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    enum ViewType {
        HEADER, ITEM;
    }

    private static final int VIEW_TYPE_COUNT = ViewType.values().length;

    public GroupBrowseListAdapter(Context context, Map<String, List<GroupMetaData>> groupMap) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        for (String accountName : groupMap.keySet()) {
            List<GroupMetaData> groupsListForAccount = groupMap.get(accountName);

            // Add account name as header for section
            mGroupList.add(GroupListEntry.createEntryForHeader(accountName,
                    groupsListForAccount.size()));

            // Add groups within that account as subsequent list items.
            for (GroupMetaData singleGroup : groupsListForAccount) {
                mGroupList.add(GroupListEntry.createEntryForGroup(singleGroup));
            }
        }
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
    }

    public void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
    }

    private boolean isSelectedGroup(Uri groupUri) {
        return mSelectedGroupUri != null && mSelectedGroupUri.equals(groupUri);
    }

    @Override
    public int getCount() {
        return mGroupList.size();
    }

    @Override
    public long getItemId(int position) {
        return mGroupList.get(position).id;
    }

    @Override
    public GroupListEntry getItem(int position) {
        return mGroupList.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return mGroupList.get(position).type.ordinal();
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return mGroupList.get(position).type == ViewType.ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        GroupListEntry item = getItem(position);
        switch (item.type) {
            case HEADER:
                return getHeaderView(item, convertView, parent);
            case ITEM:
                return getGroupListItemView(item, convertView, parent);
            default:
                throw new IllegalStateException("Invalid GroupListEntry item type " + item.type);
        }

    }

    private View getHeaderView(GroupListEntry entry, View convertView, ViewGroup parent) {
        ContactListPinnedHeaderView result = (ContactListPinnedHeaderView) (convertView == null ?
                new ContactListPinnedHeaderView(mContext, null) :
                convertView);
        String groupCountString = mContext.getResources().getQuantityString(
                R.plurals.num_groups_in_account, entry.count, entry.count);
        // TODO: Format this correctly by using 2 TextViews when the
        // ContactListPinnedHeaderView is refactored.
        result.setSectionHeader(entry.title + " " + groupCountString);
                        return result;
    }

    private View getGroupListItemView(GroupListEntry entry, View convertView, ViewGroup parent) {
        GroupListItem result = (GroupListItem) (convertView == null ?
                mLayoutInflater.inflate(R.layout.group_browse_list_item, parent, false) :
                convertView);
        result.loadFromGroup(entry.groupData);
        if (mSelectionVisible) {
            result.setActivated(isSelectedGroup(result.getUri()));
        }
        return result;
    }

    /**
     * This is a data model object to represent one row in the list of groups were the entry
     * could be a header or group item.
     */
    public static class GroupListEntry {
        public final ViewType type;
        public final String title;
        public final int count;
        public final GroupMetaData groupData;
        /**
         * The id is equal to the group ID (if groupData is available), otherwise it is -1 for
         * header entries.
         */
        public final long id;

        private GroupListEntry(ViewType entryType, String headerTitle, int headerGroupCount,
                GroupMetaData groupMetaData, long entryId) {
            type = entryType;
            title = headerTitle;
            count = headerGroupCount;
            groupData = groupMetaData;
            id = entryId;
        }

        public static GroupListEntry createEntryForHeader(String headerTitle, int groupCount) {
            return new GroupListEntry(ViewType.HEADER, headerTitle, groupCount, null, -1);
        }

        public static GroupListEntry createEntryForGroup(GroupMetaData groupMetaData) {
            if (groupMetaData == null) {
                throw new IllegalStateException("Cannot create list entry for a null group");
            }
            return new GroupListEntry(ViewType.ITEM, null, 0, groupMetaData,
                    groupMetaData.getGroupId());
        }
    }

    /**
     * A row in a list of groups, where this row displays a single group's title
     * and associated account.
     */
    public static class GroupListItem extends LinearLayout {

        private TextView mLabel;
        private TextView mAccount;
        private Uri mUri;

        public GroupListItem(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public GroupListItem(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public GroupListItem(Context context) {
            super(context);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
            mLabel = (TextView) findViewById(R.id.label);
            mAccount = (TextView) findViewById(R.id.account);
        }

        public void loadFromGroup(GroupMetaData group) {
            mLabel.setText(group.getTitle());
            mAccount.setText(group.getAccountName());
            mUri = ContentUris.withAppendedId(Groups.CONTENT_URI, group.getGroupId());
        }

        public Uri getUri() {
            return mUri;
        }
    }
}