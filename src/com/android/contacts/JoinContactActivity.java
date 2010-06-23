/*
 * Copyright (C) 2009 The Android Open Source Project
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


import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.JoinContactListFragment;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.ui.ContactsPreferencesActivity;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * An activity that shows a list of contacts that can be joined with the target contact.
 */
public class JoinContactActivity extends Activity {

    private static final String TAG = "JoinContactActivity";

    /**
     * The action for the join contact activity.
     * <p>
     * Input: extra field {@link #EXTRA_TARGET_CONTACT_ID} is the aggregate ID.
     * TODO: move to {@link ContactsContract}.
     */
    public static final String JOIN_CONTACT = "com.android.contacts.action.JOIN_CONTACT";

    /**
     * Used with {@link #JOIN_CONTACT} to give it the target for aggregation.
     * <p>
     * Type: LONG
     */
    public static final String EXTRA_TARGET_CONTACT_ID = "com.android.contacts.action.CONTACT_ID";

    private long mTargetContactId;

    private JoinContactListFragment mListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mTargetContactId = intent.getLongExtra(EXTRA_TARGET_CONTACT_ID, -1);
        if (mTargetContactId == -1) {
            Log.e(TAG, "Intent " + intent.getAction() + " is missing required extra: "
                    + EXTRA_TARGET_CONTACT_ID);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        mListFragment = new JoinContactListFragment();
        mListFragment.setTargetContactId(mTargetContactId);
        mListFragment.setOnContactPickerActionListener(new OnContactPickerActionListener() {
            public void onPickContactAction(Uri contactUri) {
                Intent intent = new Intent(null, contactUri);
                setResult(RESULT_OK, intent);
                finish();
            }

            public void onSearchAllContactsAction(String string) {
            }

            public void onShortcutIntentCreated(Intent intent) {
            }

            public void onCreateNewContactAction() {
            }
        });

        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(android.R.id.content, mListFragment);
        transaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            mListFragment.startSearch(initialQuery);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER
                && resultCode == RESULT_OK) {
            mListFragment.onPickerResult(data);
        }
    }
}
