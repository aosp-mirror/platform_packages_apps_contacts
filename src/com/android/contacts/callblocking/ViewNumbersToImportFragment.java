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

package com.android.contacts.callblocking;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.callblocking.FilteredNumbersUtil.ImportSendToVoicemailContactsListener;

public class ViewNumbersToImportFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
        View.OnClickListener {

    private ViewNumbersToImportAdapter mAdapter;

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mAdapter == null) {
            mAdapter = ViewNumbersToImportAdapter.newViewNumbersToImportAdapter(
                    getContext(), getActivity().getFragmentManager());
        }
        setListAdapter(mAdapter);
    }

    @Override
    public void onDestroy() {
        setListAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(R.string.import_send_to_voicemail_numbers_label);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        getActivity().findViewById(R.id.cancel_button).setOnClickListener(this);
        getActivity().findViewById(R.id.import_button).setOnClickListener(this);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.view_numbers_to_import_fragment, container, false);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final CursorLoader cursorLoader = new CursorLoader(
                getContext(),
                Phone.CONTENT_URI,
                FilteredNumbersUtil.PhoneQuery.PROJECTION,
                FilteredNumbersUtil.PhoneQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                null,
                null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.import_button:
                FilteredNumbersUtil.importSendToVoicemailContacts(getContext(),
                        new ImportSendToVoicemailContactsListener() {
                            @Override
                            public void onImportComplete() {
                                if (getActivity() != null) {
                                    getActivity().onBackPressed();
                                }
                            }
                        });
                break;
            case R.id.cancel_button:
                getActivity().onBackPressed();
                break;
        }
    }
}
