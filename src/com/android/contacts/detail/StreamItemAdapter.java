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

package com.android.contacts.detail;

import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.StreamItemEntry;
import com.google.android.collect.Lists;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

/**
 * List adapter for stream items of a given contact.
 */
public class StreamItemAdapter extends BaseAdapter {
    /** The header view, hidden under the tab carousel, if present. */
    private static final int ITEM_VIEW_TYPE_HEADER = 0;
    /** The title shown in the updates stream. */
    private static final int ITEM_VIEW_TYPE_TITLE = 1;
    /** The updates in the list. */
    private static final int ITEM_VIEW_TYPE_STREAM_ITEM = 2;

    private final Context mContext;
    private final View.OnClickListener mListener;
    private final LayoutInflater mInflater;

    private List<StreamItemEntry> mStreamItems;

    public StreamItemAdapter(Context context, View.OnClickListener listener) {
        mContext = context;
        mListener = listener;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStreamItems = Lists.newArrayList();
    }

    @Override
    public int getCount() {
        return mStreamItems.size() + 2;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0 || position == 1) {
            return null;
        }
        return mStreamItems.get(position - 2);
    }

    @Override
    public long getItemId(int position) {
        if (position == 0 || position == 1) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            return mInflater.inflate(R.layout.updates_header_contact, null);
        }
        if (position == 1) {
            return mInflater.inflate(R.layout.updates_title, null);
        }
        StreamItemEntry streamItem = (StreamItemEntry) getItem(position);
        View view = ContactDetailDisplayUtils.createStreamItemView(
                mInflater, mContext, streamItem, null);
        final AccountTypeManager manager = AccountTypeManager.getInstance(mContext);
        final AccountType accountType =
                manager.getAccountType(streamItem.getAccountType(), streamItem.getDataSet());
        if (accountType.getViewStreamItemActivity() != null) {
            view.setTag(streamItem);
            view.setFocusable(true);
            view.setOnClickListener(mListener);
        } else {
            view.setTag(null);
            view.setFocusable(false);
            view.setOnClickListener(null);
        }
        return view;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return ITEM_VIEW_TYPE_HEADER;
        }
        if (position == 1) {
            return ITEM_VIEW_TYPE_TITLE;
        }
        return ITEM_VIEW_TYPE_STREAM_ITEM;
    }

    public void setStreamItems(List<StreamItemEntry> streamItems) {
        mStreamItems = streamItems;
        notifyDataSetChanged();
    }
}
