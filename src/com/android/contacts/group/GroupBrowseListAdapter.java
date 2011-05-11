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

import com.android.contacts.R;
import com.android.contacts.GroupMetaData;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
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
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.group_browse_list_item, parent, false);
        }
        GroupMetaData group = getItem(position);
        ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
        TextView label = (TextView) convertView.findViewById(R.id.label);
        TextView account = (TextView) convertView.findViewById(R.id.account);
        icon.setImageResource(R.drawable.ic_menu_display_all_holo_light);
        label.setText(group.getTitle());
        account.setText(group.getAccountName());
        return convertView;
    }

}