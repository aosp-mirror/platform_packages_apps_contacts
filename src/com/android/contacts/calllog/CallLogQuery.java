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

package com.android.contacts.calllog;

import android.database.Cursor;
import android.provider.CallLog.Calls;

/**
 * The query for the call log table.
 */
public final class CallLogQuery {
    // If you alter this, you must also alter the method that inserts a fake row to the headers
    // in the CallLogQueryHandler class called createHeaderCursorFor().
    public static final String[] _PROJECTION = new String[] {
            Calls._ID,                       // 0
            Calls.NUMBER,                    // 1
            Calls.DATE,                      // 2
            Calls.DURATION,                  // 3
            Calls.TYPE,                      // 4
            Calls.COUNTRY_ISO,               // 5
            Calls.VOICEMAIL_URI,             // 6
            Calls.GEOCODED_LOCATION,         // 7
            Calls.CACHED_NAME,               // 8
            Calls.CACHED_NUMBER_TYPE,        // 9
            Calls.CACHED_NUMBER_LABEL,       // 10
            Calls.CACHED_LOOKUP_URI,         // 11
            Calls.CACHED_MATCHED_NUMBER,     // 12
            Calls.CACHED_NORMALIZED_NUMBER,  // 13
            Calls.CACHED_PHOTO_ID,           // 14
            Calls.CACHED_FORMATTED_NUMBER,   // 15
            Calls.IS_READ,                   // 16
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int DURATION = 3;
    public static final int CALL_TYPE = 4;
    public static final int COUNTRY_ISO = 5;
    public static final int VOICEMAIL_URI = 6;
    public static final int GEOCODED_LOCATION = 7;
    public static final int CACHED_NAME = 8;
    public static final int CACHED_NUMBER_TYPE = 9;
    public static final int CACHED_NUMBER_LABEL = 10;
    public static final int CACHED_LOOKUP_URI = 11;
    public static final int CACHED_MATCHED_NUMBER = 12;
    public static final int CACHED_NORMALIZED_NUMBER = 13;
    public static final int CACHED_PHOTO_ID = 14;
    public static final int CACHED_FORMATTED_NUMBER = 15;
    public static final int IS_READ = 16;
    /** The index of the synthetic "section" column in the extended projection. */
    public static final int SECTION = 17;

    /**
     * The name of the synthetic "section" column.
     * <p>
     * This column identifies whether a row is a header or an actual item, and whether it is
     * part of the new or old calls.
     */
    public static final String SECTION_NAME = "section";
    /** The value of the "section" column for the header of the new section. */
    public static final int SECTION_NEW_HEADER = 0;
    /** The value of the "section" column for the items of the new section. */
    public static final int SECTION_NEW_ITEM = 1;
    /** The value of the "section" column for the header of the old section. */
    public static final int SECTION_OLD_HEADER = 2;
    /** The value of the "section" column for the items of the old section. */
    public static final int SECTION_OLD_ITEM = 3;

    /** The call log projection including the section name. */
    public static final String[] EXTENDED_PROJECTION;
    static {
        EXTENDED_PROJECTION = new String[_PROJECTION.length + 1];
        System.arraycopy(_PROJECTION, 0, EXTENDED_PROJECTION, 0, _PROJECTION.length);
        EXTENDED_PROJECTION[_PROJECTION.length] = SECTION_NAME;
    }

    public static boolean isSectionHeader(Cursor cursor) {
        int section = cursor.getInt(CallLogQuery.SECTION);
        return section == CallLogQuery.SECTION_NEW_HEADER
                || section == CallLogQuery.SECTION_OLD_HEADER;
    }

    public static boolean isNewSection(Cursor cursor) {
        int section = cursor.getInt(CallLogQuery.SECTION);
        return section == CallLogQuery.SECTION_NEW_ITEM
                || section == CallLogQuery.SECTION_NEW_HEADER;
    }
}