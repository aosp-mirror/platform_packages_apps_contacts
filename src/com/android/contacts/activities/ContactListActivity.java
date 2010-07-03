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
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ImportExportInteraction;
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.views.detail.ContactDetailFragment;
import com.android.contacts.views.editor.ContactEditorFragment;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnFilterTextListener;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

/**
 * Displays a list of contacts.
 */
public class ContactListActivity extends Activity
        implements View.OnCreateContextMenuListener, NavigationBar.Listener {

    private static final String TAG = "ContactListActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;

    private ContactsIntentResolver mIntentResolver;
    private ContactBrowseListFragment mListFragment;

    private PhoneNumberInteraction mPhoneNumberCallInteraction;
    private PhoneNumberInteraction mSendTextMessageInteraction;
    private ContactDeletionInteraction mContactDeletionInteraction;
    private ImportExportInteraction mImportExportInteraction;

    private ContactDetailFragment mDetailFragment;
    private DetailFragmentListener mDetailFragmentListener = new DetailFragmentListener();

    private ContactEditorFragment mEditorFragment;
    private EditorFragmentListener mEditorFragmentListener = new EditorFragmentListener();

    private boolean mSearchInitiated;

    private ContactsRequest mRequest;
    private SearchEditText mSearchEditText;

    private boolean mTwoPaneLayout;
    private NavigationBar mNavigationBar;
    private int mMode = -1;

    public ContactListActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

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

        // The user launched the config based front door, pick the right activity to go to
        Configuration config = getResources().getConfiguration();
        int screenLayoutSize = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        mTwoPaneLayout = (screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_XLARGE);
        if (mTwoPaneLayout) {
            configureTwoPaneLayout(savedState);
        } else {
            configureSinglePaneLayout();
        }
    }

    private void configureSinglePaneLayout() {
        setTitle(mRequest.getActivityTitle());

        mListFragment = createListFragment(mRequest.getActionCode());

        int listFragmentContainerId;
        if (mRequest.isSearchMode()) {
            setContentView(R.layout.contacts_search_content);
            listFragmentContainerId = R.id.list_container;
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
        } else {
            listFragmentContainerId = android.R.id.content;
        }

        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(listFragmentContainerId, mListFragment);
        transaction.commit();
    }

    private void configureTwoPaneLayout(Bundle savedState) {

        // TODO: set the theme conditionally in AndroidManifest, once that feature is available
        setTheme(android.R.style.Theme_WithActionBar);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        setContentView(R.layout.two_pane_activity);

        mNavigationBar = new NavigationBar(this);
        mNavigationBar.onCreate(savedState, mRequest);

        ActionBar actionBar = getActionBar();
        View navBarView = mNavigationBar.onCreateView(getLayoutInflater());
        actionBar.setCustomNavigationMode(navBarView);

        configureListFragment();

        setupContactDetailFragment();

        mNavigationBar.setListener(this);
    }

    @Override
    public void onNavigationBarChange() {
        configureListFragment();
    }

    private void configureListFragment() {
        int mode = mNavigationBar.getMode();
        if (mode == NavigationBar.MODE_SEARCH
                && TextUtils.isEmpty(mNavigationBar.getQueryString())) {
            mode = mNavigationBar.getDefaultMode();
        }

        if (mode == mMode) {
            if (mode == NavigationBar.MODE_SEARCH) {
                mListFragment.setQueryString(mNavigationBar.getQueryString());
            }
            return;
        }

        if (mListFragment != null) {
            mListFragment.setOnContactListActionListener(null);
        }

        mMode = mode;
        switch (mMode) {
            case NavigationBar.MODE_CONTACTS: {
                mListFragment = createListFragment(ContactsRequest.ACTION_DEFAULT);
                break;
            }
            case NavigationBar.MODE_FAVORITES: {
                int favoritesAction = mRequest.getActionCode();
                if (favoritesAction == ContactsRequest.ACTION_DEFAULT) {
                    favoritesAction = ContactsRequest.ACTION_STREQUENT;
                }
                mListFragment = createListFragment(favoritesAction);
                break;
            }
            case NavigationBar.MODE_SEARCH: {
                mListFragment = createContactSearchFragment();
                mListFragment.setQueryString(mNavigationBar.getQueryString());
                break;
            }
        }

        openFragmentTransaction()
                .replace(R.id.two_pane_list, mListFragment)
                .commit();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (!mTwoPaneLayout && mRequest.isSearchMode()) {
            mSearchEditText.requestFocus();
        }
    }

    /**
     * Creates the list fragment for the specified mode.
     */
    private ContactBrowseListFragment createListFragment(int actionCode) {
        switch (actionCode) {
            case ContactsRequest.ACTION_DEFAULT: {
                DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
                fragment.setContactsRequest(mRequest);
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setDisplayWithPhonesOnlyOption(mRequest.getDisplayWithPhonesOnlyOption());
                fragment.setVisibleContactsRestrictionEnabled(mRequest.getDisplayOnlyVisible());
                fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString());
                fragment.setDirectorySearchEnabled(mRequest.isDirectorySearchEnabled());
                fragment.setAizyEnabled(!mRequest.isSearchMode());
                return fragment;
            }

            case ContactsRequest.ACTION_GROUP: {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            case ContactsRequest.ACTION_STARRED: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(false);
                fragment.setStarredContactsIncluded(true);
                return fragment;
            }

            case ContactsRequest.ACTION_FREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(false);
                return fragment;
            }

            case ContactsRequest.ACTION_STREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(true);
                return fragment;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + actionCode);
        }
    }

    private ContactBrowseListFragment createContactSearchFragment() {
        DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
        fragment.setOnContactListActionListener(new ContactBrowserActionListener());
        fragment.setDisplayWithPhonesOnlyOption(ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED);
        fragment.setVisibleContactsRestrictionEnabled(true);
        fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
        fragment.setSearchMode(true);
        fragment.setDirectorySearchEnabled(true);
        fragment.setAizyEnabled(false);
        return fragment;
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        public void onViewContactAction(Uri contactLookupUri) {
            if (mTwoPaneLayout) {
                setupContactDetailFragment();
                mDetailFragment.loadUri(contactLookupUri);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
            }
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
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 1);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onRemoveFromFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onCallContactAction(Uri contactUri) {
            getPhoneNumberCallInteraction().startInteraction(contactUri);
        }

        public void onSmsContactAction(Uri contactUri) {
            getSendTextMessageInteraction().startInteraction(contactUri);
        }

        public void onDeleteContactAction(Uri contactUri) {
            getContactDeletionInteraction().deleteContact(contactUri);
        }

        public void onFinishAction() {
            onBackPressed();
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        public void onContactNotFound() {
            Toast.makeText(ContactListActivity.this, "onContactNotFound", Toast.LENGTH_LONG).show();
        }

        public void onEditRequested(Uri contactLookupUri) {
            setupContactEditorFragment();
            mEditorFragment.load(Intent.ACTION_EDIT, contactLookupUri, Contacts.CONTENT_ITEM_TYPE,
                    new Bundle());
        }

        public void onItemClicked(Intent intent) {
            startActivity(intent);
        }

        public void onDialogRequested(int id, Bundle bundle) {
            showDialog(id, bundle);
        }
    }

    private class EditorFragmentListener implements ContactEditorFragment.Listener {
        @Override
        public void closeAfterDelete() {
            Toast.makeText(ContactListActivity.this, "closeAfterDelete", Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterRevert() {
            Toast.makeText(ContactListActivity.this, "closeAfterRevert", Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterSaving(int resultCode, Intent resultIntent) {
            Toast.makeText(ContactListActivity.this, "closeAfterSaving", Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeAfterSplit() {
            Toast.makeText(ContactListActivity.this, "closeAfterSplit", Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeBecauseAccountSelectorAborted() {
            Toast.makeText(ContactListActivity.this, "closeBecauseAccountSelectorAborted",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void closeBecauseContactNotFound() {
            Toast.makeText(ContactListActivity.this, "closeBecauseContactNotFound",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void setTitleTo(int resourceId) {
            Toast.makeText(ContactListActivity.this, "setTitleTo", Toast.LENGTH_LONG).show();
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
        if (mTwoPaneLayout) {
            inflater.inflate(R.menu.actions, menu);
            return true;
        } else if (mRequest.getActionCode() == ContactsRequest.ACTION_DEFAULT ||
                mRequest.getActionCode() == ContactsRequest.ACTION_STREQUENT) {
            inflater.inflate(R.menu.list, menu);
            return true;
        } else if (!mListFragment.isSearchMode()) {
            inflater.inflate(R.menu.search, menu);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem displayGroups = menu.findItem(R.id.menu_display_groups);
        if (displayGroups != null) {
            displayGroups.setVisible(
                    mRequest.getActionCode() == ContactsRequest.ACTION_DEFAULT);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_display_groups: {
                final Intent intent = new Intent(this, ContactsPreferencesActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                getImportExportInteraction().startInteraction();
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
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
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        Dialog dialog = getContactDeletionInteraction().onCreateDialog(id, bundle);
        if (dialog != null) {
            return dialog;
        }

        dialog = getPhoneNumberCallInteraction().onCreateDialog(id, bundle);
        if (dialog != null) {
            return dialog;
        }

        dialog = getSendTextMessageInteraction().onCreateDialog(id, bundle);
        if (dialog != null) {
            return dialog;
        }

        dialog = getImportExportInteraction().onCreateDialog(id, bundle);
        if (dialog != null) {
            return dialog;
        }

        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        if (getContactDeletionInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        if (getPhoneNumberCallInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        if (getSendTextMessageInteraction().onPrepareDialog(id, dialog, bundle)) {
            return;
        }

        super.onPrepareDialog(id, dialog, bundle);
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
        int unicodeChar = event.getUnicodeChar();
        if (unicodeChar != 0) {
            String query = new String(new int[]{unicodeChar}, 0, 1);
            if (mTwoPaneLayout) {
                if (mNavigationBar.getMode() != NavigationBar.MODE_SEARCH) {
                    mNavigationBar.setQueryString(query);
                    mNavigationBar.setMode(NavigationBar.MODE_SEARCH);
                    return true;
                }
            } else if (!mRequest.isSearchMode()) {
                if (!mSearchInitiated) {
                    mSearchInitiated = true;
                    startSearch(query, false, null, false);
                    return true;
                }
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNavigationBar != null) {
            mNavigationBar.onSaveInstanceState(outState);
        }
    }

    private PhoneNumberInteraction getPhoneNumberCallInteraction() {
        if (mPhoneNumberCallInteraction == null) {
            mPhoneNumberCallInteraction = new PhoneNumberInteraction(this, false, null);
        }
        return mPhoneNumberCallInteraction;
    }

    private PhoneNumberInteraction getSendTextMessageInteraction() {
        if (mSendTextMessageInteraction == null) {
            mSendTextMessageInteraction = new PhoneNumberInteraction(this, true, null);
        }
        return mSendTextMessageInteraction;
    }

    private ContactDeletionInteraction getContactDeletionInteraction() {
        if (mContactDeletionInteraction == null) {
            mContactDeletionInteraction = new ContactDeletionInteraction();
            mContactDeletionInteraction.attachToActivity(this);
        }
        return mContactDeletionInteraction;
    }

    private ImportExportInteraction getImportExportInteraction() {
        if (mImportExportInteraction == null) {
            mImportExportInteraction = new ImportExportInteraction(this);
        }
        return mImportExportInteraction;
    }
}
