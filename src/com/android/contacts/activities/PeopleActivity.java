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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.QuickContact;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.common.Experiments;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.ProviderStatusWatcher;
import com.android.contacts.common.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.common.logging.ListEvent;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ContactMultiDeletionInteraction;
import com.android.contacts.interactions.ContactMultiDeletionInteraction.MultiContactDeleteListener;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.MultiSelectContactsListFragment.OnCheckBoxListActionListener;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contacts.util.SyncUtil;
import com.android.contactsbind.experiments.Flags;
import com.android.contacts.widget.FloatingActionButtonBehavior;
import com.google.android.libraries.material.featurehighlight.FeatureHighlight;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends ContactsDrawerActivity implements
        View.OnCreateContextMenuListener,
        View.OnClickListener,
        ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener,
        ProviderStatusListener,
        MultiContactDeleteListener,
        DefaultContactBrowseListFragment.FeatureHighlightCallback {

    private static final String TAG = "PeopleActivity";

    private static final String ENABLE_DEBUG_OPTIONS_HIDDEN_CODE = "debug debug!";

    private static final String TAG_ALL = "contacts-all";

    private static final int ACTIVITY_REQUEST_CODE_SHARE = 0;

    private final DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private ActionBarAdapter mActionBarAdapter;
    private List<AccountWithDataSet> mWritableAccounts;
    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private boolean wasLastFabAnimationScaleIn = false;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusWatcher mProviderStatusWatcher;
    private Integer mProviderStatus;

    private BroadcastReceiver mSaveServiceListener;

    private boolean mOptionsMenuContactsAvailable;

    private CoordinatorLayout mLayoutRoot;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private DefaultContactBrowseListFragment mAllFragment;

    private View mContactsView;

    private boolean mEnableDebugMenuOptions;

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

    /**
     * This is to disable {@link #onOptionsItemSelected} when we trying to stop the activity.
     */
    private boolean mDisableOptionItemSelected;

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
        if (mActionBarAdapter.isSearchMode() || mActionBarAdapter.isSelectionMode()) {
            return;
        }

        final ContactListFilter filter = mContactListFilterController.getFilter();
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
        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);
        mContactListFilterController.addListener(this);

        mProviderStatusWatcher.addListener(this);

        mIsRecreatedInstance = (savedState != null);

        // Use FILTER_TYPE_ALL_ACCOUNTS filter if the activity is not a re-created one.
        // This is useful when user upgrades app while an account filter or a custom filter was
        // stored in sharedPreference in a previous version of Contacts app.
        final ContactListFilter filter = mIsRecreatedInstance
                ? mContactListFilterController.getFilter() : createContactsFilter();
        persistFilterIfNeeded(filter);

        createViewsAndFragments(savedState);

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        getWindow().setBackgroundDrawable(null);
    }

    private void maybeShowHamburgerFeatureHighlight() {
        if (!mActionBarAdapter.isSearchMode() && !mActionBarAdapter.isSelectionMode()
                && SharedPreferenceUtil.getShouldShowHamburgerPromo(this)) {
            final FeatureHighlight hamburgerFeatureHighlight =
                    mActionBarAdapter.getHamburgerFeatureHighlight();
            if (hamburgerFeatureHighlight != null) {
                hamburgerFeatureHighlight.show(this);
                SharedPreferenceUtil.setHamburgerPromoDisplayedBefore(this);
            }
        }
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
        mActionBarAdapter.initialize(null, mRequest);

        mContactListFilterController.checkFilterValidity(false);

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

    private void createViewsAndFragments(Bundle savedState) {
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

        mAllFragment.setFeatureHighlightCallback(this);
        mAllFragment.setOnContactListActionListener(new ContactBrowserActionListener());
        mAllFragment.setCheckBoxListListener(new CheckBoxListListener());
        mAllFragment.setListType(mContactListFilterController.getFilterListType());

        // Hide all fragments for now.  We adjust visibility when we get
        // onAction(Action.STOP_SEARCH_AND_SELECTION_MODE) from ActionBarAdapter.
        transaction.hide(mAllFragment);

        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();

        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(), mToolbar);
        mActionBarAdapter.initialize(savedState, mRequest);

        // Configure floating action button
        mFloatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        final ImageButton floatingActionButton
                = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                mFloatingActionButtonContainer, floatingActionButton);
        initializeFabVisibility();

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
        mOptionsMenuContactsAvailable = false;
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

        // Re-register the listener, which may have been cleared when onSaveInstanceState was
        // called.  See also: onSaveInstanceState
        mActionBarAdapter.setListener(this);
        mDisableOptionItemSelected = false;
        updateFragmentsVisibility();

        if (Flags.getInstance(this).getBoolean(Experiments.PULL_TO_REFRESH)) {
            mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                            | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                            | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                    mSyncStatusObserver);
            onSyncStateUpdated();
        }
        maybeShowHamburgerFeatureHighlight();

        mSaveServiceListener = new SaveServiceListener();
        LocalBroadcastManager.getInstance(this).registerReceiver(mSaveServiceListener,
                new IntentFilter(ContactSaveService.BROADCAST_ACTION_GROUP_DELETED));
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(this);

        // Some of variables will be null if this Activity redirects Intent.
        // See also onCreate() or other methods called during the Activity's initialization.
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        if (mContactListFilterController != null) {
            mContactListFilterController.removeListener(this);
        }

        super.onDestroy();
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            boolean searchMode = mRequest.isSearchMode();
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = createContactsFilter();
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
                mContactListFilterController.setContactListFilter(filter, /* persistent */ false);
                searchMode = false;
            }

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

            mActionBarAdapter.setSearchMode(searchMode);
            configureContactListFragmentForRequest();
        }

        configureContactListFragment();

        invalidateOptionsMenuIfNeeded();
    }

    private void initializeFabVisibility() {
        final boolean hideFab = mActionBarAdapter.isSearchMode()
                || mActionBarAdapter.isSelectionMode()
                || !shouldShowFabForAccount();
        mFloatingActionButtonContainer.setVisibility(hideFab ? View.GONE : View.VISIBLE);
        mFloatingActionButtonController.resetIn();
        wasLastFabAnimationScaleIn = !hideFab;
    }

    private boolean shouldShowFabForAccount() {
        return isCurrentAccountFilterWritable()
                || isAllContactsFilter(mContactListFilterController.getFilter());
    }

    private boolean isCurrentAccountFilterWritable() {
        final ContactListFilter currentFilter = mContactListFilterController.getFilter();
        final AccountWithDataSet accountOfCurrentFilter = new AccountWithDataSet(
                currentFilter.accountName, currentFilter.accountType, currentFilter.dataSet);
        return accountOfCurrentFilter.isLocalAccount()
                || (mWritableAccounts != null
                && mWritableAccounts.contains(accountOfCurrentFilter));
    }

    private void showFabWithAnimation(boolean showFab) {
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

    @Override
    public void onContactListFilterChanged() {
        if (mAllFragment == null || !mAllFragment.isAdded()) {
            return;
        }

        setFilterAndUpdateTitle(mContactListFilterController.getFilter());
        // Scroll to top after filter is changed.
        mAllFragment.getListView().setSelection(0);
        showFabWithAnimation(shouldShowFabForAccount());

        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                mAllFragment.displayCheckBoxes(true);
                startSearchOrSelectionMode();
                break;
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                if (!mIsRecreatedInstance) {
                    Logger.logScreenView(this, ScreenType.SEARCH);
                }
                startSearchOrSelectionMode();
                break;
            case ActionBarAdapter.Listener.Action.BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE:
                showFabWithAnimation(shouldShowFabForAccount());
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                // If queryString is empty, fragment data will not be reloaded,
                // so hamburger promo should be checked now.
                // If not empty, promo should be checked and displayed after reloading. (b/30706521)
                if (TextUtils.isEmpty(mAllFragment.getQueryString())) {
                    maybeShowHamburgerFeatureHighlight();
                }
                setQueryTextToFragment("");
                updateFragmentsVisibility();
                invalidateOptionsMenu();
                showFabWithAnimation(shouldShowFabForAccount());
                // Determine whether the account has pullToRefresh feature
                if (Flags.getInstance(this).getBoolean(Experiments.PULL_TO_REFRESH)) {
                    setSwipeRefreshLayoutEnabledOrNot(mContactListFilterController.getFilter());
                }
                break;
            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                final String queryString = mActionBarAdapter.getQueryString();
                setQueryTextToFragment(queryString);
                updateDebugOptionsVisibility(
                        ENABLE_DEBUG_OPTIONS_HIDDEN_CODE.equals(queryString));
                break;
            default:
                throw new IllegalStateException("Unkonwn ActionBarAdapter action: " + action);
        }
    }

    private void startSearchOrSelectionMode() {
        configureFragments(false /* from request */);
        updateFragmentsVisibility();
        invalidateOptionsMenu();
        showFabWithAnimation(/* showFab */ false);
        if (!SharedPreferenceUtil.getHamburgerPromoTriggerActionHappenedBefore(this)) {
            SharedPreferenceUtil.setHamburgerPromoTriggerActionHappenedBefore(this);
        }
    }

    @Override
    public void onUpButtonPressed() {
        onBackPressed();
    }

    private void updateDebugOptionsVisibility(boolean visible) {
        if (mEnableDebugMenuOptions != visible) {
            mEnableDebugMenuOptions = visible;
            invalidateOptionsMenu();
        }
    }

    /**
     * Updates the fragment/view visibility according to the current mode, such as
     * {@link ActionBarAdapter#isSearchMode()}.
     */
    private void updateFragmentsVisibility() {
        if (!mActionBarAdapter.isSelectionMode()) {
            mAllFragment.displayCheckBoxes(false);
        }
        invalidateOptionsMenu();
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

        setFilterAndUpdateTitle(mContactListFilterController.getFilter());
        setQueryTextToFragment(mActionBarAdapter.getQueryString());

        if (mRequest.isDirectorySearchEnabled()) {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    private void configureContactListFragment() {
        // Filter may be changed when this Activity is in background.
        setFilterAndUpdateTitle(mContactListFilterController.getFilter());

        mAllFragment.setVerticalScrollbarPosition(getScrollBarPosition());
        mAllFragment.setSelectionVisible(false);
    }

    private int getScrollBarPosition() {
        return isRTL() ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT;
    }

    private boolean isRTL() {
        final Locale locale = Locale.getDefault();
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public void onFiltersLoaded(List<ContactListFilter> accountFilterItems) {
        super.onFiltersLoaded(accountFilterItems);
        mWritableAccounts =
                AccountTypeManager.getInstance(this).getAccounts(/* contactWritableOnly */ true);
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
                mAllFragment.setEnabled(true);
            }
        } else {
            // Setting up the page so that the user can still use the app
            // even without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                        new ContactsUnavailableFragmentListener());
                getFragmentManager().beginTransaction()
                        .replace(R.id.contacts_unavailable_container, mContactsUnavailableFragment)
                        .commitAllowingStateLoss();
            }
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

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        ContactBrowserActionListener() {}

        @Override
        public void onSelectionChange() {

        }

        @Override
        public void onViewContactAction(int position, Uri contactLookupUri,
                boolean isEnterpriseContact) {
            if (isEnterpriseContact) {
                // No implicit intent as user may have a different contacts app in work profile.
                QuickContact.showQuickContact(PeopleActivity.this, new Rect(), contactLookupUri,
                        QuickContactActivity.MODE_FULLY_EXPANDED, null);
            } else {
                final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(
                        PeopleActivity.this, contactLookupUri,
                        QuickContactActivity.MODE_FULLY_EXPANDED);
                final int previousScreen;
                if (mAllFragment.isSearchMode()) {
                    previousScreen = ScreenType.SEARCH;
                } else {
                    if (isAllContactsFilter(mContactListFilterController.getFilter())) {
                        if (position < mAllFragment.getAdapter().getNumberOfFavorites()) {
                            previousScreen = ScreenType.FAVORITES;
                        } else {
                            previousScreen = ScreenType.ALL_CONTACTS;
                        }
                    } else {
                        previousScreen = ScreenType.LIST_ACCOUNT;
                    }
                }
                Logger.logListEvent(ListEvent.ActionType.CLICK,
                        /* listType */ getListTypeIncludingSearch(),
                        /* count */ mAllFragment.getAdapter().getCount(),
                        /* clickedIndex */ position, /* numSelected */ 0);
                intent.putExtra(QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE, previousScreen);
                ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
            }
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
            ContactListFilter currentFilter = mAllFragment.getFilter();
            if (currentFilter != null
                    && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = createContactsFilter();
                setFilterAndUpdateTitle(filter);
            } else {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                setFilterAndUpdateTitle(filter, /* restoreSelectedUri */ false);
            }
            persistFilterIfNeeded(filter);
        }
    }

    private final class CheckBoxListListener implements OnCheckBoxListActionListener {
        @Override
        public void onStartDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(true);
            invalidateOptionsMenu();
        }

        @Override
        public void onSelectedContactIdsChanged() {
            mActionBarAdapter.setSelectionCount(mAllFragment.getSelectedContactIds().size());
            invalidateOptionsMenu();
        }

        @Override
        public void onStopDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(false);
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!areContactsAvailable()) {
            // If contacts aren't available, hide all menu items.
            return false;
        }
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.people_options, menu);

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

        if (mAllFragment != null && mAllFragment.isOptionsMenuChanged()) {
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

        final boolean isSearchOrSelectionMode = mActionBarAdapter.isSearchMode()
                || mActionBarAdapter.isSelectionMode();
        makeMenuItemVisible(menu, R.id.menu_search, !isSearchOrSelectionMode);

        final boolean showSelectedContactOptions = mActionBarAdapter.isSelectionMode()
                && mAllFragment.getSelectedContactIds().size() != 0;
        makeMenuItemVisible(menu, R.id.menu_share, showSelectedContactOptions);
        makeMenuItemVisible(menu, R.id.menu_delete, showSelectedContactOptions);
        final boolean showLinkContactsOptions = mActionBarAdapter.isSelectionMode()
                && mAllFragment.getSelectedContactIds().size() > 1;
        makeMenuItemVisible(menu, R.id.menu_join, showLinkContactsOptions);

        // Debug options need to be visible even in search mode.
        makeMenuItemVisible(menu, R.id.export_database, mEnableDebugMenuOptions &&
                hasExportIntentHandler());

        return true;
    }

    private boolean hasExportIntentHandler() {
        final Intent intent = new Intent();
        intent.setAction("com.android.providers.contacts.DUMP_DATABASE");
        final List<ResolveInfo> receivers = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receivers != null && receivers.size() > 0;
    }

    private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
        final MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDisableOptionItemSelected) {
            return false;
        }

        switch (item.getItemId()) {
            case android.R.id.home: {
                // The home icon on the action bar is pressed
                if (mActionBarAdapter.isUpShowing()) {
                    // "UP" icon press -- should be treated as "back".
                    onBackPressed();
                }
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_share: {
                shareSelectedContacts();
                return true;
            }
            case R.id.menu_join: {
                Logger.logListEvent(ListEvent.ActionType.LINK,
                        /* listType */ getListTypeIncludingSearch(),
                        /* count */ mAllFragment.getAdapter().getCount(), /* clickedIndex */ -1,
                        /* numSelected */ mAllFragment.getAdapter().getSelectedContactIds().size());
                joinSelectedContacts();
                return true;
            }
            case R.id.menu_delete: {
                deleteSelectedContacts();
                return true;
            }
            case R.id.export_database: {
                final Intent intent = new Intent("com.android.providers.contacts.DUMP_DATABASE");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                ImplicitIntentsUtil.startActivityOutsideApp(this, intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportExportDialogFragment(){
        ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                PeopleActivity.class, ImportExportDialogFragment.EXPORT_MODE_ALL_CONTACTS);
    }

    @Override
    public boolean onSearchRequested() { // Search key pressed.
        if (!mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSearchMode(true);
        }
        return true;
    }

    /**
     * Share all contacts that are currently selected in mAllFragment. This method is pretty
     * inefficient for handling large numbers of contacts. I don't expect this to be a problem.
     */
    private void shareSelectedContacts() {
        final StringBuilder uriListBuilder = new StringBuilder();
        for (Long contactId : mAllFragment.getSelectedContactIds()) {
            final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
            final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), contactUri);
            if (lookupUri == null) {
                continue;
            }
            final List<String> pathSegments = lookupUri.getPathSegments();
            if (pathSegments.size() < 2) {
                continue;
            }
            final String lookupKey = pathSegments.get(pathSegments.size() - 2);
            if (uriListBuilder.length() > 0) {
                uriListBuilder.append(':');
            }
            uriListBuilder.append(Uri.encode(lookupKey));
        }
        if (uriListBuilder.length() == 0) {
            return;
        }
        final Uri uri = Uri.withAppendedPath(
                Contacts.CONTENT_MULTI_VCARD_URI,
                Uri.encode(uriListBuilder.toString()));
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        try {
            startActivityForResult(Intent.createChooser(intent, getResources().getQuantityString(
                    R.plurals.title_share_via,
                    /* quantity */ mAllFragment.getSelectedContactIds().size()))
                    , ACTIVITY_REQUEST_CODE_SHARE);
        } catch (final ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void joinSelectedContacts() {
        final Intent intent = ContactSaveService.createJoinSeveralContactsIntent(
                this, mAllFragment.getSelectedContactIdsArray());
        this.startService(intent);

        mActionBarAdapter.setSelectionMode(false);
    }

    private void deleteSelectedContacts() {
        ContactMultiDeletionInteraction.start(PeopleActivity.this,
                mAllFragment.getSelectedContactIds());
    }

    @Override
    public void onDeletionFinished() {
        // The parameters count and numSelected are both the number of contacts before deletion.
        Logger.logListEvent(ListEvent.ActionType.DELETE,
                /* listType */ getListTypeIncludingSearch(),
                /* count */ mAllFragment.getAdapter().getCount(), /* clickedIndex */ -1,
                /* numSelected */ mAllFragment.getSelectedContactIds().size());
        mActionBarAdapter.setSelectionMode(false);
    }

    private int getListTypeIncludingSearch() {
        return mAllFragment.isSearchMode()
                ? ListEvent.ListType.SEARCH_RESULT : mAllFragment.getListType();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mAllFragment.onPickerResult(data);
                }
            case ACTIVITY_REQUEST_CODE_SHARE:
                Logger.logListEvent(ListEvent.ActionType.SHARE,
                    /* listType */ getListTypeIncludingSearch(),
                    /* count */ mAllFragment.getAdapter().getCount(), /* clickedIndex */ -1,
                    /* numSelected */ mAllFragment.getAdapter().getSelectedContactIds().size());

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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment

        // Bring up the search UI if the user starts typing
        final int unicodeChar = event.getUnicodeChar();
        if ((unicodeChar != 0)
                // If COMBINING_ACCENT is set, it's not a unicode character.
                && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                && !Character.isWhitespace(unicodeChar)) {
            if (mActionBarAdapter.isSelectionMode()) {
                // Ignore keyboard input when in selection mode.
                return true;
            }
            String query = new String(new int[]{unicodeChar}, 0, 1);
            if (!mActionBarAdapter.isSearchMode()) {
                mActionBarAdapter.setSearchMode(true);
                mActionBarAdapter.setQueryString(query);
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
        } else if (mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
            mAllFragment.displayCheckBoxes(false);
        } else if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);

            if (mAllFragment.wasSearchResultClicked()) {
                mAllFragment.resetSearchResultClicked();
            } else {
                Logger.logScreenView(this, ScreenType.SEARCH_EXIT);
                Logger.logSearchEvent(mAllFragment.createSearchState());
            }
        } else if (!isAllContactsFilter(mContactListFilterController.getFilter())) {
            switchToAllContacts();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mActionBarAdapter.onSaveInstanceState(outState);

        // Clear the listener to make sure we don't get callbacks after onSaveInstanceState,
        // in order to avoid doing fragment transactions after it.
        // TODO Figure out a better way to deal with the issue.
        mDisableOptionItemSelected = true;
        mActionBarAdapter.setListener(null);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setFocusOnSearchView();
        }
    }

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                onFabClicked();
                break;
            default:
                Log.wtf(TAG, "Unexpected onClick event from " + view);
        }
    }

    public void onFabClicked() {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            final ContactListFilter filter = mContactListFilterController.getFilter();
            // If we are in account view, we pass the account explicitly in order to
            // create contact in the account. This will prevent the default account dialog
            // from being displayed.
            if (!isAllContactsFilter(filter) && !isDeviceContactsFilter(filter)) {
                final Account account = new Account(filter.accountName, filter.accountType);
                extras.putParcelable(Intents.Insert.EXTRA_ACCOUNT, account);
                extras.putString(Intents.Insert.EXTRA_DATA_SET, filter.dataSet);
            }
            intent.putExtras(extras);
        }
        try {
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(PeopleActivity.this, R.string.missing_app,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setFilterAndUpdateTitle(ContactListFilter filter) {
        setFilterAndUpdateTitle(filter, true);
    }

    private void setFilterAndUpdateTitle(ContactListFilter filter, boolean restoreSelectedUri) {
        mAllFragment.setFilter(filter, restoreSelectedUri);

        mAllFragment.setListType(mContactListFilterController.getFilterListType());

        updateFilterMenu(filter);

        if (getSupportActionBar() != null) {
            String actionBarTitle;
            if (filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS) {
                actionBarTitle = getString(R.string.account_phone);
            } else if (!TextUtils.isEmpty(filter.accountName)) {
                actionBarTitle = getActionBarTitleForAccount(filter);
            } else {
                actionBarTitle = getString(R.string.contactsList);
            }
            getSupportActionBar().setTitle(actionBarTitle);
            if (CompatUtils.isNCompatible()) {
                this.setTitle(actionBarTitle);
                getWindow().getDecorView()
                        .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }
        }

        // Determine whether the account has pullToRefresh feature
        if (Flags.getInstance(this).getBoolean(Experiments.PULL_TO_REFRESH)) {
            setSwipeRefreshLayoutEnabledOrNot(filter);
        }
    }

    private void setSwipeRefreshLayoutEnabledOrNot(ContactListFilter filter) {
        final SwipeRefreshLayout swipeRefreshLayout = mAllFragment.getSwipeRefreshLayout();
        if (swipeRefreshLayout == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Can not load swipeRefreshLayout, swipeRefreshLayout is null");
            }
            return;
        }

        swipeRefreshLayout.setRefreshing(false);
        swipeRefreshLayout.setEnabled(false);

        if (filter != null && !mActionBarAdapter.isSearchMode()
                && !mActionBarAdapter.isSelectionMode()) {
            final List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(this)
                    .getAccounts(/* contactsWritableOnly */ true);
            if (filter.isSyncable(accounts)) {
                swipeRefreshLayout.setEnabled(true);
            }
        }
    }

    private String getActionBarTitleForAccount(ContactListFilter filter) {
        if (GoogleAccountType.ACCOUNT_TYPE.equals(filter.accountType)) {
            return getString(R.string.title_from_google);
        }
        return getString(R.string.title_from_other_accounts, filter.accountName);
    }

    // Persist filter only when it's of the type FILTER_TYPE_ALL_ACCOUNTS.
    private void persistFilterIfNeeded(ContactListFilter filter) {
        mContactListFilterController.setContactListFilter(filter,
                /* persistent */ isAllContactsFilter(filter));
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
        return mContactListFilterController.getFilter();
    }

    @Override
    public void onLoadFinishedCallback() {
        maybeShowHamburgerFeatureHighlight();
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
