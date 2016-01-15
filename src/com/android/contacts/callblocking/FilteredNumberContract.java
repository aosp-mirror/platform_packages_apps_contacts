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

package com.android.contacts.callblocking;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * <p>
 * The contract between the filtered number provider and applications. Contains
 * definitions for the supported URIs and columns.
 * </p>
 */
public final class FilteredNumberContract {

    /** The authority for the filtered numbers provider
     *  Contacts should use this authority from GoogleDialer. */
    public static final String AUTHORITY =
            "com.google.android.dialer.provider.filterednumberprovider";

    /** A content:// style uri to the authority for the filtered numbers provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /** The type of filtering to be applied, e.g. block the number or whitelist the number. */
    public interface FilteredNumberTypes {
        int UNDEFINED = 0;
        /**
         * Dialer will disconnect the call without sending the caller to voicemail.
         */
        int BLOCKED_NUMBER = 1;
    }

    /** The original source of the filtered number, e.g. the user manually added it. */
    public interface FilteredNumberSources {
        int UNDEFINED = 0;
        /**
         * The user manually added this number through Dialer (e.g. from the call log or InCallUI).
         */
        int USER = 1;
    }

    public interface FilteredNumberColumns {
        // TYPE: INTEGER
        String _ID = "_id";
        /**
         * Represents the number to be filtered, normalized to compare phone numbers for equality.
         *
         * TYPE: TEXT
         */
        String NORMALIZED_NUMBER = "normalized_number";
        /**
         * Represents the number to be filtered, for formatting and
         * used with country iso for contact lookups.
         *
         * TYPE: TEXT
         */
        String NUMBER = "number";
        /**
         * The country code representing the country detected when
         * the phone number was added to the database.
         * Most numbers don't have the country code, so a best guess is provided by
         * the country detector system. The country iso is also needed in order to format
         * phone numbers correctly.
         *
         * TYPE: TEXT
         */
        String COUNTRY_ISO = "country_iso";
        /**
         * The number of times the number has been filtered by Dialer.
         * When this number is incremented, LAST_TIME_FILTERED should also be updated to
         * the current time.
         *
         * TYPE: INTEGER
         */
        String TIMES_FILTERED = "times_filtered";
        /**
         * Set to the current time when the phone number is filtered.
         * When this is updated, TIMES_FILTERED should also be incremented.
         *
         * TYPE: LONG
         */
        String LAST_TIME_FILTERED = "last_time_filtered";
        // TYPE: LONG
        String CREATION_TIME = "creation_time";
        /**
         * Indicates the type of filtering to be applied.
         *
         * TYPE: INTEGER
         * See {@link FilteredNumberTypes}
         */
        String TYPE = "type";
        /**
         * Integer representing the original source of the filtered number.
         *
         * TYPE: INTEGER
         * See {@link FilteredNumberSources}
         */
        String SOURCE = "source";
    }

    /**
     * <p>
     * Constants for the table of filtered numbers.
     * </p>
     * <h3>Operations</h3>
     * <dl>
     * <dt><b>Insert</b></dt>
     * <dd>Required fields: NUMBER, NORMALIZED_NUMBER, TYPE, SOURCE.
     * A default value will be used for the other fields if left null.</dd>
     * <dt><b>Update</b></dt>
     * <dt><b>Delete</b></dt>
     * <dt><b>Query</b></dt>
     * <dd>{@link #CONTENT_URI} can be used for any query, append an ID to
     * retrieve a specific filtered number entry.</dd>
     * </dl>
     */
    public static class FilteredNumber implements BaseColumns {

        public static final String FILTERED_NUMBERS_TABLE = "filtered_numbers_table";
        public static final String FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT =
                "filtered_numbers_increment_filtered_count";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI,
                FILTERED_NUMBERS_TABLE);

        public static final Uri CONTENT_URI_INCREMENT_FILTERED_COUNT = Uri.withAppendedPath(
                AUTHORITY_URI,
                FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT);

        /**
         * This utility class cannot be instantiated.
         */
        private FilteredNumber () {}

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of
         * filtered numbers.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/filtered_numbers_table";

        /**
         * The MIME type of a {@link #CONTENT_URI} single filtered number.
         */
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/filtered_numbers_table";
    }
}