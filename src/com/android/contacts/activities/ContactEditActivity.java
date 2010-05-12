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
import com.android.contacts.util.DialogManager;
import com.android.contacts.views.edit.ContactEditFragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class ContactEditActivity extends Activity implements
        DialogManager.DialogShowingViewActivity {

    private static final String TAG = "ContactEditActivity";
    private static final int DIALOG_VIEW_DIALOGS_ID1 = 1;
    private static final int DIALOG_VIEW_DIALOGS_ID2 = 2;

    private final FragmentCallbackHandler mCallbackHandler = new FragmentCallbackHandler();
    private final DialogManager mDialogManager = new DialogManager(this, DIALOG_VIEW_DIALOGS_ID1,
            DIALOG_VIEW_DIALOGS_ID2);

    private ContactEditFragment mFragment;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_edit_activity);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Uri uri = intent.getData();
        final String mimeType = intent.resolveType(getContentResolver());
        final Bundle intentExtras = intent.getExtras();

        mFragment = ContactEditFragment.sLastInstance;
        mFragment.setCallbacks(mCallbackHandler);
        mFragment.load(action, uri, mimeType, intentExtras);
    }

    private class FragmentCallbackHandler implements ContactEditFragment.Callbacks {
        public void closeAfterRevert() {
            finish();
        }

        public void closeAfterDelete() {
            finish();
        }

        public void closeBecauseContactNotFound() {
            finish();
        }

        public void closeAfterSplit() {
            finish();
        }

        public void closeBecauseAccountSelectorAborted() {
            finish();
        }

        public void setTitleTo(int resourceId) {
            setTitle(resourceId);
        }

        public void closeAfterSaving(int resultCode, Intent resultIntent) {
            setResult(resultCode, resultIntent);
            finish();
        }
    }

    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        // If this is a dynamic dialog, use the DialogManager
        if (id == DIALOG_VIEW_DIALOGS_ID1 || id == DIALOG_VIEW_DIALOGS_ID2) {
            final Dialog dialog = mDialogManager.onCreateDialog(id, args);
            if (dialog != null) return dialog;
            return super.onCreateDialog(id, args);
        }

        // ask the Fragment whether it knows about the dialog
        final Dialog fragmentResult = mFragment.onCreateDialog(id, args);
        if (fragmentResult != null) return fragmentResult;

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO: This is too hardwired.
        mFragment.onActivityResult(requestCode, resultCode, data);
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
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
}
