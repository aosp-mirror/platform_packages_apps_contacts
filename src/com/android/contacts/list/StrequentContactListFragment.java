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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;

/**
 * Fragment containing a list of starred contacts followed by a list of frequently contacted.
 */
public class StrequentContactListFragment extends ContactBrowseListFragment
        implements OnClickListener {

    private static final int CALL_BUTTON_ID = android.R.id.button1;

    private boolean mStarredContactsIncluded = true;
    private boolean mFrequentlyContactedContactsIncluded = true;

    public StrequentContactListFragment() {
        setSectionHeaderDisplayEnabled(false);
        setPhotoLoaderEnabled(true);
    }

    public void setStarredContactsIncluded(boolean flag) {
        mStarredContactsIncluded = flag;
        configureAdapter();
    }

    public void setFrequentlyContactedContactsIncluded(boolean flag) {
        mFrequentlyContactedContactsIncluded = flag;
        configureAdapter();
    }

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        adapter.moveToPosition(position);
        viewContact(adapter.getContactUri(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        StrequentContactListAdapter adapter =
                new StrequentContactListAdapter(getActivity(), CALL_BUTTON_ID);
        adapter.setSectionHeaderDisplayEnabled(false);
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(true);
        adapter.setCallButtonListener(this);

        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        StrequentContactListAdapter adapter = (StrequentContactListAdapter)getAdapter();
        if (adapter != null) {
            adapter.setStarredContactsIncluded(mStarredContactsIncluded);
            adapter.setFrequentlyContactedContactsIncluded(mFrequentlyContactedContactsIncluded);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content, null);
    }

    @Override
    protected void prepareEmptyView() {
        setEmptyText(R.string.noFavoritesHelpText);
    }

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case CALL_BUTTON_ID: {
                final int position = (Integer)v.getTag();
                ContactListAdapter adapter = getAdapter();
                adapter.moveToPosition(position);
                callContact(adapter.getContactUri(position));
                break;
            }
        }
    }
}
