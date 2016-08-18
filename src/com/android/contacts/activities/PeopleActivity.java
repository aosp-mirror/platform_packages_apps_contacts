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

import android.accounts.Account;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.common.Experiments;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.ProviderStatusWatcher;
import com.android.contacts.common.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.SyncUtil;
import com.android.contactsbind.experiments.Flags;
import com.android.contacts.widget.FloatingActionButtonBehavior;
import com.google.android.libraries.material.featurehighlight.FeatureHighlight;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends ContactsDrawerActivity implements ProviderStatusListener {

    private static final String TAG = "PeopleActivity";

    private static final String TAG_ALL = "contacts-all";

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private boolean wasLastFabAnimationScaleIn = false;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusWatcher mProviderStatusWatcher;
    private Integer mProviderStatus;

    private BroadcastReceiver mSaveServiceListener;

    private CoordinatorLayout mLayoutRoot;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private DefaultContactBrowseListFragment mAllFragment;

    private View mContactsView;

    /**
     * True if this activity instance is a re-created one.  i.e. set true after orientation change.
     * This is set in {@link #onCreate} for later use in {@link #onStart}.
     */
    private boolean mIsRecreatedInstance;

    /**
     * If {@link #configureFragments(boolean)} is already called.  Used to avoid calling it twice
     * in {@link #onStart}.
     * (This initialization only needs to be done once in onStart() when the Activity was just
     * created from scratch -- i.e. onCreate() was just called)
     */
    private boolean mFragmentInitialized;


    /** Sequential ID assigned to each instance; used for logging */
    private final int mInstanceId;
    private static final AtomicInteger sNextInstanceId = new AtomicInteger();

    private Object mStatusChangeListenerHandle;

    private final Handler mHandler = new Handler();

    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            mHandler.post(new Runnable() {
                public void run() {
                    onSyncStateUpdated();
                }
            });
        }
    };

    // Update sync status for accounts in current ContactListFilter
    private void onSyncStateUpdated() {
        if (mAllFragment.getActionBarAdapter().isSearchMode()
                || mAllFragment.getActionBarAdapter().isSelectionMode()) {
            return;
        }

        final ContactListFilter filter = mAllFragment.getFilter();
        if (filter != null) {
            final SwipeRefreshLayout swipeRefreshLayout = mAllFragment.getSwipeRefreshLayout();
            if (swipeRefreshLayout == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Can not load swipeRefreshLayout, swipeRefreshLayout is null");
                }
                return;
            }

            final List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(this)
                    .getAccounts(/* contactsWritableOnly */ true);
            final List<Account> syncableAccounts = filter.getSyncableAccounts(accounts);
            // If one of the accounts is active or pending, use spinning circle to indicate one of
            // the syncs is in progress.
            if (syncableAccounts != null && syncableAccounts.size() > 0) {
                for (Account account: syncableAccounts) {
                    if (SyncUtil.isSyncStatusPendingOrActive(account)
                            || SyncUtil.isUnsyncableGoogleAccount(account)) {
                        swipeRefreshLayout.setRefreshing(true);
                        return;
                    }
                }
            }
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public PeopleActivity() {
        mInstanceId = sNextInstanceId.getAndIncrement();
        mIntentResolver = new ContactsIntentResolver(this);
        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);
    }

    @Override
    public String toString() {
        // Shown on logcat
        return String.format("%s@%d", getClass().getSimpleName(), mInstanceId);
    }

    public boolean areContactsAvailable() {
        return (mProviderStatus != null) && mProviderStatus.equals(ProviderStatus.STATUS_NORMAL);
    }

    /**
     * Initialize fragments that are (or may not be) in the layout.
     *
     * For the fragments that are in the layout, we initialize them in
     * {@link #createViewsAndFragments(Bundle)} after inflating the layout.
     *
     * However, the {@link ContactsUnavailableFragment} is a special fragment which may not
     * be in the layout, so we have to do the initialization here.
     *
     * The ContactsUnavailableFragment is always created at runtime.
     */
    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactsUnavailableFragment) {
            mContactsUnavailableFragment = (ContactsUnavailableFragment)fragment;
            mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
        }
        super.onCreate(savedState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        if (!processIntent(false)) {
            finish();
            return;
        }

        mProviderStatusWatcher.addListener(this);

        mIsRecreatedInstance = (savedState != null);

        createViewsAndFragments();

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        getWindow().setBackgroundDrawable(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (ContactsDrawerActivity.ACTION_CREATE_GROUP.equals(intent.getAction())) {
            super.onNewIntent(intent);
            return;
        }

        setIntent(intent);
        if (!processIntent(true)) {
            finish();
            return;
        }

        // Re-initialize ActionBarAdapter because {@link #onNewIntent(Intent)} doesn't invoke
        // {@link Fragment#onActivityCreated(Bundle)} where we initialize ActionBarAdapter
        // initially.
        mAllFragment.setContactsRequest(mRequest);
        mAllFragment.initializeActionBarAdapter(null);

        // Re-configure fragments.
        configureFragments(true /* from request */);
        initializeFabVisibility();
        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Resolve the intent and initialize {@link #mRequest}, and launch another activity if redirect
     * is needed.
     *
     * @param forNewIntent set true if it's called from {@link #onNewIntent(Intent)}.
     * @return {@code true} if {@link PeopleActivity} should continue running.  {@code false}
     *         if it shouldn't, in which case the caller should finish() itself and shouldn't do
     *         farther initialization.
     */
    private boolean processIntent(boolean forNewIntent) {
        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent
                    + " intent=" + getIntent() + " request=" + mRequest);
        }
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            return false;
        }

        switch (mRequest.getActionCode()) {
            case ContactsRequest.ACTION_VIEW_CONTACT: {
                final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(
                        PeopleActivity.this, mRequest.getContactUri(),
                        QuickContactActivity.MODE_FULLY_EXPANDED);
                intent.putExtra(QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE, ScreenType.UNKNOWN);
                ImplicitIntentsUtil.startActivityInApp(this, intent);
                return false;
            }
            case ContactsRequest.ACTION_INSERT_GROUP: {
                onCreateGroupMenuItemClicked();
                return true;
            }
        }
        return true;
    }

    private void createViewsAndFragments() {
        setContentView(R.layout.people_activity);

        final FragmentManager fragmentManager = getFragmentManager();

        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        mAllFragment = (DefaultContactBrowseListFragment)
                fragmentManager.findFragmentByTag(TAG_ALL);

        mContactsView = getView(R.id.contacts_view);

        if (mAllFragment == null) {
            mAllFragment = new DefaultContactBrowseListFragment();
            mAllFragment.setAnimateOnLoad(true);
            transaction.add(R.id.contacts_list_container, mAllFragment, TAG_ALL);
        }

        mAllFragment.setContactsAvailable(areContactsAvailable());
        mAllFragment.setListType();
        mAllFragment.setContactsRequest(mRequest);

        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();

        // Configure floating action button
        mFloatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        final ImageButton floatingActionButton
                = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabClicked();
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                mFloatingActionButtonContainer, floatingActionButton);

        invalidateOptionsMenuIfNeeded();

        mLayoutRoot = (CoordinatorLayout) findViewById(R.id.root);

        // Setup the FAB to animate upwards when a snackbar is shown in this activity.
        // Normally the layout_behavior attribute could be used for this but for some reason it
        // throws a ClassNotFoundException so  the layout parameters are set programmatically.
        final CoordinatorLayout.LayoutParams fabParams = new CoordinatorLayout.LayoutParams(
                (ViewGroup.MarginLayoutParams) mFloatingActionButtonContainer.getLayoutParams());
        fabParams.setBehavior(new FloatingActionButtonBehavior());
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        mFloatingActionButtonContainer.setLayoutParams(fabParams);
    }

    @Override
    protected void onStart() {
        if (!mFragmentInitialized) {
            mFragmentInitialized = true;
            /* Configure fragments if we haven't.
             *
             * Note it's a one-shot initialization, so we want to do this in {@link #onCreate}.
             *
             * However, because this method may indirectly touch views in fragments but fragments
             * created in {@link #configureContentView} using a {@link FragmentTransaction} will NOT
             * have views until {@link Activity#onCreate} finishes (they would if they were inflated
             * from a layout), we need to do it here in {@link #onStart()}.
             *
             * (When {@link Fragment#onCreateView} is called is different in the former case and
             * in the latter case, unfortunately.)
             *
             * Also, we skip most of the work in it if the activity is a re-created one.
             * (so the argument.)
             */
            configureFragments(!mIsRecreatedInstance);
        }
        super.onStart();
    }

    @Override
    protected void onPause() {
        mProviderStatusWatcher.stop();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSaveServiceListener);

        super.onPause();

        if (Flags.getInstance(this).getBoolean(Experiments.PULL_TO_REFRESH)) {
            ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
            onSyncStateUpdated();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        if (Flags.getInstance(this).getBoolean(Experiments.PULL_TO_REFRESH)) {
            mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                            | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                            | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                    mSyncStatusObserver);
            onSyncStateUpdated();
        }
        mAllFragment.maybeShowHamburgerFeatureHighlight();
        initializeFabVisibility();

        mSaveServiceListener = new SaveServiceListener();
        LocalBroadcastManager.getInstance(this).registerReceiver(mSaveServiceListener,
                new IntentFilter(ContactSaveService.BROADCAST_ACTION_GROUP_DELETED));
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(this);
        super.onDestroy();
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            boolean searchMode = mRequest.isSearchMode();
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = AccountFilterUtil.createContactsFilter(this);
                    break;
                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                    break;

                case ContactsRequest.ACTION_FREQUENT:
                case ContactsRequest.ACTION_STREQUENT:
                case ContactsRequest.ACTION_STARRED:
                case ContactsRequest.ACTION_VIEW_CONTACT:
                default:
                    break;
            }

            if (filter != null) {
                mAllFragment.setContactListFilter(filter);
                searchMode = false;
            }

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

            mAllFragment.getActionBarAdapter().setSearchMode(searchMode);
            configureContactListFragmentForRequest();
        }

        mAllFragment.configureContactListFragment();
    }

    private void initializeFabVisibility() {
        mFloatingActionButtonContainer.setVisibility(shouldHideFab() ? View.GONE : View.VISIBLE);
        mFloatingActionButtonController.resetIn();
        wasLastFabAnimationScaleIn = !shouldHideFab();
    }

    private boolean shouldHideFab() {
        if (mAllFragment.getActionBarAdapter() == null) return false;
        return mAllFragment.getActionBarAdapter().isSearchMode()
                || mAllFragment.getActionBarAdapter().isSelectionMode();
    }

    public void showFabWithAnimation(boolean showFab) {
        if (mFloatingActionButtonContainer == null) {
            return;
        }
        if (showFab) {
            if (!wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleIn(0);
            }
            wasLastFabAnimationScaleIn = true;

        } else {
            if (wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleOut();
            }
            wasLastFabAnimationScaleIn = false;
        }
    }

    private void setQueryTextToFragment(String query) {
        mAllFragment.setQueryString(query, true);
        mAllFragment.setVisibleScrollbarEnabled(!mAllFragment.isSearchMode());
    }

    private void configureContactListFragmentForRequest() {
        Uri contactUri = mRequest.getContactUri();
        if (contactUri != null) {
            mAllFragment.setSelectedContactUri(contactUri);
        }

        setQueryTextToFragment(mAllFragment.getActionBarAdapter().getQueryString());

        if (mRequest.isDirectorySearchEnabled()) {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    @Override
    public void onFiltersLoaded(List<ContactListFilter> accountFilterItems) {
        super.onFiltersLoaded(accountFilterItems);
        initializeFabVisibility();
    }

    @Override
    public void onProviderStatusChange() {
        reloadGroupsAndFiltersIfNeeded();
        updateViewConfiguration(false);
    }

    private void reloadGroupsAndFiltersIfNeeded() {
        final int providerStatus = mProviderStatusWatcher.getProviderStatus();
        final Menu menu = mNavigationView.getMenu();
        final MenuItem groupsMenuItem = menu.findItem(R.id.nav_groups);
        final SubMenu subMenu = groupsMenuItem.getSubMenu();

        // Reload groups and filters if provider status changes to "normal" and there's no groups
        // loaded or just a "Create new..." menu item is in the menu.
        if (subMenu != null && subMenu.size() <= 1 && providerStatus == ProviderStatus.STATUS_NORMAL
                && !mProviderStatus.equals(providerStatus)) {
            loadGroupsAndFilters();
        }
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        int providerStatus = mProviderStatusWatcher.getProviderStatus();
        if (!forceUpdate && (mProviderStatus != null)
                && (mProviderStatus.equals(providerStatus))) return;
        mProviderStatus = providerStatus;

        View contactsUnavailableView = findViewById(R.id.contacts_unavailable_view);

        // Change in CP2's provider status may not take effect immediately, see b/30566908.
        // So we need to handle the case where provider status is STATUS_EMPTY and there is
        // actually at least one real account (not "local" account) on device.
        if ((mProviderStatus.equals(ProviderStatus.STATUS_EMPTY) && hasNonLocalAccount())
                || mProviderStatus.equals(ProviderStatus.STATUS_NORMAL)) {
            // Ensure that the mContactsView is visible; we may have made it invisible below.
            contactsUnavailableView.setVisibility(View.GONE);
            if (mContactsView != null) {
                mContactsView.setVisibility(View.VISIBLE);
            }

            if (mAllFragment != null) {
                getFragmentManager().beginTransaction()
                        .show(mAllFragment)
                        .commitAllowingStateLoss();
                mAllFragment.setContactsAvailable(areContactsAvailable());
                mAllFragment.setEnabled(true);
            }
        } else {
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            // Setting up the page so that the user can still use the app
            // even without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
                transaction.hide(mAllFragment);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                        new ContactsUnavailableFragmentListener());
                transaction.replace(
                        R.id.contacts_unavailable_container, mContactsUnavailableFragment);
            }
            transaction.commitAllowingStateLoss();
            mContactsUnavailableFragment.updateStatus(mProviderStatus);

            // Show the contactsUnavailableView, and hide the mContactsView so that we don't
            // see it sliding in underneath the contactsUnavailableView at the edges.
            contactsUnavailableView.setVisibility(View.VISIBLE);
            if (mContactsView != null) {
                mContactsView.setVisibility(View.GONE);
            }
        }

        invalidateOptionsMenuIfNeeded();
    }

    // Returns true if there are real accounts (not "local" account) in the list of accounts.
    private boolean hasNonLocalAccount() {
        final List<AccountWithDataSet> allAccounts =
                AccountTypeManager.getInstance(this).getAccounts(/* contactWritableOnly */ false);
        if (allAccounts == null || allAccounts.size() == 0) {
            return false;
        }
        if (allAccounts.size() > 1) {
            return true;
        }
        return !allAccounts.get(0).isLocalAccount();
    }

    private class ContactsUnavailableFragmentListener
            implements OnContactsUnavailableActionListener {
        ContactsUnavailableFragmentListener() {}

        @Override
        public void onCreateNewContactAction() {
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this,
                    EditorIntents.createCompactInsertContactIntent(PeopleActivity.this));
        }

        @Override
        public void onAddAccountAction() {
            final Intent intent = ImplicitIntentsUtil.getIntentForAddingGoogleAccount();
            ImplicitIntentsUtil.startActivityOutsideApp(PeopleActivity.this, intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            showImportExportDialogFragment();
        }
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (mAllFragment != null
                || mAllFragment.getOptionsMenuContactsAvailable() != areContactsAvailable()) {
            invalidateOptionsMenu();
        }
    }

    private void showImportExportDialogFragment(){
        ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                PeopleActivity.class, ImportExportDialogFragment.EXPORT_MODE_ALL_CONTACTS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment

        // Bring up the search UI if the user starts typing
        final int unicodeChar = event.getUnicodeChar();
        if ((unicodeChar != 0)
                // If COMBINING_ACCENT is set, it's not a unicode character.
                && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                && !Character.isWhitespace(unicodeChar)) {
            if (mAllFragment.getActionBarAdapter().isSelectionMode()) {
                // Ignore keyboard input when in selection mode.
                return true;
            }
            String query = new String(new int[]{unicodeChar}, 0, 1);
            if (!mAllFragment.getActionBarAdapter().isSearchMode()) {
                mAllFragment.getActionBarAdapter().setSearchMode(true);
                mAllFragment.getActionBarAdapter().setQueryString(query);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }

        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if (mAllFragment.getActionBarAdapter().isSelectionMode()) {
            mAllFragment.getActionBarAdapter().setSelectionMode(false);
            mAllFragment.displayCheckBoxes(false);
        } else if (mAllFragment.getActionBarAdapter().isSearchMode()) {
            mAllFragment.getActionBarAdapter().setSearchMode(false);
            if (mAllFragment.wasSearchResultClicked()) {
                mAllFragment.resetSearchResultClicked();
            } else {
                Logger.logScreenView(this, ScreenType.SEARCH_EXIT);
                Logger.logSearchEvent(mAllFragment.createSearchState());
            }
        } else if (!isAllContactsFilter(mAllFragment.getFilter())) {
            switchToAllContacts();
        } else {
            super.onBackPressed();
        }
    }

    public void onFabClicked() {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            extras = new Bundle();
        }
        final ContactListFilter filter = mAllFragment.getFilter();
        // If we are in account view, we pass the account explicitly in order to
        // create contact in the account. This will prevent the default account dialog
        // from being displayed.
        if (!isAllContactsFilter(filter) && !isDeviceContactsFilter(filter)) {
            final Account account = new Account(filter.accountName, filter.accountType);
            extras.putParcelable(Intents.Insert.EXTRA_ACCOUNT, account);
            extras.putString(Intents.Insert.EXTRA_DATA_SET, filter.dataSet);
        }
        intent.putExtras(extras);
        try {
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(PeopleActivity.this, R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAllContactsFilter(ContactListFilter filter) {
        return filter != null && filter.isContactsFilterType();
    }

    private boolean isDeviceContactsFilter(ContactListFilter filter) {
        return filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS;
    }

    @Override
    protected boolean shouldFinish() {
        return false;
    }

    @Override
    protected ContactListFilter getContactListFilter() {
        return mAllFragment.getFilter();
    }

    private void onGroupDeleted(Intent intent) {
        if (!ContactSaveService.canUndo(intent)) {
            return;
        }
        Snackbar.make(mLayoutRoot, getString(R.string.groupDeletedToast), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ContactSaveService.startService(PeopleActivity.this,
                                ContactSaveService.createUndoIntent(PeopleActivity.this, intent));
                    }
                }).show();
    }


    private class SaveServiceListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ContactSaveService.BROADCAST_ACTION_GROUP_DELETED:
                    onGroupDeleted(intent);
                    break;
            }
        }
    }
}
