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

import com.android.contacts.preference.ContactsPreferences;

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

    public static class GroupEditorQuery {
        private static final String[] PROJECTION = new String[] {
            Data.CONTACT_ID,                        // 0
            Data.RAW_CONTACT_ID,                    // 1
            Data.DISPLAY_NAME_PRIMARY,              // 2
            Data.PHOTO_URI,                         // 3
            Data.LOOKUP_KEY,                        // 4
        };

        public static final int CONTACT_ID                   = 0;
        public static final int RAW_CONTACT_ID               = 1;
        public static final int CONTACT_DISPLAY_NAME_PRIMARY = 2;
        public static final int CONTACT_PHOTO_URI            = 3;
        public static final int CONTACT_LOOKUP_KEY           = 4;
    }

    public static class GroupDetailQuery {
        private static final String[] PROJECTION = new String[] {
            Data.CONTACT_ID,                        // 0
            Data.PHOTO_URI,                         // 1
            Data.LOOKUP_KEY,                        // 2
            Data.DISPLAY_NAME_PRIMARY,              // 3
            Data.CONTACT_PRESENCE,                  // 4
            Data.CONTACT_STATUS,                    // 5
        };

        public static final int CONTACT_ID                   = 0;
        public static final int CONTACT_PHOTO_URI            = 1;
        public static final int CONTACT_LOOKUP_KEY           = 2;
        public static final int CONTACT_DISPLAY_NAME_PRIMARY = 3;
        public static final int CONTACT_PRESENCE_STATUS      = 4;
        public static final int CONTACT_STATUS               = 5;
    }

    private final long mGroupId;

    /**
     * @return GroupMemberLoader object which can be used in group editor.
     */
    public static GroupMemberLoader constructLoaderForGroupEditorQuery(
            Context context, long groupId) {
        return new GroupMemberLoader(context, groupId, GroupEditorQuery.PROJECTION);
    }

    /**
     * @return GroupMemberLoader object used in group detail page.
     */
    public static GroupMemberLoader constructLoaderForGroupDetailQuery(
            Context context, long groupId) {
        return new GroupMemberLoader(context, groupId, GroupDetailQuery.PROJECTION);
    }

    private GroupMemberLoader(Context context, long groupId, String[] projection) {
        super(context);
        mGroupId = groupId;
        setUri(createUri());
        setProjection(projection);
        setSelection(createSelection());
        setSelectionArgs(createSelectionArgs());

        ContactsPreferences prefs = new ContactsPreferences(context);
        if (prefs.getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            setSortOrder(Contacts.SORT_KEY_PRIMARY);
        } else {
            setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
        }
    }

    private Uri createUri() {
        Uri uri = Data.CONTENT_URI;
        uri = uri.buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                String.valueOf(Directory.DEFAULT)).build();
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
