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
import com.android.contacts.ContactTileLoaderFactory;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.R;
import com.android.contacts.list.ContactTileAdapter.DisplayType;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Arranges contacts in {@link ContactTileListFragment} (aka favorites) according to
 * provided {@link DisplayType}.
 * Also allows for a configurable number of columns and {@link DisplayType}
 */
public class ContactTileAdapter extends BaseAdapter {
    private static final String TAG = ContactTileAdapter.class.getSimpleName();

    private DisplayType mDisplayType;
    private Listener mListener;
    private Context mContext;
    private Resources mResources;
    private Cursor mContactCursor = null;
    private ContactPhotoManager mPhotoManager;

    /**
     * Index of the first NON starred contact in the {@link Cursor}
     * Only valid when {@link DisplayType#STREQUENT} is true
     */
    private int mDividerPosition;
    private int mColumnCount;
    private int mIdIndex;
    private int mLookupIndex;
    private int mPhotoUriIndex;
    private int mNameIndex;
    private int mStarredIndex;
    private int mPresenceIndex;
    private int mStatusIndex;

    /**
     * Only valid when {@link DisplayType#STREQUENT_PHONE_ONLY} is true
     */
    private int mPhoneNumberIndex;
    private int mPhoneNumberTypeIndex;
    private int mPhoneNumberLabelIndex;

    private boolean mIsQuickContactEnabled = false;

    /**
     * Configures the adapter to filter and display contacts using different view types.
     * TODO: Create Uris to support getting Starred_only and Frequent_only cursors.
     */
    public enum DisplayType {
        /**
         * Displays a mixed view type of starred and frequent contacts
         */
        STREQUENT,

        /**
         * Displays a mixed view type of starred and frequent contacts based on phone data.
         * Also includes secondary touch target.
         */
        STREQUENT_PHONE_ONLY,

        /**
         * Display only starred contacts
         */
        STARRED_ONLY,

        /**
         * Display only most frequently contacted
         */
        FREQUENT_ONLY,

        /**
         * Display all contacts from a group in the cursor
         * Use {@link GroupMemberLoader}
         * when passing {@link Cursor} into loadFromCusor method.
         */
        GROUP_MEMBERS
    }

    public ContactTileAdapter(Context context, Listener listener, int numCols,
            DisplayType displayType) {
        mListener = listener;
        mContext = context;
        mResources = context.getResources();
        mColumnCount = (displayType == DisplayType.FREQUENT_ONLY ? 1 : numCols);
        mDisplayType = displayType;

        bindColumnIndices();
    }

    public void setPhotoLoader(ContactPhotoManager photoLoader) {
        mPhotoManager = photoLoader;
    }

    public void setColumnCount(int columnCount) {
        mColumnCount = columnCount;
    }

    public void setDisplayType(DisplayType displayType) {
        mDisplayType = displayType;
    }

    public void enableQuickContact(boolean enableQuickContact) {
        mIsQuickContactEnabled = enableQuickContact;
    }

    /**
     * Sets the column indices for expected {@link Cursor}
     * based on {@link DisplayType}.
     */
    private void bindColumnIndices() {
        /**
         * Need to check for {@link DisplayType#GROUP_MEMBERS} because
         * it has different projections than all other {@link DisplayType}s
         * By using {@link GroupMemberLoader} and {@link ContactTileLoaderFactory}
         * the correct {@link Cursor}s will be given.
         */
        if (mDisplayType == DisplayType.GROUP_MEMBERS) {
            mIdIndex = GroupMemberLoader.CONTACT_PHOTO_ID_COLUMN_INDEX;
            mLookupIndex = GroupMemberLoader.CONTACT_LOOKUP_KEY_COLUMN_INDEX;
            mPhotoUriIndex = GroupMemberLoader.CONTACT_PHOTO_URI_COLUMN_INDEX;
            mNameIndex = GroupMemberLoader.CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
            mStarredIndex = GroupMemberLoader.CONTACT_STARRED_COLUMN_INDEX;
            mPresenceIndex = GroupMemberLoader.CONTACT_PRESENCE_STATUS_COLUMN_INDEX;
            mStatusIndex = GroupMemberLoader.CONTACT_STATUS_COLUMN_INDEX;
        } else {
            mIdIndex = ContactTileLoaderFactory.CONTACT_ID;
            mLookupIndex = ContactTileLoaderFactory.LOOKUP_KEY;
            mPhotoUriIndex = ContactTileLoaderFactory.PHOTO_URI;
            mNameIndex = ContactTileLoaderFactory.DISPLAY_NAME;
            mStarredIndex = ContactTileLoaderFactory.STARRED;
            mPresenceIndex = ContactTileLoaderFactory.CONTACT_PRESENCE;
            mStatusIndex = ContactTileLoaderFactory.CONTACT_STATUS;

            mPhoneNumberIndex = ContactTileLoaderFactory.PHONE_NUMBER;
            mPhoneNumberTypeIndex = ContactTileLoaderFactory.PHONE_NUMBER_TYPE;
            mPhoneNumberLabelIndex = ContactTileLoaderFactory.PHONE_NUMBER_LABEL;
        }
    }

