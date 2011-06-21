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
 * limitations under the License
 */
package com.android.contacts;

import com.android.contacts.list.ContactListAdapter;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;

import java.util.ArrayList;
import java.util.List;

/**
 * Group Member loader. Loads all group members from the given groupId
 */
public final class GroupMemberLoader extends CursorLoader {

    /**
     * Projection map is taken from {@link ContactListAdapter}
     */
    private final String[] PROJECTION_DATA = new String[] {
        // TODO: Pull Projection_data out into util class
        Data.CONTACT_ID,                        // 0
        Data.DISPLAY_NAME_PRIMARY,              // 1
        Data.DISPLAY_NAME_ALTERNATIVE,          // 2
        Data.SORT_KEY_PRIMARY,                  // 3
        Data.STARRED,                           // 4
        Data.CONTACT_PRESENCE,                  // 5
        Data.CONTACT_CHAT_CAPABILITY,           // 6
        Data.PHOTO_ID,                          // 7
        Data.PHOTO_THUMBNAIL_URI,               // 8
        Data.LOOKUP_KEY,                        // 9
        Data.PHONETIC_NAME,                     // 10
        Data.HAS_PHONE_NUMBER,                  // 11
    };

    private final long mGroupId;

    public static final int CONTACT_ID_COLUMN_INDEX = 0;
    public static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    public static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    public static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    public static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    public static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    public static final int CONTACT_CHAT_CAPABILITY_COLUMN_INDEX = 6;
    public static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 7;
    public static final int CONTACT_PHOTO_URI_COLUMN_INDEX = 8;
    public static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 9;
    public static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 10;
    public static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 11;

    public GroupMemberLoader(Context context, long groupId) {
        super(context);
        mGroupId = groupId;
        setUri(createUri());
        setProjection(PROJECTION_DATA);
        setSelection(createSelection());
        setSelectionArgs(createSelectionArgs());
        setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
    }

    private Uri createUri() {
        Uri uri = Data.CONTENT_URI;
        uri = uri.buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                String.valueOf(Directory.DEFAULT)).build();
        // TODO: Bring back dataRestriction
        // uri = applyDataRestriction(uri);
        return uri;
    }

    private String createSelection() {
        StringBuilder selection = new StringBuilder();
        selection.append(Data.MIMETYPE + "=?" + " AND " + GroupMembership.GROUP_ROW_ID + "=?");
        return selection.toString();
    }

    private String[] createSelectionArgs() {
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        selectionArgs.add(String.valueOf(mGroupId));
        return selectionArgs.toArray(new String[0]);
    }
}
