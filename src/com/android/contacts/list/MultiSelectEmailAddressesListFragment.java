/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.common.logging.ListEvent;

import java.util.TreeSet;

/** Displays a list of emails with check boxes. */
public class MultiSelectEmailAddressesListFragment
        extends MultiSelectContactsListFragment<MultiSelectEmailAddressesListAdapter>{

    public MultiSelectEmailAddressesListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setSearchMode(false);
        setHasOptionsMenu(true);
        setListType(ListEvent.ListType.PICK_EMAIL);
    }

    @Override
    public MultiSelectEmailAddressesListAdapter createListAdapter() {
        final MultiSelectEmailAddressesListAdapter adapter =
                new MultiSelectEmailAddressesListAdapter(getActivity());
        adapter.setArguments(getArguments());
        return adapter;
    }

    @Override
    public void onSelectedContactsChangedViaCheckBox() {
        onSelectedContactsChanged();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.items_multi_select, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem item = menu.findItem(R.id.menu_send);
        item.setVisible(getAdapter().hasSelectedItems());
        item.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(item);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_send: {
                final String scheme = getActivity().getIntent().getStringExtra(
                        UiIntentActions.SELECTION_SEND_SCHEME);
                final String title= getActivity().getIntent().getStringExtra(
                        UiIntentActions.SELECTION_SEND_TITLE);
                final Intent intent = new Intent();
                intent.putExtra(UiIntentActions.TARGET_CONTACT_IDS_EXTRA_KEY,
                        getAdapter().getSelectedContactIdsArray());
                intent.putExtra(UiIntentActions.SELECTION_SEND_SCHEME, scheme);
                intent.putExtra(UiIntentActions.SELECTION_SEND_TITLE, title);
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final long[] selectedIds = getActivity().getIntent().getLongArrayExtra(
                UiIntentActions.SELECTION_DEFAULT_SELECTION);
        if (selectedIds != null && selectedIds.length != 0) {
            final TreeSet<Long> selectedIdsTree = new TreeSet<>();
            for (int i = 0; i < selectedIds.length; i++) {
                selectedIdsTree.add(selectedIds[i]);
            }
            getAdapter().setSelectedContactIds(selectedIdsTree);
            onSelectedContactsChanged();
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        displayCheckBoxes(true);

        final long[] itemIds = getActivity().getIntent().getLongArrayExtra(
                UiIntentActions.SELECTION_ITEM_LIST);
        final boolean[] selectedFlags = getActivity().getIntent().getBooleanArrayExtra(
                UiIntentActions.SELECTION_DEFAULT_SELECTION);
        if (itemIds != null && selectedFlags != null && itemIds.length == selectedFlags.length) {
            TreeSet<Long> selectedIds = new TreeSet<>();
            for (int i = 0; i < itemIds.length; i++) {
                if (selectedFlags[i]) {
                    selectedIds.add(itemIds[i]);
                }
            }
            getAdapter().setSelectedContactIds(selectedIds);
            onSelectedContactsChanged();
        }
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        return true;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(com.android.contacts.common.R.layout.contact_list_content, null);
    }
}
