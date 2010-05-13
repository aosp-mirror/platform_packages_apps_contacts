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

import com.android.contacts.list.MultiplePhonePickerFragment;
import com.android.contacts.list.OnMultiplePhoneNumberPickerActionListener;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract.Intents;
import android.view.Menu;
import android.view.MenuInflater;

/**
 * Displays of phone numbers and allows selection of multiple numbers.
 */
public class MultiplePhonePickerActivity extends Activity {

    /**
     * Display only selected recipients or not in MODE_PICK_MULTIPLE_PHONES mode
     */
    private boolean mShowSelectedOnly = false;

    private MultiplePhonePickerFragment mListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListFragment = new MultiplePhonePickerFragment();
        mListFragment.setOnMultiplePhoneNumberPickerActionListener(
                new OnMultiplePhoneNumberPickerActionListener() {

            public void onPhoneNumbersSelectedAction(Uri[] dataUris) {
                returnActivityResult(dataUris);
            }

            public void onFinishAction() {
                finish();
            }
        });

        Parcelable[] extras = getIntent().getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);
        mListFragment.setSelectedUris(extras);
        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(mListFragment, android.R.id.content);
        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        returnActivityResult(mListFragment.getSelectedUris());
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        mListFragment.onSaveInstanceState(icicle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pick, menu);
        return true;
    }

    /*
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
*/
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        // TODO
//        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return;
//        }
//
//        if (globalSearch) {
//            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
//        } else {
//            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
//                if ((mMode & MODE_MASK_PICKER) != 0) {
//                    Bundle extras = getIntent().getExtras();
//                    if (extras == null) {
//                        extras = new Bundle();
//                    }
//                    mUserSelection.fillSelectionForSearchMode(extras);
//                    ContactsSearchManager.startSearchForResult(this, initialQuery,
//                            SUBACTIVITY_FILTER, extras);
//                } else {
//                    ContactsSearchManager.startSearch(this, initialQuery);
//                }
//            }
//        }
    }

//    @Override
//    protected void startQuery(Uri uri, String[] projection) {
//        // Filter unknown phone numbers first.
//        mPhoneNumberAdapter.doFilter(null, mShowSelectedOnly);
//        if (mShowSelectedOnly) {
//            StringBuilder idSetBuilder = new StringBuilder();
//            Iterator<Long> itr = mUserSelection.getSelectedPhonIds();
//            if (itr.hasNext()) {
//                idSetBuilder.append(Long.toString(itr.next()));
//            }
//            while (itr.hasNext()) {
//                idSetBuilder.append(',');
//                idSetBuilder.append(Long.toString(itr.next()));
//            }
//            String whereClause = Phone._ID + " IN (" + idSetBuilder.toString() + ")";
//            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, whereClause, null,
//                    getSortOrder(projection));
//        } else {
//            mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
//                    projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
//        }
//    }

//    @Override
//    public Cursor doFilter(String filter) {
//        String[] projection = getProjectionForQuery();
//        if (mSearchMode && TextUtils.isEmpty(mListFragment.getQueryString())) {
//            return new MatrixCursor(projection);
//        }
//
//        final ContentResolver resolver = getContentResolver();
//        // Filter phone numbers as well.
//        mPhoneNumberAdapter.doFilter(filter, mShowSelectedOnly);
//
//        Uri uri = getUriToQuery();
//        if (!TextUtils.isEmpty(filter)) {
//            uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
//        }
//        return resolver.query(uri, projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
//    }

    public void returnActivityResult(Uri[] dataUris) {
        Intent intent = new Intent();
        intent.putExtra(Intents.EXTRA_PHONE_URIS, dataUris);
        setResult(RESULT_OK, intent);
        finish();
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
