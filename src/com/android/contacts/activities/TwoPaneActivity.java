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

import com.android.contacts.R;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.views.detail.ContactDetailFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class TwoPaneActivity extends Activity {
    private final static String TAG = "TwoPaneActivity";
    private DefaultContactBrowseListFragment mListFragment;
    private ContactDetailFragment mDetailFragment;
    private DetailCallbackHandler mDetailCallbackHandler = new DetailCallbackHandler();
    private ListCallbackHandler mListCallbackHandler = new ListCallbackHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.two_pane_activity);

//        mListFragment = (DefaultContactBrowseListFragment) findFragmentById(R.id.two_pane_list);
        mListFragment = DefaultContactBrowseListFragment.sLastFragment;
        mListFragment.setOnContactListActionListener(mListCallbackHandler);

//        mDetailFragment = (ContactDetailFragment) findFragmentById(R.id.two_pane_detail);
        mDetailFragment = ContactDetailFragment.sLastInstance;
        mDetailFragment.setCallbacks(mDetailCallbackHandler);
    }

    private class ListCallbackHandler implements OnContactBrowserActionListener {
        public void onAddToFavoritesAction(Uri contactUri) {
            Toast.makeText(TwoPaneActivity.this, "onAddToFavoritesAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onCallContactAction(Uri contactUri) {
            Toast.makeText(TwoPaneActivity.this, "onCallContactAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onCreateNewContactAction() {
            Toast.makeText(TwoPaneActivity.this, "onCreateNewContactAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onDeleteContactAction(Uri contactUri) {
            Toast.makeText(TwoPaneActivity.this, "onDeleteContactAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onEditContactAction(Uri contactLookupUri) {
            Toast.makeText(TwoPaneActivity.this, "onEditContactAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onFinishAction() {
            Toast.makeText(TwoPaneActivity.this, "onFinishAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onRemoveFromFavoritesAction(Uri contactUri) {
            Toast.makeText(TwoPaneActivity.this, "onRemoveFromFavoritesAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onSearchAllContactsAction(String string) {
            Toast.makeText(TwoPaneActivity.this, "onSearchAllContactsAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onSmsContactAction(Uri contactUri) {
            Toast.makeText(TwoPaneActivity.this, "onSmsContactAction",
                    Toast.LENGTH_LONG).show();
        }

        public void onViewContactAction(Uri contactLookupUri) {
            mDetailFragment.loadUri(contactLookupUri);
        }
    }

    private class DetailCallbackHandler implements ContactDetailFragment.Callbacks {
        public void closeBecauseContactNotFound() {
            Toast.makeText(TwoPaneActivity.this, "closeBecauseContactNotFound",
                    Toast.LENGTH_LONG).show();
        }

        public void editContact(Uri rawContactUri) {
            Toast.makeText(TwoPaneActivity.this, "editContact",
                    Toast.LENGTH_LONG).show();
        }

        public void itemClicked(Intent intent) {
            startActivity(intent);
        }
    }
}
