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

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.net.Uri;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {
    private static final int NO_TOKEN = 0;

    public FilteredNumberAsyncQueryHandler(ContentResolver cr) {
        super(cr);
    }

    /**
     * Methods for FilteredNumberAsyncQueryHandler result returns.
     */
    private static abstract class Listener {
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        }
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
        }
        protected void onUpdateComplete(int token, Object cookie, int result) {
        }
        protected void onDeleteComplete(int token, Object cookie, int result) {
        }
    }

    public interface OnCheckBlockedListener {
        /**
         * Invoked after querying if a number is blocked.
         * @param id The ID of the row if blocked, null otherwise.
         */
        void onCheckComplete(Long id);
    }

    public interface OnBlockNumberListener {
        /**
         * Invoked after inserting a blocked number.
         * @param uri The uri of the newly created row.
         */
        void onBlockComplete(Uri uri);
    }

    public interface OnUnblockNumberListener {
        /**
         * Invoked after removing a blocked number
         * @param rows The number of rows affected (expected value 1).
         * @param values The deleted data (used for restoration).
         */
        void onUnblockComplete(int rows, ContentValues values);
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cookie != null) {
            ((Listener) cookie).onQueryComplete(token, cookie, cursor);
        }
    }

    @Override
    protected void onInsertComplete(int token, Object cookie, Uri uri) {
        if (cookie != null) {
            ((Listener) cookie).onInsertComplete(token, cookie, uri);
        }
    }

    @Override
    protected void onUpdateComplete(int token, Object cookie, int result) {
        if (cookie != null) {
            ((Listener) cookie).onUpdateComplete(token, cookie, result);
        }
    }

    @Override
    protected void onDeleteComplete(int token, Object cookie, int result) {
        if (cookie != null) {
            ((Listener) cookie).onDeleteComplete(token, cookie, result);
        }
    }

    /**
     * Check if this number has been blocked.
     *
     * @return {@code false} if the number was invalid and couldn't be checked,
     *     {@code true} otherwise,
     */
    public final boolean isBlockedNumber(
            final OnCheckBlockedListener listener, String number, String countryIso) {
        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        if (TextUtils.isEmpty(normalizedNumber)) {
            return false;
        }

        startQuery(NO_TOKEN,
                new Listener() {
                    @Override
                    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                        if (cursor == null || cursor.getCount() != 1) {
                            listener.onCheckComplete(null);
                            return;
                        }
                        cursor.moveToFirst();
                        listener.onCheckComplete(cursor.getLong(
                                        cursor.getColumnIndex(BlockedNumbers.COLUMN_ID)));
                    }
                },
                BlockedNumbers.CONTENT_URI,
                new String[]{ BlockedNumbers.COLUMN_ID},
                BlockedNumbers.COLUMN_E164_NUMBER + " = ?",
                new String[]{ normalizedNumber },
                null);

        return true;
    }

    public final void blockNumber(
            final OnBlockNumberListener listener, String number, String countryIso) {
        blockNumber(listener, null, number, countryIso);
    }

    /**
     * Add a number manually blocked by the user.
     */
    public final void blockNumber(
            final OnBlockNumberListener listener,
            String normalizedNumber,
            String number,
            String countryIso) {
        if (normalizedNumber == null) {
            normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        }
        ContentValues v = new ContentValues();
        v.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
        v.put(BlockedNumbers.COLUMN_E164_NUMBER, normalizedNumber);
        blockNumber(listener, v);
    }

    /**
     * Block a number with specified ContentValues. Can be manually added or a restored row
     * from performing the 'undo' action after unblocking.
     */
    public final void blockNumber(final OnBlockNumberListener listener, ContentValues values) {
        startInsert(NO_TOKEN,
                new Listener() {
                    @Override
                    public void onInsertComplete(int token, Object cookie, Uri uri) {
                        if (listener != null ) {
                            listener.onBlockComplete(uri);
                        }
                    }
                }, BlockedNumbers.CONTENT_URI, values);
    }

    /**
     * Removes row from database.
     * Caller should call {@link FilteredNumberAsyncQueryHandler#startBlockedQuery} first.
     * @param id The ID of row to remove, from {@link FilteredNumberAsyncQueryHandler#startBlockedQuery}.
     */
    public final void unblock(final OnUnblockNumberListener listener, Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Null id passed into unblock");
        }
        unblock(listener, ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, id));
    }

    /**
     * Removes row from database.
     * @param uri The uri of row to remove, from
     *         {@link FilteredNumberAsyncQueryHandler#blockNumber}.
     */
    public final void unblock(final OnUnblockNumberListener listener, final Uri uri) {
        startQuery(NO_TOKEN, new Listener() {
            @Override
            public void onQueryComplete(int token, Object cookie, Cursor cursor) {
                int rowsReturned = cursor == null ? 0 : cursor.getCount();
                if (rowsReturned != 1) {
                    throw new SQLiteDatabaseCorruptException
                            ("Returned " + rowsReturned + " rows for uri "
                                    + uri + "where 1 expected.");
                }
                cursor.moveToFirst();
                final ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                values.remove(BlockedNumbers.COLUMN_ID);

                startDelete(NO_TOKEN, new Listener() {
                    @Override
                    public void onDeleteComplete(int token, Object cookie, int result) {
                        if (listener != null) {
                            listener.onUnblockComplete(result, values);
                        }
                    }
                }, uri, null, null);
            }
        }, uri, null, null, null, null);
    }
}