/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;


import com.android.contacts.list.JoinContactListAdapter;
import com.android.contacts.list.JoinContactListFragment;
import com.android.internal.telephony.gsm.stk.ResultCode;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;

/**
 * An activity that shows a list of contacts that can be joined with the target contact.
 */
public class JoinContactActivity extends ContactsListActivity {

    private static final String TAG = "JoinContactActivity";

    /**
     * The action for the join contact activity.
     * <p>
     * Input: extra field {@link #EXTRA_TARGET_CONTACT_ID} is the aggregate ID.
     * TODO: move to {@link ContactsContract}.
     */
    public static final String JOIN_CONTACT = "com.android.contacts.action.JOIN_CONTACT";

    /**
     * Used with {@link #JOIN_CONTACT} to give it the target for aggregation.
     * <p>
     * Type: LONG
     */
    public static final String EXTRA_TARGET_CONTACT_ID = "com.android.contacts.action.CONTACT_ID";

    /** Maximum number of suggestions shown for joining aggregates */
    private static final int MAX_SUGGESTIONS = 4;

    private long mTargetContactId;

    /**
     * The ID of the special item described above.
     */
    private static final long JOIN_MODE_SHOW_ALL_CONTACTS_ID = -2;

    private boolean mLoadingJoinSuggestions;

    private JoinContactListAdapter mAdapter;

    @Override
    protected boolean resolveIntent(Intent intent) {
        mMode = MODE_PICK_CONTACT;
        mTargetContactId = intent.getLongExtra(EXTRA_TARGET_CONTACT_ID, -1);
        if (mTargetContactId == -1) {
            Log.e(TAG, "Intent " + intent.getAction() + " is missing required extra: "
                    + EXTRA_TARGET_CONTACT_ID);
            setResult(RESULT_CANCELED);
            finish();
            return false;
        }

        mListFragment = new JoinContactListFragment();

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO move this to onAttach of the corresponding fragment
        TextView blurbView = (TextView)findViewById(R.id.join_contact_blurb);

        String blurb = getString(R.string.blurbJoinContactDataWith,
                getContactDisplayName(mTargetContactId));
        blurbView.setText(blurb);

        ListView listView = (ListView)findViewById(android.R.id.list);
        mAdapter = (JoinContactListAdapter)listView.getAdapter();
        mAdapter.setJoinModeShowAllContacts(true);
    }

    @Override
    public void onListItemClick(int position, long id) {
        if (id == JOIN_MODE_SHOW_ALL_CONTACTS_ID) {
            mAdapter.setJoinModeShowAllContacts(false);
            startQuery();
        } else {
            final Uri uri = getSelectedUri(position);
            setResult(RESULT_OK, new Intent(null, uri));
            finish();
        }
    }

    @Override
    protected Uri getUriToQuery() {
        return getJoinSuggestionsUri(null);
    }

    /*
     * TODO: move to a background thread.
     */
    private String getContactDisplayName(long contactId) {
        String contactName = null;
        Cursor c = getContentResolver().query(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                new String[] {Contacts.DISPLAY_NAME}, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                contactName = c.getString(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (contactName == null) {
            contactName = "";
        }

        return contactName;
    }

    private Uri getJoinSuggestionsUri(String filter) {
        Builder builder = Contacts.CONTENT_URI.buildUpon();
        builder.appendEncodedPath(String.valueOf(mTargetContactId));
        builder.appendEncodedPath(AggregationSuggestions.CONTENT_DIRECTORY);
        if (!TextUtils.isEmpty(filter)) {
            builder.appendEncodedPath(Uri.encode(filter));
        }
        builder.appendQueryParameter("limit", String.valueOf(MAX_SUGGESTIONS));
        return builder.build();
    }

    @Override
    public
    Cursor doFilter(String filter) {
        throw new UnsupportedOperationException();
    }

    private Cursor getShowAllContactsLabelCursor(String[] projection) {
        MatrixCursor matrixCursor = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        // The only columns we care about is the id
        row[SUMMARY_ID_COLUMN_INDEX] = JOIN_MODE_SHOW_ALL_CONTACTS_ID;
        matrixCursor.addRow(row);
        return matrixCursor;
    }

    @Override
    protected void startQuery(Uri uri, String[] projection) {
        mLoadingJoinSuggestions = true;
        startQuery(uri, projection, null, null, null);
    }

    @Override
    protected void onQueryComplete(Cursor cursor) {
        // Whenever we get a suggestions cursor, we need to immediately kick off
        // another query for the complete list of contacts
        if (cursor != null && mLoadingJoinSuggestions) {
            mLoadingJoinSuggestions = false;
            if (cursor.getCount() > 0) {
                mAdapter.setSuggestionsCursor(cursor);
            } else {
                cursor.close();
                mAdapter.setSuggestionsCursor(null);
            }

            if (mAdapter.getSuggestionsCursorCount() == 0
                    || !mAdapter.isJoinModeShowAllContacts()) {
                startQuery(getContactFilterUri(mListFragment.getQueryString()),
                        CONTACTS_SUMMARY_PROJECTION,
                        Contacts._ID + " != " + mTargetContactId
                                + " AND " + ContactsContract.Contacts.IN_VISIBLE_GROUP + "=1", null,
                        getSortOrder(CONTACTS_SUMMARY_PROJECTION));
                return;
            }

            cursor = getShowAllContactsLabelCursor(CONTACTS_SUMMARY_PROJECTION);
        }

        super.onQueryComplete(cursor);
    }
}
