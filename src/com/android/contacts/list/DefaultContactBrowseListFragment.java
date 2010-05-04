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

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {

    private boolean mEditMode;
    private boolean mCreateContactEnabled;

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        if (adapter.isSearchAllContactsItemPosition(position)) {
            searchAllContacts();
        } else {
            if (isEditMode()) {
                if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                    createNewContact();
                } else {
                    adapter.moveToPosition(position);
                    editContact(adapter.getContactUri());
                }
            } else {
                adapter.moveToPosition(position);
                viewContact(adapter.getContactUri());
            }
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());

        adapter.setSearchMode(isSearchMode());
        adapter.setSearchResultsMode(isSearchResultsMode());
        adapter.setQueryString(getQueryString());

        adapter.setContactNameDisplayOrder(getContactNameDisplayOrder());
        adapter.setSortOrder(getSortOrder());

        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(true);

        adapter.configureLoader(getLoader());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        if (isSearchMode()) {
            return inflater.inflate(R.layout.contacts_search_content, null);
        } else if (isSearchResultsMode()) {
            return inflater.inflate(R.layout.contacts_list_search_results, null);
        } else {
            return inflater.inflate(R.layout.contacts_list_content, null);
        }
    }

    @Override
    protected View createView(LayoutInflater inflater, ViewGroup container) {
        View view = super.createView(inflater, container);
        if (!isSearchResultsMode()) {
            // In the search-results mode the count is shown in the fat header above the list
            getListView().addHeaderView(inflater.inflate(R.layout.total_contacts, null, false));
        }
        return view;
    }

    @Override
    protected void showCount(Cursor data) {
        int count = data.getCount();
        if (isSearchMode()) {
            TextView textView = (TextView) getView().findViewById(R.id.totalContactsText);
            // TODO
            // if (TextUtils.isEmpty(getQueryString())) {
            String text = getQuantityText(count, R.string.listFoundAllContactsZero,
                    R.plurals.searchFoundContacts);
            textView.setText(text);
        }
        else if (isSearchResultsMode()) {
            TextView countText = (TextView)getView().findViewById(R.id.search_results_found);
            String text = getQuantityText(data.getCount(),
                    R.string.listFoundAllContactsZero, R.plurals.listFoundAllContacts);
            countText.setText(text);
        } else {
            // TODO
            // if (contactsListActivity.mDisplayOnlyPhones) {
            // text = contactsListActivity.getQuantityText(count,
            // R.string.listTotalPhoneContactsZero,
            // R.plurals.listTotalPhoneContacts);
            TextView textView = (TextView)getView().findViewById(R.id.totalContactsText);
            String text = getQuantityText(count, R.string.listTotalAllContactsZero,
                    R.plurals.listTotalAllContacts);
            textView.setText(text);
        }
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }
}
