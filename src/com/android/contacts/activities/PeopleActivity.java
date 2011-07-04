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

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.group.GroupBrowseListFragment;
import com.android.contacts.group.GroupBrowseListFragment.OnGroupBrowserActionListener;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ImportExportDialogFragment;
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactTileAdapter.DisplayType;
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
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.DialogManager;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListPopupWindow;
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
    private static final int SUBACTIVITY_NEW_GROUP = 4;
    private static final int SUBACTIVITY_EDIT_GROUP = 5;
    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 6;

    private static final int FAVORITES_COLUMN_COUNT = 4;

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
    private final ContactDetailFragmentListener mContactDetailFragmentListener =
            new ContactDetailFragmentListener();
    private final GroupDetailFragmentListener mGroupDetailFragmentListener =
            new GroupDetailFragmentListener();

    private GroupDetailFragment mGroupDetailFragment;

    private StrequentContactListFragment.Listener mFavoritesFragmentListener =
            new StrequentContactListFragmentListener();

    private boolean mSearchInitiated;

    private ContactListFilterController mContactListFilterController;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusLoader mProviderStatusLoader;
    private int mProviderStatus = -1;

    private boolean mOptionsMenuContactsAvailable;

    private DefaultContactBrowseListFragment mContactsFragment;
    private StrequentContactListFragment mFavoritesFragment;
    private StrequentContactListFragment mFrequentFragment;
    private GroupBrowseListFragment mGroupsFragment;

    private View mFavoritesView;
    private View mBrowserView;
    private View mDetailsView;

    private View mAddGroupImageView;

    private enum TabState {
        FAVORITES, CONTACTS, GROUPS
    }

    private TabState mSelectedTab;

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
            mGroupDetailFragment.setListener(mGroupDetailFragmentListener);
            mContentPaneDisplayed = true;
        } else if (fragment instanceof StrequentContactListFragment) {
            mFavoritesFragment = (StrequentContactListFragment) fragment;
            mFavoritesFragment.setListener(mFavoritesFragmentListener);
            mFavoritesFragment.setColumnCount(FAVORITES_COLUMN_COUNT);
            mFavoritesFragment.setDisplayType(DisplayType.STARRED_ONLY);
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

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

            mFavoritesView = findViewById(R.id.favorites_view);
            mDetailsView = findViewById(R.id.details_view);
            mBrowserView = findViewById(R.id.browse_view);

            final FragmentManager fragmentManager = getFragmentManager();
            mFavoritesFragment = (StrequentContactListFragment) fragmentManager
                    .findFragmentById(R.id.favorites_fragment);
            mFrequentFragment = (StrequentContactListFragment) fragmentManager
                    .findFragmentById(R.id.frequent_fragment);
            mContactsFragment = (DefaultContactBrowseListFragment) fragmentManager
                    .findFragmentById(R.id.contacts_fragment);
            mGroupsFragment = (GroupBrowseListFragment) fragmentManager
                    .findFragmentById(R.id.groups_fragment);
            // Hide all tabs (the current tab will later be reshown once a tab is selected)
            final FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.hide(mContactsFragment);
            transaction.hide(mGroupsFragment);

            if (mFrequentFragment != null) {
                mFrequentFragment.setDisplayType(DisplayType.FREQUENT_ONLY);
            }
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
        mActionBarAdapter = new ActionBarAdapter(this, this);
        mActionBarAdapter.onCreate(savedState, mRequest, getActionBar(), !mContentPaneDisplayed);
        mActionBarAdapter.setContactListFilterController(mContactListFilterController);

        if (createContentView) {
            actionBar.removeAllTabs();
            Tab favoritesTab = actionBar.newTab();
            favoritesTab.setText(getString(R.string.strequentList));
            favoritesTab.setTabListener(new TabChangeListener(mFavoritesFragment,
                    mFrequentFragment, TabState.FAVORITES));
            actionBar.addTab(favoritesTab);

            Tab peopleTab = actionBar.newTab();
            peopleTab.setText(getString(R.string.people));
            peopleTab.setTabListener(new TabChangeListener(mContactsFragment,
                    mContactDetailFragment, TabState.CONTACTS));
            actionBar.addTab(peopleTab);

            Tab groupsTab = actionBar.newTab();
            groupsTab.setText(getString(R.string.contactsGroupsLabel));
            groupsTab.setTabListener(new TabChangeListener(mGroupsFragment,
                    mGroupDetailFragment, TabState.GROUPS));
            actionBar.addTab(groupsTab);
            actionBar.setDisplayShowTitleEnabled(true);

            TypedArray a = obtainStyledAttributes(null, R.styleable.ActionBarHomeIcon);
            boolean showHomeIcon = a.getBoolean(R.styleable.ActionBarHomeIcon_show_home_icon, true);
            actionBar.setDisplayShowHomeEnabled(showHomeIcon);

            invalidateOptionsMenuIfNeeded();
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
        private final TabState mTabState;

        public TabChangeListener(Fragment listFragment, Fragment detailFragment, TabState state) {
            mBrowseListFragment = listFragment;
            mDetailFragment = detailFragment;
            mTabState = state;
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
            setSelectedTab(mTabState);
            invalidateOptionsMenu();
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }

    private void setSelectedTab(TabState tab) {
        mSelectedTab = tab;

        if (mContentPaneDisplayed) {
            switch (mSelectedTab) {
                case FAVORITES:
                    mFavoritesView.setVisibility(View.VISIBLE);
                    mBrowserView.setVisibility(View.GONE);
                    mDetailsView.setVisibility(View.GONE);
                    break;
                case GROUPS:
                case CONTACTS:
                    mFavoritesView.setVisibility(View.GONE);
                    mBrowserView.setVisibility(View.VISIBLE);
                    mDetailsView.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    @Override
    protected void onPause() {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }

        mOptionsMenuContactsAvailable = false;

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
            configureContactListFragmentForRequest();

        } else {
            mSearchMode = mActionBarAdapter.isSearchMode();
        }

        configureContactListFragment();
        configureGroupListFragment();

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    public void onContactListFiltersLoaded() {
        if (mListFragment == null || !mListFragment.isAdded()) {
            return;
        }

        mListFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    public void onContactListFilterChanged() {
        if (mListFragment == null || !mListFragment.isAdded()) {
            return;
        }

        mListFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    public void onContactListFilterCustomizationRequest() {
        startActivityForResult(new Intent(this, CustomContactListFilterActivity.class),
                SUBACTIVITY_CUSTOMIZE_FILTER);
    }

    private void setupContactDetailFragment(final Uri contactLookupUri) {
        mContactDetailFragment.loadUri(contactLookupUri);
        invalidateOptionsMenuIfNeeded();
    }

    private void setupGroupDetailFragment(Uri groupUri) {
        mGroupDetailFragment.loadGroup(groupUri);
        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(Action action) {
        switch (action) {
            case START_SEARCH_MODE:
                // Checking if multi fragments are being displayed
                if (mContentPaneDisplayed) {
                    mFavoritesView.setVisibility(View.GONE);
                    mBrowserView.setVisibility(View.VISIBLE);
                    mDetailsView.setVisibility(View.VISIBLE);
                }
                // Bring the contact list fragment (and detail fragment if applicable) to the front
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.show(mContactsFragment);
                if (mContactDetailFragment != null) ft.show(mContactDetailFragment);
                ft.commit();
                clearSearch();
                break;
            case STOP_SEARCH_MODE:
                // Refresh the fragments because search mode was using them to display search
                // results.
                clearSearch();

                // If the last selected tab was not the "All contacts" tab, then hide these
                // fragments because we need to show favorites or groups.
                if (mSelectedTab != null && !mSelectedTab.equals(TabState.CONTACTS)) {
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.hide(mContactsFragment);
                    if (mContactDetailFragment != null) transaction.hide(mContactDetailFragment);
                    transaction.commit();
                }
                if (mSelectedTab != null) setSelectedTab(mSelectedTab);
                break;
            case CHANGE_SEARCH_QUERY:
                loadSearch(mActionBarAdapter.getQueryString());
                break;
            default:
                throw new IllegalStateException("Unkonwn ActionBarAdapter action: " + action);
        }
    }

    private void clearSearch() {
        loadSearch("");
    }

    private void loadSearch(String query) {
        configureFragments(false /* from request */);
        mListFragment.setQueryString(query, true);
    }

    private void configureContactListFragmentForRequest() {
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

    private void configureContactListFragment() {
        mListFragment.setSearchMode(mSearchMode);

        mListFragment.setVisibleScrollbarEnabled(!mSearchMode);
        mListFragment.setVerticalScrollbarPosition(
                mContentPaneDisplayed
                        ? View.SCROLLBAR_POSITION_LEFT
                        : View.SCROLLBAR_POSITION_RIGHT);
        mListFragment.setSelectionVisible(mContentPaneDisplayed);
        mListFragment.setQuickContactEnabled(!mContentPaneDisplayed);
    }

    private void configureGroupListFragment() {
        mGroupsFragment.setVerticalScrollbarPosition(
                mContentPaneDisplayed
                        ? View.SCROLLBAR_POSITION_LEFT
                        : View.SCROLLBAR_POSITION_RIGHT);
        mGroupsFragment.setSelectionVisible(mContentPaneDisplayed);
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

        invalidateOptionsMenuIfNeeded();
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
            PhoneNumberInteraction.startInteractionForPhoneCall(PeopleActivity.this, contactUri);
        }

        @Override
        public void onSmsContactAction(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForTextMessage(PeopleActivity.this, contactUri);
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

    private final class StrequentContactListFragmentListener
            implements StrequentContactListFragment.Listener {
        @Override
        public void onContactSelected(Uri contactUri) {
            if (mContentPaneDisplayed) {
                setupContactDetailFragment(contactUri);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
            }
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

    private class GroupDetailFragmentListener implements GroupDetailFragment.Listener {
        @Override
        public void onGroupSizeUpdated(String size) {
            // Nothing needs to be done here because the size will be displayed in the detail
            // fragment
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            // Nothing needs to be done here because the title will be displayed in the detail
            // fragment
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(PeopleActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivityForResult(intent, SUBACTIVITY_EDIT_GROUP);
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
        MenuItem searchMenuItem = menu.findItem(R.id.menu_search);
        if (searchMenuItem != null && searchMenuItem.getActionView() instanceof SearchView) {
            SearchView searchView = (SearchView) searchMenuItem.getActionView();
            searchView.setQueryHint(getString(R.string.hint_findContacts));
            searchView.setIconifiedByDefault(false);

            if (mActionBarAdapter != null) {
                mActionBarAdapter.setSearchView(searchView);
            }
        }

        // On narrow screens we specify a NEW group button in the {@link ActionBar}, so that
        // it can be in the overflow menu. On wide screens, we use a custom view because we need
        // its location for anchoring the account-selector popup.
        final MenuItem addGroup = menu.findItem(R.id.menu_custom_add_group);
        if (addGroup != null) {
            mAddGroupImageView = getLayoutInflater().inflate(
                    R.layout.add_group_menu_item, null, false);
            View item = mAddGroupImageView.findViewById(R.id.menu_item);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNewGroupWithAccountDisambiguation();
                }
            });
            addGroup.setActionView(mAddGroupImageView);
        }
        return true;
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            invalidateOptionsMenu();
        }
    }

    public boolean isOptionsMenuChanged() {
        if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
            return true;
        }

        if (mListFragment != null && mListFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mContactDetailFragment != null && mContactDetailFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mGroupDetailFragment != null && mGroupDetailFragment.isOptionsMenuChanged()) {
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

        final MenuItem searchMenu = menu.findItem(R.id.menu_search);
        final MenuItem addContactMenu = menu.findItem(R.id.menu_add_contact);
        MenuItem addGroupMenu = menu.findItem(R.id.menu_add_group);
        if (addGroupMenu == null) {
            addGroupMenu = menu.findItem(R.id.menu_custom_add_group);
        }

        if (mActionBarAdapter.isSearchMode()) {
            addContactMenu.setVisible(false);
            addGroupMenu.setVisible(false);
            // If search is normally in the overflow menu, when we are in search
            // mode, hide this option.
            if (mActionBarAdapter.isSearchInOverflowMenu()) {
                searchMenu.setVisible(false);
            }
        } else {
            switch (mSelectedTab) {
                case FAVORITES:
                    // TODO: Fall through until we determine what the menu items should be for
                    // this tab
                case CONTACTS:
                    addContactMenu.setVisible(true);
                    addGroupMenu.setVisible(false);
                    break;
                case GROUPS:
                    addContactMenu.setVisible(false);
                    addGroupMenu.setVisible(true);
                    break;
            }
        }

        MenuItem settings = menu.findItem(R.id.menu_settings);
        if (settings != null) {
            settings.setVisible(!ContactsPreferenceActivity.isEmpty(this));
        }

        return true;
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
            case R.id.menu_add_contact: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
                return true;
            }
            case R.id.menu_add_group: {
                createNewGroupWithAccountDisambiguation();
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
        }
        return false;
    }

    private void createNewGroupWithAccountDisambiguation() {
        final ArrayList<Account> accounts =
                AccountTypeManager.getInstance(this).getAccounts(true);
        if (accounts.size() <= 1 || mAddGroupImageView == null) {
            // No account to choose or no control to anchor the popup-menu to
            // ==> just go straight to the editor which will disambig if necessary
            final Intent intent = new Intent(this, GroupEditorActivity.class);
            intent.setAction(Intent.ACTION_INSERT);
            startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
            return;
        }

        final ListPopupWindow popup = new ListPopupWindow(this, null);
        popup.setWidth(getResources().getDimensionPixelSize(R.dimen.account_selector_popup_width));
        popup.setAnchorView(mAddGroupImageView);
        // Create a list adapter with all writeable accounts (assume that the writeable accounts all
        // allow group creation).
        final AccountsListAdapter adapter = new AccountsListAdapter(this, true);
        popup.setAdapter(adapter);
        popup.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                popup.dismiss();
                final Intent intent = new Intent(PeopleActivity.this, GroupEditorActivity.class);
                intent.setAction(Intent.ACTION_INSERT);
                intent.putExtra(Intents.Insert.ACCOUNT, adapter.getItem(position));
                startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
            }
        });
        popup.setModal(true);
        popup.show();
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

            case SUBACTIVITY_NEW_GROUP:
            case SUBACTIVITY_EDIT_GROUP: {
                if (resultCode == RESULT_OK && mContentPaneDisplayed) {
                    mRequest.setActionCode(ContactsRequest.ACTION_GROUP);
                    mGroupsFragment.setSelectedUri(data.getData());
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
