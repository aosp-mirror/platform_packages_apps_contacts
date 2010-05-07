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

package com.android.contacts;

import com.android.contacts.list.MultiplePhoneExtraAdapter;
import com.android.contacts.list.MultiplePhonePickerAdapter;
import com.android.contacts.list.MultiplePhonePickerItemView;
import com.android.contacts.list.MultiplePhoneSelection;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Displays of phone numbers and allows selection of multiple numbers.
 */
public class MultiplePhonePickerActivity extends ContactsListActivity implements OnClickListener {
    /**
     * User selected phone number and id in MODE_PICK_MULTIPLE_PHONES mode.
     */
    public final MultiplePhoneSelection mUserSelection = new MultiplePhoneSelection(this);

    /**
     * The adapter for the phone numbers, used in MODE_PICK_MULTIPLE_PHONES mode.
     */
    public final MultiplePhoneExtraAdapter mPhoneNumberAdapter =
            new MultiplePhoneExtraAdapter(this, this, mUserSelection);

    private static int[] CHIP_COLOR_ARRAY = {
        R.drawable.appointment_indicator_leftside_1,
        R.drawable.appointment_indicator_leftside_2,
        R.drawable.appointment_indicator_leftside_3,
        R.drawable.appointment_indicator_leftside_4,
        R.drawable.appointment_indicator_leftside_5,
        R.drawable.appointment_indicator_leftside_6,
        R.drawable.appointment_indicator_leftside_7,
        R.drawable.appointment_indicator_leftside_8,
        R.drawable.appointment_indicator_leftside_9,
        R.drawable.appointment_indicator_leftside_10,
        R.drawable.appointment_indicator_leftside_11,
        R.drawable.appointment_indicator_leftside_12,
        R.drawable.appointment_indicator_leftside_13,
        R.drawable.appointment_indicator_leftside_14,
        R.drawable.appointment_indicator_leftside_15,
        R.drawable.appointment_indicator_leftside_16,
        R.drawable.appointment_indicator_leftside_17,
        R.drawable.appointment_indicator_leftside_18,
        R.drawable.appointment_indicator_leftside_19,
        R.drawable.appointment_indicator_leftside_20,
        R.drawable.appointment_indicator_leftside_21,
    };

    /**
     * This is the map from contact to color index.
     * A colored chip in MODE_PICK_MULTIPLE_PHONES mode is used to indicate the number of phone
     * numbers belong to one contact
     */
    SparseIntArray mContactColor = new SparseIntArray();

    /**
     * UI control of action panel in MODE_PICK_MULTIPLE_PHONES mode.
     */
    private View mFooterView;

    /**
     * Display only selected recipients or not in MODE_PICK_MULTIPLE_PHONES mode
     */
    public boolean mShowSelectedOnly = false;

