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

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.TextView;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type loading
 * a combination of starred and frequently contacted.
 */
public class StrequentContactListAdapter extends ContactListAdapter {

    private int mFrequentSeparatorPos;
    private TextView mSeparatorView;
    private OnClickListener mCallButtonListener;
    private int mCallButtonId;
    private boolean mStarredContactsIncluded;
    private boolean mFrequentlyContactedContactsIncluded;

    public StrequentContactListAdapter(Context context, int callButtonId) {
        super(context);
        mCallButtonId = callButtonId;
    }

    public void setCallButtonListener(OnClickListener callButtonListener) {
        mCallButtonListener = callButtonListener;
    }

    public void setStarredContactsIncluded(boolean flag) {
        mStarredContactsIncluded = flag;
    }

    public void setFrequentlyContactedContactsIncluded(boolean flag) {
        mFrequentlyContactedContactsIncluded = flag;
    }

    @Override
    public void configureLoader(CursorLoader loader) {
        String sortOrder = getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY
                ? Contacts.SORT_KEY_PRIMARY
                : Contacts.SORT_KEY_ALTERNATIVE;
        if (mStarredContactsIncluded && mFrequentlyContactedContactsIncluded) {
            loader.setUri(Contacts.CONTENT_STREQUENT_URI);
        } else if (mStarredContactsIncluded) {
            loader.setUri(Contacts.CONTENT_URI);
            loader.setSelection(Contacts.STARRED + "!=0");
        } else if (mFrequentlyContactedContactsIncluded) {
            loader.setUri(Contacts.CONTENT_URI);
            loader.setSelection(Contacts.TIMES_CONTACTED + " > 0");
            sortOrder = Contacts.TIMES_CONTACTED + " DESC";
        } else {
            throw new UnsupportedOperationException("Neither StarredContactsIncluded nor "
                    + "FrequentlyContactedContactsIncluded is set");
        }

        loader.setProjection(PROJECTION);
        loader.setSortOrder(sortOrder);
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);

        // Get the split between starred and frequent items, if the mode is strequent
        mFrequentSeparatorPos = ListView.INVALID_POSITION;

        if (mStarredContactsIncluded && mFrequentlyContactedContactsIncluded) {
            int count = 0;
            if (cursor != null && (count = cursor.getCount()) > 0) {
                cursor.moveToPosition(-1);
                for (int i = 0; cursor.moveToNext(); i++) {
                    int starred = cursor.getInt(CONTACT_STARRED_COLUMN_INDEX);
                    if (starred == 0) {
                        if (i > 0) {
                            // Only add the separator when there are starred items present
                            mFrequentSeparatorPos = i;
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public int getCount() {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION) {
            return super.getCount();
        } else {
            // Add a row for the separator
            return super.getCount() + 1;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return mFrequentSeparatorPos == ListView.INVALID_POSITION;
    }

    @Override
    public boolean isEnabled(int position) {
        return position != mFrequentSeparatorPos;
    }

    @Override
    public Object getItem(int position) {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION
                || position < mFrequentSeparatorPos) {
            return super.getItem(position);
        } else {
            return super.getItem(position - 1);
        }
    }

    @Override
    public long getItemId(int position) {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION
                || position < mFrequentSeparatorPos) {
            return super.getItemId(position);
        } else {
            return super.getItemId(position - 1);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION
                || position < mFrequentSeparatorPos) {
            return super.getItemViewType(position);
        } else if (position == mFrequentSeparatorPos) {
            return IGNORE_ITEM_VIEW_TYPE;
        } else {
            return super.getItemViewType(position - 1);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION
                || position < mFrequentSeparatorPos) {
            return super.getView(position, convertView, parent);
        } else if (position == mFrequentSeparatorPos) {
            if (mSeparatorView == null) {
                mSeparatorView = (TextView)LayoutInflater.from(getContext()).
                        inflate(R.layout.list_separator, parent, false);
                mSeparatorView.setText(R.string.favoritesFrquentSeparator);
            }
            return mSeparatorView;
        } else {
            return super.getView(position - 1, convertView, parent);
        }
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        ContactListItemView view = (ContactListItemView)super.newView(context, partition, cursor,
                position, parent);
        view.setOnCallButtonClickListener(mCallButtonListener);
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        bindName(view, cursor);
        bindQuickContact(view, cursor);
        bindPresence(view, cursor);

        // Make the call button visible if requested.
        if (getHasPhoneNumber(position)) {
            view.showCallButton(mCallButtonId, position);
        } else {
            view.hideCallButton();
        }
    }
}
