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

import com.android.contacts.list.ContactTileView;

import android.content.Context;
import android.content.CursorLoader;
import android.provider.ContactsContract.Contacts;

/**
 * Used to create {@link CursorLoader}s to load different groups of {@link ContactTileView}s
 */
public final class ContactTileLoaderFactory {

    public final static int CONTACT_ID = 0;
    public final static int DISPLAY_NAME = 1;
    public final static int STARRED = 2;
    public final static int PHOTO_URI = 3;
    public final static int LOOKUP_KEY = 4;

    private static final String[] COLUMNS = new String[] {
        Contacts._ID,
        Contacts.DISPLAY_NAME,
        Contacts.STARRED,
        Contacts.PHOTO_URI,
        Contacts.LOOKUP_KEY
    };

    public static CursorLoader createStrequentLoader(Context context) {
        return new CursorLoader(context, Contacts.CONTENT_STREQUENT_URI, COLUMNS, null, null, null);
    }

    public static CursorLoader createStarredLoader(Context context) {
        return new CursorLoader(context, Contacts.CONTENT_URI, COLUMNS,
                Contacts.STARRED + "=?", new String[]{"1"}, Contacts.DISPLAY_NAME + " ASC");
    }

    public static CursorLoader createFrequentLoader(Context context) {
        return new CursorLoader(context, Contacts.CONTENT_STREQUENT_URI, COLUMNS,
                 Contacts.STARRED + "=?", new String[]{"0"}, null);
    }
}
