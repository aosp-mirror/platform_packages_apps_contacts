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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * Arranges contacts in {@link StrequentFragment} (aka favorites) according to if the contact
 * is starred or not. Also shows a configurable but fixed number of contacts per row.
 */
public class ContactTileAdapter extends BaseAdapter {
    private static final String TAG = "ContactTileAdapter";

    private ArrayList<StrequentEntry> mAllEntries;
    private Listener mListener;
    private int mMostFrequentCount;
    private int mStarredCount;
    private Context mContext;
    private int mColumnCount;
    private ContactPhotoManager mPhotoManager;

    public ContactTileAdapter(Context context, Listener listener, int numCols) {
        mListener = listener;
        mContext = context;
        mColumnCount = numCols;
        mPhotoManager = ContactPhotoManager.createContactPhotoManager(context);
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

                long id = cursor.getLong(StrequentMetaDataLoader.CONTACT_ID);
                String lookupKey = cursor.getString(StrequentMetaDataLoader.LOOKUP_KEY);
                String photoUri = cursor.getString(StrequentMetaDataLoader.PHOTO_URI);

                if (photoUri != null) contact.photoUri = Uri.parse(photoUri);
                else contact.photoUri = null;

                contact.lookupKey = ContentUris.withAppendedId(
                        Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);
                contact.name = cursor.getString(StrequentMetaDataLoader.DISPLAY_NAME);

                if (cursor.getInt(StrequentMetaDataLoader.STARRED) == 1) mStarredCount++;
                else mMostFrequentCount++;

                mAllEntries.add(contact);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    /*
     * Doing some math to make sure number to rounded up to account
     * for a partially filled row, if necessary.
     */
    public int getCount() {
        return ((mAllEntries.size() - 1) / mColumnCount) + 1;
    }

    @Override
    public Object getItem(int position) {
        return mAllEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int rowIndex = position * mColumnCount;
        ContactTileRow contactTileRowView = (ContactTileRow) convertView;

        if (contactTileRowView == null) {
            contactTileRowView = new ContactTileRow(mContext);
        }

        for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
            int contactIndex = rowIndex + columnCounter;

            if (contactIndex >= mAllEntries.size()) {
                contactTileRowView.removeViewAt(columnCounter);
            } else {
                contactTileRowView.addTileFromEntry(mAllEntries.get(contactIndex), columnCounter);
            }
        }
        return contactTileRowView;
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
     * Acts as a row item composed of {@link ContactTileView}
     */
    private class ContactTileRow extends LinearLayout implements OnClickListener {

        public ContactTileRow(Context context) {
            super(context);
        }

        public void addTileFromEntry(StrequentEntry entry, int tileIndex) {
            ContactTileView contactTile;

            if (getChildCount() <= tileIndex) {
                contactTile = (ContactTileView)
                        inflate(mContext, R.layout.contact_tile_regular, null);

                contactTile.setContactPhotoManager(mPhotoManager);
                contactTile.setOnClickListener(this);
                addView(contactTile);
            } else {
                contactTile = (ContactTileView) getChildAt(tileIndex);
            }
            contactTile.loadFromContact(entry);
        }

        @Override
        public void onClick(View v) {
            mListener.onContactSelected(((ContactTileView) v).getLookupUri());
        }
    }

    /**
     * Class to hold contact information
     */
    public static class StrequentEntry {
        public Uri photoUri;
        public String name;
        public Uri lookupKey;
    }

    public interface Listener {
        public void onContactSelected(Uri contactUri);
    }
}