    /**
     * Creates {@link ContactTileView}s for each item in {@link Cursor}.
     * If {@link DisplayType} is {@link DisplayType#GROUP_MEMBERS} use {@link GroupMemberLoader}
     * Else use {@link ContactTileLoaderFactory}
     */
    public void setContactCursor(Cursor cursor) {
        mContactCursor = cursor;
        mDividerPosition = getDividerPosition(cursor);
        notifyDataSetChanged();
    }

    /**
     * Iterates over the {@link Cursor}
     * Returns position of the first NON Starred Contact
     * Returns -1 if {@link DisplayType#STARRED_ONLY} or {@link DisplayType#GROUP_MEMBERS}
     * Returns 0 if {@link DisplayType#FREQUENT_ONLY}
     */
    private int getDividerPosition(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) {
            throw new IllegalStateException("Unable to access cursor");
        }

        switch (mDisplayType) {
            case STREQUENT:
            case STREQUENT_PHONE_ONLY:
                while (cursor.moveToNext()) {
                    if (cursor.getInt(mStarredIndex) == 0) {
                        return cursor.getPosition();
                    }
                }
                break;
            case GROUP_MEMBERS:
            case STARRED_ONLY:
                // There is no divider
                return -1;
            case FREQUENT_ONLY:
                // Divider is first
                return 0;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }

