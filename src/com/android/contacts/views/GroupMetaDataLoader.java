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
 * limitations under the License
 */
package com.android.contacts.views;

import android.content.Context;
import android.content.CursorLoader;
import android.provider.ContactsContract.Groups;

/**
 * Group meta-data loader.  Loads all groups from the database.
 */
public final class GroupMetaDataLoader extends CursorLoader {

    private final static String[] COLUMNS = new String[] {
        Groups.ACCOUNT_NAME,
        Groups.ACCOUNT_TYPE,
        Groups._ID,
        Groups.TITLE,
        Groups.AUTO_ADD,
        Groups.FAVORITES,
    };

    public final static int ACCOUNT_NAME = 0;
    public final static int ACCOUNT_TYPE = 1;
    public final static int GROUP_ID = 2;
    public final static int TITLE = 3;
    public final static int AUTO_ADD = 4;
    public final static int FAVORITES = 5;

    public GroupMetaDataLoader(Context context) {
        super(context, Groups.CONTENT_URI, COLUMNS, Groups.ACCOUNT_TYPE + " NOT NULL AND "
                + Groups.ACCOUNT_NAME + " NOT NULL", null, null);
    }
}
