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
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Arranges contacts in {@link StrequentContactListFragment} (aka favorites) according to
 * provided {@link DisplayType}.
 * Also allows for a configurable number of columns and {@link DisplayType}
 */
public class ContactTileAdapter extends BaseAdapter {
    private static final String TAG = "ContactTileAdapter";

    /**
     * mContacts2 is only used if {@link DisplayType} is Strequent
     * All starred contacts are placed into mContacts2
     */
    private ArrayList<ContactEntry> mContacts2 = new ArrayList<ContactEntry>();

    /**
     * In {@link DisplayType#STREQUENT} only the frequently contacted
     * contacts will be placed into mContacts.
     * All other {@link DisplayType} will put all contacts into mContacts.
     */
    private ArrayList<ContactEntry> mContacts = new ArrayList<ContactEntry>();
    private DisplayType mDisplayType;
    private Listener mListener;
    private Context mContext;
    private int mColumnCount;
    private int mDividerRowIndex;
    private ContactPhotoManager mPhotoManager;

    /**
     * Configures the adapter to filter and display contacts using different view types.
     * TODO: Create Uris to support getting Starred_only and Frequent_only cursors.
     */
    public enum DisplayType {
        /**
         * Displays a mixed view type where Starred Contacts
         * are in a regular {@link ContactTileView} layout and
         * frequent contacts are in a small {@link ContactTileView} layout.
         */
        STREQUENT,

        /**
         * Display only starred contacts in
         * regular {@link ContactTileView} layout.
         */
        STARRED_ONLY,

        /**
         * Display only most frequently contacted in a
         * small {@link ContactTileView} layout.
         */
        FREQUENT_ONLY,

        /**
         * Display all contacts from a group in the cursor in a
         * regular {@link ContactTileView} layout.
         */
        GROUP_MEMBERS
    }

    public ContactTileAdapter(Context context, Listener listener, int numCols,
            DisplayType displayType) {
        mListener = listener;
        mContext = context;
        mColumnCount = numCols;
        mPhotoManager = ContactPhotoManager.getInstance(context);
        mDisplayType = displayType;
    }

    public void loadFromCursor(Cursor cursor) {
        mContacts.clear();
        mContacts2.clear();

        // If the loader was canceled we will be given a null cursor.
        // In that case, show an empty list of contacts.
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContactEntry contact = new ContactEntry();

                long id = cursor.getLong(StrequentMetaDataLoader.CONTACT_ID);
                String lookupKey = cursor.getString(StrequentMetaDataLoader.LOOKUP_KEY);
                String photoUri = cursor.getString(StrequentMetaDataLoader.PHOTO_URI);

                contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);

                contact.lookupKey = ContentUris.withAppendedId(
                        Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);
                contact.name = cursor.getString(StrequentMetaDataLoader.DISPLAY_NAME);

                boolean isStarred = (cursor.getInt(StrequentMetaDataLoader.STARRED) == 1);

