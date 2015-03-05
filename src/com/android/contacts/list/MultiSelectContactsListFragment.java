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

package com.android.contacts.list;

import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.list.MultiSelectEntryContactListAdapter.SelectedContactsListener;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.TreeSet;

/**
 * Fragment containing a contact list used for browsing contacts and optionally selecting
 * multiple contacts via checkboxes.
 */
public class MultiSelectContactsListFragment extends DefaultContactBrowseListFragment
        implements SelectedContactsListener {

    public interface OnCheckBoxListActionListener {
        void onStartDisplayingCheckBoxes();
        void onSelectedContactIdsChanged();
        void onStopDisplayingCheckBoxes();
    }

    private static final String EXTRA_KEY_SELECTED_CONTACTS = "selected_contacts";

    private OnCheckBoxListActionListener mCheckBoxListListener;

    public void setCheckBoxListListener(OnCheckBoxListActionListener checkBoxListListener) {
        mCheckBoxListListener = checkBoxListListener;
    }

    @Override
    public void onSelectedContactsChanged() {
        if (mCheckBoxListListener != null) {
            mCheckBoxListListener.onSelectedContactIdsChanged();
        }
    }

    @Override
    public void onSelectedContactsChangedViaCheckBox() {
        if (getAdapter().getSelectedContactIds().size() == 0) {
            // Last checkbox has been unchecked. So we should stop displaying checkboxes.
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        } else {
            onSelectedContactsChanged();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            final TreeSet<Long> selectedContactIds = (TreeSet<Long>)
                    savedInstanceState.getSerializable(EXTRA_KEY_SELECTED_CONTACTS);
            getAdapter().setSelectedContactIds(selectedContactIds);
            if (mCheckBoxListListener != null) {
                mCheckBoxListListener.onSelectedContactIdsChanged();
            }
        }
    }

    public TreeSet<Long> getSelectedContactIds() {
        final MultiSelectEntryContactListAdapter adapter = getAdapter();
        return adapter.getSelectedContactIds();
    }

    @Override
    public MultiSelectEntryContactListAdapter getAdapter() {
        return (MultiSelectEntryContactListAdapter) super.getAdapter();
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        getAdapter().setSelectedContactsListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_KEY_SELECTED_CONTACTS, getSelectedContactIds());
    }

    public void displayCheckBoxes(boolean displayCheckBoxes) {
        getAdapter().setDisplayCheckBoxes(displayCheckBoxes);
        if (!displayCheckBoxes) {
            clearCheckBoxes();
        }
    }

    public void clearCheckBoxes() {
        getAdapter().setSelectedContactIds(new TreeSet<Long>());
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        final int previouslySelectedCount = getAdapter().getSelectedContactIds().size();
        final Uri uri = getAdapter().getContactUri(position);
        if (uri != null && (position > 0 || !getAdapter().hasProfile())) {
            final String contactId = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(contactId)) {
                if (mCheckBoxListListener != null) {
                    mCheckBoxListListener.onStartDisplayingCheckBoxes();
                }
                getAdapter().toggleSelectionOfContactId(Long.valueOf(contactId));
            }
        }
        final int nowSelectedCount = getAdapter().getSelectedContactIds().size();
        if (mCheckBoxListListener != null
                && previouslySelectedCount != 0 && nowSelectedCount == 0) {
            // Last checkbox has been unchecked. So we should stop displaying checkboxes.
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
        return true;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            final String contactId = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(contactId)) {
                getAdapter().toggleSelectionOfContactId(Long.valueOf(contactId));
            }
        } else {
            super.onItemClick(position, id);
        }
        if (mCheckBoxListListener != null && getAdapter().getSelectedContactIds().size() == 0) {
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new MultiSelectEntryContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));
        return adapter;
    }
}
