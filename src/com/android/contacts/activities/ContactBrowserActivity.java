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
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DirectoryListLoader;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.model.AccountTypes;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.DialogManager;
import com.android.contacts.views.ContactSaveService;
import com.android.contacts.views.detail.ContactDetailFragment;
import com.android.contacts.views.detail.ContactNoneFragment;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Displays a list to browse contacts. For xlarge screens, this also displays a detail-pane on
 * the right
 */
public class ContactBrowserActivity extends Activity
        implements View.OnCreateContextMenuListener, ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity {

    private static final String TAG = "ContactBrowserActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_SETTINGS = 3;
    private static final int SUBACTIVITY_EDIT_CONTACT = 4;

    private static final String KEY_DEFAULT_CONTACT_URI = "defaultSelectedContactUri";

    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    private static final String KEY_SEARCH_MODE = "searchMode";

    private DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private SharedPreferences mPrefs;

    private boolean mHasActionBar;
    private ActionBarAdapter mActionBarAdapter;

    private boolean mSearchMode;

    private ContactBrowseListFragment mListFragment;
    private ContactNoneFragment mEmptyFragment;

    private boolean mContactContentDisplayed;
    private ContactDetailFragment mDetailFragment;
    private DetailFragmentListener mDetailFragmentListener = new DetailFragmentListener();

    private PhoneNumberInteraction mPhoneNumberCallInteraction;
    private PhoneNumberInteraction mSendTextMessageInteraction;
    private ContactDeletionInteraction mContactDeletionInteraction;
    private ImportExportInteraction mImportExportInteraction;

    private boolean mSearchInitiated;

    private ContactListFilterController mContactListFilterController;

    private ImageView mAddContactImageView;

    public ContactBrowserActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
        mContactListFilterController = new ContactListFilterController(this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactBrowseListFragment) {
            mListFragment = (ContactBrowseListFragment)fragment;
            mListFragment.setOnContactListActionListener(new ContactBrowserActionListener());
            if (mListFragment instanceof DefaultContactBrowseListFragment) {
                ((DefaultContactBrowseListFragment) mListFragment).setContactListFilterController(
                        mContactListFilterController);
            }
        } else if (fragment instanceof ContactNoneFragment) {
            mEmptyFragment = (ContactNoneFragment)fragment;
        } else if (fragment instanceof ContactDetailFragment) {
            mDetailFragment = (ContactDetailFragment)fragment;
            mDetailFragment.setListener(mDetailFragmentListener);
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE);
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

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setTitle(mRequest.getActivityTitle());
        setContentView(R.layout.contact_browser);

        mHasActionBar = getWindow().hasFeature(Window.FEATURE_ACTION_BAR);
        mContactContentDisplayed = findViewById(R.id.detail_container) != null;

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT) {
            if (!mContactContentDisplayed) {
                startActivity(new Intent(Intent.ACTION_VIEW, mRequest.getContactUri()));
                finish();
                return;
            }
        }

        if (mHasActionBar) {
            mActionBarAdapter = new ActionBarAdapter(this);
            mActionBarAdapter.onCreate(savedState, mRequest, getActionBar());
            mActionBarAdapter.setListener(this);
            mActionBarAdapter.setContactListFilterController(mContactListFilterController);
            // TODO: request may ask for FREQUENT - set the filter accordingly
            mAddContactImageView = new ImageView(this);
            mAddContactImageView.setImageResource(R.drawable.ic_menu_add_contact);
            mAddContactImageView.setContentDescription(getString(R.string.menu_newContact));
            mAddContactImageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNewContact();
                }
            });
        }

        configureListFragment(true /* from request */);

        if (mContactContentDisplayed) {
            setupContactDetailFragment(mListFragment.getSelectedContactUri());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null) {
                return;
            }

            if (mHasActionBar) {
                if (mActionBarAdapter.isSearchMode()) {
                    mActionBarAdapter.setSavedStateForSearchMode(null);
                    mActionBarAdapter.setSearchMode(false);
                }
            }
            setSelectedContactUri(uri);
            setupContactDetailFragment(uri);
            mListFragment.requestSelectionOnScreen(true);
        }
    }

    public void setSelectedContactUri(Uri contactLookupUri) {
        mListFragment.setSelectedContactUri(contactLookupUri);

        Editor editor = mPrefs.edit();
        if (contactLookupUri == null) {
            editor.remove(KEY_DEFAULT_CONTACT_URI);
        } else {
            editor.putString(KEY_DEFAULT_CONTACT_URI, contactLookupUri.toString());
        }
        editor.apply();
    }

    public Uri getDefaultSelectedContactUri() {
        String uriString = mPrefs.getString(KEY_DEFAULT_CONTACT_URI, null);
        return TextUtils.isEmpty(uriString) ? null : Uri.parse(uriString);
    }

    private void configureListFragment(boolean fromRequest) {
        boolean searchMode = mSearchMode;
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                    break;
                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                    filter = new ContactListFilter(
                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                    break;

                // TODO: handle FREQUENT and STREQUENT according to the spec
                case ContactsRequest.ACTION_FREQUENT:
                case ContactsRequest.ACTION_STREQUENT:
                    // For now they are treated the same as STARRED
                case ContactsRequest.ACTION_STARRED:
                    filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_STARRED);
                    break;
            }

            if (filter != null) {
                mContactListFilterController.setContactListFilter(filter, false);
                searchMode = false;
            } else if (mRequest.getActionCode() == ContactsRequest.ACTION_ALL_CONTACTS) {
                mContactListFilterController.setContactListFilter(new ContactListFilter(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS), false);
            }
        } else {
            if (mHasActionBar) {
                searchMode = mActionBarAdapter.isSearchMode();
            }
        }

        boolean replaceList = mListFragment == null || (mSearchMode != searchMode);
        if (replaceList) {
            closeListFragment();
            mSearchMode = searchMode;
            if (mSearchMode) {
                mListFragment = createContactSearchFragment();
            } else {
                mListFragment = createListFragment(ContactsRequest.ACTION_DEFAULT);
            }
        }

        if (mHasActionBar) {
            if (mSearchMode) {
                Bundle savedState = mActionBarAdapter.getSavedStateForSearchMode();
                if (savedState != null) {
                    mListFragment.restoreSavedState(savedState);
                    mActionBarAdapter.setSavedStateForSearchMode(null);
                }

                mListFragment.setQueryString(mActionBarAdapter.getQueryString());
            } else {
                Bundle savedState = mActionBarAdapter.getSavedStateForDefaultMode();
                if (savedState != null) {
                    mListFragment.restoreSavedState(savedState);
                    mActionBarAdapter.setSavedStateForDefaultMode(null);
                }
            }
        }

        Uri selectUri = null;
        if (fromRequest) {
            selectUri = mRequest.getContactUri();
            if (selectUri != null) {
                setSelectedContactUri(selectUri);
            }
        }

        if (selectUri == null && mListFragment.getSelectedContactUri() == null) {
            selectUri = getDefaultSelectedContactUri();
        }

        if (selectUri != null) {
            mListFragment.setSelectedContactUri(selectUri);
            mListFragment.requestSelectionOnScreen(false);
        }

        if (replaceList) {
            getFragmentManager().openTransaction()
                    .replace(R.id.list_container, mListFragment)
                    .commit();
        }
    }

    private void closeListFragment() {
        if (mListFragment != null) {
            mListFragment.setOnContactListActionListener(null);

            if (mHasActionBar) {
                Bundle state = new Bundle();
                mListFragment.onSaveInstanceState(state);
                if (mSearchMode) {
                    mActionBarAdapter.setSavedStateForSearchMode(state);
                } else {
                    mActionBarAdapter.setSavedStateForDefaultMode(state);
                }
            }

            mListFragment = null;
        }
    }

    private void setupContactDetailFragment(Uri contactLookupUri) {
        if (mDetailFragment != null && contactLookupUri != null
                && contactLookupUri.equals(mDetailFragment.getUri())) {
            return;
        }

        if (contactLookupUri != null) {
            // Already showing? Nothing to do
            if (mDetailFragment != null) {
                mDetailFragment.loadUri(contactLookupUri);
                return;
            }

            closeEmptyFragment();

            mDetailFragment = new ContactDetailFragment();
            mDetailFragment.loadUri(contactLookupUri);

            // Nothing showing yet? Create (this happens during Activity-Startup)
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mDetailFragment)
                    .commit();
        } else {
            closeDetailFragment();

            mEmptyFragment = new ContactNoneFragment();
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mEmptyFragment)
                    .commit();
        }
    }

    private void closeDetailFragment() {
        if (mDetailFragment != null) {
            mDetailFragment.setListener(null);
            mDetailFragment = null;
        }
    }

    private void closeEmptyFragment() {
        mEmptyFragment = null;
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction() {
        configureListFragment(false /* from request */);
        setupContactDetailFragment(mListFragment.getSelectedContactUri());
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
                if (!mHasActionBar) {
                    fragment.setContextMenuAdapter(
                            new ContactBrowseListContextMenuAdapter(fragment));
                }
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setQueryString(mRequest.getQueryString());
                if (mRequest.isSearchMode() && mRequest.isDirectorySearchEnabled()) {
                    fragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
                } else {
                    fragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
                }
                fragment.setAizyEnabled(!mRequest.isSearchMode());
                fragment.setSelectionVisible(mContactContentDisplayed);
                fragment.setQuickContactEnabled(!mContactContentDisplayed);
                fragment.setFilterEnabled(!mRequest.isSearchMode());
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
                fragment.setSelectionVisible(mContactContentDisplayed);
                fragment.setQuickContactEnabled(!mContactContentDisplayed);
                return fragment;
            }

            case ContactsRequest.ACTION_FREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(false);
                fragment.setSelectionVisible(mContactContentDisplayed);
                fragment.setQuickContactEnabled(!mContactContentDisplayed);
                return fragment;
            }

            case ContactsRequest.ACTION_STREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(true);
                fragment.setSelectionVisible(mContactContentDisplayed);
                fragment.setQuickContactEnabled(!mContactContentDisplayed);
                return fragment;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + actionCode);
        }
    }

    private ContactBrowseListFragment createContactSearchFragment() {
        DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
        fragment.setOnContactListActionListener(new ContactBrowserActionListener());
        if (!mHasActionBar) {
            fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
        }
        fragment.setSearchMode(true);
        fragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        fragment.setDirectoryResultLimit(DEFAULT_DIRECTORY_RESULT_LIMIT);
        fragment.setAizyEnabled(false);
        fragment.setSelectionVisible(true);
        fragment.setQuickContactEnabled(!mContactContentDisplayed);
        return fragment;
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            if (mContactContentDisplayed) {
                setSelectedContactUri(contactLookupUri);
                setupContactDetailFragment(contactLookupUri);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
            }
        }

        @Override
        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivityForResult(intent, SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onAddToFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 1);
            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onRemoveFromFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onCallContactAction(Uri contactUri) {
            getPhoneNumberCallInteraction().startInteraction(contactUri);
        }

        @Override
        public void onSmsContactAction(Uri contactUri) {
            getSendTextMessageInteraction().startInteraction(contactUri);
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            getContactDeletionInteraction().deleteContact(contactUri);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        @Override
        public void onContactNotFound() {
            setupContactDetailFragment(null);
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            startActivityForResult(
                    new Intent(Intent.ACTION_EDIT, contactLookupUri), SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onItemClicked(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onDeleteRequested(Uri contactLookupUri) {
            getContactDeletionInteraction().deleteContact(contactLookupUri);
        }

        @Override
        public void onCreateRawContactRequested(ArrayList<ContentValues> values, Account account) {
            Toast.makeText(ContactBrowserActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactBrowserActivity.this, values, account);
            startService(serviceIntent);
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
        if (mHasActionBar) {
            inflater.inflate(R.menu.actions, menu);

            // Change add contact button to button with a custom view
            final MenuItem addContact = menu.findItem(R.id.menu_add);
            addContact.setActionView(mAddContactImageView);
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
            case R.id.menu_settings: {
                final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
                startActivityForResult(intent, SUBACTIVITY_SETTINGS);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
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

    private void createNewContact() {
        final ArrayList<Account> accounts =
                AccountTypes.getInstance(this).getAccounts(true);
        if (accounts.size() <= 1 || mAddContactImageView == null) {
            // No account to choose or no control to anchor the popup-menu to
            // ==> just go straight to the editor which will disambig if necessary
            final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
            return;
        }

        final ListPopupWindow popup = new ListPopupWindow(this, null);
        popup.setWidth(getResources().getDimensionPixelSize(R.dimen.account_selector_popup_width));
        popup.setAnchorView(mAddContactImageView);
        final AccountsListAdapter adapter = new AccountsListAdapter(this, true);
        popup.setAdapter(adapter);
        popup.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                popup.dismiss();
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(Intents.Insert.ACCOUNT, adapter.getItem(position));
                startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
            }
        });
        popup.setModal(true);
        popup.show();
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
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, bundle);

        Dialog dialog = getContactDeletionInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getPhoneNumberCallInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getSendTextMessageInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getImportExportInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

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
            case SUBACTIVITY_EDIT_CONTACT: {
                mListFragment.requestSelectionOnScreen(true);
                break;
            }

            case SUBACTIVITY_NEW_CONTACT: {
                if (resultCode == RESULT_OK && mContactContentDisplayed) {
                    final Uri newContactUri = data.getData();
                    setSelectedContactUri(newContactUri);
                    setupContactDetailFragment(newContactUri);
                }
                break;
            }

            case SUBACTIVITY_SETTINGS:
                // TODO: Force the ListFragment to reload its setting and update (don't lookup
                // directories here)
                break;

            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
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
            default: {
                // Bring up the search UI if the user starts typing
                final int unicodeChar = event.getUnicodeChar();

                if (unicodeChar != 0) {
                    String query = new String(new int[]{ unicodeChar }, 0, 1);
                    if (mHasActionBar) {
                        if (!mActionBarAdapter.isSearchMode()) {
                            mActionBarAdapter.setQueryString(query);
                            mActionBarAdapter.setSearchMode(true);
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
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode);
        if (mActionBarAdapter != null) {
            mActionBarAdapter.onSaveInstanceState(outState);
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

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