                switch (mDisplayType) {
                    case STREQUENT:
                        (isStarred ? mContacts2 : mContacts).add(contact);
                        break;
                    case STARRED_ONLY:
                        if (isStarred) {
                            mContacts.add(contact);
                        }
                        break;
                    case FREQUENT_ONLY:
                        if (!isStarred) {
                            mContacts.add(contact);
                        }
                        break;
                    case GROUP_MEMBERS:
                        mContacts.add(contact);
                        break;
                    default:
                        throw new IllegalArgumentException("Unrecognized displayType");
                }
            }
        }

        mDividerRowIndex =
                (mDisplayType == DisplayType.STREQUENT ? getNumRows(mContacts2.size()) : -1);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        int numRows = getNumRows(mContacts2.size()) + getNumRows(mContacts.size());
        // Adding Divider Row if Neccessary
        if (mDisplayType == DisplayType.STREQUENT && mContacts.size() > 0) numRows++;
        return numRows;
    }

    public void setColumnCount(int colCount) {
        mColumnCount = colCount;
        notifyDataSetChanged();
    }

    /**
     * Returns the number of rows required to show the provided number of entries
     * with the current number of columns.
     */
    private int getNumRows(int entryCount) {
        return entryCount == 0 ? 0 : ((entryCount - 1) / mColumnCount) + 1;
    }

    /**
     * Returns an ArrayList of the {@link ContactEntry}s that are to appear
     * on the row for the given position.
     */
    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        if (position == mDividerRowIndex) return null;
        ArrayList<ContactEntry> resultList = new ArrayList<ContactEntry>();

        // Determining which Arraylist to use for display
        int contactIndex = position * mColumnCount;
        ArrayList<ContactEntry> contactList;
        if (contactIndex < mContacts2.size()) {
            contactList = mContacts2;
        } else {
            if (mDisplayType == DisplayType.STREQUENT) {
                contactIndex = (position - mDividerRowIndex - 1) * mColumnCount;
            }
            contactList = mContacts;
        }

        // Populating with the Contacts to appear at position
        for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
            if (contactIndex >= contactList.size()) break;
            resultList.add(contactList.get(contactIndex));
            contactIndex++;
        }

        return resultList;
    }

    @Override
    public long getItemId(int position) {
        /*
         * As we show several selectable items for each ListView row,
         * we can not determine a stable id. But as we don't rely on ListView's selection,
         * this should not be a problem.
         */
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return mDisplayType != DisplayType.STREQUENT;
    }

    @Override
    public boolean isEnabled(int position) {
        return position != mDividerRowIndex;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // Checking position to draw the divider
        if (position == mDividerRowIndex) {
            return convertView == null ? createDivider() : convertView;
        }

        ContactTileRow contactTileRowView = (ContactTileRow) convertView;
        ArrayList<ContactEntry> contactList = getItem(position);

        // Creating new row if needed
        if (contactTileRowView == null) {
            int itemViewType = getItemViewType(position);
            int layoutResId = getLayoutResourceId(itemViewType);
            contactTileRowView = new ContactTileRow(mContext, layoutResId,
                    itemViewType == ViewTypes.REGULAR);
        }

        contactTileRowView.configureRow(contactList);
        return contactTileRowView;
    }

    /**
     * Divider uses a list_seperator.xml along with text to denote
     * the most frequently contacted contacts.
     */
    private View createDivider() {
        View dividerView = View.inflate(mContext, R.layout.list_separator, null);
        dividerView.setFocusable(false);
        TextView text = (TextView) dividerView.findViewById(R.id.header_text);
        text.setText(mContext.getString(R.string.favoritesFrquentSeparator));
        return dividerView;
    }

    private int getLayoutResourceId(int viewType) {
        switch (viewType) {
            case ViewTypes.SMALL:
                return R.layout.contact_tile_small;
            case ViewTypes.REGULAR:
                return R.layout.contact_tile_regular;
            default:
                throw new IllegalArgumentException("Received unrecognized viewType " + viewType);
        }
    }
    @Override
    public int getViewTypeCount() {
        return mDisplayType == DisplayType.STREQUENT ? ViewTypes.COUNT : 1;
    }

    /**
     * Returns view type based on {@link DisplayType}.
     * {@link DisplayType#STARRED_ONLY} and {@link DisplayType#GROUP_MEMBERS}
     * are {@link ViewTypes#REGULAR}.
     * {@link DisplayType#FREQUENT_ONLY} is {@link ViewTypes#SMALL}.
     * {@link DisplayType#STREQUENT} mixes both {@link ViewTypes}
     * and also adds in {@link ViewTypes#DIVIDER}.
     */
    @Override
    public int getItemViewType(int position) {
        switch (mDisplayType) {
            case STREQUENT:
                if (position < mDividerRowIndex) {
                    return ViewTypes.REGULAR;
                } else if (position == mDividerRowIndex) {
                    return ViewTypes.DIVIDER;
                } else {
                    return ViewTypes.SMALL;
                }
            case STARRED_ONLY:
            case GROUP_MEMBERS:
                return ViewTypes.REGULAR;
            case FREQUENT_ONLY:
                return ViewTypes.SMALL;
            default:
                throw new IllegalStateException(
                        "Received unrecognized DisplayType " + mDisplayType);
        }
    }

    /**
     * Acts as a row item composed of {@link ContactTileView}
     */
    private class ContactTileRow extends LinearLayout implements OnClickListener {
        private int mLayoutResId;
        private boolean mIsContactTileSquare;

        public ContactTileRow(Context context, int layoutResId, boolean isSquare) {
            super(context);
            mLayoutResId = layoutResId;
            mIsContactTileSquare = isSquare;
        }

        /**
         * Configures the row to add {@link ContactEntry}s information to the views
         */
        public void configureRow(ArrayList<ContactEntry> list) {
            // Adding tiles to row and filling in contact information
            for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
                ContactEntry entry =
                        columnCounter < list.size() ? list.get(columnCounter) : null;
                addTileFromEntry(entry, columnCounter);
            }
        }

        private void addTileFromEntry(ContactEntry entry, int tileIndex) {
            ContactTileView contactTile;

            if (getChildCount() <= tileIndex) {
                contactTile = (ContactTileView) inflate(mContext, mLayoutResId, null);
                contactTile.setIsSquare(mIsContactTileSquare);
                contactTile.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                contactTile.setPhotoManager(mPhotoManager);
                contactTile.setOnClickListener(this);
                addView(contactTile);
            } else {
                contactTile = (ContactTileView) getChildAt(tileIndex);
            }
            contactTile.setClickable(entry != null);
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
    public static class ContactEntry {
        public Uri photoUri;
        public String name;
        public Uri lookupKey;
    }

    private static class ViewTypes {
        public static final int COUNT = 3;
        public static final int SMALL = 0;
        public static final int REGULAR = 1;
        public static final int DIVIDER = 2;
    }

    public interface Listener {
        public void onContactSelected(Uri contactUri);
    }
}
