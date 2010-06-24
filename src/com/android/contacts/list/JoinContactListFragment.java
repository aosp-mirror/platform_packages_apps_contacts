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

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;

import android.app.Activity;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Fragment for the Join Contact list.
 */
public class JoinContactListFragment extends ContactEntryListFragment<JoinContactListAdapter> {

    private static final int DISPLAY_NAME_LOADER = -2;

    private OnContactPickerActionListener mListener;
    private long mTargetContactId;
    private boolean mAllContactsListShown = false;

    public JoinContactListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setAizyEnabled(false);
    }

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    @Override
    protected void onInitializeLoaders() {
        super.onInitializeLoaders();
        startLoading(DISPLAY_NAME_LOADER, null);
    }

    @Override
    protected Loader<Cursor> startLoading(int id, Bundle args) {

        // The first two partitions don't require loaders
        if (id == JoinContactListAdapter.PARTITION_SUGGESTIONS ||
                id == JoinContactListAdapter.PARTITION_SHOW_ALL_CONTACTS) {
            return null;
        }

        return super.startLoading(id, args);
    }

    @Override
    protected Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == DISPLAY_NAME_LOADER) {
            // Loader for the display name of the target contact
            return new CursorLoader(getActivity(),
                    ContentUris.withAppendedId(Contacts.CONTENT_URI, mTargetContactId),
                    new String[] {Contacts.DISPLAY_NAME}, null, null, null);
        } else if (id == JoinContactListAdapter.PARTITION_ALL_CONTACTS) {
            JoinContactLoader loader = new JoinContactLoader(getActivity());
            JoinContactListAdapter adapter = getAdapter();
            if (adapter != null) {
                adapter.configureLoader(loader, 0);
            }
            return loader;
        } else {
            throw new IllegalArgumentException("No loader for ID=" + id);
        }
    }

    @Override
    protected void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == DISPLAY_NAME_LOADER) {
            if (data != null && data.moveToFirst()) {
                showTargetContactName(data.getString(0));
            }
        } else {
            JoinContactListAdapter adapter = getAdapter();
            Cursor suggestionsCursor = ((JoinContactLoader)loader).getSuggestionsCursor();
            adapter.setSuggestionsCursor(suggestionsCursor);
            super.onLoadFinished(loader, data);
        }
    }

     private void showTargetContactName(String displayName) {
        Activity activity = getActivity();
        TextView blurbView = (TextView)activity.findViewById(R.id.join_contact_blurb);
        String blurb = activity.getString(R.string.blurbJoinContactDataWith, displayName);
        blurbView.setText(blurb);
    }

    public void setTargetContactId(long targetContactId) {
        mTargetContactId = targetContactId;
    }

    @Override
    public JoinContactListAdapter createListAdapter() {
        return new JoinContactListAdapter(getActivity());
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        JoinContactListAdapter adapter = getAdapter();
        adapter.setAllContactsListShown(mAllContactsListShown);
        adapter.setTargetContactId(mTargetContactId);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content_join, null);
    }

    @Override
    protected void onItemClick(int position, long id) {
        JoinContactListAdapter adapter = getAdapter();
        int partition = adapter.getPartitionForPosition(position);
        if (partition == JoinContactListAdapter.PARTITION_SHOW_ALL_CONTACTS) {
            mAllContactsListShown = true;
            reloadData();
        } else {
            mListener.onPickContactAction(adapter.getContactUri(position));
        }
    }

    @Override
    protected void reloadData() {
        setAizyEnabled(mAllContactsListShown);
        super.reloadData();
    }

    @Override
    public void startSearch(String initialQuery) {
        ContactsRequest request = new ContactsRequest();
        request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
        request.setDirectorySearchEnabled(false);
        ContactsSearchManager.startSearchForResult(getActivity(), initialQuery,
                ACTIVITY_REQUEST_CODE_PICKER, request);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickContactAction(data.getData());
    }
}
