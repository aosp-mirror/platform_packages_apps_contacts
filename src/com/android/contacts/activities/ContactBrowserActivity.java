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

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.interactions.GroupRenamingDialogFragment;
import com.android.contacts.interactions.ImportExportInteraction;
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.CustomContactListFilterActivity;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DirectoryListLoader;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.list.ProviderStatusLoader;
import com.android.contacts.list.ProviderStatusLoader.ProviderStatusListener;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.model.AccountTypes;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.DialogManager;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.Settings;
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
import android.widget.ListPopupWindow;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Displays a list to browse contacts. For xlarge screens, this also displays a detail-pane on
 * the right
 */
public class ContactBrowserActivity extends Activity
        implements View.OnCreateContextMenuListener, ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener, ProviderStatusListener {

    private static final String TAG = "ContactBrowserActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_SETTINGS = 3;
    private static final int SUBACTIVITY_EDIT_CONTACT = 4;
    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 5;

    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    private static final String KEY_SEARCH_MODE = "searchMode";

    private DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private boolean mHasActionBar;
    private ActionBarAdapter mActionBarAdapter;

    private boolean mSearchMode;

    private ContactBrowseListFragment mListFragment;
    private boolean mContactContentDisplayed;
    private ContactDetailFragment mDetailFragment;
    private DetailFragmentListener mDetailFragmentListener = new DetailFragmentListener();

    private PhoneNumberInteraction mPhoneNumberCallInteraction;
    private PhoneNumberInteraction mSendTextMessageInteraction;
    private ImportExportInteraction mImportExportInteraction;

    private boolean mSearchInitiated;

    private ContactListFilterController mContactListFilterController;

    private View mAddContactImageView;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusLoader mProviderStatusLoader;
    private int mProviderStatus = -1;

    public ContactBrowserActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
        mContactListFilterController = new ContactListFilterController(this);
        mContactListFilterController.addListener(this);
        mProviderStatusLoader = new ProviderStatusLoader(this);
    }

    public boolean areContactsAvailable() {
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactBrowseListFragment) {
            mListFragment = (ContactBrowseListFragment)fragment;
            mListFragment.setOnContactListActionListener(new ContactBrowserActionListener());
        } else if (fragment instanceof ContactDetailFragment) {
            mDetailFragment = (ContactDetailFragment)fragment;
            mDetailFragment.setListener(mDetailFragmentListener);
        } else if (fragment instanceof ContactsUnavailableFragment) {
            mContactsUnavailableFragment = (ContactsUnavailableFragment)fragment;
            mContactsUnavailableFragment.setProviderStatusLoader(mProviderStatusLoader);
            mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
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

        setContentView(R.layout.contact_browser);
        mContactContentDisplayed = findViewById(R.id.detail_container) != null;

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT
                && !mContactContentDisplayed) {
            redirect = new Intent(this, ContactDetailActivity.class);
            redirect.setAction(Intent.ACTION_VIEW);
            redirect.setData(mRequest.getContactUri());
            startActivity(redirect);
            finish();
            return;
        }

        setTitle(mRequest.getActivityTitle());
        mHasActionBar = getWindow().hasFeature(Window.FEATURE_ACTION_BAR);
        if (mHasActionBar) {
            ActionBar actionBar = getActionBar();

            mActionBarAdapter = new ActionBarAdapter(this);
            mActionBarAdapter.onCreate(savedState, mRequest, actionBar);
            mActionBarAdapter.setContactListFilterController(mContactListFilterController);
            // TODO: request may ask for FREQUENT - set the filter accordingly

            mAddContactImageView = getLayoutInflater().inflate(
                    R.layout.add_contact_menu_item, null, false);
            View item = mAddContactImageView.findViewById(R.id.menu_item);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNewContact();
                }
            });
        }

        configureFragments(true /* from request */);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            mRequest = mIntentResolver.resolveIntent(intent);

            Uri uri = mRequest.getContactUri();
            if (uri == null) {
                return;
            }

            if (mHasActionBar) {
                mActionBarAdapter.setSearchMode(false);
                configureFragments(false /* from request */);
            }

            mListFragment.setSelectedContactUri(uri);
        }
    }

    @Override
    protected void onPause() {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        mProviderStatusLoader.setProviderStatusListener(null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(this);
        }
        mProviderStatusLoader.setProviderStatusListener(this);
        updateFragmentVisibility();
    }

    @Override
    protected void onStart() {
        mContactListFilterController.startLoading();
        super.onStart();
    }

    private void configureFragments(boolean fromRequest) {
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

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

        } else if (mHasActionBar) {
            searchMode = mActionBarAdapter.isSearchMode();
        }

        boolean replaceList = mListFragment == null || (mSearchMode != searchMode);
        if (replaceList) {
            if (mListFragment != null) {
                mListFragment.setOnContactListActionListener(null);
            }

            mSearchMode = searchMode;

            if (mSearchMode) {
                mListFragment = createContactSearchFragment();
            } else {
                mListFragment = createListFragment(ContactsRequest.ACTION_DEFAULT);
                if (mRequest.getContactUri() != null) {
                    mListFragment.setSelectedContactUri(mRequest.getContactUri());

                }
            }
        }

        if (mSearchMode && mHasActionBar) {
            mListFragment.setQueryString(mActionBarAdapter.getQueryString());
        }

        if (replaceList) {
            getFragmentManager().openTransaction()
                    .replace(R.id.list_container, mListFragment)
                    .commit();
        }

        if (mContactContentDisplayed && mDetailFragment == null) {
            mDetailFragment = new ContactDetailFragment();
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mDetailFragment)
                    .commit();
        }
    }

    @Override
    public void onContactListFiltersLoaded() {
        if (mListFragment == null) {
            return;
        }

        mListFragment.setFilter(mContactListFilterController.getFilter());

        // Filters have been loaded - now we can start loading the list itself
        mListFragment.startLoading();

        invalidateOptionsMenu();
    }

    @Override
    public void onContactListFilterChanged() {
        if (mListFragment == null) {
            return;
        }

        mListFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenu();
    }

    @Override
    public void onContactListFilterCustomizationRequest() {
        startActivityForResult(new Intent(this, CustomContactListFilterActivity.class),
                SUBACTIVITY_CUSTOMIZE_FILTER);
    }

    private void setupContactDetailFragment(final Uri contactLookupUri) {
        mDetailFragment.loadUri(contactLookupUri);
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction() {
        configureFragments(false /* from request */);
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
                fragment.setVisibleScrollbarEnabled(!mRequest.isSearchMode());
                fragment.setVerticalScrollbarPosition(
                        mContactContentDisplayed
                                ? View.SCROLLBAR_POSITION_LEFT
                                : View.SCROLLBAR_POSITION_RIGHT);
                fragment.setSelectionVisible(mContactContentDisplayed);
                fragment.setQuickContactEnabled(!mContactContentDisplayed);
                fragment.setFilterEnabled(!mRequest.isSearchMode());
                fragment.setPersistentSelectionEnabled(!mRequest.isSearchMode());
                if (mContactListFilterController.isLoaded()) {
                    fragment.setFilter(mContactListFilterController.getFilter());
                }
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
        fragment.setVisibleScrollbarEnabled(false);
        fragment.setVerticalScrollbarPosition(
                mContactContentDisplayed
                        ? View.SCROLLBAR_POSITION_LEFT
                        : View.SCROLLBAR_POSITION_RIGHT);
        fragment.setSelectionVisible(true);
        fragment.setQuickContactEnabled(!mContactContentDisplayed);
        invalidateOptionsMenu();
        return fragment;
    }

    @Override
    public void onProviderStatusChange() {
        updateFragmentVisibility();
    }

    private void updateFragmentVisibility() {
        int providerStatus = mProviderStatusLoader.getProviderStatus();
        if (providerStatus == mProviderStatus) {
            return;
        }

        mProviderStatus = providerStatus;

        View contactsUnavailableView = findViewById(R.id.contacts_unavailable_view);
        View mainView = findViewById(R.id.main_view);

        if (mProviderStatus == ProviderStatus.STATUS_NORMAL) {
            contactsUnavailableView.setVisibility(View.GONE);
            mainView.setVisibility(View.VISIBLE);
            if (mListFragment != null) {
                mListFragment.setEnabled(true);
            }
            if (mHasActionBar) {
                mActionBarAdapter.setEnabled(true);
            }
        } else {
            if (mHasActionBar) {
                mActionBarAdapter.setEnabled(false);
            }
            if (mListFragment != null) {
                mListFragment.setEnabled(false);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setProviderStatusLoader(mProviderStatusLoader);
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                        new ContactsUnavailableFragmentListener());
                getFragmentManager().openTransaction()
                        .replace(R.id.contacts_unavailable_container, mContactsUnavailableFragment)
                        .commit();
            } else {
                mContactsUnavailableFragment.update();
            }
            contactsUnavailableView.setVisibility(View.VISIBLE);
            mainView.setVisibility(View.INVISIBLE);
        }

        invalidateOptionsMenu();
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {

        @Override
        public void onSelectionChange() {
            if (mContactContentDisplayed) {
                setupContactDetailFragment(mListFragment.getSelectedContactUri());
            }
        }

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            if (mContactContentDisplayed) {
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
            ContactDeletionInteraction.start(ContactBrowserActivity.this, contactUri);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter =
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
            mContactListFilterController.setContactListFilter(filter, true);
            mListFragment.setFilter(filter);
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        @Override
        public void onContactNotFound() {
            // Nothing needs to be done here
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
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactBrowserActivity.this, contactUri);
        }

        @Override
        public void onCreateRawContactRequested(ArrayList<ContentValues> values, Account account) {
            Toast.makeText(ContactBrowserActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactBrowserActivity.this, values, account,
                    ContactBrowserActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);
        }
    }

    private class ContactsUnavailableFragmentListener
            implements OnContactsUnavailableActionListener {

        @Override
        public void onCreateNewContactAction() {
            startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
        }

        @Override
        public void onAddAccountAction() {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.putExtra(Settings.EXTRA_AUTHORITIES,
                    new String[] { ContactsContract.AUTHORITY });
            startActivity(intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            AccountSelectionUtil.doImportFromSdCard(ContactBrowserActivity.this, null);
        }

        @Override
        public void onFreeInternalStorageAction() {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
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
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        // No menu if contacts are unavailable
        if (!areContactsAvailable()) {
            return false;
        }

        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        // No menu if contacts are unavailable
        if (!areContactsAvailable()) {
            return false;
        }

        return super.onPreparePanel(featureId, view, menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!areContactsAvailable()) {
            return false;
        }

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
        if (!areContactsAvailable()) {
            return false;
        }

        MenuItem settings = menu.findItem(R.id.menu_settings);
        settings.setVisible(!ContactsPreferenceActivity.isEmpty(this));

        MenuItem displayGroups = menu.findItem(R.id.menu_display_groups);
        if (displayGroups != null) {
            displayGroups.setVisible(
                    mRequest.getActionCode() == ContactsRequest.ACTION_DEFAULT);
        }

        boolean groupActionsEnabled = false;
        if (mListFragment != null) {
            ContactListFilter filter = mListFragment.getFilter();
            if (filter != null
                    && filter.filterType == ContactListFilter.FILTER_TYPE_GROUP
                    && !filter.groupReadOnly) {
                groupActionsEnabled = true;
            }
        }

        MenuItem renameGroup = menu.findItem(R.id.menu_rename_group);
        if (renameGroup != null) {
            renameGroup.setVisible(groupActionsEnabled);
        }

        MenuItem deleteGroup = menu.findItem(R.id.menu_delete_group);
        if (deleteGroup != null) {
            deleteGroup.setVisible(groupActionsEnabled);
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
            case R.id.menu_rename_group: {
                ContactListFilter filter = mListFragment.getFilter();
                GroupRenamingDialogFragment.show(getFragmentManager(), filter.groupId,
                        filter.title);
                return true;
            }
            case R.id.menu_delete_group: {
                ContactListFilter filter = mListFragment.getFilter();
                GroupDeletionDialogFragment.show(getFragmentManager(), filter.groupId,
                        filter.title);
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
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            mListFragment.startSearch(initialQuery);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, bundle);

        Dialog dialog = getPhoneNumberCallInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getSendTextMessageInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getImportExportInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
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
            case SUBACTIVITY_CUSTOMIZE_FILTER: {
                if (resultCode == Activity.RESULT_OK) {
                    mContactListFilterController.selectCustomFilter();
                }
                break;
            }

            case SUBACTIVITY_EDIT_CONTACT:
            case SUBACTIVITY_NEW_CONTACT: {
                if (resultCode == RESULT_OK) {
                    mRequest.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                    mListFragment.setSelectedContactUri(data.getData());
                }
                break;
            }

            case SUBACTIVITY_SETTINGS:
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

    @Override
    public void onBackPressed() {
        if (mSearchMode && mActionBarAdapter != null) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
        }
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
