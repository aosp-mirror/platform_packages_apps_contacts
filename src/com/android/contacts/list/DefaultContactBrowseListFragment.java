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
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.widget.NotifyingSpinner;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment
        implements OnItemSelectedListener, NotifyingSpinner.SelectionListener {

    private static final String KEY_EDIT_MODE = "editMode";
    private static final String KEY_CREATE_CONTACT_ENABLED = "createContactEnabled";
    private static final String KEY_DISPLAY_WITH_PHONES_ONLY = "displayWithPhonesOnly";
    private static final String KEY_VISIBLE_CONTACTS_RESTRICTION = "visibleContactsRestriction";
    private static final String KEY_FILTER_ENABLED = "filterEnabled";

    private static final int REQUEST_CODE_CUSTOMIZE_FILTER = 3;

    private static final int MESSAGE_REFRESH_FILTERS = 0;

    /**
     * The delay before the contact filter list is refreshed. This is needed because
     * during contact sync we will get lots of notifications in rapid succession. This
     * delay will prevent the slowly changing list of filters from reloading too often.
     */
    private static final int FILTER_SPINNER_REFRESH_DELAY_MILLIS = 5000;

    private boolean mEditMode;
    private boolean mCreateContactEnabled;
    private int mDisplayWithPhonesOnlyOption = ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED;
    private boolean mVisibleContactsRestrictionEnabled = true;
    private View mCounterHeaderView;
    private View mSearchHeaderView;

    private boolean mFilterEnabled;
    private SparseArray<ContactListFilter> mFilters;
    private ArrayList<ContactListFilter> mFilterList;
    private int mNextFilterId = 1;
    private NotifyingSpinner mFilterSpinner;
    private FilterSpinnerAdapter mFilterSpinnerAdapter;
    private ContactListFilter mFilter;
    private boolean mFiltersLoaded;
    private SharedPreferences mPrefs;
    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_REFRESH_FILTERS) {
                loadFilters();
            }
        }
    };

    private LoaderCallbacks<List<ContactListFilter>> mGroupFilterLoaderCallbacks =
            new LoaderCallbacks<List<ContactListFilter>>() {

        @Override
        public ContactGroupFilterLoader onCreateLoader(int id, Bundle args) {
            return new ContactGroupFilterLoader(getContext());
        }

        @Override
        public void onLoadFinished(
                Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
            onGroupFilterLoadFinished(data);
        }
    };

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setAizyEnabled(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EDIT_MODE, mEditMode);
        outState.putBoolean(KEY_CREATE_CONTACT_ENABLED, mCreateContactEnabled);
        outState.putInt(KEY_DISPLAY_WITH_PHONES_ONLY, mDisplayWithPhonesOnlyOption);
        outState.putBoolean(KEY_VISIBLE_CONTACTS_RESTRICTION, mVisibleContactsRestrictionEnabled);
        outState.putBoolean(KEY_FILTER_ENABLED, mFilterEnabled);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mEditMode = savedState.getBoolean(KEY_EDIT_MODE);
        mCreateContactEnabled = savedState.getBoolean(KEY_CREATE_CONTACT_ENABLED);
        mDisplayWithPhonesOnlyOption = savedState.getInt(KEY_DISPLAY_WITH_PHONES_ONLY);
        mVisibleContactsRestrictionEnabled =
                savedState.getBoolean(KEY_VISIBLE_CONTACTS_RESTRICTION);
        mFilterEnabled = savedState.getBoolean(KEY_FILTER_ENABLED);
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
                        .getDefaultSharedPreferences(getContext());
                return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                        ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
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
            viewContact(adapter.getContactUri(position), false);
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        DefaultContactListAdapter adapter = (DefaultContactListAdapter)getAdapter();
        if (adapter != null) {
            adapter.setContactsWithPhoneNumbersOnly(isShowingContactsWithPhonesOnly());
            adapter.setVisibleContactsOnly(mVisibleContactsRestrictionEnabled);
            adapter.setFilter(mFilter, mFilterList);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mCounterHeaderView = inflater.inflate(R.layout.total_contacts, null, false);
        headerContainer.addView(mCounterHeaderView);
        mSearchHeaderView = inflater.inflate(R.layout.search_header, null, false);
        headerContainer.addView(mSearchHeaderView);
        getListView().addHeaderView(headerContainer);
        checkHeaderViewVisibility();
        configureFilterSpinner();
    }

    protected void configureFilterSpinner() {
        mFilterSpinner = (NotifyingSpinner)getView().findViewById(R.id.filter_spinner);
        if (mFilterSpinner == null) {
            return;
        }

        if (!mFilterEnabled) {
            mFilterSpinner.setVisibility(View.GONE);
            return;
        }

        mFilterSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
    }

    private void checkHeaderViewVisibility() {
        if (mCounterHeaderView != null) {
            mCounterHeaderView.setVisibility(isSearchMode() ? View.GONE : View.VISIBLE);
        }

        // Hide the search header by default. See showCount().
        if (mSearchHeaderView != null) {
            mSearchHeaderView.setVisibility(View.GONE);
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
            TextView textView = (TextView)mCounterHeaderView.findViewById(R.id.totalContactsText);
            String text = getQuantityText(count, R.string.listTotalAllContactsZero,
                    R.plurals.listTotalAllContacts);
            textView.setText(text);
        } else {
            ContactListAdapter adapter = getAdapter();
            if (adapter == null) {
                return;
            }

            // In search mode we only display the header if there is nothing found
            if (!adapter.areAllPartitionsEmpty()) {
                mSearchHeaderView.setVisibility(View.GONE);
            } else {
                TextView textView = (TextView) mSearchHeaderView.findViewById(
                        R.id.totalContactsText);
                ProgressBar progress = (ProgressBar) mSearchHeaderView.findViewById(
                        R.id.progress);
                if (adapter.isLoading()) {
                    textView.setText(R.string.search_results_searching);
                    progress.setVisibility(View.VISIBLE);
                } else {
                    textView.setText(R.string.listFoundAllContactsZero);
                    progress.setVisibility(View.GONE);
                }
                mSearchHeaderView.setVisibility(View.VISIBLE);
            }
        }
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public boolean isFilterEnabled() {
        return mFilterEnabled;
    }

    public void setFilterEnabled(boolean flag) {
        this.mFilterEnabled = flag;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setContactListFilter((int) id);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        setContactListFilter(0);
    }

    @Override
    public void onStart() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (mFilterEnabled) {
            mFiltersLoaded = false;
            mFilter = ContactListFilter.restoreFromPreferences(mPrefs);
        }
        super.onStart();
    }

    @Override
    protected void startLoading() {
        // We need to load filters before we can load the list contents
        if (mFilterEnabled && !mFiltersLoaded) {
            loadFilters();
        } else {
            super.startLoading();
        }
    }

    private void loadFilters() {
        getLoaderManager().restartLoader(
                R.id.contact_list_filter_loader, null, mGroupFilterLoaderCallbacks);
    }

    protected void onGroupFilterLoadFinished(List<ContactListFilter> filters) {
        if (mFilters == null) {
            mFilters = new SparseArray<ContactListFilter>(filters.size());
            mFilterList = new ArrayList<ContactListFilter>();
        } else {
            mFilters.clear();
            mFilterList.clear();
        }

        boolean filterValid = mFilter != null
                && (mFilter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                        || mFilter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM);

        int accountCount = 0;
        int count = filters.size();
        for (int index = 0; index < count; index++) {
            if (filters.get(index).filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                accountCount++;
            }
        }

        if (accountCount > 1) {
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM));
        }

        for (int index = 0; index < count; index++) {
            ContactListFilter filter = filters.get(index);

            boolean firstAndOnly = accountCount == 1
                    && filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT;

            // If we only have one account, don't show it as "account", instead show it as "all"
            if (firstAndOnly) {
                filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
            }

            mFilters.append(mNextFilterId++, filter);
            mFilterList.add(filter);
            filterValid |= filter.equals(mFilter);

            if (firstAndOnly) {
                mFilters.append(mNextFilterId++,
                        new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM));
            }
        }

        boolean filterChanged = false;
        if (mFilter == null  || !filterValid) {
            filterChanged = mFilter != null;
            mFilter = getDefaultFilter();
        }

        if (mFilterSpinnerAdapter == null) {
            mFilterSpinnerAdapter = new FilterSpinnerAdapter();
            mFilterSpinner.setAdapter(mFilterSpinnerAdapter);
        } else {
            mFilterSpinnerAdapter.notifyDataSetChanged();
        }

        if (filterChanged) {
            mFiltersLoaded = true;
            reloadData();
        } else if (!mFiltersLoaded) {
            mFiltersLoaded = true;
            startLoading();
        }

        updateFilterView();
    }

    @Override
    protected void onPartitionLoaded(int partitionIndex, Cursor data) {
        super.onPartitionLoaded(partitionIndex, data);
        if (mFilterEnabled && !mHandler.hasMessages(MESSAGE_REFRESH_FILTERS)) {
            mHandler.sendEmptyMessageDelayed(
                    MESSAGE_REFRESH_FILTERS, FILTER_SPINNER_REFRESH_DELAY_MILLIS);
        }
    }

    protected void setContactListFilter(int filterId) {
        ContactListFilter filter;
        if (filterId == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
        } else if (filterId == ContactListFilter.FILTER_TYPE_CUSTOM) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM);
        } else {
            filter = mFilters.get(filterId);
            if (filter == null) {
                filter = getDefaultFilter();
            }
        }

        if (!filter.equals(mFilter)) {
            mFilter = filter;
            ContactListFilter.storeToPreferences(mPrefs, mFilter);
            updateFilterView();
            reloadData();
        }
    }

    @Override
    public void onSetSelection(NotifyingSpinner spinner, int position) {
        ContactListFilter filter = mFilters.valueAt(position);
        if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            startActivityForResult(new Intent(getContext(), CustomContactListFilterActivity.class),
                    REQUEST_CODE_CUSTOMIZE_FILTER);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CUSTOMIZE_FILTER && resultCode == Activity.RESULT_OK) {
            mFilter = new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM);
            updateFilterView();
            reloadData();
        }
    }

    private ContactListFilter getDefaultFilter() {
        return mFilters.valueAt(0);
    }

    protected void updateFilterView() {
        if (mFiltersLoaded) {
            if (mFilters.size() == 0) {
                mFilterSpinner.setVisibility(View.GONE);
                return;
            }

            mFilterSpinner.setSetSelectionListener(null);
            if (mFilter != null && mFilters != null) {
                int size = mFilters.size();
                for (int i = 0; i < size; i++) {
                    if (mFilters.valueAt(i).equals(mFilter)) {
                        mFilterSpinner.setSelection(i);
                        break;
                    }
                }
            }
            mFilterSpinner.setSetSelectionListener(this);
            mFilterSpinner.setVisibility(View.VISIBLE);
        }
    }

    private class FilterSpinnerAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        public FilterSpinnerAdapter() {
            mLayoutInflater = LayoutInflater.from(getContext());
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return mFilters.keyAt(position);
        }

        @Override
        public Object getItem(int position) {
            return mFilters.valueAt(position);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, true);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, false);
        }

        public View getView(int position, View convertView, ViewGroup parent, boolean dropdown) {
            FilterSpinnerItemView view;
            if (dropdown) {
                if (convertView != null) {
                    view = (FilterSpinnerItemView) convertView;
                } else {
                    view = (FilterSpinnerItemView) mLayoutInflater.inflate(
                            R.layout.filter_spinner_item, parent, false);
                }
            } else {
                view = (FilterSpinnerItemView) mLayoutInflater.inflate(
                        R.layout.filter_spinner, parent, false);
            }
            view.setContactListFilter(mFilters.valueAt(position));
            view.bindView(dropdown);
            return view;
        }
    }
}
