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
package com.android.contacts.util;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;

import java.util.Map;
import java.util.TreeMap;

/**
 * Utility class for converting between a display name and structured name (and vice-versa), via
 * calls to the contact provider.
 */
public class NameConverter {

    /**
     * The array of fields that comprise a structured name.
     */
    public static final String[] STRUCTURED_NAME_FIELDS = new String[] {
            StructuredName.PREFIX,
            StructuredName.GIVEN_NAME,
            StructuredName.MIDDLE_NAME,
            StructuredName.FAMILY_NAME,
            StructuredName.SUFFIX
    };

    /**
     * Converts the given structured name (provided as a map from {@link StructuredName} fields to
     * corresponding values) into a display name string.
     * <p>
     * Note that this operates via a call back to the ContactProvider, but it does not access the
     * database, so it should be safe to call from the UI thread.  See
     * ContactsProvider2.completeName() for the underlying method call.
     * @param context Activity context.
     * @param structuredName The structured name map to convert.
     * @return The display name computed from the structured name map.
     */
    public static String structuredNameToDisplayName(Context context,
            Map<String, String> structuredName) {
        Builder builder = ContactsContract.AUTHORITY_URI.buildUpon().appendPath("complete_name");
        for (String key : STRUCTURED_NAME_FIELDS) {
            if (structuredName.containsKey(key)) {
                appendQueryParameter(builder, key, structuredName.get(key));
            }
        }
        return fetchDisplayName(context, builder.build());
    }

    /**
     * Converts the given structured name (provided as ContentValues) into a display name string.
     * @param context Activity context.
     * @param values The content values containing values comprising the structured name.
     * @return
     */
    public static String structuredNameToDisplayName(Context context, ContentValues values) {
        Builder builder = ContactsContract.AUTHORITY_URI.buildUpon().appendPath("complete_name");
        for (String key : STRUCTURED_NAME_FIELDS) {
            if (values.containsKey(key)) {
                appendQueryParameter(builder, key, values.getAsString(key));
            }
        }
        return fetchDisplayName(context, builder.build());
    }

    /**
     * Helper method for fetching the display name via the given URI.
     */
    private static String fetchDisplayName(Context context, Uri uri) {
        String displayName = null;
        Cursor cursor = context.getContentResolver().query(uri, new String[]{
                StructuredName.DISPLAY_NAME,
        }, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        return displayName;
    }

    /**
     * Converts the given display name string into a structured name (as a map from
     * {@link StructuredName} fields to corresponding values).
     * <p>
     * Note that this operates via a call back to the ContactProvider, but it does not access the
     * database, so it should be safe to call from the UI thread.
     * @param context Activity context.
     * @param displayName The display name to convert.
     * @return The structured name map computed from the display name.
     */
    public static Map<String, String> displayNameToStructuredName(Context context,
            String displayName) {
        Map<String, String> structuredName = new TreeMap<String, String>();
        Builder builder = ContactsContract.AUTHORITY_URI.buildUpon().appendPath("complete_name");

        appendQueryParameter(builder, StructuredName.DISPLAY_NAME, displayName);
        Cursor cursor = context.getContentResolver().query(builder.build(), STRUCTURED_NAME_FIELDS,
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                for (int i = 0; i < STRUCTURED_NAME_FIELDS.length; i++) {
                    structuredName.put(STRUCTURED_NAME_FIELDS[i], cursor.getString(i));
                }
            }
        } finally {
            cursor.close();
        }
        return structuredName;
    }

    /**
     * Converts the given display name string into a structured name (inserting the structured
     * values into a new or existing ContentValues object).
     * <p>
     * Note that this operates via a call back to the ContactProvider, but it does not access the
     * database, so it should be safe to call from the UI thread.
     * @param context Activity context.
     * @param displayName The display name to convert.
     * @param contentValues The content values object to place the structured name values into.  If
     *     null, a new one will be created and returned.
     * @return The ContentValues object containing the structured name fields derived from the
     *     display name.
     */
    public static ContentValues displayNameToStructuredName(Context context, String displayName,
            ContentValues contentValues) {
        if (contentValues == null) {
            contentValues = new ContentValues();
        }
        Map<String, String> mapValues = displayNameToStructuredName(context, displayName);
        for (String key : mapValues.keySet()) {
            contentValues.put(key, mapValues.get(key));
        }
        return contentValues;
    }

    private static void appendQueryParameter(Builder builder, String field, String value) {
        if (!TextUtils.isEmpty(value)) {
            builder.appendQueryParameter(field, value);
        }
    }
}
