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

import com.android.contacts.views.editor.ContactFieldEditorBaseFragment;
import com.android.contacts.views.editor.ContactFieldEditorEmailFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class ContactFieldEditorActivity extends Activity {
    public final static String BUNDLE_RAW_CONTACT_URI = "RawContactUri";

    private ContactFieldEditorBaseFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragment = new ContactFieldEditorEmailFragment();
        mFragment.setListener(mFragmentListener);

        openFragmentTransaction()
            .add(android.R.id.content, mFragment)
            .commit();

        final Intent intent = getIntent();
        final Uri rawContactUri = Uri.parse(intent.getStringExtra(BUNDLE_RAW_CONTACT_URI));
        final boolean isInsert;
        if (Intent.ACTION_EDIT.equals(intent.getAction())) {
            isInsert = false;
        } else if (Intent.ACTION_INSERT.equals(intent.getAction())) {
            isInsert = true;
        } else throw new IllegalArgumentException("Action is neither EDIT nor INSERT");

        if (isInsert) {
            mFragment.setupUris(rawContactUri, null);
        } else {
            mFragment.setupUris(rawContactUri, intent.getData());
        }
    }

    private ContactFieldEditorBaseFragment.Listener mFragmentListener =
            new ContactFieldEditorBaseFragment.Listener() {
        public void onCancel() {
            setResult(RESULT_CANCELED);
            finish();
        }

        public void onContactNotFound() {
            setResult(RESULT_CANCELED);
            finish();
        }

        public void onDataNotFound() {
            setResult(RESULT_CANCELED);
            finish();
        }

        public void onSaved() {
            setResult(RESULT_OK);
            finish();
        }
    };
}
