/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.compat;

import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;

/**
 * This class contains Builder class extracted from ContactsContract, and it became visible in API
 * level 23. We need maintain this class and keep it synced with ContactsContract.
 */
public class AggregationSuggestionsCompat {

    /**
     * Used to specify what kind of data is supplied for the suggestion query.
     */
    public static final String PARAMETER_MATCH_NAME = "name";

    /**
     * A convenience builder for aggregation suggestion content URIs.
     */
    public static final class Builder {
        private long mContactId;
        private final ArrayList<String> mValues = new ArrayList<String>();
        private int mLimit;

        /**
         * Optional existing contact ID.  If it is not provided, the search
         * will be based exclusively on the values supplied with {@link #addNameParameter}.
         *
         * @param contactId contact to find aggregation suggestions for
         * @return This Builder object to allow for chaining of calls to builder methods
         */
        public Builder setContactId(long contactId) {
            this.mContactId = contactId;
            return this;
        }

        /**
         * Add a name to be used when searching for aggregation suggestions.
         *
         * @param name name to find aggregation suggestions for
         * @return This Builder object to allow for chaining of calls to builder methods
         */
        public Builder addNameParameter(String name) {
            mValues.add(name);
            return this;
        }

        /**
         * Sets the Maximum number of suggested aggregations that should be returned.
         * @param limit The maximum number of suggested aggregations
         *
         * @return This Builder object to allow for chaining of calls to builder methods
         */
        public Builder setLimit(int limit) {
            mLimit = limit;
            return this;
        }

        /**
         * Combine all of the options that have been set and return a new {@link Uri}
         * object for fetching aggregation suggestions.
         */
        public Uri build() {
            android.net.Uri.Builder builder = ContactsContract.Contacts.CONTENT_URI.buildUpon();
            builder.appendEncodedPath(String.valueOf(mContactId));
            builder.appendPath(ContactsContract.Contacts.AggregationSuggestions.CONTENT_DIRECTORY);
            if (mLimit != 0) {
                builder.appendQueryParameter("limit", String.valueOf(mLimit));
            }

            int count = mValues.size();
            for (int i = 0; i < count; i++) {
                builder.appendQueryParameter("query", PARAMETER_MATCH_NAME
                        + ":" + mValues.get(i));
            }

            return builder.build();
        }
    }
}
