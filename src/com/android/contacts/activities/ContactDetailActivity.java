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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.views.ContactSaveService;
import com.android.contacts.views.detail.ContactDetailFragment;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayList;

public class ContactDetailActivity extends Activity {
    private static final String TAG = "ContactDetailActivity";

    private ContactDetailFragment mFragment;
    private ContactDeletionInteraction mContactDeletionInteraction;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_detail_activity);

        mFragment = (ContactDetailFragment) getFragmentManager().findFragmentById(
                R.id.contact_detail_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.loadUri(getIntent().getData());

        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        final Dialog deletionDialog = getContactDeletionInteraction().onCreateDialog(id, args);
        if (deletionDialog != null) return deletionDialog;

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (getContactDeletionInteraction().onPrepareDialog(id, dialog, args)) {
            return;
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mFragment.handleKeyDown(keyCode)) return true;

        return super.onKeyDown(keyCode, event);
    }

    private ContactDeletionInteraction getContactDeletionInteraction() {
        if (mContactDeletionInteraction == null) {
            mContactDeletionInteraction = new ContactDeletionInteraction();
            mContactDeletionInteraction.attachToActivity(this);
        }
        return mContactDeletionInteraction;
    }

    private final ContactDetailFragment.Listener mFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            startActivity(new Intent(Intent.ACTION_EDIT, contactLookupUri));
        }

        @Override
        public void onItemClicked(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onDeleteRequested(Uri lookupUri) {
            getContactDeletionInteraction().deleteContact(lookupUri);
        }

        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, Account account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };
}
