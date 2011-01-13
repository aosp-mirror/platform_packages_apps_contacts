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
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {

    private View mCounterHeaderView;
    private View mSearchHeaderView;

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    protected void onItemClick(int position, long id) {
        viewContact(getAdapter().getContactUri(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        return adapter;
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
        getListView().addHeaderView(headerContainer, null, false);
        checkHeaderViewVisibility();
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
}
