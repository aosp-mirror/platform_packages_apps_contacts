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

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.view.View;
import android.view.ViewGroup;

/**
 * A cursor adapter for the {@link StructuredPostal#CONTENT_TYPE} content type.
 */
public class PostalAddressListAdapter extends ContactEntryListAdapter {

    static final String[] POSTALS_PROJECTION = new String[] {
        StructuredPostal._ID,                       // 0
        StructuredPostal.TYPE,                      // 1
        StructuredPostal.LABEL,                     // 2
        StructuredPostal.DATA,                      // 3
        StructuredPostal.DISPLAY_NAME_PRIMARY,      // 4
        StructuredPostal.DISPLAY_NAME_ALTERNATIVE,  // 5
        StructuredPostal.PHOTO_ID,                  // 6
    };

    protected static final int POSTAL_ID_COLUMN_INDEX = 0;
    protected static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    protected static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    protected static final int POSTAL_ADDRESS_COLUMN_INDEX = 3;
    protected static final int POSTAL_PRIMARY_DISPLAY_NAME_COLUMN_INDEX = 4;
    protected static final int POSTAL_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX = 5;
    protected static final int POSTAL_PHOTO_ID_COLUMN_INDEX = 6;

    private CharSequence mUnknownNameText;
    private int mDisplayNameColumnIndex;
    private int mAlternativeDisplayNameColumnIndex;

    public PostalAddressListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        Uri uri = buildSectionIndexerUri(StructuredPostal.CONTENT_URI);
        loader.setUri(uri);
        loader.setProjection(POSTALS_PROJECTION);

        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(StructuredPostal.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(StructuredPostal.SORT_KEY_ALTERNATIVE);
        }
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor)getItem(position)).getString(mDisplayNameColumnIndex);
    }

    @Override
    public void setContactNameDisplayOrder(int displayOrder) {
        super.setContactNameDisplayOrder(displayOrder);
        if (getContactNameDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mDisplayNameColumnIndex = POSTAL_PRIMARY_DISPLAY_NAME_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = POSTAL_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX;
        } else {
            mDisplayNameColumnIndex = POSTAL_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = POSTAL_PRIMARY_DISPLAY_NAME_COLUMN_INDEX;
        }
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the current cursor
     * position.
     */
    public Uri getDataUri(int position) {
        long id = ((Cursor)getItem(position)).getLong(POSTAL_ID_COLUMN_INDEX);
        return ContentUris.withAppendedId(Data.CONTENT_URI, id);
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;
        bindSectionHeaderAndDivider(view, position);
        bindName(view, cursor);
        bindPhoto(view, cursor);
        bindPostalAddress(view, cursor);
    }

    protected void bindPostalAddress(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(POSTAL_TYPE_COLUMN_INDEX)) {
            final int type = cursor.getInt(POSTAL_TYPE_COLUMN_INDEX);
            final String customLabel = cursor.getString(POSTAL_LABEL_COLUMN_INDEX);

            // TODO cache
            label = StructuredPostal.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, POSTAL_ADDRESS_COLUMN_INDEX);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
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

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, mDisplayNameColumnIndex, mAlternativeDisplayNameColumnIndex,
                false, getContactNameDisplayOrder());
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(POSTAL_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(POSTAL_PHOTO_ID_COLUMN_INDEX);
        }

        getPhotoLoader().loadPhoto(view.getPhotoView(), photoId, false, false);
    }
//
//    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
//        view.showSnippet(cursor, SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX,
//                SUMMARY_SNIPPET_DATA1_COLUMN_INDEX, SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
//    }

}
