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

package com.android.contacts.common.list;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;

import java.util.TreeSet;

/**
 * An extension of the default contact adapter that adds checkboxes and the ability
 * to select multiple contacts.
 */
public abstract class MultiSelectEntryContactListAdapter extends ContactEntryListAdapter {

    private SelectedContactsListener mSelectedContactsListener;
    private DeleteContactListener mDeleteContactListener;
    private TreeSet<Long> mSelectedContactIds = new TreeSet<Long>();
    private boolean mDisplayCheckBoxes;
    private final int mContactIdColumnIndex;

    public interface SelectedContactsListener {
        void onSelectedContactsChanged();
        void onSelectedContactsChangedViaCheckBox();
    }

    public interface DeleteContactListener {
        void onContactDeleteClicked(int position);
    }

    /**
     * @param contactIdColumnIndex the column index of the contact ID in the underlying cursor;
     *         it is passed in so that this adapter can support different kinds of contact
     *         lists (e.g. aggregate contacts or raw contacts).
     */
    public MultiSelectEntryContactListAdapter(Context context, int contactIdColumnIndex) {
        super(context);
        mContactIdColumnIndex = contactIdColumnIndex;
    }

    /**
     * Returns the column index of the contact ID in the underlying cursor; the contact ID
     * retrieved using this index is the value that is selected by this adapter (and returned
     * by {@link #getSelectedContactIds}).
     */
    public int getContactColumnIdIndex() {
        return mContactIdColumnIndex;
    }

    public DeleteContactListener getDeleteContactListener() {
        return mDeleteContactListener;
    }

    public void setDeleteContactListener(DeleteContactListener deleteContactListener) {
        mDeleteContactListener = deleteContactListener;
    }

    public void setSelectedContactsListener(SelectedContactsListener listener) {
        mSelectedContactsListener = listener;
    }

    /**
     * Returns set of selected contacts.
     */
    public TreeSet<Long> getSelectedContactIds() {
        return mSelectedContactIds;
    }

    /**
     * Returns the selected contacts as an array.
     */
    public long[] getSelectedContactIdsArray() {
        final Long[] contactIds = mSelectedContactIds.toArray(
                new Long[mSelectedContactIds.size()]);
        final long[] result = new long[contactIds.length];
        for (int i = 0; i < contactIds.length; i++) {
            result[i] = contactIds[i];
        }
        return result;
    }

    /**
     * Update set of selected contacts. This changes which checkboxes are set.
     */
    public void setSelectedContactIds(TreeSet<Long> selectedContactIds) {
        this.mSelectedContactIds = selectedContactIds;
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    /**
     * Shows checkboxes beside contacts if {@param displayCheckBoxes} is {@code TRUE}.
     * Not guaranteed to work with all configurations of this adapter.
     */
    public void setDisplayCheckBoxes(boolean showCheckBoxes) {
        mDisplayCheckBoxes = showCheckBoxes;
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    /**
     * Checkboxes are being displayed beside contacts.
     */
    public boolean isDisplayingCheckBoxes() {
        return mDisplayCheckBoxes;
    }

    /**
     * Toggle the checkbox beside the contact for {@param contactId}.
     */
    public void toggleSelectionOfContactId(long contactId) {
        if (mSelectedContactIds.contains(contactId)) {
            mSelectedContactIds.remove(contactId);
        } else {
            mSelectedContactIds.add(contactId);
        }
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor != null) {
            return cursor.getLong(getContactColumnIdIndex());
        }
        return 0;
     }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        final ContactListItemView view = (ContactListItemView) itemView;
        bindViewId(view, cursor, getContactColumnIdIndex());
        bindCheckBox(view, cursor, position, partition == ContactsContract.Directory.DEFAULT);
    }

    private void bindCheckBox(ContactListItemView view, Cursor cursor, int position,
            boolean isLocalDirectory) {
        // Disable clicking on all contacts from remote directories when showing check boxes. We do
        // this by telling the view to handle clicking itself.
        view.setClickable(!isLocalDirectory && mDisplayCheckBoxes);
        // Only show checkboxes if mDisplayCheckBoxes is enabled. Also, never show the
        // checkbox for other directory contacts except local directory.
        if (!mDisplayCheckBoxes || !isLocalDirectory) {
            view.hideCheckBox();
            return;
        }
        final CheckBox checkBox = view.getCheckBox();
        final long contactId = cursor.getLong(mContactIdColumnIndex);
        checkBox.setChecked(mSelectedContactIds.contains(contactId));
        checkBox.setTag(contactId);
        checkBox.setOnClickListener(mCheckBoxClickListener);
    }

    private final OnClickListener mCheckBoxClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final CheckBox checkBox = (CheckBox) v;
            final Long contactId = (Long) checkBox.getTag();
            if (checkBox.isChecked()) {
                mSelectedContactIds.add(contactId);
            } else {
                mSelectedContactIds.remove(contactId);
            }
            notifyDataSetChanged();
            if (mSelectedContactsListener != null) {
                mSelectedContactsListener.onSelectedContactsChangedViaCheckBox();
            }
        }
    };
}
