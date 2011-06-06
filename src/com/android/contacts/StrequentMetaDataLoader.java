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

import android.content.Context;
import android.content.CursorLoader;
import android.provider.ContactsContract.Contacts;

/**
 * Strequent meta-data loader.  Loads all starred and frequent contacts from the database.
 */
public final class StrequentMetaDataLoader extends CursorLoader {

    public final static int CONTACT_ID = 0;
    public final static int DISPLAY_NAME = 1;
    public final static int STARRED = 2;
    public final static int PHOTO_URI = 3;
    public final static int PHOTO_ID = 4;

    private static final String[] COLUMNS = new String[] {
        Contacts._ID,
        Contacts.DISPLAY_NAME,
        Contacts.STARRED,
        Contacts.PHOTO_URI,
        Contacts.PHOTO_ID
    };

    public StrequentMetaDataLoader(Context context) {
        super(context, Contacts.CONTENT_STREQUENT_URI, COLUMNS, null, null, null);
    }
}
