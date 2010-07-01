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

import com.android.contacts.R;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.OnPostalAddressPickerActionListener;
import com.android.contacts.list.PhoneNumberPickerFragment;
import com.android.contacts.list.PostalAddressPickerFragment;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnFilterTextListener;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

/**
 * Displays a list of contacts (or phone numbers or postal addresses) for the
 * purposes of selecting one.
 */
public class ContactSelectionActivity extends Activity implements View.OnCreateContextMenuListener {

    private static final String TAG = "ContactSelectionActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;

    private ContactsIntentResolver mIntentResolver;
    protected ContactEntryListFragment<?> mListFragment;

    private int mActionCode;

    private boolean mSearchInitiated;

    private ContactsRequest mRequest;
    private SearchEditText mSearchEditText;

    public ContactSelectionActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

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

        setTitle(mRequest.getActivityTitle());

        onCreateFragment();

        int listFragmentContainerId;
        if (mRequest.isSearchMode()) {
            setContentView(R.layout.contacts_search_content);
            listFragmentContainerId = R.id.list_container;
            setupSearchUI();
        } else {
            listFragmentContainerId = android.R.id.content;
        }

        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(listFragmentContainerId, mListFragment);
        transaction.commit();
    }

    private void setupSearchUI() {
        mSearchEditText = (SearchEditText)findViewById(R.id.search_src_text);
        mSearchEditText.setText(mRequest.getQueryString());
        mSearchEditText.setOnFilterTextListener(new OnFilterTextListener() {
            public void onFilterChange(String queryString) {
                mListFragment.setQueryString(queryString);
            }

            public void onCancelSearch() {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRequest.isSearchMode()) {
            mSearchEditText.requestFocus();
        }
    }

    /**
     * Creates the fragment based on the current request.
     */
    private void onCreateFragment() {
        mActionCode = mRequest.getActionCode();
        switch (mActionCode) {
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setEditMode(true);
                fragment.setCreateContactEnabled(true);
                fragment.setDisplayWithPhonesOnlyOption(mRequest.getDisplayWithPhonesOnlyOption());
                fragment.setVisibleContactsRestrictionEnabled(mRequest.getDisplayOnlyVisible());
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString());
                fragment.setDirectorySearchEnabled(false);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setSearchMode(mRequest.isSearchMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setCreateContactEnabled(!mRequest.isSearchMode());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setCreateContactEnabled(!mRequest.isSearchMode());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString());
                fragment.setDirectorySearchEnabled(mRequest.isDirectorySearchEnabled());
                fragment.setShortcutRequested(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_PHONE: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setShortcutAction(Intent.ACTION_CALL);
                fragment.setSearchMode(mRequest.isSearchMode());

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setShortcutAction(Intent.ACTION_SENDTO);
                fragment.setSearchMode(mRequest.isSearchMode());

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_POSTAL: {
                PostalAddressPickerFragment fragment = new PostalAddressPickerFragment();
                fragment.setOnPostalAddressPickerActionListener(
                        new PostalAddressPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + mActionCode);
        }
        mListFragment.setContactsRequest(mRequest);
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        public void onViewContactAction(Uri contactLookupUri) {
            throw new UnsupportedOperationException();
        }

        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        public void onAddToFavoritesAction(Uri contactUri) {
            throw new UnsupportedOperationException();
        }

        public void onRemoveFromFavoritesAction(Uri contactUri) {
            throw new UnsupportedOperationException();
        }

        public void onCallContactAction(Uri contactUri) {
            throw new UnsupportedOperationException();
        }

        public void onSmsContactAction(Uri contactUri) {
            throw new UnsupportedOperationException();
        }

        public void onDeleteContactAction(Uri contactUri) {
            throw new UnsupportedOperationException();
        }

        public void onFinishAction() {
            onBackPressed();
        }
    }

    private final class ContactPickerActionListener implements OnContactPickerActionListener {
        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            startActivityAndForwardResult(intent);
        }

        public void onPickContactAction(Uri contactUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(contactUri));
            finish();
        }

        public void onShortcutIntentCreated(Intent intent) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private final class PhoneNumberPickerActionListener implements
            OnPhoneNumberPickerActionListener {
        public void onPickPhoneNumberAction(Uri dataUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(dataUri));
            finish();
        }

        public void onShortcutIntentCreated(Intent intent) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private final class PostalAddressPickerActionListener implements
            OnPostalAddressPickerActionListener {
        public void onPickPostalAddressAction(Uri dataUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(dataUri));
            finish();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        if (!mListFragment.isSearchMode()) {
            inflater.inflate(R.menu.search, menu);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
// TODO
//        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return;
//        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            mListFragment.startSearch(initialQuery);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
//            case SUBACTIVITY_NEW_CONTACT:
//                if (resultCode == RESULT_OK) {
//                    returnPickerResult(null, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
//                            data.getData());
//                    setRe
//                }
//                break;

//            case SUBACTIVITY_VIEW_CONTACT:
//                if (resultCode == RESULT_OK) {
//                    mAdapter.notifyDataSetChanged();
//                }
//                break;
//
//            case SUBACTIVITY_DISPLAY_GROUP:
//                // Mark as just created so we re-run the view query
////                mJustCreated = true;
//                break;
//
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mListFragment.onPickerResult(data);
                }

// TODO fix or remove multipicker code
//                else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
//                    // Finish the activity if the sub activity was canceled as back key is used
//                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
//                    finish();
//                }
//                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuAdapter menuAdapter = mListFragment.getContextMenuAdapter();
        if (menuAdapter != null) {
            return menuAdapter.onContextItemSelected(item);
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Event handler for the use case where the user starts typing without
     * bringing up the search UI first.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mSearchInitiated && !mRequest.isSearchMode()) {
            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar != 0) {
                mSearchInitiated = true;
                startSearch(new String(new int[]{unicodeChar}, 0, 1), false, null, false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment
        switch (keyCode) {
//            case KeyEvent.KEYCODE_CALL: {
//                if (callSelection()) {
//                    return true;
//                }
//                break;
//            }

            case KeyEvent.KEYCODE_DEL: {
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteSelection() {
        // TODO move to the fragment
//        if (mActionCode == ContactsRequest.ACTION_DEFAULT) {
//            final int position = mListView.getSelectedItemPosition();
//            if (position != ListView.INVALID_POSITION) {
//                Uri contactUri = getContactUri(position);
//                if (contactUri != null) {
//                    doContactDelete(contactUri);
//                    return true;
//                }
//            }
//        }
        return false;
    }
}
