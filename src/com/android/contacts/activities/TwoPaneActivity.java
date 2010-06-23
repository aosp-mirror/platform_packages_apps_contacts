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
import com.android.contacts.views.editor.ContactEditorFragment;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnFilterTextListener;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

public class TwoPaneActivity extends Activity {
    private final static String TAG = "TwoPaneActivity";

    private DefaultContactBrowseListFragment mListFragment;
    private ListFragmentListener mListFragmentListener = new ListFragmentListener();

    private ContactDetailFragment mDetailFragment;
    private DetailFragmentListener mDetailFragmentListener = new DetailFragmentListener();

    private ContactEditorFragment mEditorFragment;
    private EditorFragmentListener mEditorFragmentListener = new EditorFragmentListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.two_pane_activity);

        mListFragment = (DefaultContactBrowseListFragment) findFragmentById(R.id.two_pane_list);
        mListFragment.setOnContactListActionListener(mListFragmentListener);

        setupContactDetailFragment();

        setupSearchUI();
    }

    private void setupContactDetailFragment() {
        // No editor here
        if (mEditorFragment != null) {
            mEditorFragment.setListener(null);
            mEditorFragment = null;
        }

        // Already showing? Nothing to do
        if (mDetailFragment != null) return;

        mDetailFragment = new ContactDetailFragment();
        mDetailFragment.setListener(mDetailFragmentListener);

        // Nothing showing yet? Create (this happens during Activity-Startup)
        openFragmentTransaction()
                .replace(R.id.two_pane_right_view, mDetailFragment)
                .commit();

    }

    private void setupContactEditorFragment() {
        // No detail view here
        if (mDetailFragment != null) {
            mDetailFragment.setListener(null);
            mDetailFragment = null;
        }

        // Already showing? Nothing to do
        if (mEditorFragment != null) return;

        mEditorFragment = new ContactEditorFragment();
        mEditorFragment.setListener(mEditorFragmentListener);

        // Nothing showing yet? Create (this happens during Activity-Startup)
        openFragmentTransaction()
                .replace(R.id.two_pane_right_view, mEditorFragment)
                .commit();

    }

    private void setupSearchUI() {
        SearchEditText searchEditText = (SearchEditText)findViewById(R.id.search_src_text);
        searchEditText.setOnFilterTextListener(new OnFilterTextListener() {
            public void onFilterChange(String queryString) {
                mListFragment.setSearchMode(!TextUtils.isEmpty(queryString));
                mListFragment.setQueryString(queryString);
            }

            public void onCancelSearch() {
            }
        });
    }

    private class ListFragmentListener implements OnContactBrowserActionListener {
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
            setupContactDetailFragment();
            mDetailFragment.loadUri(contactLookupUri);
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        public void onContactNotFound() {
            Toast.makeText(TwoPaneActivity.this, "onContactNotFound", Toast.LENGTH_LONG).show();
        }

        public void onEditRequested(Uri contactLookupUri) {
            setupContactEditorFragment();
            mEditorFragment.loadUri(contactLookupUri);
        }

        public void onItemClicked(Intent intent) {
            startActivity(intent);
        }

        public void onDialogRequested(int id, Bundle bundle) {
            showDialog(id, bundle);
        }
    }

    private class EditorFragmentListener implements ContactEditorFragment.Listener {
        public void onContactNotFound() {
            Toast.makeText(TwoPaneActivity.this, "onContactNotFound", Toast.LENGTH_LONG).show();
        }

        public void onDialogRequested(int id, Bundle bundle) {
            Toast.makeText(TwoPaneActivity.this, "onDialogRequested", Toast.LENGTH_LONG).show();
        }

        public void onEditorRequested(Intent intent) {
            Toast.makeText(TwoPaneActivity.this, "onEditorRequested", Toast.LENGTH_LONG).show();
        }

        public void onError() {
            Toast.makeText(TwoPaneActivity.this, "onError", Toast.LENGTH_LONG).show();
        }
    }
}
