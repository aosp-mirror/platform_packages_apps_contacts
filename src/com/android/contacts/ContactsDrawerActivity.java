/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts;

import android.accounts.Account;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;
import android.support.annotation.LayoutRes;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.contacts.activities.ActionBarAdapter;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferenceActivity;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.editor.CompactContactEditorFragment;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.group.GroupsFragment;
import com.android.contacts.group.GroupsFragment.GroupsListener;
import com.android.contacts.interactions.AccountFiltersFragment;
import com.android.contacts.interactions.AccountFiltersFragment.AccountFiltersListener;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contactsbind.HelpUtils;
import com.android.contactsbind.ObjectFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A common superclass for Contacts activities with a navigation drawer.
 */
public abstract class ContactsDrawerActivity extends AppCompatContactsActivity implements
        AccountFiltersListener,
        GroupsListener,
        NavigationView.OnNavigationItemSelectedListener,
        SelectAccountDialogFragment.Listener {

    /** Possible views of Contacts app. */
    public enum ContactsView {
        NONE,
        ALL_CONTACTS,
        DUPLICATES,
        GROUP_VIEW,
        ACCOUNT_VIEW,
    }

    protected static String TAG = "ContactsDrawerActivity";

    private static final String TAG_GROUPS = "groups";
    private static final String TAG_FILTERS = "filters";
    private static final String TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    private static final String KEY_NEW_GROUP_ACCOUNT = "newGroupAccount";
    private static final String KEY_CONTACTS_VIEW = "contactsView";

    protected ContactsView mCurrentView;

    private class ContactsActionBarDrawerToggle extends ActionBarDrawerToggle {

        private Runnable mRunnable;
        private boolean mMenuClickedBefore = SharedPreferenceUtil.getHamburgerMenuClickedBefore(
                ContactsDrawerActivity.this);

        public ContactsActionBarDrawerToggle(AppCompatActivity activity, DrawerLayout drawerLayout,
                Toolbar toolbar, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
            super(activity, drawerLayout, toolbar, openDrawerContentDescRes,
                    closeDrawerContentDescRes);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            if (!mMenuClickedBefore) {
                SharedPreferenceUtil.setHamburgerMenuClickedBefore(ContactsDrawerActivity.this);
                mMenuClickedBefore = true;
            }
            invalidateOptionsMenu();
            // Stop search and selection mode like Gmail and Keep. Otherwise, if user switches to
            // another fragment in navigation drawer, the current search/selection mode will be
            // overlaid by the action bar of the newly-created fragment.
            stopSearchAndSelection();
        }

        private void stopSearchAndSelection() {
            final MultiSelectContactsListFragment listFragment;
            if (isAllContactsView() || isAccountView()) {
                listFragment = getAllFragment();
            } else if (isGroupView()) {
                listFragment = getGroupFragment();
            } else {
                listFragment = null;
            }
            if (listFragment == null) {
                return;
            }
            final ActionBarAdapter actionBarAdapter = listFragment.getActionBarAdapter();
            if (actionBarAdapter == null) {
                return;
            }
            if (actionBarAdapter.isSearchMode()) {
                actionBarAdapter.setSearchMode(false);
            } else if (actionBarAdapter.isSelectionMode()) {
                actionBarAdapter.setSelectionMode(false);
            }
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            invalidateOptionsMenu();
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            super.onDrawerStateChanged(newState);
            // Set transparent status bar when drawer starts to move.
            if (newState != DrawerLayout.STATE_IDLE) {
                makeStatusBarTransparent();
            }
            if (mRunnable != null && newState == DrawerLayout.STATE_IDLE) {
                mRunnable.run();
                mRunnable = null;
            }
        }

        public void runWhenIdle(Runnable runnable) {
            mRunnable = runnable;
        }
    }

    protected ContactListFilterController mContactListFilterController;
    protected DrawerLayout mDrawer;
    protected ContactsActionBarDrawerToggle mToggle;
    protected Toolbar mToolbar;
    protected NavigationView mNavigationView;
    protected GroupsFragment mGroupsFragment;
    protected AccountFiltersFragment mAccountFiltersFragment;

    // The account the new group will be created under.
    private AccountWithDataSet mNewGroupAccount;

    // Checkable menu item lookup maps. Every map declared here should be added to
    // clearCheckedMenus() so that they can be cleared.
    // TODO find a better way to handle selected menu item state, when switching to fragments.
    protected Map<Long, MenuItem> mGroupMenuMap = new HashMap<>();
    protected Map<ContactListFilter, MenuItem> mFilterMenuMap = new HashMap<>();
    protected Map<Integer, MenuItem> mIdMenuMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);

        super.setContentView(R.layout.contacts_drawer_activity);

        // Set up the action bar.
        mToolbar = getView(R.id.toolbar);
        setSupportActionBar(mToolbar);

        // Add shadow under toolbar.
        ViewUtil.addRectangularOutlineProvider(findViewById(R.id.toolbar_parent), getResources());

        // Set up hamburger button.
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mToggle = new ContactsActionBarDrawerToggle(this, mDrawer, mToolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawer.setDrawerListener(mToggle);
        mToggle.syncState();

        // Set up navigation mode.
        if (savedState != null) {
            mCurrentView = ContactsView.values()[savedState.getInt(KEY_CONTACTS_VIEW)];
        } else {
            mCurrentView = ContactsView.ALL_CONTACTS;
        }

        // Set up hamburger menu items.
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        setUpMenu();

        loadGroupsAndFilters();

        if (savedState != null && savedState.containsKey(KEY_NEW_GROUP_ACCOUNT)) {
            mNewGroupAccount = AccountWithDataSet.unstringify(
                    savedState.getString(KEY_NEW_GROUP_ACCOUNT));
        }
    }

    private void setUpMenu() {
        final Menu menu = mNavigationView.getMenu();

        if (ObjectFactory.getDuplicatesFragment() == null) {
            menu.removeItem(R.id.nav_find_duplicates);
        } else {
            final MenuItem findDupMenu = menu.findItem(R.id.nav_find_duplicates);
            mIdMenuMap.put(R.id.nav_find_duplicates, findDupMenu);
            if (isDuplicatesView()) {
                updateMenuSelection(findDupMenu);
            }
        }

        if (!HelpUtils.isHelpAndFeedbackAvailable()) {
            menu.removeItem(R.id.nav_help);
        }

        final MenuItem allContactsMenu = menu.findItem(R.id.nav_all_contacts);
        mIdMenuMap.put(R.id.nav_all_contacts, allContactsMenu);
        if (isAllContactsView()) {
            updateMenuSelection(allContactsMenu);
        }
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNewGroupAccount != null) {
            outState.putString(KEY_NEW_GROUP_ACCOUNT, mNewGroupAccount.stringify());
        }
        outState.putInt(KEY_CONTACTS_VIEW, mCurrentView.ordinal());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            makeStatusBarTransparent();
        }
    }

    private void makeStatusBarTransparent() {
        // Avoid making status bar transparent when action bar's selection mode is on.
        if (getWindow().getStatusBarColor() !=
                ContextCompat.getColor(this, R.color.contextual_selection_bar_status_bar_color)
                        && CompatUtils.isLollipopCompatible()) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    // Set up fragment manager to load groups and filters.
    protected void loadGroupsAndFilters() {
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        addGroupsAndFiltersFragments(transaction);
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        final ViewGroup parent = (ViewGroup) findViewById(R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(this).inflate(layoutResID, parent);
    }

    protected void addGroupsAndFiltersFragments(FragmentTransaction transaction) {
        final FragmentManager fragmentManager = getFragmentManager();
        mGroupsFragment = (GroupsFragment) fragmentManager.findFragmentByTag(TAG_GROUPS);
        mAccountFiltersFragment = (AccountFiltersFragment)
                fragmentManager.findFragmentByTag(TAG_FILTERS);

        if (mGroupsFragment == null && ContactsUtils.areGroupWritableAccountsAvailable(this)) {
            mGroupsFragment = new GroupsFragment();
            transaction.add(mGroupsFragment, TAG_GROUPS);
        }

        if (mAccountFiltersFragment == null) {
            mAccountFiltersFragment = new AccountFiltersFragment();
            transaction.add(mAccountFiltersFragment, TAG_FILTERS);
        }

        if (ContactsUtils.areGroupWritableAccountsAvailable(this) && mGroupsFragment != null) {
            mGroupsFragment.setListener(this);
        }
        mAccountFiltersFragment.setListener(this);
    }

    @Override
    public void onGroupsLoaded(List<GroupListItem> groupListItems) {
        final Menu menu = mNavigationView.getMenu();
        final MenuItem groupsMenuItem = menu.findItem(R.id.nav_groups);
        final SubMenu subMenu = groupsMenuItem.getSubMenu();
        subMenu.removeGroup(R.id.nav_groups_items);
        mGroupMenuMap = new HashMap<>();

        final GroupMetadata groupMetaData = getGroupMetadata();

        if (groupListItems != null) {
            // Add each group
            for (final GroupListItem groupListItem : groupListItems) {
                if (GroupUtil.isEmptyFFCGroup(groupListItem)) {
                    continue;
                }
                final String title = groupListItem.getTitle();
                final MenuItem menuItem =
                        subMenu.add(R.id.nav_groups_items, Menu.NONE, Menu.NONE, title);
                mGroupMenuMap.put(groupListItem.getGroupId(), menuItem);
                if (isGroupView() && groupMetaData != null
                        && groupMetaData.groupId == groupListItem.getGroupId()) {
                    updateMenuSelection(menuItem);
                }
                menuItem.setIcon(R.drawable.ic_menu_label);
                menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        mToggle.runWhenIdle(new Runnable() {
                            @Override
                            public void run() {
                                onGroupMenuItemClicked(groupListItem.getGroupId(),
                                        groupListItem.getTitle());
                                updateMenuSelection(menuItem);
                            }
                        });
                        mDrawer.closeDrawer(GravityCompat.START);
                        return true;
                    }
                });
            }
        }

        // Don't show "Create new..." menu if there's no group-writable accounts available.
        if (!ContactsUtils.areGroupWritableAccountsAvailable(this)) {
            return;
        }

        // Create a menu item in the sub menu to add new groups
        final MenuItem menuItem = subMenu.add(R.id.nav_groups_items, Menu.NONE,
                Menu.NONE, getString(R.string.menu_new_group_action_bar));
        menuItem.setIcon(R.drawable.ic_add);
        menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mToggle.runWhenIdle(new Runnable() {
                    @Override
                    public void run() {
                        onCreateGroupMenuItemClicked();
                    }
                });
                mDrawer.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        if (isGroupView() && groupMetaData != null) {
            updateGroupMenu(groupMetaData);
        }
    }

    public void updateGroupMenu(GroupMetadata groupMetadata) {
        clearCheckedMenus();
        if (groupMetadata != null && mGroupMenuMap != null
                && mGroupMenuMap.get(groupMetadata.groupId) != null) {
            setMenuChecked(mGroupMenuMap.get(groupMetadata.groupId), true);
        }
    }

    protected GroupMetadata getGroupMetadata() {
        return null;
    }

    protected boolean isGroupView() {
        return mCurrentView == ContactsView.GROUP_VIEW;
    }

    protected boolean isDuplicatesView() {
        return mCurrentView == ContactsView.DUPLICATES;
    }

    protected boolean isAllContactsView() {
        return mCurrentView == ContactsView.ALL_CONTACTS;
    }

    protected boolean isAccountView() {
        return mCurrentView == ContactsView.ACCOUNT_VIEW;
    }

    public boolean isInSecondLevel() {
        return isGroupView() || isDuplicatesView();
    }

    protected abstract void onGroupMenuItemClicked(long groupId, String title);

    protected void onCreateGroupMenuItemClicked() {
        // Select the account to create the group
        final Bundle extras = getIntent().getExtras();
        final Account account = extras == null ? null :
                (Account) extras.getParcelable(Intents.Insert.EXTRA_ACCOUNT);
        if (account == null) {
            selectAccount();
        } else {
            final String dataSet = extras == null
                    ? null : extras.getString(Intents.Insert.EXTRA_DATA_SET);
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    account.name, account.type, dataSet);
            onAccountChosen(accountWithDataSet, /* extraArgs */ null);
        }
    }

    @Override
    public void onFiltersLoaded(List<ContactListFilter> accountFilterItems) {
        final AccountDisplayInfoFactory accountDisplayFactory = AccountDisplayInfoFactory.
                fromListFilters(this, accountFilterItems);

        final Menu menu = mNavigationView.getMenu();
        final MenuItem filtersMenuItem = menu.findItem(R.id.nav_filters);
        final SubMenu subMenu = filtersMenuItem.getSubMenu();
        subMenu.removeGroup(R.id.nav_filters_items);
        mFilterMenuMap = new HashMap<>();

        if (accountFilterItems == null || accountFilterItems.size() < 2) {
            return;
        }


        for (int i = 0; i < accountFilterItems.size(); i++) {
            final ContactListFilter filter = accountFilterItems.get(i);
            final AccountDisplayInfo displayableAccount =
                    accountDisplayFactory.getAccountDisplayInfoFor(filter);
            final CharSequence menuName = displayableAccount.getNameLabel();
            final MenuItem menuItem = subMenu.add(R.id.nav_filters_items, Menu.NONE,
                    Menu.NONE, menuName);
            if (isAccountView() && filter == mContactListFilterController.getFilter()) {
                updateMenuSelection(menuItem);
            }
            mFilterMenuMap.put(filter, menuItem);
            final Intent intent = new Intent();
            intent.putExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER, filter);
            menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mToggle.runWhenIdle(new Runnable() {
                        @Override
                        public void run() {
                            onFilterMenuItemClicked(intent);
                            updateMenuSelection(menuItem);
                        }
                    });
                    mDrawer.closeDrawer(GravityCompat.START);
                    return true;
                }
            });
            menuItem.setIcon(displayableAccount.getIcon());
            // Get rid of the default menu item overlay and show original account icons.
            menuItem.getIcon().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_ATOP);
            // Create a dummy action view to attach extra hidden content description to the menuItem
            // for Talkback. We want Talkback to read out the account type but not have it be part
            // of the menuItem title.
            LinearLayout view = (LinearLayout) LayoutInflater.from(this)
                    .inflate(R.layout.account_type_info, null);
            view.setContentDescription(displayableAccount.getTypeLabel());
            view.setVisibility(View.VISIBLE);
            menuItem.setActionView(view);
        }

        if (isAccountView()) {
            updateFilterMenu(mContactListFilterController.getFilter());
        }
    }

    public void updateFilterMenu(ContactListFilter filter) {
        clearCheckedMenus();
        if (filter != null && filter.isContactsFilterType()) {
            if (mIdMenuMap != null && mIdMenuMap.get(R.id.nav_all_contacts) != null) {
                setMenuChecked(mIdMenuMap.get(R.id.nav_all_contacts), true);
            }
        } else {
            if (mFilterMenuMap != null && mFilterMenuMap.get(filter) != null) {
                setMenuChecked(mFilterMenuMap.get(filter), true);
            }
        }
    }

    protected void onFilterMenuItemClicked(Intent intent) {
        AccountFilterUtil.handleAccountFilterResult(mContactListFilterController,
                AppCompatActivity.RESULT_OK, intent);
    }

    @Override
    public boolean onNavigationItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        mToggle.runWhenIdle(new Runnable() {
            @Override
            public void run() {
                if (id == R.id.nav_settings) {
                    startActivity(createPreferenceIntent());
                } else if (id == R.id.nav_help) {
                    HelpUtils.launchHelpAndFeedbackForMainScreen(ContactsDrawerActivity.this);
                } else if (id == R.id.nav_all_contacts) {
                    switchToAllContacts();
                } else if (id == R.id.nav_find_duplicates) {
                    if (!isDuplicatesView()) {
                        launchFindDuplicates();
                        updateMenuSelection(item);
                    }
                } else if (item.getIntent() != null) {
                    ImplicitIntentsUtil.startActivityInApp(ContactsDrawerActivity.this,
                            item.getIntent());
                } else {
                    Log.w(TAG, "Unhandled navigation view item selection");
                }
            }
        });

        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private Intent createPreferenceIntent() {
        final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_NEW_LOCAL_PROFILE,
                CompactContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_MODE_FULLY_EXPANDED,
                QuickContactActivity.MODE_FULLY_EXPANDED);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_PREVIOUS_SCREEN_TYPE,
                QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE);
        return intent;
    }

    public void switchToAllContacts() {
        resetFilter();

        final Menu menu = mNavigationView.getMenu();
        final MenuItem allContacts = menu.findItem(R.id.nav_all_contacts);
        updateMenuSelection(allContacts);

        setTitle(getString(R.string.contactsList));
    }

    protected void resetFilter() {
        final Intent intent = new Intent();
        final ContactListFilter filter = AccountFilterUtil.createContactsFilter(this);
        intent.putExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER, filter);
        AccountFilterUtil.handleAccountFilterResult(
                mContactListFilterController, AppCompatActivity.RESULT_OK, intent);
    }

    protected abstract void launchFindDuplicates();

    protected abstract DefaultContactBrowseListFragment getAllFragment();

    protected abstract GroupMembersFragment getGroupFragment();

    public abstract void showFabWithAnimation(boolean showFab);

    private void clearCheckedMenus() {
        clearCheckedMenu(mFilterMenuMap);
        clearCheckedMenu(mGroupMenuMap);
        clearCheckedMenu(mIdMenuMap);
    }

    private void clearCheckedMenu(Map<?, MenuItem> map) {
        final Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry pair = (Entry) it.next();
            setMenuChecked(map.get(pair.getKey()), false);
        }
    }

    private void setMenuChecked(MenuItem menuItem, boolean checked) {
        menuItem.setCheckable(checked);
        menuItem.setChecked(checked);
    }

    private void selectAccount() {
        final List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(this)
                .getAccounts(/* writable */ true);
        if (accounts.isEmpty()) {
            // We shouldn't present the add group button if there are no writable accounts
            // but check it since it's possible we are started with an Intent.
            Toast.makeText(this, R.string.groupCreateFailedToast, Toast.LENGTH_SHORT).show();
            return;
        }
        // If there is a single writable account, use it w/o showing a dialog.
        if (accounts.size() == 1) {
            onAccountChosen(accounts.get(0), /* extraArgs */ null);
            return;
        }
        SelectAccountDialogFragment.show(getFragmentManager(), null,
                R.string.dialog_new_group_account, AccountListFilter.ACCOUNTS_GROUP_WRITABLE,
                /* extraArgs */ null, TAG_SELECT_ACCOUNT_DIALOG);
    }

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        mNewGroupAccount = account;
        GroupNameEditDialogFragment.newInstanceForCreation(
                mNewGroupAccount, GroupUtil.ACTION_CREATE_GROUP)
                .show(getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG);
    }

    @Override
    public void onAccountSelectorCancelled() {
    }

    private void updateMenuSelection(MenuItem menuItem) {
        clearCheckedMenus();
        setMenuChecked(menuItem, true);
    }
}
