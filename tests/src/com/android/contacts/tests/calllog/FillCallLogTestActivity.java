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

package com.android.contacts.tests.calllog;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CallLog.Calls;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.tests.R;

import java.util.Random;

/**
 * Activity to add entries to the call log for testing.
 */
public class FillCallLogTestActivity extends Activity {
    private static final String TAG = "FillCallLogTestActivity";
    /** Identifier of the loader for querying the call log. */
    private static final int CALLLOG_LOADER_ID = 1;

    private static final Random RNG = new Random();
    private static final int[] CALL_TYPES = new int[] {
        Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE, Calls.MISSED_TYPE,
    };

    private TextView mNumberTextView;
    private Button mAddButton;
    private ProgressBar mProgressBar;
    private CheckBox mUseRandomNumbers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fill_call_log_test);
        mNumberTextView = (TextView) findViewById(R.id.number);
        mAddButton = (Button) findViewById(R.id.add);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mUseRandomNumbers = (CheckBox) findViewById(R.id.use_random_numbers);

        mAddButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                int count;
                try {
                    count = Integer.parseInt(mNumberTextView.getText().toString());
                    if (count > 100) {
                        throw new RuntimeException("Number too large.  Max=100");
                    }
                } catch (RuntimeException e) {
                    Toast.makeText(FillCallLogTestActivity.this, e.toString(), Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                addEntriesToCallLog(count, mUseRandomNumbers.isChecked());
                mNumberTextView.setEnabled(false);
                mAddButton.setEnabled(false);
                mProgressBar.setProgress(0);
                mProgressBar.setMax(count);
                mProgressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Adds a number of entries to the call log. The content of the entries is based on existing
     * entries.
     *
     * @param count the number of entries to add
     */
    private void addEntriesToCallLog(final int count, boolean useRandomNumbers) {
        if (useRandomNumbers) {
            addRandomNumbers(count);
        } else {
            getLoaderManager().initLoader(CALLLOG_LOADER_ID, null,
                    new CallLogLoaderListener(count));
        }
    }

    /**
     * Calls when the insertion has completed.
     *
     * @param message the message to show in a toast to the user
     */
    private void insertCompleted(String message) {
        // Hide the progress bar.
        mProgressBar.setVisibility(View.GONE);
        // Re-enable the add button.
        mNumberTextView.setEnabled(true);
        mAddButton.setEnabled(true);
        mNumberTextView.setText("");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }


    /**
     * Creates a {@link ContentValues} object containing values corresponding to the given cursor.
     *
     * @param cursor the cursor from which to get the values
     * @return a newly created content values object
     */
    private ContentValues createContentValuesFromCursor(Cursor cursor) {
        ContentValues values = new ContentValues();
        for (int column = 0; column < cursor.getColumnCount();
                ++column) {
            String name = cursor.getColumnName(column);
            switch (cursor.getType(column)) {
                case Cursor.FIELD_TYPE_STRING:
                    values.put(name, cursor.getString(column));
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    values.put(name, cursor.getLong(column));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    values.put(name, cursor.getDouble(column));
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    values.put(name, cursor.getBlob(column));
                    break;
                case Cursor.FIELD_TYPE_NULL:
                    values.putNull(name);
                    break;
                default:
                    Log.d(TAG, "Invalid value in cursor: " + cursor.getType(column));
                    break;
            }
        }
        return values;
    }

    private void addRandomNumbers(int count) {
        ContentValues[] values = new ContentValues[count];
        for (int i = 0; i < count; i++) {
            values[i] = new ContentValues();
            values[i].put(Calls.NUMBER, generateRandomNumber());
            values[i].put(Calls.DATE, System.currentTimeMillis()); // Will be randomized later
            values[i].put(Calls.DURATION, 1); // Will be overwritten later
        }
        new AsyncCallLogInserter(values).execute(new Void[0]);
    }

    private static String generateRandomNumber() {
        return String.format("5%09d", RNG.nextInt(1000000000));
    }

    /** Invokes {@link AsyncCallLogInserter} when the call log has loaded. */
    private final class CallLogLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        /** The number of items to insert when done. */
        private final int mCount;

        private CallLogLoaderListener(int count) {
            mCount = count;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Log.d(TAG, "onCreateLoader");
            return new CursorLoader(FillCallLogTestActivity.this, Calls.CONTENT_URI,
                    null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            try {
                Log.d(TAG, "onLoadFinished");

                if (data.getCount() == 0) {
                    // If there are no entries in the call log, we cannot generate new ones.
                    insertCompleted(getString(R.string.noLogEntriesToast));
                    return;
                }

                data.moveToPosition(-1);

                ContentValues[] values = new ContentValues[mCount];
                for (int index = 0; index < mCount; ++index) {
                    if (!data.moveToNext()) {
                        data.moveToFirst();
                    }
                    values[index] = createContentValuesFromCursor(data);
                }
                new AsyncCallLogInserter(values).execute(new Void[0]);
            } finally {
                // This is a one shot loader.
                getLoaderManager().destroyLoader(CALLLOG_LOADER_ID);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    }

    /** Inserts a given number of entries in the call log based on the values given. */
    private final class AsyncCallLogInserter extends AsyncTask<Void, Integer, Integer> {
        /** The number of items to insert. */
        private final ContentValues[] mValues;

        public AsyncCallLogInserter(ContentValues[] values) {
            mValues = values;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            Log.d(TAG, "doInBackground");
            return insertIntoCallLog();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(TAG, "onProgressUpdate");
            updateCount(values[0]);
        }

        @Override
        protected void onPostExecute(Integer count) {
            Log.d(TAG, "onPostExecute");
            insertCompleted(getString(R.string.addedLogEntriesToast, count));
        }

        /**
         * Inserts a number of entries in the call log based on the given templates.
         *
         * @return the number of inserted entries
         */
        private Integer insertIntoCallLog() {
            int inserted = 0;

            for (int index = 0; index < mValues.length; ++index) {
                ContentValues values = mValues[index];
                // These should not be set.
                values.putNull(Calls._ID);
                // Add some randomness to the date. For each new entry being added, add an extra
                // day to the maximum possible offset from the original.
                values.put(Calls.DATE,
                        values.getAsLong(Calls.DATE)
                        - RNG.nextInt(24 * 60 * 60 * (index + 1)) * 1000L);
                // Add some randomness to the duration.
                if (values.getAsLong(Calls.DURATION) > 0) {
                    values.put(Calls.DURATION, RNG.nextInt(30 * 60 * 60 * 1000));
                }

                // Overwrite type.
                values.put(Calls.TYPE, CALL_TYPES[RNG.nextInt(CALL_TYPES.length)]);

                // Clear cached columns.
                values.putNull(Calls.CACHED_FORMATTED_NUMBER);
                values.putNull(Calls.CACHED_LOOKUP_URI);
                values.putNull(Calls.CACHED_MATCHED_NUMBER);
                values.putNull(Calls.CACHED_NAME);
                values.putNull(Calls.CACHED_NORMALIZED_NUMBER);
                values.putNull(Calls.CACHED_NUMBER_LABEL);
                values.putNull(Calls.CACHED_NUMBER_TYPE);
                values.putNull(Calls.CACHED_PHOTO_ID);

                // Insert into the call log the newly generated entry.
                ContentProviderClient contentProvider =
                        getContentResolver().acquireContentProviderClient(
                                Calls.CONTENT_URI);
                try {
                    Log.d(TAG, "adding entry to call log");
                    contentProvider.insert(Calls.CONTENT_URI, values);
                    ++inserted;
                    this.publishProgress(inserted);
                } catch (RemoteException e) {
                    Log.d(TAG, "insert failed", e);
                }
            }
            return inserted;
        }
    }

    /**
     * Updates the count shown to the user corresponding to the number of entries added.
     *
     * @param count the number of entries inserted so far
     */
    public void updateCount(Integer count) {
        mProgressBar.setProgress(count);
    }
}
