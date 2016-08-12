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
package com.android.contacts.common.util;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;

import com.android.contacts.common.model.dataitem.StructuredNameDataItem;

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

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
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

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    for (int i = 0; i < STRUCTURED_NAME_FIELDS.length; i++) {
                        structuredName.put(STRUCTURED_NAME_FIELDS[i], cursor.getString(i));
                    }
                }
            } finally {
                cursor.close();
            }
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

    /**
     * Parses phonetic name and returns parsed data (family, middle, given) as ContentValues.
     * Parsed data should be {@link StructuredName#PHONETIC_FAMILY_NAME},
     * {@link StructuredName#PHONETIC_MIDDLE_NAME}, and
     * {@link StructuredName#PHONETIC_GIVEN_NAME}.
     * If this method cannot parse given phoneticName, null values will be stored.
     *
     * @param phoneticName Phonetic name to be parsed
     * @param values ContentValues to be used for storing data. If null, new instance will be
     * created.
     * @return ContentValues with parsed data. Those data can be null.
     */
    public static StructuredNameDataItem parsePhoneticName(String phoneticName,
            StructuredNameDataItem item) {
        String family = null;
        String middle = null;
        String given = null;

        if (!TextUtils.isEmpty(phoneticName)) {
            String[] strings = phoneticName.split(" ", 3);
            switch (strings.length) {
                case 1:
                    family = strings[0];
                    break;
                case 2:
                    family = strings[0];
                    given = strings[1];
                    break;
                case 3:
                    family = strings[0];
                    middle = strings[1];
                    given = strings[2];
                    break;
            }
        }

        if (item == null) {
            item = new StructuredNameDataItem();
        }
        item.setPhoneticFamilyName(family);
        item.setPhoneticMiddleName(middle);
        item.setPhoneticGivenName(given);
        return item;
    }

    /**
     * Constructs and returns a phonetic full name from given parts.
     */
    public static String buildPhoneticName(String family, String middle, String given) {
        if (!TextUtils.isEmpty(family) || !TextUtils.isEmpty(middle)
                || !TextUtils.isEmpty(given)) {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(family)) {
                sb.append(family.trim()).append(' ');
            }
            if (!TextUtils.isEmpty(middle)) {
                sb.append(middle.trim()).append(' ');
            }
            if (!TextUtils.isEmpty(given)) {
                sb.append(given.trim()).append(' ');
            }
            sb.setLength(sb.length() - 1); // Yank the last space
            return sb.toString();
        } else {
            return null;
        }
    }
}
