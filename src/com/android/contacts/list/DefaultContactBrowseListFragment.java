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
import com.android.contacts.ui.ContactsPreferencesActivity.Prefs;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {

    private boolean mEditMode;
    private boolean mCreateContactEnabled;
    private int mDisplayWithPhonesOnlyOption = ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED;
    private boolean mVisibleContactsRestrictionEnabled = true;
    private View mHeaderView;

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setAizyEnabled(true);
    }

    @Override
    protected void prepareEmptyView() {
        if (isShowingContactsWithPhonesOnly()) {
            setEmptyText(R.string.noContactsWithPhoneNumbers);
        } else {
            super.prepareEmptyView();
        }
    }

    private boolean isShowingContactsWithPhonesOnly() {
        switch (mDisplayWithPhonesOnlyOption) {
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED:
                return false;
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_ENABLED:
                return true;
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_PREFERENCE:
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(getActivity());
                return prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                        Prefs.DISPLAY_ONLY_PHONES_DEFAULT);
        }
        return false;
    }

    public void setDisplayWithPhonesOnlyOption(int displayWithPhonesOnly) {
        mDisplayWithPhonesOnlyOption = displayWithPhonesOnly;
        configureAdapter();
    }

    public void setVisibleContactsRestrictionEnabled(boolean flag) {
        mVisibleContactsRestrictionEnabled = flag;
        configureAdapter();
    }

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        if (isEditMode()) {
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                createNewContact();
            } else {
                editContact(adapter.getContactUri(position));
            }
        } else {
            viewContact(adapter.getContactUri(position));
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(true);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        DefaultContactListAdapter adapter = (DefaultContactListAdapter)getAdapter();
        if (adapter != null) {
            adapter.setContactsWithPhoneNumbersOnly(isShowingContactsWithPhonesOnly());
            adapter.setVisibleContactsOnly(mVisibleContactsRestrictionEnabled);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        if (isSearchResultsMode()) {
            return inflater.inflate(R.layout.contacts_list_search_results, null);
        } else {
            return inflater.inflate(R.layout.contacts_list_content, null);
        }
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mHeaderView = inflater.inflate(R.layout.total_contacts, null, false);
        headerContainer.addView(mHeaderView);
        getListView().addHeaderView(headerContainer);
        checkHeaderViewVisibility();
    }

    @Override
    public void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
    }

    private void checkHeaderViewVisibility() {
        if (mHeaderView != null) {
            mHeaderView.setVisibility(isSearchMode() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void showCount(int partitionIndex, Cursor data) {
        if (!isSearchMode() && data != null) {
            int count = data.getCount();
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
