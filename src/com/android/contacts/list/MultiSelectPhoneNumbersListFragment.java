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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.logging.ListEvent;

/** Displays a list of phone numbers with check boxes. */
public class MultiSelectPhoneNumbersListFragment
        extends MultiSelectContactsListFragment<MultiSelectPhoneNumbersListAdapter> {

    public MultiSelectPhoneNumbersListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setSearchMode(false);
        setHasOptionsMenu(false);
        setListType(ListEvent.ListType.PICK_PHONE);
    }

    @Override
    public MultiSelectPhoneNumbersListAdapter createListAdapter() {
        final MultiSelectPhoneNumbersListAdapter adapter =
                new MultiSelectPhoneNumbersListAdapter(getActivity());
        adapter.setArguments(getArguments());
        return adapter;
    }

    @Override
    public void onStart() {
        super.onStart();
        displayCheckBoxes(true);
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
