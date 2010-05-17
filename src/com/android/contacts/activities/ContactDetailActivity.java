/*
 * Copyright (C) 2010 Google Inc.
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
import com.android.contacts.views.detail.ContactDetailFragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class ContactDetailActivity extends Activity {
    private static final String TAG = "ContactDetailActivity";

    private ContactDetailFragment mFragment;

    private final FragmentCallbackHandler mCallbackHandler = new FragmentCallbackHandler();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_detail_activity);

        Log.i(TAG, getIntent().getData().toString());

        final View view = findViewById(R.id.contact_detail_fragment);
        mFragment = (ContactDetailFragment) findFragmentById(R.id.contact_detail_fragment);
        mFragment.setCallbacks(mCallbackHandler);
        mFragment.loadUri(getIntent().getData());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO: This is too hardwired.
        if (mFragment.onCreateOptionsMenu(menu, getMenuInflater())) return true;

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO: This is too hardwired.
        if (mFragment.onPrepareOptionsMenu(menu)) return true;

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO: This is too hardwired.
        if (mFragment.onOptionsItemSelected(item)) return true;

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        // ask the Fragment whether it knows about the dialog
        final Dialog fragmentResult = mFragment.onCreateDialog(id, args);
        if (fragmentResult != null) return fragmentResult;

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // TODO: This is too hardwired.
        if (mFragment.onContextItemSelected(item)) return true;

        return super.onContextItemSelected(item);
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
        // TODO: This is too hardwired.
        if (mFragment.onKeyDown(keyCode, event)) return true;

        return super.onKeyDown(keyCode, event);
    }

    private class FragmentCallbackHandler implements ContactDetailFragment.Callbacks {
        public void closeBecauseContactNotFound() {
            finish();
        }

        public void editContact(Uri rawContactUri) {
            startActivity(new Intent(Intent.ACTION_EDIT, rawContactUri));
        }

        public void itemClicked(Intent intent) {
            startActivity(intent);
        }
    }
}
