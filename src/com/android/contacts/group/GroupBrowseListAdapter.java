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

import java.util.List;

/**
 * Adapter to populate the list of groups.
 */
public class GroupBrowseListAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private List<GroupMetaData> mGroupList;

    public GroupBrowseListAdapter(Context context, List<GroupMetaData> groupList) {
        mLayoutInflater = LayoutInflater.from(context);
        mGroupList = groupList;
    }

    @Override
    public int getCount() {
        return mGroupList.size();
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getGroupId();
    }

    @Override
    public GroupMetaData getItem(int position) {
        return mGroupList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        GroupListItem result = (GroupListItem) (convertView == null ?
                mLayoutInflater.inflate(R.layout.group_browse_list_item, parent, false) :
                convertView);
        result.loadFromGroup(getItem(position));
        return result;
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