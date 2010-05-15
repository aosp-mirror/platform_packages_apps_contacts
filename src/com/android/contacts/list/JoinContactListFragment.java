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

import android.app.Activity;
import android.content.ContentUris;
import android.content.CursorLoader;
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

    private static final int DISPLAY_NAME_LOADER = 1;

    private OnContactPickerActionListener mListener;
    private long mTargetContactId;
    private boolean mAllContactsListShown = false;

    public JoinContactListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
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
    protected Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == DISPLAY_NAME_LOADER) {
            // Loader for the display name of the target contact
            return new CursorLoader(getActivity(),
                    ContentUris.withAppendedId(Contacts.CONTENT_URI, mTargetContactId),
                    new String[] {Contacts.DISPLAY_NAME}, null, null, null);
        } else {
            return new JoinContactLoader(getActivity());
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
            if (adapter.isAllContactsListShown()) {
                Cursor suggestionsCursor = ((JoinContactLoader)loader).getSuggestionsCursor();
                adapter.setSuggestionsCursor(suggestionsCursor);
                super.onLoadFinished(loader, data);
            } else {
                adapter.setSuggestionsCursor(data);
                super.onLoadFinished(loader, adapter.getShowAllContactsLabelCursor());
            }
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
        if (adapter.isShowAllContactsItemPosition(position)) {
            mAllContactsListShown = true;
            reloadData();
        } else {
            mListener.onPickContactAction(adapter.getContactUri(position));
        }
    }
}
