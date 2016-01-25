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
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.contacts.R;
import com.android.contacts.activities.BlockedNumbersActivity;
import com.android.contacts.callblocking.FilteredNumbersUtil.CheckForSendToVoicemailContactListener;
import com.android.contacts.callblocking.FilteredNumbersUtil.ImportSendToVoicemailContactsListener;
import com.android.contacts.common.lettertiles.LetterTileDrawable;

/**
 * This class is copied from Dialer, but we don't check whether visual voicemail is enabled here.
  */
public class BlockedNumbersFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private static final char ADD_BLOCKED_NUMBER_ICON_LETTER = '+';

    private BlockedNumbersAdapter mAdapter;

    private View mImportSettings;
    private View mBlockedNumbersDisabledForEmergency;
    private View mBlockedNumberListDivider;
    private View mHeaderTextView;

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LayoutInflater inflater =
                (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        getListView().addHeaderView(inflater.inflate(R.layout.blocked_number_header, null));
        //replace the icon for add number with LetterTileDrawable(), so it will have identical style
        ImageView addNumberIcon = (ImageView) getActivity().findViewById(R.id.add_number_icon);
        LetterTileDrawable drawable = new LetterTileDrawable(getResources());
        drawable.setLetter(ADD_BLOCKED_NUMBER_ICON_LETTER);
        drawable.setColor(ActivityCompat.getColor(getActivity(),
                R.color.add_blocked_number_icon_color));
        drawable.setIsCircular(true);
        addNumberIcon.setImageDrawable(drawable);

        if (mAdapter == null) {
            mAdapter = BlockedNumbersAdapter.newBlockedNumbersAdapter(
                    getContext(), getActivity().getFragmentManager());
        }
        setListAdapter(mAdapter);

        mHeaderTextView = getListView().findViewById(R.id.header_textview);
        mImportSettings = getListView().findViewById(R.id.import_settings);
        mBlockedNumbersDisabledForEmergency =
                getListView().findViewById(R.id.blocked_numbers_disabled_for_emergency);
        mBlockedNumberListDivider = getActivity().findViewById(R.id.blocked_number_list_divider);
        getListView().findViewById(R.id.import_button).setOnClickListener(this);
        getListView().findViewById(R.id.view_numbers_button).setOnClickListener(this);
        getListView().findViewById(R.id.add_number_linear_layout).setOnClickListener(this);
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
        ColorDrawable backgroundDrawable = new ColorDrawable(
                ActivityCompat.getColor(getActivity(), R.color.primary_color));
        actionBar.setBackgroundDrawable(backgroundDrawable);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.blocked_numbers_title);

        FilteredNumbersUtil.checkForSendToVoicemailContact(
            getActivity(), new CheckForSendToVoicemailContactListener() {
                @Override
                public void onComplete(boolean hasSendToVoicemailContact) {
                    mImportSettings.setVisibility(
                            hasSendToVoicemailContact ? View.VISIBLE : View.GONE);
                    mHeaderTextView.setVisibility(
                            mImportSettings.getVisibility() == View.GONE ?
                                    View.VISIBLE : View.GONE);
                }
            });

        // Visibility of mBlockedNumbersDisabledForEmergency will always be GONE for now, until
        // we could check recent emergency call from framework.
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.blocked_number_fragment, container, false);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final String[] projection = {
            BlockedNumbers.COLUMN_ID,
            BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            BlockedNumbers.COLUMN_E164_NUMBER
        };
        final CursorLoader cursorLoader = new CursorLoader(
                getContext(), BlockedNumbers.CONTENT_URI, projection, null, null, null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        if (data.getCount() == 0) {
            mBlockedNumberListDivider.setVisibility(View.INVISIBLE);
        } else {
            mBlockedNumberListDivider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onClick(View view) {
        BlockedNumbersActivity activity = (BlockedNumbersActivity) getActivity();
        if (activity == null) {
            return;
        }

        switch (view.getId()) {
            case R.id.add_number_linear_layout:
                activity.showSearchUi();
                break;
            case R.id.view_numbers_button:
                activity.showNumbersToImportPreviewUi();
                break;
            case R.id.import_button:
                FilteredNumbersUtil.importSendToVoicemailContacts(activity,
                        new ImportSendToVoicemailContactsListener() {
                            @Override
                            public void onImportComplete() {
                                mImportSettings.setVisibility(View.GONE);
                                mHeaderTextView.setVisibility(View.VISIBLE);
                            }
                        });
                break;
        }
    }
}