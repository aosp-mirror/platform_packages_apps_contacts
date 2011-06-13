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
package com.android.contacts.list;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.StrequentMetaDataLoader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Arranges contacts in {@link StrequentFragment} (aka favorites) according to if the contact
 * is starred or not. Also shows two contacts per row.
 */
public class StrequentAdapter extends BaseAdapter implements OnClickListener {

    private LayoutInflater mInflater;
    private ArrayList<StrequentEntry> mAllEntries;
    private ContactPhotoManager mPhotoManager;
    private Listener mListener;
    private int mMostFrequentCount;
    private int mStarredCount;
    private static final int NUMCOLS = 2;

    public StrequentAdapter(Context context, Listener listener) {
        mInflater = LayoutInflater.from(context);
        mPhotoManager = ContactPhotoManager.createContactPhotoManager(context);
        mListener = listener;
    }

    public void setCursor(Cursor cursor){
        populateStrequentEntries(cursor);
    }

    private void populateStrequentEntries(Cursor cursor) {
        mAllEntries = new ArrayList<StrequentEntry>();
        mMostFrequentCount = mStarredCount = 0;

        // If the loader was canceled we will be given a null cursor.
        // In that case, show an empty list of contacts.
        if (cursor != null) {
            while (cursor.moveToNext()) {
                StrequentEntry contact = new StrequentEntry();

                contact.id = cursor.getLong(StrequentMetaDataLoader.CONTACT_ID);
                contact.photoId = cursor.getLong(StrequentMetaDataLoader.PHOTO_ID);
                contact.name = cursor.getString(StrequentMetaDataLoader.DISPLAY_NAME);

                // Adding Starred Contact
                if (cursor.getInt(StrequentMetaDataLoader.STARRED) == 1) {
                    mStarredCount++;
                } else {
                    mMostFrequentCount++;
                }
                mAllEntries.add(contact);
            }
        }
        this.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mAllEntries.size() / NUMCOLS;
    }

    @Override
    public Object getItem(int position) {
        return mAllEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mAllEntries.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        // Note: For now, every row except for the last row will have 2 columns
        int index = position * NUMCOLS;

        // Try to recycle convertView
        if (convertView != null) {
            holder = (ViewHolder) convertView.getTag();
        } else {
            // Must create new View
            convertView = mInflater.inflate(R.layout.contact_tile_row_regular, null);
            holder = new ViewHolder(convertView);

            holder.getLayoutLeft().setOnClickListener(this);
            holder.getLayoutRight().setOnClickListener(this);

            convertView.setTag(holder);
        }

        holder.getTextLeft().setText(mAllEntries.get(index).name);
        mPhotoManager.loadPhoto(holder.getImageLeft(), mAllEntries.get(index).photoId);
        holder.getLayoutLeft().setTag(mAllEntries.get(index));

        if (++index < mAllEntries.size()) {
            holder.getTextRight().setText(mAllEntries.get(index).name);
            mPhotoManager.loadPhoto(holder.mImageRight, mAllEntries.get(index).photoId);
            holder.getLayoutRight().setTag(mAllEntries.get(index));
        } else {
            holder.getTextRight().setText(null);
            holder.getImageRight().setImageBitmap(null);
            holder.getLayoutRight().setOnClickListener(null);
        }

        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    /**
     * Class it hold views for layout to help make wonderful listview faster
     */
    private static class ViewHolder {
        private TextView mTextLeft, mTextRight;
        private ImageView mImageLeft, mImageRight;
        private View mLayoutLeft, mLayoutRight;

        public ViewHolder(View convertView){
            // Left Column
            mTextLeft = (TextView) convertView.findViewById(R.id.contactTile_left_name);
            mImageLeft = (ImageView) convertView.findViewById(R.id.contactTile_left_image);
            mLayoutLeft = convertView.findViewById(R.id.contactTile_row_left);

            // Right Column
            mTextRight = (TextView) convertView.findViewById(R.id.contactTile_right_name);
            mImageRight = (ImageView) convertView.findViewById(R.id.contactTile_right_image);
            mLayoutRight = convertView.findViewById(R.id.contactTile_row_right);
        }

        public TextView getTextLeft() {
            return mTextLeft;
        }

        public void setTextLeft(TextView textLeft) {
            this.mTextLeft = textLeft;
        }

        public TextView getTextRight() {
            return mTextRight;
        }

        public void setTextRight(TextView textRight) {
            this.mTextRight = textRight;
        }

        public ImageView getImageLeft() {
            return mImageLeft;
        }

        public void setImageLeft(ImageView imageLeft) {
            this.mImageLeft = imageLeft;
        }

        public ImageView getImageRight() {
            return mImageRight;
        }

        public void setImageRight(ImageView imageRight) {
            this.mImageRight = imageRight;
        }

        public View getLayoutLeft() {
            return mLayoutLeft;
        }

        public void setLayoutLeft(View layoutLeft) {
            this.mLayoutLeft = layoutLeft;
        }

        public View getLayoutRight() {
            return mLayoutRight;
        }

        public void setLayoutRight(View layoutRight) {
            this.mLayoutRight = layoutRight;
        }
    }

    /**
     * Class to hold contact information
     */
    private static class StrequentEntry {
        public long id;
        public long photoId;
        public String name;
    }

    @Override
    public void onClick(View v) {
        StrequentEntry entry = (StrequentEntry)v.getTag();
        Uri data = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(entry.id));
        mListener.onContactSelected(data);
    }

    public interface Listener {
        public void onContactSelected(Uri contactUri);
    }
}
