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
import com.android.contacts.ContactsActivity;
import com.android.contacts.GroupMetaData;
import com.android.contacts.R;
import com.android.contacts.calllog.CallLogFragment;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.dialpad.DialpadFragment;
import com.android.contacts.group.GroupBrowseListFragment;
import com.android.contacts.group.GroupBrowseListFragment.OnGroupBrowserActionListener;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.interactions.GroupRenamingDialogFragment;
import com.android.contacts.interactions.ImportExportDialogFragment;
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
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.DialogManager;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.TypedArray;
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
import android.widget.SearchView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Displays a list to browse contacts. For xlarge screens, this also displays a detail-pane on
 * the right.
 */
public class PeopleActivity extends ContactsActivity
        implements View.OnCreateContextMenuListener, ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener, ProviderStatusListener {

    private static final String TAG = "PeopleActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_EDIT_CONTACT = 3;
    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 4;

    private static final String KEY_SEARCH_MODE = "searchMode";

    private DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private ActionBarAdapter mActionBarAdapter;

    private boolean mSearchMode;

    private ContactBrowseListFragment mListFragment;

    /**
     * Whether we have a right-side contact or group detail pane for displaying info on that
     * contact or group while browsing. Generally means "this is a tablet".
     */
    private boolean mContentPaneDisplayed;

    private ContactDetailFragment mContactDetailFragment;
    private ContactDetailFragmentListener mContactDetailFragmentListener =
            new ContactDetailFragmentListener();

    private GroupDetailFragment mGroupDetailFragment;

    private PhoneNumberInteraction mPhoneNumberCallInteraction;
    private PhoneNumberInteraction mSendTextMessageInteraction;

    private boolean mSearchInitiated;

    private ContactListFilterController mContactListFilterController;

    private View mAddContactImageView;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusLoader mProviderStatusLoader;
    private int mProviderStatus = -1;

    private boolean mOptionsMenuContactsAvailable;
    private boolean mOptionsMenuGroupActionsEnabled;

    private DefaultContactBrowseListFragment mContactsFragment;
    private StrequentContactListFragment mFavoritesFragment;
    private GroupBrowseListFragment mGroupsFragment;

    public PeopleActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
        // TODO: Get rid of the ContactListFilterController class because there aren't any
        // dropdown filters anymore. Just store the selected filter as a member variable.
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
            if (!getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
                mListFragment.setContextMenuAdapter(
                        new ContactBrowseListContextMenuAdapter(mListFragment));
            }
        } else if (fragment instanceof GroupBrowseListFragment) {
            mGroupsFragment = (GroupBrowseListFragment) fragment;
            mGroupsFragment.setListener(new GroupBrowserActionListener());
        } else if (fragment instanceof ContactDetailFragment) {
            mContactDetailFragment = (ContactDetailFragment) fragment;
            mContactDetailFragment.setListener(mContactDetailFragmentListener);
            mContentPaneDisplayed = true;
        } else if (fragment instanceof ContactsUnavailableFragment) {
            mContactsUnavailableFragment = (ContactsUnavailableFragment)fragment;
            mContactsUnavailableFragment.setProviderStatusLoader(mProviderStatusLoader);
            mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
        } else if (fragment instanceof GroupDetailFragment) {
            mGroupDetailFragment = (GroupDetailFragment) fragment;
            mContentPaneDisplayed = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mAddContactImageView = getLayoutInflater().inflate(
                R.layout.add_contact_menu_item, null, false);
        View item = mAddContactImageView.findViewById(R.id.menu_item);
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
            }
        });

        configureContentView(true, savedState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        configureContentView(false, null);
    }

    private void configureContentView(boolean createContentView, Bundle savedState) {
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

        if (createContentView) {
            setContentView(R.layout.people_activity);

            final FragmentManager fragmentManager = getFragmentManager();
            mFavoritesFragment = (StrequentContactListFragment) fragmentManager
                    .findFragmentById(R.id.favorites_fragment);
            mContactsFragment = (DefaultContactBrowseListFragment) fragmentManager
                    .findFragmentById(R.id.contacts_fragment);
            mGroupsFragment = (GroupBrowseListFragment) fragmentManager
                    .findFragmentById(R.id.groups_fragment);

            // Hide all tabs (the current tab will later be reshown once a tab is selected)
            final FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.hide(mFavoritesFragment);
            transaction.hide(mContactsFragment);
            transaction.hide(mGroupsFragment);
            if (mContactDetailFragment != null) {
                transaction.hide(mContactDetailFragment);
            }
            if (mGroupDetailFragment != null) {
                transaction.hide(mGroupDetailFragment);
            }
            transaction.commit();
        }

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT
                && !mContentPaneDisplayed) {
            redirect = new Intent(this, ContactDetailActivity.class);
            redirect.setAction(Intent.ACTION_VIEW);
            redirect.setData(mRequest.getContactUri());
            startActivity(redirect);
            finish();
            return;
        }

        setTitle(mRequest.getActivityTitle());
        ActionBar actionBar = getActionBar();
        mActionBarAdapter = new ActionBarAdapter(this);
        mActionBarAdapter.onCreate(savedState, mRequest, getActionBar());
        mActionBarAdapter.setContactListFilterController(mContactListFilterController);

        if (createContentView) {
            actionBar.removeAllTabs();
            Tab favoritesTab = actionBar.newTab();
            favoritesTab.setText(getString(R.string.strequentList));
            favoritesTab.setTabListener(new TabChangeListener(mFavoritesFragment,
                    mContactDetailFragment));
            actionBar.addTab(favoritesTab);

            Tab peopleTab = actionBar.newTab();
            peopleTab.setText(getString(R.string.people));
            peopleTab.setTabListener(new TabChangeListener(mContactsFragment,
                    mContactDetailFragment));
            actionBar.addTab(peopleTab);

            Tab groupsTab = actionBar.newTab();
            groupsTab.setText(getString(R.string.contactsGroupsLabel));
            groupsTab.setTabListener(new TabChangeListener(mGroupsFragment,
                    mGroupDetailFragment));
            actionBar.addTab(groupsTab);
            actionBar.setDisplayShowTitleEnabled(true);

            TypedArray a = obtainStyledAttributes(null, R.styleable.ActionBarHomeIcon);
            boolean showHomeIcon = a.getBoolean(R.styleable.ActionBarHomeIcon_show_home_icon, true);
            actionBar.setDisplayShowHomeEnabled(showHomeIcon);

            invalidateOptionsMenu();
        }

        configureFragments(savedState == null);
    }

    /**
     * Tab change listener that is instantiated once for each tab. Handles showing/hiding fragments.
     * TODO: Use ViewPager so that tabs can be swiped left and right. Figure out how to use the
     * support library in our app.
     */
    private class TabChangeListener implements TabListener {
        private final Fragment mBrowseListFragment;

        /**
         * Right pane fragment that is present on larger screen sizes (can be
         * null for smaller screen sizes).
         */
        private final Fragment mDetailFragment;

        public TabChangeListener(Fragment listFragment, Fragment detailFragment) {
            mBrowseListFragment = listFragment;
            mDetailFragment = detailFragment;
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            ft.hide(mBrowseListFragment);
            if (mDetailFragment != null) {
                ft.hide(mDetailFragment);
            }
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            ft.show(mBrowseListFragment);
            if (mDetailFragment != null) {
                ft.show(mDetailFragment);
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }

    @Override
    protected void onPause() {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }

        mOptionsMenuContactsAvailable = false;
        mOptionsMenuGroupActionsEnabled = false;

        mProviderStatus = -1;
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
        mContactListFilterController.onStart();
        super.onStart();
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                    break;
                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                    break;

                // TODO: handle FREQUENT and STREQUENT according to the spec
                case ContactsRequest.ACTION_FREQUENT:
                case ContactsRequest.ACTION_STREQUENT:
                    // For now they are treated the same as STARRED
                case ContactsRequest.ACTION_STARRED:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_STARRED);
                    break;
            }

            mSearchMode = mRequest.isSearchMode();
            if (filter != null) {
                mContactListFilterController.setContactListFilter(filter, false);
                mSearchMode = false;
            } else if (mRequest.getActionCode() == ContactsRequest.ACTION_ALL_CONTACTS) {
                mContactListFilterController.setContactListFilter(
                        ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS), false);
            }

            if (mRequest.getContactUri() != null) {
                mSearchMode = false;
            }

            mListFragment.setContactsRequest(mRequest);
            configureListFragmentForRequest();

        } else {
            mSearchMode = mActionBarAdapter.isSearchMode();
        }

        configureListFragment();

        invalidateOptionsMenu();
    }

    @Override
    public void onContactListFiltersLoaded() {
        if (mListFragment == null || !mListFragment.isAdded()) {
            return;
        }

        mListFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenu();
    }

    @Override
    public void onContactListFilterChanged() {
        if (mListFragment == null || !mListFragment.isAdded()) {
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
        mContactDetailFragment.loadUri(contactLookupUri);
    }

    private void setupGroupDetailFragment(Uri groupUri) {
        mGroupDetailFragment.loadGroup(groupUri);
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(Action action) {
        switch (action) {
            case START_SEARCH_MODE:
                // Bring the contact list fragment to the front.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.show(mContactsFragment);
                ft.commit();
                break;
            case STOP_SEARCH_MODE:
            case CHANGE_SEARCH_QUERY:
                // Refresh the contact list fragment.
                configureFragments(false /* from request */);
                mListFragment.setQueryString(mActionBarAdapter.getQueryString(), true);
                break;
            default:
                throw new IllegalStateException("Unkonwn ActionBarAdapter action: " + action);
        }
    }

    private void configureListFragmentForRequest() {
        Uri contactUri = mRequest.getContactUri();
        if (contactUri != null) {
            mListFragment.setSelectedContactUri(contactUri);
        }

        mListFragment.setSearchMode(mRequest.isSearchMode());
        mListFragment.setQueryString(mRequest.getQueryString(), false);

        if (mRequest.isDirectorySearchEnabled()) {
            mListFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            mListFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }

        if (mContactListFilterController.isLoaded()) {
            mListFragment.setFilter(mContactListFilterController.getFilter());
        }
    }

    private void configureListFragment() {
        mListFragment.setSearchMode(mSearchMode);

        mListFragment.setVisibleScrollbarEnabled(!mSearchMode);
        mListFragment.setVerticalScrollbarPosition(
                mContentPaneDisplayed
                        ? View.SCROLLBAR_POSITION_LEFT
                        : View.SCROLLBAR_POSITION_RIGHT);
        mListFragment.setSelectionVisible(mContentPaneDisplayed);
        mListFragment.setQuickContactEnabled(!mContentPaneDisplayed);
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
            if (mainView != null) {
                mainView.setVisibility(View.VISIBLE);
            }
            if (mListFragment != null) {
                mListFragment.setEnabled(true);
            }
        } else {
            if (mListFragment != null) {
                mListFragment.setEnabled(false);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setProviderStatusLoader(mProviderStatusLoader);
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                        new ContactsUnavailableFragmentListener());
                getFragmentManager().beginTransaction()
                        .replace(R.id.contacts_unavailable_container, mContactsUnavailableFragment)
                        .commit();
            } else {
                mContactsUnavailableFragment.update();
            }
            contactsUnavailableView.setVisibility(View.VISIBLE);
            if (mainView != null) {
                mainView.setVisibility(View.INVISIBLE);
            }
        }

        invalidateOptionsMenu();
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {

        @Override
        public void onSelectionChange() {
            if (mContentPaneDisplayed) {
                setupContactDetailFragment(mListFragment.getSelectedContactUri());
            }
        }

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            if (mContentPaneDisplayed) {
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
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter;
            ContactListFilter currentFilter = mListFragment.getFilter();
            if (currentFilter != null
                    && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                mListFragment.setFilter(filter);
            } else {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                mListFragment.setFilter(filter, false);
            }
            mContactListFilterController.setContactListFilter(filter, true);
        }
    }

    private class ContactDetailFragmentListener implements ContactDetailFragment.Listener {
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
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }

        @Override
        public void onCreateRawContactRequested(ArrayList<ContentValues> values, Account account) {
            Toast.makeText(PeopleActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    PeopleActivity.this, values, account,
                    PeopleActivity.class, Intent.ACTION_VIEW);
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
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.putExtra(Settings.EXTRA_AUTHORITIES,
                    new String[] { ContactsContract.AUTHORITY });
            startActivity(intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            AccountSelectionUtil.doImportFromSdCard(PeopleActivity.this, null);
        }

        @Override
        public void onFreeInternalStorageAction() {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        }
    }

    private final class GroupBrowserActionListener implements OnGroupBrowserActionListener {

        @Override
        public void onViewGroupAction(Uri groupUri) {
            if (mContentPaneDisplayed) {
                setupGroupDetailFragment(groupUri);
            } else {
                Intent intent = new Intent(PeopleActivity.this, GroupDetailActivity.class);
                intent.setData(groupUri);
                startActivity(intent);
            }
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
        inflater.inflate(R.menu.actions, menu);
        // TODO: Figure out if R.menu.list or R.menu.search are necessary according to the overflow
        // menus on the UX mocks.
        MenuItem searchMenuItem = menu.findItem(R.id.menu_search);
        if (searchMenuItem != null && searchMenuItem.getActionView() instanceof SearchView) {
            SearchView searchView = (SearchView) searchMenuItem.getActionView();
            searchView.setQueryHint(getString(R.string.hint_findContacts));
            searchView.setIconifiedByDefault(false);

            if (mActionBarAdapter != null) {
                mActionBarAdapter.setSearchView(searchView);
            }
        }

        // TODO: Can remove this as a custom view because the account selector is in the editor now.
        // Change add contact button to button with a custom view
        final MenuItem addContact = menu.findItem(R.id.menu_add);
        addContact.setActionView(mAddContactImageView);
        return true;
    }

    @Override
    public void invalidateOptionsMenu() {
        if (isOptionsMenuChanged()) {
            super.invalidateOptionsMenu();
        }
    }

    public boolean isOptionsMenuChanged() {
        if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
            return true;
        }

        if (mOptionsMenuGroupActionsEnabled != areGroupActionsEnabled()) {
            return true;
        }

        if (mListFragment != null && mListFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mContactDetailFragment != null && mContactDetailFragment.isOptionsMenuChanged()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuContactsAvailable = areContactsAvailable();
        if (!mOptionsMenuContactsAvailable) {
            return false;
        }

        MenuItem settings = menu.findItem(R.id.menu_settings);
        if (settings != null) {
            settings.setVisible(!ContactsPreferenceActivity.isEmpty(this));
        }

        mOptionsMenuGroupActionsEnabled = areGroupActionsEnabled();

        MenuItem renameGroup = menu.findItem(R.id.menu_rename_group);
        if (renameGroup != null) {
            renameGroup.setVisible(mOptionsMenuGroupActionsEnabled);
        }

        MenuItem deleteGroup = menu.findItem(R.id.menu_delete_group);
        if (deleteGroup != null) {
            deleteGroup.setVisible(mOptionsMenuGroupActionsEnabled);
        }

        return true;
    }

    private boolean areGroupActionsEnabled() {
        boolean groupActionsEnabled = false;
        if (mListFragment != null) {
            ContactListFilter filter = mListFragment.getFilter();
            if (filter != null
                    && filter.filterType == ContactListFilter.FILTER_TYPE_GROUP
                    && !filter.groupReadOnly) {
                groupActionsEnabled = true;
            }
        }
        return groupActionsEnabled;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings: {
                final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.menu_contacts_filter: {
                final Intent intent = new Intent(this, CustomContactListFilterActivity.class);
                startActivityForResult(intent, SUBACTIVITY_CUSTOMIZE_FILTER);
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
                ImportExportDialogFragment.show(getFragmentManager());
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
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

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (mListFragment != null && mListFragment.isAdded() && !globalSearch) {
            mListFragment.startSearch(initialQuery);
        } else {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, bundle);

        Dialog dialog = getPhoneNumberCallInteraction().onCreateDialog(id, bundle);
        if (dialog != null) return dialog;

        dialog = getSendTextMessageInteraction().onCreateDialog(id, bundle);
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
                if (resultCode == RESULT_OK && mContentPaneDisplayed) {
                    mRequest.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                    mListFragment.reloadDataAndSetSelectedUri(data.getData());
                }
                break;
            }

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
                if (unicodeChar != 0 && !Character.isWhitespace(unicodeChar)) {
                    String query = new String(new int[]{ unicodeChar }, 0, 1);
                    if (!mActionBarAdapter.isSearchMode()) {
                        mActionBarAdapter.setQueryString(query);
                        mActionBarAdapter.setSearchMode(true);
                        return true;
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

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        mSearchMode = inState.getBoolean(KEY_SEARCH_MODE);
        if (mActionBarAdapter != null) {
            mActionBarAdapter.onRestoreInstanceState(inState);
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

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    // Visible for testing
    public ContactBrowseListFragment getListFragment() {
        return mListFragment;
    }

    // Visible for testing
    public ContactDetailFragment getDetailFragment() {
        return mContactDetailFragment;
    }
}
