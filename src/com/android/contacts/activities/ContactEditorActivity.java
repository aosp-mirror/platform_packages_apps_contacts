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
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.DialogManager;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.util.ArrayList;

public class ContactEditorActivity extends Activity implements
        DialogManager.DialogShowingViewActivity {
    private static final String TAG = "ContactEditorActivity";

    public static final String ACTION_JOIN_COMPLETED = "joinCompleted";

    private ContactEditorFragment mFragment;
    private Button mDoneButton;
    private Button mRevertButton;

    private DialogManager mDialogManager = new DialogManager(this);

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        String action = getIntent().getAction();

        // The only situation where action could be ACTION_JOIN_COMPLETED is if the
        // user joined the contact with another and closed the activity before
        // the save operation was completed.  The activity should remain closed then.
        if (ACTION_JOIN_COMPLETED.equals(action)) {
            finish();
            return;
        }

        setContentView(R.layout.contact_editor_activity);

        // This Activity will always fall back to the "top" Contacts screen when touched on the
        // app up icon, regardless of launch context.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        mFragment = (ContactEditorFragment) getFragmentManager().findFragmentById(
                R.id.contact_editor_fragment);
        mFragment.setListener(mFragmentListener);
        Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());

        // Depending on the use-case, this activity has Done and Revert buttons or not.
        mDoneButton = (Button) findViewById(R.id.done);
        mRevertButton = (Button) findViewById(R.id.revert);
        if (mDoneButton != null) mDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.save(SaveMode.CLOSE);
            }
        });
        if (mRevertButton != null) mRevertButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mFragment == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            mFragment.setIntentExtras(intent.getExtras());
        } else if (ACTION_JOIN_COMPLETED.equals(action)) {
            mFragment.onJoinCompleted(intent.getData());
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

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

    @Override
    public void onBackPressed() {
        mFragment.save(SaveMode.CLOSE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                mFragment.save(SaveMode.HOME);
                return true;
            }
        }
        return false;
    }

    private final ContactEditorFragment.Listener mFragmentListener =
            new ContactEditorFragment.Listener() {
        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent, boolean navigateHome) {
            setResult(resultCode, resultIntent);
            if (navigateHome) {
                Intent intent = new Intent(ContactEditorActivity.this,
                        ContactBrowserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            finish();
        }

        @Override
        public void onContactSplit(Uri newLookupUri) {
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

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactEditorActivity.this, contactUri);
        }

        @Override
        public void onEditOtherContactRequested(
                Uri contactLookupUri, ArrayList<ContentValues> values) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY, "");

            // Pass on all the data that has been entered so far
            if (values != null && values.size() != 0) {
                intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, values);
            }

            startActivity(intent);
            finish();
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