    public OnClickListener mCheckBoxClickerListener = new OnClickListener () {
        public void onClick(View v) {
            final MultiplePhonePickerItemView itemView =
                    (MultiplePhonePickerItemView) v.getParent();
            if (itemView.phoneId != MultiplePhoneExtraAdapter.INVALID_PHONE_ID) {
                mUserSelection.setPhoneSelected(itemView.phoneId, ((CheckBox) v).isChecked());
            } else {
                mUserSelection.setPhoneSelected(itemView.phoneNumber,
                        ((CheckBox) v).isChecked());
            }
            updateWidgets(true);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        initMultiPicker(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO move this to onAttach of the corresponding fragment
        ListView listView = (ListView)findViewById(android.R.id.list);
        ((MultiplePhonePickerAdapter)listView.getAdapter()).setExtraAdapter(mPhoneNumberAdapter);
        ViewStub stub = (ViewStub)findViewById(R.id.footer_stub);
        if (stub != null) {
            View stubView = stub.inflate();
            mFooterView = stubView.findViewById(R.id.footer);
            mFooterView.setVisibility(View.GONE);
            Button doneButton = (Button) stubView.findViewById(R.id.done);
            doneButton.setOnClickListener(this);
            Button revertButton = (Button) stubView.findViewById(R.id.revert);
            revertButton.setOnClickListener(this);
        }
    }

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.done:
                setMultiPickerResult();
                finish();
                break;
            case R.id.revert:
                finish();
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        ListView listView = (ListView)findViewById(android.R.id.list);
        if (listView != null) {
            if (mUserSelection != null) {
                mUserSelection.saveInstanceState(icicle);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mUserSelection.restoreInstanceState(icicle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pick, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mShowSelectedOnly) {
            menu.findItem(R.id.menu_display_selected).setVisible(false);
            menu.findItem(R.id.menu_display_all).setVisible(true);
            menu.findItem(R.id.menu_select_all).setVisible(false);
            menu.findItem(R.id.menu_select_none).setVisible(false);
            return true;
        }
        menu.findItem(R.id.menu_display_all).setVisible(false);
        menu.findItem(R.id.menu_display_selected).setVisible(true);
        if (mUserSelection.isAllSelected()) {
            menu.findItem(R.id.menu_select_all).setVisible(false);
            menu.findItem(R.id.menu_select_none).setVisible(true);
        } else {
            menu.findItem(R.id.menu_select_all).setVisible(true);
            menu.findItem(R.id.menu_select_none).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_select_all: {
                mUserSelection.setAllPhonesSelected(true);
                checkAll(true);
                updateWidgets(true);
                return true;
            }
            case R.id.menu_select_none: {
                mUserSelection.setAllPhonesSelected(false);
                checkAll(false);
                updateWidgets(true);
                return true;
            }
            case R.id.menu_display_selected: {
                mShowSelectedOnly = true;
                startQuery();
                return true;
            }
            case R.id.menu_display_all: {
                mShowSelectedOnly = false;
                startQuery();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        // TODO
//        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return;
//        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
                if ((mMode & MODE_MASK_PICKER) != 0) {
                    Bundle extras = getIntent().getExtras();
                    if (extras == null) {
                        extras = new Bundle();
                    }
                    mUserSelection.fillSelectionForSearchMode(extras);
                    ContactsSearchManager.startSearchForResult(this, initialQuery,
                            SUBACTIVITY_FILTER, extras);
                } else {
                    ContactsSearchManager.startSearch(this, initialQuery);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        setMultiPickerResult();
        super.onBackPressed();
    }

    @Override
    protected void startQuery(Uri uri, String[] projection) {
        // Filter unknown phone numbers first.
        mPhoneNumberAdapter.doFilter(null, mShowSelectedOnly);
        if (mShowSelectedOnly) {
            StringBuilder idSetBuilder = new StringBuilder();
            Iterator<Long> itr = mUserSelection.getSelectedPhonIds();
            if (itr.hasNext()) {
                idSetBuilder.append(Long.toString(itr.next()));
            }
            while (itr.hasNext()) {
                idSetBuilder.append(',');
                idSetBuilder.append(Long.toString(itr.next()));
            }
            String whereClause = Phone._ID + " IN (" + idSetBuilder.toString() + ")";
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, whereClause, null,
                    getSortOrder(projection));
        } else {
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                    projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
        }
    }

    @Override
    public Cursor doFilter(String filter) {
        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(mListFragment.getQueryString())) {
            return new MatrixCursor(projection);
        }

        final ContentResolver resolver = getContentResolver();
        // Filter phone numbers as well.
        mPhoneNumberAdapter.doFilter(filter, mShowSelectedOnly);

        Uri uri = getUriToQuery();
        if (!TextUtils.isEmpty(filter)) {
            uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
        }
        return resolver.query(uri, projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
    }

    private void initMultiPicker(final Intent intent) {
        final Handler handler = new Handler();
        // TODO : Shall we still show the progressDialog in search mode.
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getText(R.string.adding_recipients));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        final Runnable showProgress = new Runnable() {
            public void run() {
                progressDialog.show();
            }
        };
        handler.postDelayed(showProgress, 1);

        new Thread(new Runnable() {
            public void run() {
                try {
                    loadSelectionFromIntent(intent);
                } finally {
                    handler.removeCallbacks(showProgress);
                    progressDialog.dismiss();
                }
                final Runnable populateWorker = new Runnable() {
                    public void run() {
                        if (mAdapter != null) {
                            mAdapter.notifyDataSetChanged();
                        }
                        updateWidgets(false);
                    }
                };
                handler.post(populateWorker);
            }
        }).start();
    }

    private void getPhoneNumbersOrIdsFromURIs(final Parcelable[] uris,
            final List<String> phoneNumbers, final List<Long> phoneIds) {
        if (uris != null) {
            for (Parcelable paracelable : uris) {
                Uri uri = (Uri) paracelable;
                if (uri == null) continue;
                String scheme = uri.getScheme();
                if (phoneNumbers != null && "tel".equals(scheme)) {
                    phoneNumbers.add(uri.getSchemeSpecificPart());
                } else if (phoneIds != null && "content".equals(scheme)) {
                    phoneIds.add(ContentUris.parseId(uri));
                }
            }
        }
    }

    private void loadSelectionFromIntent(Intent intent) {
        Parcelable[] uris = intent.getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);
        ArrayList<String> phoneNumbers = new ArrayList<String>();
        ArrayList<Long> phoneIds = new ArrayList<Long>();
        ArrayList<String> selectedPhoneNumbers = null;
        if (mSearchMode) {
            // All selection will be read from EXTRA_SELECTION
            getPhoneNumbersOrIdsFromURIs(uris, phoneNumbers, null);
            uris = intent.getParcelableArrayExtra(MultiplePhoneSelection.EXTRA_SELECTION);
            if (uris != null) {
                selectedPhoneNumbers = new ArrayList<String>();
                getPhoneNumbersOrIdsFromURIs(uris, selectedPhoneNumbers, phoneIds);
            }
        } else {
            getPhoneNumbersOrIdsFromURIs(uris, phoneNumbers, phoneIds);
            selectedPhoneNumbers = phoneNumbers;
        }
        mPhoneNumberAdapter.setPhoneNumbers(phoneNumbers);
        mUserSelection.setSelection(selectedPhoneNumbers, phoneIds);
    }

    private void setMultiPickerResult() {
        setResult(RESULT_OK, mUserSelection.createSelectionIntent());
    }

    /**
     * Go through the cursor and assign the chip color to contact who has more than one phone
     * numbers.
     * Assume the cursor is sorted by CONTACT_ID.
     */
    public void updateChipColor(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return;
        }
        mContactColor.clear();
        int backupPos = cursor.getPosition();
        cursor.moveToFirst();
        int color = 0;
        long prevContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
        while (cursor.moveToNext()) {
            long contactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
            if (prevContactId == contactId) {
                if (mContactColor.indexOfKey(Long.valueOf(contactId).hashCode()) < 0) {
                    mContactColor.put(Long.valueOf(contactId).hashCode(), CHIP_COLOR_ARRAY[color]);
                    color++;
                    if (color >= CHIP_COLOR_ARRAY.length) {
                        color = 0;
                    }
                }
            }
            prevContactId = contactId;
        }
        cursor.moveToPosition(backupPos);
    }

    /**
     * Get assigned chip color resource id for a given contact, 0 is returned if there is no mapped
     * resource.
     */
    public int getChipColor(long contactId) {
        return mContactColor.get(Long.valueOf(contactId).hashCode());
    }

    private void updateWidgets(boolean changed) {
        int selected = mUserSelection.selectedCount();

        if (selected >= 1) {
            final String format =
                getResources().getQuantityString(R.plurals.multiple_picker_title, selected);
            setTitle(String.format(format, selected));
        } else {
            setTitle(getString(R.string.contactsList));
        }

        if (changed && mFooterView.getVisibility() == View.GONE) {
            mFooterView.setVisibility(View.VISIBLE);
            mFooterView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.footer_appear));
        }
    }

    private void checkAll(boolean checked) {
        // TODO fix this. It should iterate over the cursor rather than the views in the list.
        /*
        final ListView listView = getListView();
        int childCount = listView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final ContactListItemView child = (ContactListItemView)listView.getChildAt(i);
            child.getCheckBoxView().setChecked(checked);
        }
        */
    }
}
