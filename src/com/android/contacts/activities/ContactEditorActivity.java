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
import com.android.contacts.views.editor.ContactEditorFragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class ContactEditorActivity extends Activity {
    private static final String TAG = "ContactEditorActivity";

    private ContactEditorFragment mFragment;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_editor_activity);

        mFragment = (ContactEditorFragment) findFragmentById(R.id.contact_editor_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.load(getIntent().getAction(), getIntent().getData(),
                getIntent().resolveType(getContentResolver()), getIntent().getExtras());

        Log.i(TAG, getIntent().getData().toString());
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
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    private final ContactEditorFragment.Listener mFragmentListener =
            new ContactEditorFragment.Listener() {
        @Override
        public void closeAfterDelete() {
            Toast.makeText(ContactEditorActivity.this, "closeAfterDelete",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterRevert() {
            Toast.makeText(ContactEditorActivity.this, "closeAfterRevert",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterSaving(int resultCode, Intent resultIntent) {
            Toast.makeText(ContactEditorActivity.this, "closeAfterSaving",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterSplit() {
            Toast.makeText(ContactEditorActivity.this, "closeAfterSplit", Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeBecauseAccountSelectorAborted() {
            Toast.makeText(ContactEditorActivity.this, "closeBecauseAccountSelectorAborted",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeBecauseContactNotFound() {
            Toast.makeText(ContactEditorActivity.this, "closeBecauseContactNotFound",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void setTitleTo(int resourceId) {
            Toast.makeText(ContactEditorActivity.this, "setTitleTo", Toast.LENGTH_LONG).show();
        }
    };
}
