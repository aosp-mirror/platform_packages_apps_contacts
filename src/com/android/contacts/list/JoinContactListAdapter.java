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

import com.android.contacts.ContactsListActivity;
import com.android.contacts.R;
import com.android.contacts.list.ContactItemListAdapter;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class JoinContactListAdapter extends ContactItemListAdapter {
    Cursor mSuggestionsCursor;
    int mSuggestionsCursorCount;

    /**
     * Determines whether we display a list item with the label
     * "Show all contacts" or actually show all contacts
     */
    boolean mJoinModeShowAllContacts;

    public JoinContactListAdapter(ContactsListActivity context) {
        super(context);
    }

    public boolean isJoinModeShowAllContacts() {
        return mJoinModeShowAllContacts;
    }

    public void setJoinModeShowAllContacts(boolean flag) {
        mJoinModeShowAllContacts = flag;
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
            return LayoutInflater.from(getContext()).
                    inflate(R.layout.contacts_list_show_all_item, parent, false);
        }

        // Handle the separator specially
        int separatorId = getSeparatorId(position);
        if (separatorId != 0) {
            TextView view = (TextView) LayoutInflater.from(getContext()).
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
            v = newView(getContext(), cursor, parent);
        } else {
            newView = false;
            v = convertView;
        }
        bindView(v, getContext(), cursor);
        bindSectionHeader(v, realPosition, !showingSuggestion);
        return v;
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

    public int getSuggestionsCursorCount() {
        return mSuggestionsCursorCount;
    }

    @Override
    protected int getRealPosition(int pos) {
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