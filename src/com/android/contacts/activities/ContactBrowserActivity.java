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
import com.android.contacts.util.ThemeUtils;
import com.android.contacts.views.ContactSaveService;
import com.android.contacts.views.detail.ContactDetailFragment;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
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
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener, ProviderStatusListener {

    private static final String TAG = "ContactBrowserActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_SETTINGS = 3;
    private static final int SUBACTIVITY_EDIT_CONTACT = 4;
    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 5;

    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    /**
     * The id for a delayed message that triggers automatic selection of the first
     * found contact in search mode.
     */
    private static final int MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT = 1;

    /**
     * The delay that is used for automatically selecting the first found contact.
     */
    private static final int DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS = 500;

    /**
     * The minimum number of characters in the search query that is required
     * before we automatically select the first found contact.
     */
    private static final int AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH = 2;

    private static final String KEY_SEARCH_MODE = "searchMode";

    private DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private SharedPreferences mPrefs;

    private boolean mHasActionBar;
    private ActionBarAdapter mActionBarAdapter;

    private boolean mSearchMode;

    private ContactBrowseListFragment mListFragment;
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

    private Handler mHandler;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusLoader mProviderStatusLoader;
    private int mProviderStatus = -1;

    public ContactBrowserActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
        mContactListFilterController = new ContactListFilterController(this);
        mContactListFilterController.addListener(this);
        mProviderStatusLoader = new ProviderStatusLoader(this);
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT) {
                        selectFirstFoundContact();
                    }
                }
            };
        }
        return mHandler;
    }

    public boolean areContactsAvailable() {
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactBrowseListFragment) {
            mListFragment = (ContactBrowseListFragment)fragment;
            mListFragment.setOnContactListActionListener(new ContactBrowserActionListener());
            configureListSelection();
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
            mActionBarAdapter.setContactListFilterController(mContactListFilterController);
            // TODO: request may ask for FREQUENT - set the filter accordingly
            mAddContactImageView = new ImageView(this);
            mAddContactImageView.setImageResource(R.drawable.ic_menu_add_contact_holo_light);
            mAddContactImageView.setBackgroundResource(
                    ThemeUtils.getSelectableItemBackground(getTheme()));
            mAddContactImageView.setContentDescription(getString(R.string.menu_newContact));
            mAddContactImageView.setOnClickListener(new OnClickListener() {
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

                // onNewIntent is called when the activity is paused, so it is not
                // registered as a listener of the action bar adapter. Simulate the listener call.
                onAction();
            }

            mListFragment.setSelectedContactUri(uri);
            mListFragment.saveSelectedUri(mPrefs);
            mListFragment.requestSelectionOnScreen(true);
            if (mContactContentDisplayed) {
                setupContactDetailFragment(uri);
            }
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
                // When switching to the search mode, erase previous state of the search UI
                mListFragment.eraseSelectedUri(mPrefs);
            } else {
                mListFragment = createListFragment(ContactsRequest.ACTION_DEFAULT);
            }
        }

        if (mSearchMode) {
            if (mHasActionBar) {
                mListFragment.setQueryString(mActionBarAdapter.getQueryString());
            }
        } else {
            configureListSelection();
        }

        if (replaceList) {
            getFragmentManager().openTransaction()
                    .replace(R.id.list_container, mListFragment)
                    .commit();
        }
    }

    @Override
    public void onContactListFiltersLoaded() {
        configureListSelection();

        // Filters have been loaded - now we can start loading the list itself
        mListFragment.startLoading();
    }

    @Override
    public void onContactListFilterChanged() {
        resetContactSelectionInIntent();

        if (mListFragment == null) {
            return;
        }

        DefaultContactBrowseListFragment fragment =
                (DefaultContactBrowseListFragment) mListFragment;
        ContactListFilter filter = mContactListFilterController.getFilter();
        fragment.setFilter(filter);
        fragment.reloadData();
        fragment.restoreSelectedUri(mPrefs);
        fragment.requestSelectionOnScreen(false);

        if (mContactContentDisplayed) {
            setupContactDetailFragment(mListFragment.getSelectedContactUri());
        }
    }

    /**
     * Configures filter-specific persistent selection.
     */
    private void configureListSelection() {
        if (mListFragment == null) {
            return;
        }

        if (mListFragment instanceof DefaultContactBrowseListFragment
                && mContactListFilterController != null
                && mContactListFilterController.isLoaded()) {
            DefaultContactBrowseListFragment fragment =
                    (DefaultContactBrowseListFragment) mListFragment;
            ContactListFilter filter = mContactListFilterController.getFilter();
            fragment.setFilter(filter);
            if (mRequest.getContactUri() != null) {
                fragment.setSelectedContactUri(mRequest.getContactUri());
                fragment.saveSelectedUri(mPrefs);
            } else {
                fragment.restoreSelectedUri(mPrefs);
            }
            fragment.requestSelectionOnScreen(false);
            if (mContactContentDisplayed) {
                setupContactDetailFragment(mListFragment.getSelectedContactUri());
            }
        } else if (mContactContentDisplayed) {
            setupContactDetailFragment(mListFragment.getSelectedContactUri());
        }
    }

    /**
     * Removes the selected contact URI that was supplied with the intent (if any),
     * because the user has explicitly changed the selection.
     */
    private void resetContactSelectionInIntent() {
        mRequest.setContactUri(null);

        getIntent().setAction(Intent.ACTION_DEFAULT);
        getIntent().setData(null);
    }

    private void showDefaultSelection() {
        if (mSearchMode) {
            selectFirstFoundContactAfterDelay();
            return;
        }

        Uri requestedContactUri = mRequest.getContactUri();
        if (requestedContactUri != null
                && mListFragment instanceof DefaultContactBrowseListFragment) {
            // If a specific selection was requested, adjust the filter so
            // that the requested selection is unconditionally visible.
            DefaultContactBrowseListFragment fragment =
                    (DefaultContactBrowseListFragment) mListFragment;
            ContactListFilter filter =
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
            fragment.setFilter(filter);
            fragment.setSelectedContactUri(requestedContactUri);
            fragment.saveSelectedUri(mPrefs);
            fragment.reloadData();
            if (mContactListFilterController != null) {
                mContactListFilterController.setContactListFilter(filter, true);
            }
        } else {
            // Otherwise, choose the first contact on the list and select it
            requestedContactUri = mListFragment.getFirstContactUri();
            if (requestedContactUri != null) {
                mListFragment.setSelectedContactUri(requestedContactUri);
                mListFragment.eraseSelectedUri(mPrefs);
                mListFragment.requestSelectionOnScreen(false);
            }
        }
        if (mContactContentDisplayed) {
            setupContactDetailFragment(requestedContactUri);
        }
    }

    /**
     * Automatically selects the first found contact in search mode.  The selection
     * is updated after a delay to allow the user to type without to much UI churn
     * and to save bandwidth on directory queries.
     */
    public void selectFirstFoundContactAfterDelay() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT);
        handler.sendEmptyMessageDelayed(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT,
                DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS);
    }

    /**
     * Selects the first contact in the list in search mode.
     */
    protected void selectFirstFoundContact() {
        if (!mSearchMode) {
            return;
        }

        Uri selectedUri = null;
        String queryString = mListFragment.getQueryString();
        if (queryString != null
                && queryString.length() >= AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH) {
            selectedUri = mListFragment.getFirstContactUri();
        }

        mListFragment.setSelectedContactUri(selectedUri);
        mListFragment.eraseSelectedUri(mPrefs);
        mListFragment.requestSelectionOnScreen(true);
        if (mContactContentDisplayed) {
            setupContactDetailFragment(selectedUri);
        }
    }

    @Override
    public void onContactListFilterCustomizationRequest() {
        startActivityForResult(new Intent(this, CustomContactListFilterActivity.class),
                SUBACTIVITY_CUSTOMIZE_FILTER);
    }

    private void setupContactDetailFragment(final Uri contactLookupUri) {
        if (mDetailFragment == null) {
            mDetailFragment = new ContactDetailFragment();
            mDetailFragment.loadUri(contactLookupUri);
            getFragmentManager().openTransaction()
                    .replace(R.id.detail_container, mDetailFragment)
                    .commit();
        } else {
            mDetailFragment.loadUri(contactLookupUri);
        }
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
        public void onViewContactAction(Uri contactLookupUri) {
            if (mContactContentDisplayed) {
                resetContactSelectionInIntent();

                mListFragment.setSelectedContactUri(contactLookupUri);
                mListFragment.saveSelectedUri(mPrefs);
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

        @Override
        public void onInvalidSelection() {
            showDefaultSelection();
        }
    }

    private class DetailFragmentListener implements ContactDetailFragment.Listener {
        @Override
        public void onContactNotFound() {
            resetContactSelectionInIntent();
            setupContactDetailFragment(null);
            showDefaultSelection();
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
            case SUBACTIVITY_CUSTOMIZE_FILTER: {
                if (resultCode == Activity.RESULT_OK) {
                    mContactListFilterController.selectCustomFilter();
                }
                break;
            }
            case SUBACTIVITY_EDIT_CONTACT: {
                mListFragment.requestSelectionOnScreen(true);
                break;
            }

            case SUBACTIVITY_NEW_CONTACT: {
                if (resultCode == RESULT_OK && mContactContentDisplayed) {
                    resetContactSelectionInIntent();

                    final Uri newContactUri = data.getData();
                    if (mContactContentDisplayed) {
                        setupContactDetailFragment(newContactUri);
                    }

                    mRequest.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                    mListFragment.setSelectedContactUri(newContactUri);
                    mListFragment.saveSelectedUri(mPrefs);
                    mListFragment.requestSelectionOnScreen(true);
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