        // There are not NON Starred contacts in cursor
        // Set divider positon to end
        return cursor.getCount();
    }

    private ContactEntry createContactEntryFromCursor(Cursor cursor, int position) {
        // If the loader was canceled we will be given a null cursor.
        // In that case, show an empty list of contacts.
        if (cursor == null || cursor.isClosed() || cursor.getCount() <= position) return null;

        cursor.moveToPosition(position);
        long id = cursor.getLong(mIdIndex);
        String photoUri = cursor.getString(mPhotoUriIndex);
        String lookupKey = cursor.getString(mLookupIndex);

        ContactEntry contact = new ContactEntry();
        String name = cursor.getString(mNameIndex);
        contact.name = (name != null) ? name : mResources.getString(R.string.missing_name);
        contact.status = cursor.getString(mStatusIndex);
        contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);
        contact.lookupKey = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);
        contact.presence = cursor.isNull(mPresenceIndex) ? null : cursor.getInt(mPresenceIndex);

        if (mDisplayType == DisplayType.STREQUENT_PHONE_ONLY) {
            int phoneNumberType = cursor.getInt(mPhoneNumberTypeIndex);
            String phoneNumberCustomLabel = cursor.getString(mPhoneNumberLabelIndex);
            contact.phoneLabel = (String) Phone.getTypeLabel(mResources, phoneNumberType,
                    phoneNumberCustomLabel);
            contact.phoneNumber = cursor.getString(mPhoneNumberIndex);
        } else {
            contact.status = cursor.getString(mStatusIndex);
            contact.presence = cursor.isNull(mPresenceIndex) ? null : cursor.getInt(mPresenceIndex);
        }

        return contact;
    }

    @Override
    public int getCount() {
        if (mContactCursor == null) {
            return 0;
        }

        switch (mDisplayType) {
            case STARRED_ONLY:
            case GROUP_MEMBERS:
                return getRowCount(mContactCursor.getCount());
            case STREQUENT:
            case STREQUENT_PHONE_ONLY:
                // Takes numbers of rows the Starred Contacts Occupy
                int starredRowCount = getRowCount(mDividerPosition);

                // Calculates the number of frequent contacts
                int frequentRowCount = mContactCursor.getCount() - mDividerPosition ;

                // If there are any frequent contacts add one for the divider
                frequentRowCount += frequentRowCount == 0 ? 0 : 1;

                // Return the number of starred plus frequent rows
                return starredRowCount + frequentRowCount;
            case FREQUENT_ONLY:
                // Number of frequent contacts plus one for the header
                return mContactCursor.getCount() + 1;
            default:
                throw new IllegalArgumentException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    /**
     * Returns the number of rows required to show the provided number of entries
     * with the current number of columns.
     */
    private int getRowCount(int entryCount) {
        return entryCount == 0 ? 0 : ((entryCount - 1) / mColumnCount) + 1;
    }

    /**
     * Returns an ArrayList of the {@link ContactEntry}s that are to appear
     * on the row for the given position.
     */
    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        ArrayList<ContactEntry> resultList = new ArrayList<ContactEntry>(mColumnCount);
        int contactIndex = position * mColumnCount;

        switch (mDisplayType) {
            case FREQUENT_ONLY:
                // Taking the current position and subtracting one because of the header
                resultList.add(createContactEntryFromCursor(mContactCursor, position - 1));
                break;
            case STARRED_ONLY:
            case GROUP_MEMBERS:
                for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
                    resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                    contactIndex++;
                }
                break;
            case STREQUENT:
            case STREQUENT_PHONE_ONLY:
                if (position < getRowCount(mDividerPosition)) {
                    for (int columnCounter = 0; columnCounter < mColumnCount &&
                            contactIndex != mDividerPosition; columnCounter++) {
                        resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                        contactIndex++;
                    }
                } else {
                    /*
                     * Current position minus how many rows are before the divider and
                     * Minus 1 for the divider itself provides the relative index of the frequent
                     * contact being displayed. Then add the dividerPostion to give the offset
                     * into the contacts cursor to get the absoulte index.
                     */
                    contactIndex = position - getRowCount(mDividerPosition) - 1 + mDividerPosition;
                    resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                }
                break;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }
        return resultList;
    }

    @Override
    public long getItemId(int position) {
        // As we show several selectable items for each ListView row,
        // we can not determine a stable id. But as we don't rely on ListView's selection,
        // this should not be a problem.
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return (mDisplayType != DisplayType.STREQUENT &&
                mDisplayType != DisplayType.STREQUENT_PHONE_ONLY);
    }

    @Override
    public boolean isEnabled(int position) {
        return position != getRowCount(mDividerPosition);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int itemViewType = getItemViewType(position);

        if (itemViewType == ViewTypes.DIVIDER) {
            // Checking For Divider First so not to cast convertView
            return convertView == null ? getDivider() : convertView;
        }

        ContactTileRow contactTileRowView = (ContactTileRow) convertView;
        ArrayList<ContactEntry> contactList = getItem(position);

        if (contactTileRowView == null) {
            // Creating new row if needed
            contactTileRowView = new ContactTileRow(mContext, itemViewType);
        }

        contactTileRowView.configureRow(contactList, position == getCount() - 1);
        return contactTileRowView;
    }

    /**
     * Divider uses a list_seperator.xml along with text to denote
     * the most frequently contacted contacts.
     */
    private View getDivider() {
        View dividerView = View.inflate(mContext, R.layout.list_separator, null);
        dividerView.setFocusable(false);
        TextView text = (TextView) dividerView.findViewById(R.id.header_text);

        text.setText(mDisplayType == DisplayType.STREQUENT_PHONE_ONLY ?
                mContext.getString(R.string.favoritesFrequentCalled) :
                mContext.getString(R.string.favoritesFrequentContacted));

       return dividerView;
    }

    private int getLayoutResourceId(int viewType) {
        switch (viewType) {
            case ViewTypes.STARRED:
                return mIsQuickContactEnabled ?
                        R.layout.contact_tile_starred_quick_contact : R.layout.contact_tile_starred;
            case ViewTypes.FREQUENT:
                return mDisplayType == DisplayType.STREQUENT_PHONE_ONLY ?
                        R.layout.contact_tile_frequent_phone : R.layout.contact_tile_frequent;
            case ViewTypes.STARRED_WITH_SECONDARY_ACTION:
                return R.layout.contact_tile_starred_secondary_target;
            default:
                throw new IllegalArgumentException("Unrecognized viewType " + viewType);
        }
    }
    @Override
    public int getViewTypeCount() {
        return ViewTypes.MAX_VIEW_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        /*
         * Returns view type based on {@link DisplayType}.
         * {@link DisplayType#STARRED_ONLY} and {@link DisplayType#GROUP_MEMBERS}
         * are {@link ViewTypes#STARRED}.
         * {@link DisplayType#FREQUENT_ONLY} is {@link ViewTypes#FREQUENT}.
         * {@link DisplayType#STREQUENT} mixes both {@link ViewTypes}
         * and also adds in {@link ViewTypes#DIVIDER}.
         */
        switch (mDisplayType) {
            case STREQUENT:
                if (position < getRowCount(mDividerPosition)) {
                    return ViewTypes.STARRED;
                } else if (position == getRowCount(mDividerPosition)) {
                    return ViewTypes.DIVIDER;
                } else {
                    return ViewTypes.FREQUENT;
                }
            case STREQUENT_PHONE_ONLY:
                if (position < getRowCount(mDividerPosition)) {
                    return ViewTypes.STARRED_WITH_SECONDARY_ACTION;
                 } else if (position == getRowCount(mDividerPosition)) {
                    return ViewTypes.DIVIDER;
                } else {
                    return ViewTypes.FREQUENT;
                }
            case STARRED_ONLY:
            case GROUP_MEMBERS:
                return ViewTypes.STARRED;
            case FREQUENT_ONLY:
                return position == 0 ? ViewTypes.DIVIDER : ViewTypes.FREQUENT;
            default:
                throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
        }
    }

    private ContactTileView.Listener mContactTileListener = new ContactTileView.Listener() {
        @Override
        public void onClick(ContactTileView contactTileView) {
            if (mListener != null) {
                mListener.onContactSelected(contactTileView.getLookupUri());
            }
        }
    };

    /**
     * Acts as a row item composed of {@link ContactTileView}
     */
    private class ContactTileRow extends LinearLayout {
        private int mItemViewType;
        private int mLayoutResId;

        public ContactTileRow(Context context, int itemViewType) {
            super(context);
            mItemViewType = itemViewType;
            mLayoutResId = getLayoutResourceId(mItemViewType);
        }

        /**
         * Configures the row to add {@link ContactEntry}s information to the views
         */
        public void configureRow(ArrayList<ContactEntry> list, boolean isLastRow) {
            int columnCount = mItemViewType == ViewTypes.FREQUENT ? 1 : mColumnCount;

            // Adding tiles to row and filling in contact information
            for (int columnCounter = 0; columnCounter < columnCount; columnCounter++) {
                ContactEntry entry =
                        columnCounter < list.size() ? list.get(columnCounter) : null;
                addTileFromEntry(entry, columnCounter, isLastRow);
            }
        }

        private void addTileFromEntry(ContactEntry entry, int childIndex, boolean isLastRow) {
            final ContactTileView contactTile;

            if (getChildCount() <= childIndex) {
                contactTile = (ContactTileView) inflate(mContext, mLayoutResId, null);
                contactTile.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                contactTile.setPhotoManager(mPhotoManager);
                contactTile.setListener(mContactTileListener);
                addView(contactTile);
            } else {
                contactTile = (ContactTileView) getChildAt(childIndex);
            }
            contactTile.setClickable(entry != null);
            contactTile.loadFromContact(entry);

            contactTile.setVerticalDividerVisibility(
                    childIndex >= mColumnCount - 1 ? View.GONE : View.VISIBLE);
            contactTile.setHorizontalDividerVisibility(
                    isLastRow ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Class to hold contact information
     */
    public static class ContactEntry {
        public String name;
        public String status;
        public String phoneLabel;
        public String phoneNumber;
        public Uri photoUri;
        public Uri lookupKey;
        public Integer presence;
    }

    private static class ViewTypes {
        public static final int MAX_VIEW_COUNT = 4;
        public static final int STARRED = 0;
        public static final int DIVIDER = 1;
        public static final int FREQUENT = 2;
        public static final int STARRED_WITH_SECONDARY_ACTION = 3;
    }

    public interface Listener {
        public void onContactSelected(Uri contactUri);
    }
}
