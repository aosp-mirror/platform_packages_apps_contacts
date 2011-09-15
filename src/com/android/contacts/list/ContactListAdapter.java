/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.contacts.R;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 * Also includes support for including the {@link ContactsContract.Profile} record in the
 * list.
 */
public abstract class ContactListAdapter extends ContactEntryListAdapter {

    protected static final String[] PROJECTION_CONTACT = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.CONTACT_STATUS,                // 6
        Contacts.PHOTO_ID,                      // 7
        Contacts.PHOTO_THUMBNAIL_URI,           // 8
        Contacts.LOOKUP_KEY,                    // 9
        Contacts.PHONETIC_NAME,                 // 10
        Contacts.HAS_PHONE_NUMBER,              // 11
        Contacts.IS_USER_PROFILE,               // 12
    };

    protected static final String[] PROJECTION_DATA = new String[] {
        Data.CONTACT_ID,                        // 0
        Data.DISPLAY_NAME_PRIMARY,              // 1
        Data.DISPLAY_NAME_ALTERNATIVE,          // 2
        Data.SORT_KEY_PRIMARY,                  // 3
        Data.STARRED,                           // 4
        Data.CONTACT_PRESENCE,                  // 5
        Data.CONTACT_STATUS,                    // 6
        Data.PHOTO_ID,                          // 7
        Data.PHOTO_THUMBNAIL_URI,               // 8
        Data.LOOKUP_KEY,                        // 9
        Data.PHONETIC_NAME,                     // 10
        Data.HAS_PHONE_NUMBER,                  // 11
    };

    protected static final String[] FILTER_PROJECTION = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.CONTACT_STATUS,                // 6
        Contacts.PHOTO_ID,                      // 7
        Contacts.PHOTO_THUMBNAIL_URI,           // 8
        Contacts.LOOKUP_KEY,                    // 9
        Contacts.PHONETIC_NAME,                 // 10
        Contacts.HAS_PHONE_NUMBER,              // 11
        Contacts.IS_USER_PROFILE,               // 12
        SearchSnippetColumns.SNIPPET,           // 13
    };

    protected static final int CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    protected static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    protected static final int CONTACT_CONTACT_STATUS_COLUMN_INDEX = 6;
    protected static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int CONTACT_PHOTO_URI_COLUMN_INDEX = 8;
    protected static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 9;
    protected static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 10;
    protected static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 11;
    protected static final int CONTACT_IS_USER_PROFILE = 12;
    protected static final int CONTACT_SNIPPET_COLUMN_INDEX = 13;

    private CharSequence mUnknownNameText;
    private int mDisplayNameColumnIndex;
    private int mAlternativeDisplayNameColumnIndex;

    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private long mSelectedContactId;

    private ContactListFilter mFilter;

    public ContactListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(R.string.missing_name);
    }

    public CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    public long getSelectedContactDirectoryId() {
        return mSelectedContactDirectoryId;
    }

    public String getSelectedContactLookupKey() {
        return mSelectedContactLookupKey;
    }

    public long getSelectedContactId() {
        return mSelectedContactId;
    }

    public void setSelectedContact(long selectedDirectoryId, String lookupKey, long contactId) {
        mSelectedContactDirectoryId = selectedDirectoryId;
        mSelectedContactLookupKey = lookupKey;
        mSelectedContactId = contactId;
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    public boolean getHasPhoneNumber(int position) {
        return ((Cursor)getItem(position)).getInt(CONTACT_HAS_PHONE_COLUMN_INDEX) != 0;
    }

    public boolean isContactStarred(int position) {
        return ((Cursor)getItem(position)).getInt(CONTACT_STARRED_COLUMN_INDEX) != 0;
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor)getItem(position)).getString(mDisplayNameColumnIndex);
    }

    @Override
    public void setContactNameDisplayOrder(int displayOrder) {
        super.setContactNameDisplayOrder(displayOrder);
        if (getContactNameDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mDisplayNameColumnIndex = CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        } else {
            mDisplayNameColumnIndex = CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        }
    }

    /**
     * Builds the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position.
     */
    public Uri getContactUri(int position) {
        int partitionIndex = getPartitionForPosition(position);
        Cursor item = (Cursor)getItem(position);
        return item != null ? getContactUri(partitionIndex, item) : null;
    }

    public Uri getContactUri(int partitionIndex, Cursor cursor) {
        long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
        String lookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
        Uri uri = Contacts.getLookupUri(contactId, lookupKey);
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (directoryId != Directory.DEFAULT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
        }
        return uri;
    }

    /**
     * Returns true if the specified contact is selected in the list. For a
     * contact to be shown as selected, we need both the directory and and the
     * lookup key to be the same. We are paying no attention to the contactId,
     * because it is volatile, especially in the case of directories.
     */
    public boolean isSelectedContact(int partitionIndex, Cursor cursor) {
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (getSelectedContactDirectoryId() != directoryId) {
            return false;
        }
        String lookupKey = getSelectedContactLookupKey();
        if (lookupKey != null && TextUtils.equals(lookupKey,
                cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX))) {
            return true;
        }

        return directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE
                && getSelectedContactId() == cursor.getLong(CONTACT_ID_COLUMN_INDEX);
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        view.setActivatedStateSupported(isSelectionVisible());
        return view;
    }

    protected void bindSectionHeaderAndDivider(ContactListItemView view, int position,
            Cursor cursor) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);

            // First position, set the contacts number string
            if (position == 0 && cursor.getInt(CONTACT_IS_USER_PROFILE) == 1) {
                view.setCountView(getContactsCount());
            } else {
                view.setCountView(null);
            }
            view.setSectionHeader(placement.sectionHeader);
            view.setDividerVisible(!placement.lastInSection);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
            view.setCountView(null);
        }
    }

    protected void bindPhoto(final ContactListItemView view, int partitionIndex, Cursor cursor) {
        if (!isPhotoSupported(partitionIndex)) {
            view.removePhotoView();
            return;
        }

        // Set the photo, if available
        long photoId = 0;
        if (!cursor.isNull(CONTACT_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
        }

        if (photoId != 0) {
            getPhotoLoader().loadPhoto(view.getPhotoView(), photoId, false, false);
        } else {
            final String photoUriString = cursor.getString(CONTACT_PHOTO_URI_COLUMN_INDEX);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            getPhotoLoader().loadPhoto(view.getPhotoView(), photoUri, false, false);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, mDisplayNameColumnIndex, mAlternativeDisplayNameColumnIndex,
                false, getContactNameDisplayOrder());
        view.showPhoneticName(cursor, CONTACT_PHONETIC_NAME_COLUMN_INDEX);
    }

    protected void bindPresenceAndStatusMessage(final ContactListItemView view, Cursor cursor) {
        view.showPresenceAndStatusMessage(cursor, CONTACT_PRESENCE_STATUS_COLUMN_INDEX,
                CONTACT_CONTACT_STATUS_COLUMN_INDEX);
    }

    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
        view.showSnippet(cursor, CONTACT_SNIPPET_COLUMN_INDEX);
    }

    public int getSelectedContactPosition() {
        if (mSelectedContactLookupKey == null && mSelectedContactId == 0) {
            return -1;
        }

        Cursor cursor = null;
        int partitionIndex = -1;
        int partitionCount = getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            DirectoryPartition partition = (DirectoryPartition) getPartition(i);
            if (partition.getDirectoryId() == mSelectedContactDirectoryId) {
                partitionIndex = i;
                break;
            }
        }
        if (partitionIndex == -1) {
            return -1;
        }

        cursor = getCursor(partitionIndex);
        if (cursor == null) {
            return -1;
        }

        cursor.moveToPosition(-1);      // Reset cursor
        int offset = -1;
        while (cursor.moveToNext()) {
            if (mSelectedContactLookupKey != null) {
                String lookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
                if (mSelectedContactLookupKey.equals(lookupKey)) {
                    offset = cursor.getPosition();
                    break;
                }
            }
            if (mSelectedContactId != 0 && (mSelectedContactDirectoryId == Directory.DEFAULT
                    || mSelectedContactDirectoryId == Directory.LOCAL_INVISIBLE)) {
                long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
                if (contactId == mSelectedContactId) {
                    offset = cursor.getPosition();
                    break;
                }
            }
        }
        if (offset == -1) {
            return -1;
        }

        int position = getPositionForPartition(partitionIndex) + offset;
        if (hasHeader(partitionIndex)) {
            position++;
        }
        return position;
    }

    public boolean hasValidSelection() {
        return getSelectedContactPosition() != -1;
    }

    public Uri getFirstContactUri() {
        int partitionCount = getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            DirectoryPartition partition = (DirectoryPartition) getPartition(i);
            if (partition.isLoading()) {
                continue;
            }

            Cursor cursor = getCursor(i);
            if (cursor == null) {
                continue;
            }

            if (!cursor.moveToFirst()) {
                continue;
            }

            return getContactUri(i, cursor);
        }

        return null;
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        super.changeCursor(partitionIndex, cursor);

        // Check if a profile exists
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            setProfileExists(cursor.getInt(CONTACT_IS_USER_PROFILE) == 1);
        }
    }
}
