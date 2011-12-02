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

package com.android.contacts.tests;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * A service that executes a query specified by an intent and dump the result on logcat.  Use the
 * "am" command to launch it.
 *
   Usage:
     adb shell am startservice -d URI \
       [-e p OPTIONAL PROJECTION] [-e s OPTIONAL SELECTION] [-e s OPTIONAL ORDER BY]  \
       com.android.contacts.tests/.QueryService

   Example:

   adb shell am startservice -d content://com.android.contacts/directories \
     -e p accountName,accountType -e s 'accountName NOT NULL' -e o '_id'  \
     com.android.contacts.tests/.QueryService
 */
public class QueryService extends IntentService {
    private static final String TAG = "contactsquery";

    private static final String EXTRA_PROJECTION = "p";
    private static final String EXTRA_SELECTION = "s";
    private static final String EXTRA_ORDER = "o";
    private static final String NULL_STRING = "*null*";
    private static final String SEPARATOR = "|";

    public QueryService() {
        super("ContactsQueryService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final Uri uri = intent.getData();
        // Unfortunately "am" doesn't support string arrays...
        final String projection = intent.getStringExtra(EXTRA_PROJECTION);
        final String selection = intent.getStringExtra(EXTRA_SELECTION);
        final String order = intent.getStringExtra(EXTRA_ORDER);

        Log.i(TAG, "URI: " + uri);
        Log.i(TAG, "Projection: " + projection);
        Log.i(TAG, "Selection: " + selection);

        try {
            Cursor c = getContentResolver().query(uri, parseProjection(projection), selection, null,
                    order);
            if (c == null) {
                Log.i(TAG, "(no results)");
                return;
            }
            StringBuilder sb = new StringBuilder();
            try {
                Log.i(TAG, "Result count: " + c.getCount());

                final int columnCount = c.getColumnCount();

                sb.setLength(0);
                for (int i = 0; i < columnCount; i++) {
                    add(sb, c.getColumnName(i));
                }
                Log.i(TAG, sb.toString());

                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    sb.setLength(0);
                    for (int i = 0; i < columnCount; i++) {
                        add(sb, c.getString(i));
                    }
                    Log.i(TAG, sb.toString());
                }
            } finally {
                c.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exeption while executing query", e);
        }
    }

    private StringBuilder add(StringBuilder sb, String s) {
        if (sb.length() > 0) {
            sb.append(SEPARATOR);
        }
        sb.append(s == null ? NULL_STRING : s);
        return sb;
    }

    private static String[] parseProjection(String projectionString) {
        if (TextUtils.isEmpty(projectionString)) {
            return null; // all columns
        }
        final String[] columns = projectionString.split(",");
        if (columns.length == 0) {
            return null; // all columns
        }
        return columns;
    }
}
