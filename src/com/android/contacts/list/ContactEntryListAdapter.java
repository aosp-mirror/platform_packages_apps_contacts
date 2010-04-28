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

import com.android.contacts.ContactPhotoLoader;
import com.android.contacts.widget.TextWithHighlighting;
import com.android.contacts.widget.TextWithHighlightingFactory;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.widget.ListView;

/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ContactEntryListAdapter extends PinnedHeaderListAdapter {

    // TODO move to a type-specific adapter
    public static final int SUMMARY_ID_COLUMN_INDEX = 0;
    public static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    public static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    public static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    public static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    public static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;

    /**
     * The animation is used here to allocate animated name text views.
     */
    private TextWithHighlightingFactory mTextWithHighlightingFactory;

    private int mDisplayOrder;
    private boolean mNameHighlightingEnabled;
    private ContactPhotoLoader mPhotoLoader;

    // TODO move to Loader
    protected String mQueryString;

    public ContactEntryListAdapter(Context context) {
        super(context);
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
    }

    public Context getContext() {
        return mContext;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setNameHighlightingEnabled(boolean flag) {
        mNameHighlightingEnabled = flag;
    }

    public boolean isNameHighlightingEnabled() {
        return mNameHighlightingEnabled;
    }

    public void setTextWithHighlightingFactory(TextWithHighlightingFactory factory) {
        mTextWithHighlightingFactory = factory;
    }

    protected TextWithHighlighting createTextWithHighlighting() {
        return mTextWithHighlightingFactory.createTextWithHighlighting();
    }

    public void setPhotoLoader(ContactPhotoLoader photoLoader) {
        mPhotoLoader = photoLoader;
    }

    protected ContactPhotoLoader getPhotoLoader() {
        return mPhotoLoader;
    }

    /*
     * TODO change this method when loaders are introduced.
     */
    @Override
    @Deprecated
    public void onContentChanged() {
        super.onContentChanged();
    }

    public void moveToPosition(int position) {
        // For side-effect
        getItem(position);
        DatabaseUtils.dumpCurrentRow(getCursor());
    }

    public boolean getHasPhoneNumber() {
        return getCursor().getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
    }

    public boolean isContactStarred() {
        return getCursor().getInt(SUMMARY_STARRED_COLUMN_INDEX) != 0;
    }

    public String getContactDisplayName() {
        return getCursor().getString(getSummaryDisplayNameColumnIndex());
    }

    public int getSummaryDisplayNameColumnIndex() {
        if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        } else {
            return SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
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
}
