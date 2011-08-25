/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.contacts.activities;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DirectoryListLoader;
import com.android.contacts.list.EmailAddressPickerFragment;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.list.OnEmailAddressPickerActionListener;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.OnPostalAddressPickerActionListener;
import com.android.contacts.list.PhoneNumberPickerFragment;
import com.android.contacts.list.PostalAddressPickerFragment;
import com.android.contacts.widget.ContextMenuAdapter;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

/**
 * Displays a list of contacts (or phone numbers or postal addresses) for the
 * purposes of selecting one.
 */
public class ContactSelectionActivity extends ContactsActivity
        implements View.OnCreateContextMenuListener, OnQueryTextListener, OnClickListener {
    private static final String TAG = "ContactSelectionActivity";

    private static final String KEY_ACTION_CODE = "actionCode";
    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    // Delay to allow the UI to settle before making search view visible
    private static final int FOCUS_DELAY = 200;

    private ContactsIntentResolver mIntentResolver;
    protected ContactEntryListFragment<?> mListFragment;

    private int mActionCode = -1;

    private ContactsRequest mRequest;
    private SearchView mSearchView;

    public ContactSelectionActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactEntryListFragment<?>) {
            mListFragment = (ContactEntryListFragment<?>) fragment;
            setupActionListener();
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mActionCode = savedState.getInt(KEY_ACTION_CODE);
        }

        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            finish();
            return;
        }

        configureActivityTitle();

        setContentView(R.layout.contact_picker);

        configureListFragment();

        mSearchView = (SearchView)findViewById(R.id.search_view);
        mSearchView.setQueryHint(getString(R.string.hint_findContacts));
        mSearchView.setOnQueryTextListener(this);

        // TODO: re-enable search for postal addresses
        if (mRequest.getActionCode() == ContactsRequest.ACTION_PICK_POSTAL) {
            mSearchView.setVisibility(View.GONE);
        } else {
            // This is a hack to prevent the search view from grabbing focus
            // at this point.  If search view were visible, it would always grabs focus
            // because it is the first focusable widget in the window.
            mSearchView.setVisibility(View.INVISIBLE);
            mSearchView.postDelayed(new Runnable() {

                @Override
                public void run() {
                    mSearchView.setVisibility(View.VISIBLE);
                }
            }, FOCUS_DELAY);
        }

        Button cancel = (Button) findViewById(R.id.cancel);
        cancel.setOnClickListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTION_CODE, mActionCode);
    }

    private void configureActivityTitle() {
        if (mRequest.getActivityTitle() != null) {
            setTitle(mRequest.getActivityTitle());
            return;
        }

        int actionCode = mRequest.getActionCode();
        switch (actionCode) {
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_PICK_CONTACT: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                setTitle(R.string.shortcutActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_PICK_PHONE: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_PICK_EMAIL: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                setTitle(R.string.callShortcutActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                setTitle(R.string.messageShortcutActivityTitle);
                break;
            }

            case ContactsRequest.ACTION_PICK_POSTAL: {
                setTitle(R.string.contactPickerActivityTitle);
                break;
            }
        }
    }

    /**
     * Creates the fragment based on the current request.
     */
    public void configureListFragment() {
        if (mActionCode == mRequest.getActionCode()) {
            return;
        }

        mActionCode = mRequest.getActionCode();
        switch (mActionCode) {
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setCreateContactEnabled(true);
                fragment.setEditMode(true);
                fragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setIncludeProfile(mRequest.shouldIncludeProfile());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setCreateContactEnabled(!mRequest.isSearchMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString(), false);
                fragment.setShortcutRequested(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_PHONE: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_EMAIL: {
                mListFragment = new EmailAddressPickerFragment();
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setShortcutAction(Intent.ACTION_CALL);
                fragment.setSearchMode(mRequest.isSearchMode());

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setShortcutAction(Intent.ACTION_SENDTO);

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_POSTAL: {
                PostalAddressPickerFragment fragment = new PostalAddressPickerFragment();
                mListFragment = fragment;
                break;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + mActionCode);
        }

        mListFragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
        mListFragment.setContactsRequest(mRequest);
        mListFragment.setSearchMode(mRequest.isSearchMode());
        mListFragment.setQueryString(mRequest.getQueryString(), false);
        mListFragment.setDirectoryResultLimit(DEFAULT_DIRECTORY_RESULT_LIMIT);

        getFragmentManager().beginTransaction()
                .replace(R.id.list_container, mListFragment)
                .commitAllowingStateLoss();
    }

    public void setupActionListener() {
        if (mListFragment instanceof ContactPickerFragment) {
            ((ContactPickerFragment) mListFragment).setOnContactPickerActionListener(
                    new ContactPickerActionListener());
        } else if (mListFragment instanceof PhoneNumberPickerFragment) {
            ((PhoneNumberPickerFragment) mListFragment).setOnPhoneNumberPickerActionListener(
                    new PhoneNumberPickerActionListener());
        } else if (mListFragment instanceof PostalAddressPickerFragment) {
            ((PostalAddressPickerFragment) mListFragment).setOnPostalAddressPickerActionListener(
                    new PostalAddressPickerActionListener());
        } else if (mListFragment instanceof EmailAddressPickerFragment) {
            ((EmailAddressPickerFragment) mListFragment).setOnEmailAddressPickerActionListener(
                    new EmailAddressPickerActionListener());
        } else {
            throw new IllegalStateException("Unsupported list fragment type: " + mListFragment);
        }
    }

    private final class ContactPickerActionListener implements OnContactPickerActionListener {
        @Override
        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            startActivityAndForwardResult(intent);
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            startActivityAndForwardResult(intent);
        }

        @Override
        public void onPickContactAction(Uri contactUri) {
            returnPickerResult(contactUri);
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {
            returnPickerResult(intent);
        }
    }

    private final class PhoneNumberPickerActionListener implements
            OnPhoneNumberPickerActionListener {
        @Override
        public void onPickPhoneNumberAction(Uri dataUri) {
            returnPickerResult(dataUri);
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {
            returnPickerResult(intent);
        }

        public void onHomeInActionBarSelected() {
            ContactSelectionActivity.this.onBackPressed();
        }
    }

    private final class PostalAddressPickerActionListener implements
            OnPostalAddressPickerActionListener {
        @Override
        public void onPickPostalAddressAction(Uri dataUri) {
            returnPickerResult(dataUri);
        }
    }

    private final class EmailAddressPickerActionListener implements
            OnEmailAddressPickerActionListener {
        @Override
        public void onPickEmailAddressAction(Uri dataUri) {
            returnPickerResult(dataUri);
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuAdapter menuAdapter = mListFragment.getContextMenuAdapter();
        if (menuAdapter != null) {
            return menuAdapter.onContextItemSelected(item);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mListFragment.setQueryString(newText, true);
        mListFragment.setSearchMode(!TextUtils.isEmpty(newText));
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    public void returnPickerResult(Uri data) {
        Intent intent = new Intent();
        intent.setData(data);
        returnPickerResult(intent);
    }

    public void returnPickerResult(Intent intent) {
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.cancel) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
