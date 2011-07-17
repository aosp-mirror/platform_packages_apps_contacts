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

import com.android.common.io.MoreCloseables;
import com.android.contacts.calllog.CallLogFragment.CallLogQuery;

import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.util.Log;

import java.lang.ref.WeakReference;

import javax.annotation.concurrent.GuardedBy;

/** Handles asynchronous queries to the call log. */
/*package*/ class CallLogQueryHandler extends AsyncQueryHandler {
    private static final String TAG = "CallLogQueryHandler";

    /** The token for the query to fetch the new entries from the call log. */
    private static final int QUERY_NEW_CALLS_TOKEN = 53;
    /** The token for the query to fetch the old entries from the call log. */
    private static final int QUERY_OLD_CALLS_TOKEN = 54;
    /** The token for the query to mark all missed calls as old after seeing the call log. */
    private static final int UPDATE_MISSED_CALLS_TOKEN = 55;

    private final WeakReference<CallLogFragment> mFragment;

    /** The cursor containing the new calls, or null if they have not yet been fetched. */
    @GuardedBy("this") private Cursor mNewCallsCursor;
    /** The cursor containing the old calls, or null if they have not yet been fetched. */
    @GuardedBy("this") private Cursor mOldCallsCursor;

    /**
     * Simple handler that wraps background calls to catch
     * {@link SQLiteException}, such as when the disk is full.
     */
    protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
        public CatchingWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                // Perform same query while catching any exceptions
                super.handleMessage(msg);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            }
        }
    }

    @Override
    protected Handler createHandler(Looper looper) {
        // Provide our special handler that catches exceptions
        return new CatchingWorkerHandler(looper);
    }

    public CallLogQueryHandler(CallLogFragment fragment) {
        super(fragment.getActivity().getContentResolver());
        mFragment = new WeakReference<CallLogFragment>(fragment);
    }

    /** Returns the list of columns for the headers. */
    private String[] getHeaderColumns() {
        int length = CallLogQuery._PROJECTION.length;
        String[] columns = new String[length + 1];
        System.arraycopy(CallLogQuery._PROJECTION, 0, columns, 0, length);
        columns[length] = CallLogQuery.SECTION_NAME;
        return columns;
    }

    /** Creates a cursor that contains a single row and maps the section to the given value. */
    private Cursor createHeaderCursorFor(int section) {
        MatrixCursor matrixCursor = new MatrixCursor(getHeaderColumns());
        // The values in this row correspond to default values for _PROJECTION from CallLogQuery
        // plus the section value.
        matrixCursor.addRow(new Object[]{ -1L, "", 0L, 0L, 0, "", "", section });
        return matrixCursor;
    }

    /** Returns a cursor for the old calls header. */
    private Cursor createOldCallsHeaderCursor() {
        return createHeaderCursorFor(CallLogQuery.SECTION_OLD_HEADER);
    }

    /** Returns a cursor for the new calls header. */
    private Cursor createNewCallsHeaderCursor() {
        return createHeaderCursorFor(CallLogQuery.SECTION_NEW_HEADER);
    }

    /**
     * Fetches the list of calls from the call log.
     * <p>
     * It will asynchronously update the content of the list view when the fetch completes.
     */
    public void fetchCalls() {
        cancelFetch();
        invalidate();
        fetchNewCalls();
        fetchOldCalls();
    }

    /** Fetches the list of new calls in the call log. */
    private void fetchNewCalls() {
        fetchCalls(QUERY_NEW_CALLS_TOKEN, true);
    }

    /** Fetch the list of old calls in the call log. */
    private void fetchOldCalls() {
        fetchCalls(QUERY_OLD_CALLS_TOKEN, false);
    }

    /** Fetches the list of calls in the call log, either the new one or the old ones. */
    private void fetchCalls(int token, boolean isNew) {
        // We need to check for NULL explicitly otherwise entries with where NEW is NULL will not
        // match either the query or its negation.
        String selection =
                String.format("%s IS NOT NULL AND %s = 1 AND (%s = ? OR %s = ?)",
                        Calls.NEW, Calls.NEW, Calls.TYPE, Calls.TYPE);
        String[] selectionArgs = new String[]{
                Integer.toString(Calls.MISSED_TYPE),
                Integer.toString(Calls.VOICEMAIL_TYPE),
        };
        if (!isNew) {
            selection = String.format("NOT (%s)", selection);
        }
        startQuery(token, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                CallLogQuery._PROJECTION, selection, selectionArgs, Calls.DEFAULT_SORT_ORDER);
    }

    /** Cancel any pending fetch request. */
    private void cancelFetch() {
        cancelOperation(QUERY_NEW_CALLS_TOKEN);
        cancelOperation(QUERY_OLD_CALLS_TOKEN);
    }

    /** Updates the missed calls to mark them as old. */
    public void updateMissedCalls() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder();
        where.append("type = ");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND ");
        where.append(Calls.NEW);
        where.append(" = 1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");

        startUpdate(UPDATE_MISSED_CALLS_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                values, where.toString(), null);
    }

    /**
     * Invalidate the current list of calls.
     * <p>
     * This method is synchronized because it must close the cursors and reset them atomically.
     */
    private synchronized void invalidate() {
        MoreCloseables.closeQuietly(mNewCallsCursor);
        MoreCloseables.closeQuietly(mOldCallsCursor);
        mNewCallsCursor = null;
        mOldCallsCursor = null;
    }

    @Override
    protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == QUERY_NEW_CALLS_TOKEN) {
            // Store the returned cursor.
            mNewCallsCursor = new ExtendedCursor(
                    cursor, CallLogQuery.SECTION_NAME, CallLogQuery.SECTION_NEW_ITEM);
        } else if (token == QUERY_OLD_CALLS_TOKEN) {
            // Store the returned cursor.
            mOldCallsCursor = new ExtendedCursor(
                    cursor, CallLogQuery.SECTION_NAME, CallLogQuery.SECTION_OLD_ITEM);
        } else {
            Log.w(TAG, "Unknown query completed: ignoring: " + token);
            return;
        }

        if (mNewCallsCursor != null && mOldCallsCursor != null) {
            updateAdapterData(createMergedCursor());
        }
    }

    /** Creates the merged cursor representing the data to show in the call log. */
    @GuardedBy("this")
    private Cursor createMergedCursor() {
        try {
            final boolean hasNewCalls = mNewCallsCursor.getCount() != 0;
            final boolean hasOldCalls = mOldCallsCursor.getCount() != 0;

            if (!hasNewCalls) {
                // Return only the old calls, without the header.
                MoreCloseables.closeQuietly(mNewCallsCursor);
                return mOldCallsCursor;
            }

            if (!hasOldCalls) {
                // Return only the new calls.
                MoreCloseables.closeQuietly(mOldCallsCursor);
                return new MergeCursor(
                        new Cursor[]{ createNewCallsHeaderCursor(), mNewCallsCursor });
            }

            return new MergeCursor(new Cursor[]{
                    createNewCallsHeaderCursor(), mNewCallsCursor,
                    createOldCallsHeaderCursor(), mOldCallsCursor});
        } finally {
            // Any cursor still open is now owned, directly or indirectly, by the caller.
            mNewCallsCursor = null;
            mOldCallsCursor = null;
        }
    }

    /**
     * Updates the adapter in the call log fragment to show the new cursor data.
     */
    private void updateAdapterData(Cursor combinedCursor) {
        final CallLogFragment fragment = mFragment.get();
        if (fragment != null) {
            fragment.onCallsFetched(combinedCursor);
        }
    }
}
