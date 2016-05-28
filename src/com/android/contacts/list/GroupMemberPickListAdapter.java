/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapter for raw contacts owned by an account that are not already members of a given group.
 */
public class GroupMemberPickListAdapter extends ContactEntryListAdapter {

    static class GroupMembersQuery {

        private static final String[] PROJECTION_PRIMARY = new String[] {
                RawContacts._ID,                        // 0
                RawContacts.CONTACT_ID,                 // 1
                RawContacts.DISPLAY_NAME_PRIMARY,       // 2
                // Dummy columns overwritten by the cursor wrapper
                RawContacts.SYNC1,                      // 3
                RawContacts.SYNC2                       // 4
        };

        private static final String[] PROJECTION_ALTERNATIVE = new String[] {
                RawContacts._ID,                        // 0
                RawContacts.CONTACT_ID,                 // 1
                RawContacts.DISPLAY_NAME_ALTERNATIVE,   // 2
                // Dummy columns overwritten by the cursor wrapper
                RawContacts.SYNC1,                      // 3
                RawContacts.SYNC2                       // 4
        };

        static final int RAW_CONTACT_ID = 0;
        static final int CONTACT_ID = 1;
        static final int CONTACT_DISPLAY_NAME = 2;
        // Provided by the cursor wrapper.
        static final int CONTACT_PHOTO_ID = 3;
        // Provided by the cursor wrapper.
        static final int CONTACT_LOOKUP_KEY = 4;

        private GroupMembersQuery() {
        }
    }

    private AccountWithDataSet mAccount;
    private final Set<String> mRawContactIds = new HashSet<>();

    private final CharSequence mUnknownNameText;

    public GroupMemberPickListAdapter(Context context) {
        super(context);
        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    public GroupMemberPickListAdapter setAccount(AccountWithDataSet account) {
        mAccount = account;
        return this;
    }

    public GroupMemberPickListAdapter setRawContactIds(ArrayList<String> rawContactIds) {
        mRawContactIds.clear();
        mRawContactIds.addAll(rawContactIds);
        return this;
    }

    @Override
    public String getContactDisplayName(int position) {
        final Cursor cursor = (Cursor) getItem(position);
        return cursor.getString(GroupMembersQuery.CONTACT_DISPLAY_NAME);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        loader.setUri(RawContacts.CONTENT_URI);
        loader.setProjection(
                getContactNameDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY
                        ? GroupMembersQuery.PROJECTION_PRIMARY
                        : GroupMembersQuery.PROJECTION_ALTERNATIVE);
        loader.setSelection(getSelection());
        loader.setSelectionArgs(getSelectionArgs());
        loader.setSortOrder(getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY
                ? Data.SORT_KEY_PRIMARY : Data.SORT_KEY_ALTERNATIVE
                + " COLLATE LOCALIZED ASC");
    }

    private String getSelection() {
        // Select raw contacts by account
        String result = RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=? AND ";
        if (TextUtils.isEmpty(mAccount.dataSet)) {
            result += Data.DATA_SET + " IS NULL";
        } else {
            result += Data.DATA_SET + "=?";
        }
        return result;
    }

    private String[] getSelectionArgs() {
        final ArrayList<String> result = new ArrayList<>();
        result.add(mAccount.name);
        result.add(mAccount.type);
        if (!TextUtils.isEmpty(mAccount.dataSet)) result.add(mAccount.dataSet);
        return result.toArray(new String[0]);
    }

    public Uri getRawContactUri(int position) {
        final Cursor cursor = (Cursor) getItem(position);
        final long rawContactId = cursor.getLong(GroupMembersQuery.RAW_CONTACT_ID);
        return RawContacts.CONTENT_URI.buildUpon()
                .appendPath(Long.toString(rawContactId))
                .build();
    }

    @Override
    protected ContactListItemView newView(Context context, int partition, Cursor cursor,
            int position, ViewGroup parent) {
        final ContactListItemView view =
                super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        return view;
    }

    @Override
    protected void bindView(View v, int partition, Cursor cursor, int position) {
        super.bindView(v, partition, cursor, position);
        final ContactListItemView view = (ContactListItemView) v;
        bindName(view, cursor);
        bindViewId(view, cursor, GroupMembersQuery.RAW_CONTACT_ID);
        bindPhoto(view, cursor);
    }

    private void bindName(ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, GroupMembersQuery.CONTACT_DISPLAY_NAME,
                getContactNameDisplayOrder());
    }

    private void bindPhoto(final ContactListItemView view, Cursor cursor) {
        final long photoId = cursor.isNull(GroupMembersQuery.CONTACT_PHOTO_ID)
                ? 0 : cursor.getLong(GroupMembersQuery.CONTACT_PHOTO_ID);
        final DefaultImageRequest imageRequest = photoId == 0
                ? getDefaultImageRequestFromCursor(cursor, GroupMembersQuery.CONTACT_DISPLAY_NAME,
                GroupMembersQuery.CONTACT_LOOKUP_KEY)
                : null;
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, getCircularPhotos(),
                imageRequest);
    }
}
