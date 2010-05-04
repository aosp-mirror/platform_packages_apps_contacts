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
import android.provider.ContactsContract.SearchSnippetColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public abstract class ContactListAdapter extends ContactEntryListAdapter {

    protected static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
    };

    protected static final String[] CONTACTS_SUMMARY_FILTER_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        SearchSnippetColumns.SNIPPET_MIMETYPE, // 11
        SearchSnippetColumns.SNIPPET_DATA1,     // 12
        SearchSnippetColumns.SNIPPET_DATA4,     // 13
    };

    protected static final int SUMMARY_ID_COLUMN_INDEX = 0;
    protected static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int SUMMARY_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    protected static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 5;
    protected static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 6;
    protected static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    protected static final int SUMMARY_PHONETIC_NAME_COLUMN_INDEX = 9;
    protected static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;
    protected static final int SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX = 11;
    protected static final int SUMMARY_SNIPPET_DATA1_COLUMN_INDEX = 12;
    protected static final int SUMMARY_SNIPPET_DATA4_COLUMN_INDEX = 13;

    private boolean mQuickContactEnabled;
    private CharSequence mUnknownNameText;
    private int mDisplayNameColumnIndex;
    private int mAlternativeDisplayNameColumnIndex;

    public ContactListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    public CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    public boolean getHasPhoneNumber() {
        return getCursor().getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
    }

    public boolean isContactStarred() {
        return getCursor().getInt(SUMMARY_STARRED_COLUMN_INDEX) != 0;
    }

    @Override
    public String getContactDisplayName() {
        return getCursor().getString(mDisplayNameColumnIndex);
    }

    public void setQuickContactEnabled(boolean quickContactEnabled) {
        mQuickContactEnabled = quickContactEnabled;
    }

    @Override
    public void setContactNameDisplayOrder(int displayOrder) {
        super.setContactNameDisplayOrder(displayOrder);
        if (getContactNameDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mDisplayNameColumnIndex = SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        } else {
            mDisplayNameColumnIndex = SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        }
    }

    /**
     * Builds the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position.
     */
    public Uri getContactUri() {
        Cursor cursor = getCursor();
        long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
        String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
        return Contacts.getLookupUri(contactId, lookupKey);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        // TODO
//        view.setOnCallButtonClickListener(contactsListActivity);
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final ContactListItemView view = (ContactListItemView)itemView;

        if (isSectionHeaderDisplayEnabled()) {
            final int position = cursor.getPosition();
            final int section = getSectionForPosition(position);
            if (getPositionForSection(section) == position) {
                String title = (String)getSections()[section];
                view.setSectionHeader(title);
            } else {
                view.setDividerVisible(false);
                view.setSectionHeader(null);
            }

            // move the divider for the last item in a section
            if (getPositionForSection(section + 1) - 1 == position) {
                view.setDividerVisible(false);
            } else {
                view.setDividerVisible(true);
            }
        }

        view.showDisplayName(cursor, mDisplayNameColumnIndex, isNameHighlightingEnabled(),
                mAlternativeDisplayNameColumnIndex);
//
//        // Make the call button visible if requested.
//        if (mDisplayCallButton
//                && cursor.getColumnCount() > ContactsListActivity.SUMMARY_HAS_PHONE_COLUMN_INDEX
//                && cursor.getInt(ContactsListActivity.SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0) {
//            int pos = cursor.getPosition();
//            view.showCallButton(android.R.id.button1, pos);
//        } else {
//            view.hideCallButton();
//        }
//

        // Set the photo, if available
        long photoId = 0;
        if (!cursor.isNull(SUMMARY_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(SUMMARY_PHOTO_ID_COLUMN_INDEX);
        }

        ImageView viewToUse;
        if (mQuickContactEnabled) {
            QuickContactBadge quickContact = view.getQuickContact();
            quickContact.assignContactUri(getContactUri());
            viewToUse = quickContact;
        } else {
            viewToUse = view.getPhotoView();
        }

        getPhotoLoader().loadPhoto(viewToUse, photoId);

        view.showPresence(cursor, SUMMARY_PRESENCE_STATUS_COLUMN_INDEX);
        view.showPhoneticName(cursor, SUMMARY_PHONETIC_NAME_COLUMN_INDEX);

        if (isSearchMode() || isSearchResultsMode()) {
            view.showSnippet(cursor, SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX,
                    SUMMARY_SNIPPET_DATA1_COLUMN_INDEX, SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
        }
    }
}
