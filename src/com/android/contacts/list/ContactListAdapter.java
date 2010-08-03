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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.QuickContactBadge;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public abstract class ContactListAdapter extends ContactEntryListAdapter {

    protected static final String[] PROJECTION = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.PHOTO_ID,                      // 6
        Contacts.LOOKUP_KEY,                    // 7
        Contacts.PHONETIC_NAME,                 // 8
        Contacts.HAS_PHONE_NUMBER,              // 9
    };

    protected static final String[] FILTER_PROJECTION = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.PHOTO_ID,                      // 6
        Contacts.LOOKUP_KEY,                    // 7
        Contacts.PHONETIC_NAME,                 // 8
        Contacts.HAS_PHONE_NUMBER,              // 9
        SearchSnippetColumns.SNIPPET_MIMETYPE,  // 10
        SearchSnippetColumns.SNIPPET_DATA1,     // 11
        SearchSnippetColumns.SNIPPET_DATA4,     // 12
    };

    protected static final int CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    protected static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    protected static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 6;
    protected static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 7;
    protected static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 8;
    protected static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 9;
    protected static final int CONTACT_SNIPPET_MIMETYPE_COLUMN_INDEX = 10;
    protected static final int CONTACT_SNIPPET_DATA1_COLUMN_INDEX = 11;
    protected static final int CONTACT_SNIPPET_DATA4_COLUMN_INDEX = 12;

    private boolean mQuickContactEnabled;
    private CharSequence mUnknownNameText;
    private int mDisplayNameColumnIndex;
    private int mAlternativeDisplayNameColumnIndex;

    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;

    public ContactListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
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

    public void setSelectedContact(long selectedDirectoryId, String lookupKey) {
        if (mSelectedContactDirectoryId != selectedDirectoryId ||
                !TextUtils.equals(mSelectedContactLookupKey, lookupKey)) {
            this.mSelectedContactDirectoryId = selectedDirectoryId;
            this.mSelectedContactLookupKey = lookupKey;
            notifyDataSetChanged();
        }
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

    public boolean isQuickContactEnabled() {
        return mQuickContactEnabled;
    }

    public void setQuickContactEnabled(boolean quickContactEnabled) {
        mQuickContactEnabled = quickContactEnabled;
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
        if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
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
        return getSelectedContactDirectoryId() == directoryId
                && TextUtils.equals(getSelectedContactLookupKey(),
                        cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX));
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        return view;
    }

    protected void bindSectionHeaderAndDivider(ContactListItemView view, int position) {
        Placement placement = getItemPlacementInSection(position);
        view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
        view.setDividerVisible(!placement.lastInSection);
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        // Set the photo, if available
        long photoId = 0;
        if (!cursor.isNull(CONTACT_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
        }

        getPhotoLoader().loadPhoto(view.getPhotoView(), photoId);
    }

    protected void bindQuickContact(
            final ContactListItemView view, int partitionIndex, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(CONTACT_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
        }

        QuickContactBadge quickContact = view.getQuickContact();
        quickContact.assignContactUri(getContactUri(partitionIndex, cursor));
        getPhotoLoader().loadPhoto(quickContact, photoId);
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, mDisplayNameColumnIndex, isNameHighlightingEnabled(),
                mAlternativeDisplayNameColumnIndex);
        view.showPhoneticName(cursor, CONTACT_PHONETIC_NAME_COLUMN_INDEX);
    }

    protected void bindPresence(final ContactListItemView view, Cursor cursor) {
        view.showPresence(cursor, CONTACT_PRESENCE_STATUS_COLUMN_INDEX);
    }

    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
        view.showSnippet(cursor, CONTACT_SNIPPET_MIMETYPE_COLUMN_INDEX,
                CONTACT_SNIPPET_DATA1_COLUMN_INDEX, CONTACT_SNIPPET_DATA4_COLUMN_INDEX);
    }
}
