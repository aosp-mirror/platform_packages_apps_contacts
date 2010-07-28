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
import com.android.contacts.util.DialogManager;
import com.android.contacts.views.editor.ContactEditorFragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class ContactEditorActivity extends Activity implements
        DialogManager.DialogShowingViewActivity {
    private static final String TAG = "ContactEditorActivity";

    private ContactEditorFragment mFragment;
    private Button mDoneButton;
    private Button mRevertButton;

    private DialogManager mDialogManager = new DialogManager(this);

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.contact_editor_activity);

        mFragment = (ContactEditorFragment) findFragmentById(R.id.contact_editor_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.load(getIntent().getAction(), getIntent().getData(),
                getIntent().resolveType(getContentResolver()), getIntent().getExtras());

        // Depending on the use-case, this activity has Done and Revert buttons or not.
        mDoneButton = (Button) findViewById(R.id.done);
        mRevertButton = (Button) findViewById(R.id.revert);
        if (mDoneButton != null) mDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.save();
            }
        });
        if (mRevertButton != null) mRevertButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

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
        public void onReverted() {
            finish();
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent) {
            setResult(resultCode, resultIntent);
            finish();
        }

        @Override
        public void onSplit() {
            finish();
        }

        @Override
        public void onAccountSelectorAborted() {
            finish();
        }

        @Override
        public void onContactNotFound() {
            setResult(Activity.RESULT_CANCELED, null);
            finish();
        }

        @Override
        public void setTitleTo(int resourceId) {
            setTitle(resourceId);
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
