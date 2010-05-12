/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import com.android.contacts.R;

import android.app.patterns.CursorLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class JoinContactListAdapter extends ContactListAdapter {

    /** Maximum number of suggestions shown for joining aggregates */
    private static final int MAX_SUGGESTIONS = 4;

    private Cursor mSuggestionsCursor;
    private int mSuggestionsCursorCount;
    private long mTargetContactId;

    /**
     * Determines whether we display a list item with the label
     * "Show all contacts" or actually show all contacts
     */
    private boolean mAllContactsListShown;

    public JoinContactListAdapter(Context context) {
        super(context);
        setSectionHeaderDisplayEnabled(true);
    }

    public void setTargetContactId(long targetContactId) {
        this.mTargetContactId = targetContactId;
    }

    @Override
    public void configureLoader(CursorLoader cursorLoader) {
        JoinContactLoader loader = (JoinContactLoader)cursorLoader;
        loader.setLoadSuggestionsAndAllContacts(mAllContactsListShown);

        Builder builder = Contacts.CONTENT_URI.buildUpon();
        builder.appendEncodedPath(String.valueOf(mTargetContactId));
        builder.appendEncodedPath(AggregationSuggestions.CONTENT_DIRECTORY);

        String filter = getQueryString();
        if (!TextUtils.isEmpty(filter)) {
            builder.appendEncodedPath(Uri.encode(filter));
        }

        builder.appendQueryParameter("limit", String.valueOf(MAX_SUGGESTIONS));

        loader.setSuggestionUri(builder.build());

        // TODO simplify projection
        loader.setProjection(PROJECTION);

        if (mAllContactsListShown) {
            loader.setUri(buildSectionIndexerUri(Contacts.CONTENT_URI));
            loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1 AND " + Contacts._ID + "!=?");
            loader.setSelectionArgs(new String[]{String.valueOf(mTargetContactId)});
            if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
                loader.setSortOrder(Contacts.SORT_KEY_PRIMARY);
            } else {
                loader.setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    private boolean hasSuggestions() {
        return mSuggestionsCursorCount != 0;
    }

    public boolean isAllContactsListShown() {
        return mAllContactsListShown;
    }

    public void setAllContactsListShown(boolean flag) {
        mAllContactsListShown = flag;
    }

    public void setSuggestionsCursor(Cursor cursor) {
        mSuggestionsCursor = cursor;
        mSuggestionsCursorCount = cursor == null ? 0 : cursor.getCount();
    }

    public boolean isShowAllContactsItemPosition(int position) {
        return !mAllContactsListShown
                && hasSuggestions() && position == mSuggestionsCursorCount + 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (!mDataValid) {
            throw new IllegalStateException(
                    "this should only be called when the cursor is valid");
        }

        Cursor cursor;
        boolean showingSuggestion = false;
        if (hasSuggestions()) {
            if (position == 0) {
                // First section: "suggestions"
                TextView view = (TextView) inflate(R.layout.list_separator, parent);
                view.setText(R.string.separatorJoinAggregateSuggestions);
                return view;
            } else if (position < mSuggestionsCursorCount + 1) {
                showingSuggestion = true;
                cursor = mSuggestionsCursor;
                cursor.moveToPosition(position - 1);
            } else if (position == mSuggestionsCursorCount + 1) {
                // Second section: "all contacts"
                TextView view = (TextView) inflate(R.layout.list_separator, parent);
                view.setText(R.string.separatorJoinAggregateAll);
                return view;
            } else if (!mAllContactsListShown && position == mSuggestionsCursorCount + 2) {
                return inflate(R.layout.contacts_list_show_all_item, parent);
            } else {
                cursor = mCursor;
                cursor.moveToPosition(position - mSuggestionsCursorCount - 2);
            }
        } else {
            cursor = mCursor;
            cursor.moveToPosition(position);
        }

        View v;
        if (convertView == null || convertView.getTag() == null) {
            v = newView(getContext(), cursor, parent);
        } else {
            v = convertView;
        }
        bindView(position, v, cursor, showingSuggestion);
        return v;
    }

    private View inflate(int layoutId, ViewGroup parent) {
        return LayoutInflater.from(getContext()).inflate(layoutId, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // not used
    }

    public void bindView(int position, View itemView, Cursor cursor, boolean showingSuggestion) {
        final ContactListItemView view = (ContactListItemView)itemView;
        if (!showingSuggestion) {
            bindSectionHeaderAndDivider(view, position);
        }
        bindPhoto(view, cursor);
        bindName(view, cursor);
    }

    public Cursor getShowAllContactsLabelCursor() {
        MatrixCursor matrixCursor = new MatrixCursor(PROJECTION);
        Object[] row = new Object[PROJECTION.length];
        matrixCursor.addRow(row);
        return matrixCursor;
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (cursor == null) {
            setSuggestionsCursor(null);
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

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (mSuggestionsCursorCount == 0) {
            return super.getPositionForSection(sectionIndex);
        }

        // Get section position in the full list
        int position = super.getPositionForSection(sectionIndex);
        return position + mSuggestionsCursorCount + 2;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (mSuggestionsCursorCount == 0) {
            return super.getSectionForPosition(position);
        }

        if (position < mSuggestionsCursorCount + 2) {
            return -1;
        }

        return super.getSectionForPosition(position - mSuggestionsCursorCount - 2);
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
        if (hasSuggestions()) {
            // When showing suggestions, we have 2 additional list items: the "Suggestions"
            // and "All contacts" headers.
            return mSuggestionsCursorCount + superCount + 2;
        }
        return superCount;
    }

    public int getSuggestionsCursorCount() {
        return mSuggestionsCursorCount;
    }

    @Override
    public Object getItem(int pos) {
        if (hasSuggestions()) {
            // When showing suggestions, we have 2 additional list items: the "Suggestions"
            // and "All contacts" separators.
            if (pos == 0) {
                return null;
            }
            else if (pos < mSuggestionsCursorCount + 1) {
                // We are in the upper partition (Suggestions). Adjusting for the "Suggestions"
                // separator.
                mSuggestionsCursor.moveToPosition(pos - 1);
                return mSuggestionsCursor;
            } else if (pos == mSuggestionsCursorCount + 1) {
                // This is the "All contacts" separator
                return null;
            } else {
                if (!isAllContactsListShown()) {
                    // This is the "Show all contacts" item
                    return null;
                } else {
                    // We are in the lower partition (All contacts). Adjusting for the size
                    // of the upper partition plus the two separators.
                    mCursor.moveToPosition(pos - mSuggestionsCursorCount - 2);
                    return mCursor;
                }
            }
        } else if (mCursor != null) {
            // No separators
            mCursor.moveToPosition(pos);
            return mCursor;
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int pos) {
        Cursor cursor = (Cursor)getItem(pos);
        return cursor == null ? 0 : cursor.getLong(mRowIDColumn);
    }

    public Uri getContactUri(int position) {
        Cursor cursor = (Cursor)getItem(position);
        long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
        String lookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
        return Contacts.getLookupUri(contactId, lookupKey);
    }
}