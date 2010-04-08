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
import com.android.contacts.mvcframework.DialogManager;
import com.android.contacts.views.detail.ContactDetailView;
import com.android.contacts.views.detail.ContactLoader;
import com.android.contacts.views.detail.ContactLoader.Result;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class ContactDetailActivity extends Activity implements ContactLoader.Callbacks,
        DialogManager.DialogShowingViewActivity {
    private ContactDetailView mDetails;
    private ContactLoader mLoader;
    private Uri mUri;
    private DialogManager mDialogManager;

    private static final String TAG = "ContactDetailActivity";

    private static final int DIALOG_VIEW_DIALOGS_ID1 = 1;
    private static final int DIALOG_VIEW_DIALOGS_ID2 = 2;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_detail);

        mDialogManager = new DialogManager(this, DIALOG_VIEW_DIALOGS_ID1, DIALOG_VIEW_DIALOGS_ID2);

        mDetails = (ContactDetailView) findViewById(R.id.contact_details);
        mDetails.setCallbacks(new ContactDetailView.DefaultCallbacks(this));

        mUri = getIntent().getData();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mLoader == null) {
            // Look for a passed along loader and create a new one if it's not there
            mLoader = (ContactLoader) getLastNonConfigurationInstance();
            if (mLoader == null) {
                mLoader = new ContactLoader(this, mUri);
            }
        }
        mLoader.registerCallbacks(this);
        mLoader.startLoading();
    }

    @Override
    public void onStop() {
        super.onStop();

        // Let the loader know we're done with it
        mLoader.unregisterCallbacks(this);

        // The loader isn't getting passed along to the next instance so ask it to stop loading
        // TODO: Readd this once we have framework support
        /*if (!isChangingConfigurations()) {
            mLoader.stopLoading();
        }*/
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Pass the loader along to the next guy
        Object result = mLoader;
        mLoader = null;
        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mLoader != null) {
            mLoader.destroy();
        }
    }

    public void onContactLoaded(Result contact) {
        if (contact == ContactLoader.Result.NOT_FOUND) {
            // Item has been deleted
            Log.i(TAG, "No contact found. Closing activity");
            finish();
            return;
        }
        mDetails.setData(contact);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO: This is too hardwired.
        if (mDetails.onCreateOptionsMenu(menu, getMenuInflater())) return true;

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO: This is too hardwired.
        if (mDetails.onPrepareOptionsMenu(menu)) return true;

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO: This is too hardwired.
        if (mDetails.onOptionsItemSelected(item)) return true;

        return super.onOptionsItemSelected(item);
    }

    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        return mDialogManager.onCreateDialog(id, args);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // TODO: This is too hardwired.
        if (mDetails.onContextItemSelected(item)) return true;

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
        if (mDetails.onKeyDown(keyCode, event)) return true;

        return super.onKeyDown(keyCode, event);
    }
}
