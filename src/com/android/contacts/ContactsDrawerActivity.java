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
import android.net.Uri;
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
import android.widget.Toast;

import com.android.contacts.activities.GroupMembersActivity;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferenceActivity;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.group.GroupsFragment;
import com.android.contacts.group.GroupsFragment.GroupsListener;
import com.android.contacts.interactions.AccountFiltersFragment;
import com.android.contacts.interactions.AccountFiltersFragment.AccountFiltersListener;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contactsbind.Assistants;
import com.android.contactsbind.HelpUtils;

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
        SelectAccountDialogFragment.Listener,
        GroupNameEditDialogFragment.Listener {

    protected static String TAG = "ContactsDrawerActivity";

    private static final String TAG_GROUPS = "groups";
    private static final String TAG_FILTERS = "filters";
    private static final String TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    private static final String KEY_NEW_GROUP_ACCOUNT = "newGroupAccount";

    protected static final String ACTION_CREATE_GROUP = "createGroup";

    private class ContactsActionBarDrawerToggle extends ActionBarDrawerToggle {

        private Runnable mRunnable;

        public ContactsActionBarDrawerToggle(AppCompatActivity activity, DrawerLayout drawerLayout,
                Toolbar toolbar, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
            super(activity, drawerLayout, toolbar, openDrawerContentDescRes,
                    closeDrawerContentDescRes);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            invalidateOptionsMenu();
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

    // Checkable menu item lookup maps. Every map declared here should be added to
    // clearCheckedMenus() so that they can be cleared.
    // TODO find a better way to handle selected menu item state, when swicthing to fragments.
    protected Map<Long, MenuItem> mGroupMenuMap = new HashMap<>();
    protected Map<ContactListFilter, MenuItem> mFilterMenuMap = new HashMap<>();
    protected Map<Integer, MenuItem> mIdMenuMap = new HashMap<>();

    // The account the new group will be created under.
    private AccountWithDataSet mNewGroupAccount;

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

        // Set up hamburger menu items.
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        final Menu menu = mNavigationView.getMenu();

        final MenuItem allContacts = menu.findItem(R.id.nav_all_contacts);
        mIdMenuMap.put(R.id.nav_all_contacts, allContacts);

        if (Assistants.getDuplicatesActivityIntent(this) == null) {
            menu.removeItem(R.id.nav_find_duplicates);
        } else {
            final MenuItem findDup = menu.findItem(R.id.nav_find_duplicates);
            mIdMenuMap.put(R.id.nav_find_duplicates, findDup);
        }

        if (!HelpUtils.isHelpAndFeedbackAvailable()) {
            menu.removeItem(R.id.nav_help);
        }

        loadGroupsAndFilters();

        if (isDuplicatesActivity()) {
            clearCheckedMenus();
            mIdMenuMap.get(R.id.nav_find_duplicates).setCheckable(true);
            mIdMenuMap.get(R.id.nav_find_duplicates).setChecked(true);
        }

        if (savedState != null && savedState.containsKey(KEY_NEW_GROUP_ACCOUNT)) {
            mNewGroupAccount = AccountWithDataSet.unstringify(
                    savedState.getString(KEY_NEW_GROUP_ACCOUNT));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNewGroupAccount != null) {
            outState.putString(KEY_NEW_GROUP_ACCOUNT, mNewGroupAccount.stringify());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            makeStatusBarTransparent();
        }
    }

    private void makeStatusBarTransparent() {
        if (CompatUtils.isLollipopCompatible()
                && getWindow().getStatusBarColor() ==
                        ContextCompat.getColor(this, R.color.primary_color_dark)) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        if (ACTION_CREATE_GROUP.equals(newIntent.getAction())) {
            final Uri groupUri = newIntent.getData();
            if (groupUri == null) {
                Toast.makeText(this, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + groupUri);
            Toast.makeText(this, R.string.groupCreatedToast, Toast.LENGTH_SHORT).show();
            startActivity(GroupUtil.createViewGroupIntent(this, groupUri, /* title */ null));
            if (this instanceof GroupMembersActivity) {
                // If we created a group while viewing the members of an existing group (i.e.
                // while on GroupMembersActivity), finish the current GroupMembersActivity so that
                // hitting back from the new GroupMembersActivity that was just stared will open
                // the all contacts list. See b/30047708.
                finish();
            }
        } else {
            super.onNewIntent(newIntent);
        }
    }

    /**
     * Returns true if child class is DuplicatesActivity
     */
    protected boolean isDuplicatesActivity() {
        return false;
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
                menuItem.setIcon(R.drawable.ic_menu_label);
                menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        mToggle.runWhenIdle(new Runnable() {
                            @Override
                            public void run() {
                                onGroupMenuItemClicked(groupListItem.getGroupId(),
                                        groupListItem.getTitle());
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
        final MenuItem menuItem = subMenu.add(R.id.nav_groups_items, Menu.NONE, Menu.NONE,
                getString(R.string.menu_new_group_action_bar));
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

        if (getGroupMetadata() != null) {
            updateGroupMenu(getGroupMetadata());
        }
    }

    protected void updateGroupMenu(GroupMetadata groupMetadata) {
        clearCheckedMenus();
        if (groupMetadata != null && mGroupMenuMap != null
                && mGroupMenuMap.get(groupMetadata.groupId) != null) {
            mGroupMenuMap.get(groupMetadata.groupId).setCheckable(true);
            mGroupMenuMap.get(groupMetadata.groupId).setChecked(true);
        }
    }

    /**
     * Returns group metadata if the child class is {@link GroupMembersActivity}, and null
     * otherwise.
     */
    protected GroupMetadata getGroupMetadata() {
        return null;
    }

    protected void onGroupMenuItemClicked(long groupId, String title) {
        startActivity(GroupUtil.createViewGroupIntent(this, groupId, title));
        if (shouldFinish()) {
            finish();
        }
    }

    private void onCreateGroupMenuItemClicked() {
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
            final String menuName =
                    filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS
                            ? getString(R.string.account_phone) : filter.accountName;
            final MenuItem menuItem = subMenu.add(R.id.nav_filters_items, Menu.NONE, Menu.NONE,
                    menuName);
            mFilterMenuMap.put(filter, menuItem);
            final Intent intent = new Intent();
            intent.putExtra(AccountFilterUtil.EXTRA_CONTACT_LIST_FILTER, filter);
            menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mToggle.runWhenIdle(new Runnable() {
                        @Override
                        public void run() {
                            AccountFilterUtil.handleAccountFilterResult(
                                    mContactListFilterController, AppCompatActivity.RESULT_OK,
                                    intent);
                            if (shouldFinish()) {
                                finish();
                            }
                        }
                    });
                    mDrawer.closeDrawer(GravityCompat.START);
                    return true;
                }
            });
            menuItem.setIcon(filter.icon);
            // Get rid of the default memu item overlay and show original account icons.
            menuItem.getIcon().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_ATOP);
        }

        if (getContactListFilter() != null) {
            updateFilterMenu(getContactListFilter());
        }
    }

    protected void updateFilterMenu(ContactListFilter filter) {
        clearCheckedMenus();
        if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            if (mIdMenuMap != null && mIdMenuMap.get(R.id.nav_all_contacts) != null) {
                mIdMenuMap.get(R.id.nav_all_contacts).setCheckable(true);
                mIdMenuMap.get(R.id.nav_all_contacts).setChecked(true);
            }
        } else {
            if (mFilterMenuMap != null && mFilterMenuMap.get(filter) != null) {
                mFilterMenuMap.get(filter).setCheckable(true);
                mFilterMenuMap.get(filter).setChecked(true);
            }
        }
    }

    /**
     * Returns the current filter if the child class is {@link PeopleActivity}, and null otherwise.
     */
    protected ContactListFilter getContactListFilter() {
        return null;
    }

    /**
     * Returns true if the child activity should finish after launching another activity.
     */
    protected abstract boolean shouldFinish();

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
                    launchFindDuplicates();
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
                ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_MODE_FULLY_EXPANDED,
                QuickContactActivity.MODE_FULLY_EXPANDED);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_PREVIOUS_SCREEN_TYPE,
                QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE);
        return intent;
    }

    protected void switchToAllContacts() {
        final Intent intent = new Intent();
        final ContactListFilter filter = createAllAccountsFilter();
        intent.putExtra(AccountFilterUtil.EXTRA_CONTACT_LIST_FILTER, filter);
        AccountFilterUtil.handleAccountFilterResult(
                mContactListFilterController, AppCompatActivity.RESULT_OK, intent);
        if (shouldFinish()) {
            finish();
        }
    }

    protected void launchFindDuplicates() {
        ImplicitIntentsUtil.startActivityInAppIfPossible(this,
                Assistants.getDuplicatesActivityIntent(this));
    }

    protected ContactListFilter createAllAccountsFilter() {
        return ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
    }

    private void clearCheckedMenus() {
        clearCheckedMenu(mFilterMenuMap);
        clearCheckedMenu(mGroupMenuMap);
        clearCheckedMenu(mIdMenuMap);
    }

    private void clearCheckedMenu(Map<?, MenuItem> map) {
        final Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry pair = (Entry)it.next();
            map.get(pair.getKey()).setCheckable(false);
            map.get(pair.getKey()).setChecked(false);
        }
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
        GroupNameEditDialogFragment.showInsertDialog(
                getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG);
    }

    @Override
    public void onAccountSelectorCancelled() {
    }

    @Override
    public void onGroupNameEdit(String groupName, boolean isInsert) {
        if (mNewGroupAccount == null) {
            Toast.makeText(this, R.string.groupCreateFailedToast, Toast.LENGTH_SHORT).show();
            return;
        }
        startService(ContactSaveService.createNewGroupIntent(this,
                mNewGroupAccount, groupName, /* rawContactsToAdd */ null, getClass(),
                ACTION_CREATE_GROUP));
    }

    @Override
    public void onGroupNameEditCancelled() {
    }
}
