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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.util.StreamItemEntry;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * List adapter for stream items of a given contact.
 */
public class StreamItemAdapter extends BaseAdapter {
    /** The header view, hidden under the tab carousel, if present. */
    private static final int ITEM_VIEW_TYPE_HEADER = 0;
    /** The updates in the list. */
    private static final int ITEM_VIEW_TYPE_STREAM_ITEM = 1;

    private final Context mContext;
    private final View.OnClickListener mItemClickListener;
    private final View.OnClickListener mPhotoClickListener;
    private final LayoutInflater mInflater;

    private List<StreamItemEntry> mStreamItems;

    public StreamItemAdapter(Context context, View.OnClickListener itemClickListener,
            View.OnClickListener photoClickListener) {
        mContext = context;
        mItemClickListener = itemClickListener;
        mPhotoClickListener = photoClickListener;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStreamItems = Lists.newArrayList();
    }

    @Override
    public int getCount() {
        // The header should only be included as items in the list if there are other
        // stream items.
        int count = mStreamItems.size();
        return (count == 0) ? 0 : (count + 1);
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        }
        return mStreamItems.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public boolean isEnabled(int position) {
        // Make all list items disabled, so they're not clickable.
        // We make child views clickable in getvView() if the account type supports
        // viewStreamItemActivity or viewStreamItemPhotoActivity.
        return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // See isEnabled().
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            return mInflater.inflate(R.layout.updates_header_contact, null);
        }
        final StreamItemEntry streamItem = (StreamItemEntry) getItem(position);
        final AccountTypeManager manager = AccountTypeManager.getInstance(mContext);
        final AccountType accountType =
                manager.getAccountType(streamItem.getAccountType(), streamItem.getDataSet());

        final View view = ContactDetailDisplayUtils.createStreamItemView(
                mInflater, mContext, convertView, streamItem,
                // Only pass the photo click listener if the account type has the photo
                // view activity.
                (accountType.getViewStreamItemPhotoActivity() == null) ? null : mPhotoClickListener
                );
        final View contentView = view.findViewById(R.id.stream_item_content);

        // If the account type has the stream item view activity, make the stream container
        // clickable.
        if (accountType.getViewStreamItemActivity() != null) {
            contentView.setTag(streamItem);
            contentView.setFocusable(true);
            contentView.setOnClickListener(mItemClickListener);
            contentView.setEnabled(true);
        } else {
            contentView.setTag(null);
            contentView.setFocusable(false);
            contentView.setOnClickListener(null);
            // setOnClickListener makes it clickable, so we need to overwrite it.
            contentView.setClickable(false);
            contentView.setEnabled(false);
        }
        return view;
    }

    @Override
    public int getViewTypeCount() {
        // ITEM_VIEW_TYPE_HEADER and ITEM_VIEW_TYPE_STREAM_ITEM
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return ITEM_VIEW_TYPE_HEADER;
        }
        return ITEM_VIEW_TYPE_STREAM_ITEM;
    }

    public void setStreamItems(List<StreamItemEntry> streamItems) {
        mStreamItems = streamItems;
        notifyDataSetChanged();
    }
}
