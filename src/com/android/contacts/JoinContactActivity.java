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

import android.content.ContentUris;
import android.content.Context;
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
import android.view.View;
import android.view.ViewGroup;
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
     * Determines whether we display a list item with the label
     * "Show all contacts" or actually show all contacts
     */
    private boolean mJoinModeShowAllContacts;

    /**
     * The ID of the special item described above.
     */
    private static final long JOIN_MODE_SHOW_ALL_CONTACTS_ID = -2;

    private boolean mLoadingJoinSuggestions;

    private JoinContactListAdapter mAdapter;

    @Override
    protected void resolveIntent(Intent intent) {
        mMode = MODE_PICK_CONTACT;
        mTargetContactId = intent.getLongExtra(EXTRA_TARGET_CONTACT_ID, -1);
        if (mTargetContactId == -1) {
            Log.e(TAG, "Intent " + intent.getAction() + " is missing required extra: "
                    + EXTRA_TARGET_CONTACT_ID);
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void initContentView() {
        setContentView(R.layout.contacts_list_content_join);
        TextView blurbView = (TextView)findViewById(R.id.join_contact_blurb);

        String blurb = getString(R.string.blurbJoinContactDataWith,
                getContactDisplayName(mTargetContactId));
        blurbView.setText(blurb);
        mJoinModeShowAllContacts = true;
        mAdapter = new JoinContactListAdapter(this);
        setupListView(mAdapter);
    }

    @Override
    protected void onListItemClick(int position, long id) {
        if (id == JOIN_MODE_SHOW_ALL_CONTACTS_ID) {
            mJoinModeShowAllContacts = false;
            startQuery();
        } else {
            final Uri uri = getSelectedUri(position);
            returnPickerResult(null, null, uri);
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

            if (mAdapter.mSuggestionsCursorCount == 0
                    || !mJoinModeShowAllContacts) {
                startQuery(getContactFilterUri(getTextFilter()),
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

    @Override
    protected void setEmptyText() {
        return;
    }

    private class JoinContactListAdapter extends ContactItemListAdapter {
        Cursor mSuggestionsCursor;
        int mSuggestionsCursorCount;

        public JoinContactListAdapter(Context context) {
            super(context);
        }

        public void setSuggestionsCursor(Cursor cursor) {
            if (mSuggestionsCursor != null) {
                mSuggestionsCursor.close();
            }
            mSuggestionsCursor = cursor;
            mSuggestionsCursorCount = cursor == null ? 0 : cursor.getCount();
        }

        private boolean isShowAllContactsItemPosition(int position) {
            return mJoinModeShowAllContacts
                    && mSuggestionsCursorCount != 0 && position == mSuggestionsCursorCount + 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }

            if (isShowAllContactsItemPosition(position)) {
                return getLayoutInflater().
                        inflate(R.layout.contacts_list_show_all_item, parent, false);
            }

            // Handle the separator specially
            int separatorId = getSeparatorId(position);
            if (separatorId != 0) {
                TextView view = (TextView) getLayoutInflater().
                        inflate(R.layout.list_separator, parent, false);
                view.setText(separatorId);
                return view;
            }

            boolean showingSuggestion;
            Cursor cursor;
            if (mSuggestionsCursorCount != 0 && position < mSuggestionsCursorCount + 2) {
                showingSuggestion = true;
                cursor = mSuggestionsCursor;
            } else {
                showingSuggestion = false;
                cursor = mCursor;
            }

            int realPosition = getRealPosition(position);
            if (!cursor.moveToPosition(realPosition)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            boolean newView;
            View v;
            if (convertView == null || convertView.getTag() == null) {
                newView = true;
                v = newView(mContext, cursor, parent);
            } else {
                newView = false;
                v = convertView;
            }
            bindView(v, mContext, cursor);
            bindSectionHeader(v, realPosition, !showingSuggestion);
            return v;
        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (cursor == null) {
                mAdapter.setSuggestionsCursor(null);
            }

            super.changeCursor(cursor);
        }
        @Override
        public int getItemViewType(int position) {
            if (isShowAllContactsItemPosition(position)) {
                return IGNORE_ITEM_VIEW_TYPE;
            }

            return super.getItemViewType(position);
        }

        private int getSeparatorId(int position) {
            if (mSuggestionsCursorCount != 0) {
                if (position == 0) {
                    return R.string.separatorJoinAggregateSuggestions;
                } else if (position == mSuggestionsCursorCount + 1) {
                    return R.string.separatorJoinAggregateAll;
                }
            }
            return 0;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return super.areAllItemsEnabled() && mSuggestionsCursorCount == 0;
        }

        @Override
        public boolean isEnabled(int position) {
            if (position == 0) {
                return false;
            }

            if (mSuggestionsCursorCount > 0) {
                return position != 0 && position != mSuggestionsCursorCount + 1;
            }
            return true;
        }

        @Override
        public int getCount() {
            if (!mDataValid) {
                return 0;
            }
            int superCount = super.getCount();
            if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" headers.
                return mSuggestionsCursorCount + superCount + 2;
            }
            return superCount;
        }

        private int getRealPosition(int pos) {
            if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" separators.
                if (pos < mSuggestionsCursorCount + 2) {
                    // We are in the upper partition (Suggestions). Adjusting for the "Suggestions"
                    // separator.
                    return pos - 1;
                } else {
                    // We are in the lower partition (All contacts). Adjusting for the size
                    // of the upper partition plus the two separators.
                    return pos - mSuggestionsCursorCount - 2;
                }
            } else {
                // No separator, identity map
                return pos;
            }
        }

        @Override
        public Object getItem(int pos) {
            if (mSuggestionsCursorCount != 0 && pos <= mSuggestionsCursorCount) {
                mSuggestionsCursor.moveToPosition(getRealPosition(pos));
                return mSuggestionsCursor;
            } else {
                int realPosition = getRealPosition(pos);
                if (realPosition < 0) {
                    return null;
                }
                return super.getItem(realPosition);
            }
        }

        @Override
        public long getItemId(int pos) {
            if (mSuggestionsCursorCount != 0 && pos < mSuggestionsCursorCount + 2) {
                if (mSuggestionsCursor.moveToPosition(pos - 1)) {
                    return mSuggestionsCursor.getLong(mRowIDColumn);
                } else {
                    return 0;
                }
            }
            int realPosition = getRealPosition(pos);
            if (realPosition < 0) {
                return 0;
            }
            return super.getItemId(realPosition);
        }
    }
}
