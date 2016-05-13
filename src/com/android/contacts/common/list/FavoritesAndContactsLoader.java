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
package com.android.contacts.common.list;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * A loader for use in the default contact list, which will also query for favorite contacts
 * if configured to do so.
 */
public class FavoritesAndContactsLoader extends CursorLoader {

    private boolean mLoadFavorites;

    private String[] mProjection;

    private Uri mExtraUri;
    private String[] mExtraProjection;
    private String mExtraSelection;
    private String[] mExtraSelectionArgs;
    private boolean mMergeExtraContactsAfterPrimary;

    public FavoritesAndContactsLoader(Context context) {
        super(context);
    }

    /** Whether to load favorites and merge results in before any other results. */
    public void setLoadFavorites(boolean flag) {
        mLoadFavorites = flag;
    }

    public void setProjection(String[] projection) {
        super.setProjection(projection);
        mProjection = projection;
    }

    /** Configure an extra query and merge results in before the primary results. */
    public void setLoadExtraContactsFirst(Uri uri, String[] projection) {
        mExtraUri = uri;
        mExtraProjection = projection;
        mMergeExtraContactsAfterPrimary = false;
    }

    /** Configure an extra query and merge results in after the primary results. */
    public void setLoadExtraContactsLast(Uri uri, String[] projection, String selection,
            String[] selectionArgs) {
        mExtraUri = uri;
        mExtraProjection = projection;
        mExtraSelection = selection;
        mExtraSelectionArgs = selectionArgs;
        mMergeExtraContactsAfterPrimary = true;
    }

    private boolean canLoadExtraContacts() {
        return mExtraUri != null && mExtraProjection != null;
    }

    @Override
    public Cursor loadInBackground() {
        List<Cursor> cursors = Lists.newArrayList();
        if (mLoadFavorites) {
            cursors.add(loadFavoritesContacts());
        }
        if (canLoadExtraContacts() && !mMergeExtraContactsAfterPrimary) {
            cursors.add(loadExtraContacts());
        }
        // ContactsCursor.loadInBackground() can return null; MergeCursor
        // correctly handles null cursors.
        Cursor cursor = null;
        try {
            cursor = super.loadInBackground();
        } catch (NullPointerException | SecurityException e) {
            // Ignore NPEs and SecurityExceptions thrown by providers
        }
        final Cursor contactsCursor = cursor;
        cursors.add(contactsCursor);
        if (canLoadExtraContacts() && mMergeExtraContactsAfterPrimary) {
            cursors.add(loadExtraContacts());
        }
        return new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) {
            @Override
            public Bundle getExtras() {
                // Need to get the extras from the contacts cursor.
                return contactsCursor == null ? new Bundle() : contactsCursor.getExtras();
            }
        };
    }

    private Cursor loadExtraContacts() {
        return getContext().getContentResolver().query(
                mExtraUri, mExtraProjection, mExtraSelection, mExtraSelectionArgs, null);
    }

    private Cursor loadFavoritesContacts() {
        return getContext().getContentResolver().query(
                Contacts.CONTENT_URI, mProjection, Contacts.STARRED + "=?", new String[]{"1"},
                Contacts.DISPLAY_NAME+" COLLATE NOCASE ASC");
    }
}
