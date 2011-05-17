/*
 * Copyright (C) 2008 The Android Open Source Project
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
import com.android.contacts.calllog.CallLogFragment;
import com.android.contacts.dialpad.DialpadFragment;
import com.android.contacts.interactions.ImportExportDialogFragment;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DirectoryListLoader;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.internal.telephony.ITelephony;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.UI;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * The dialer activity that has one tab with the virtual 12key
 * dialer, a tab with recent calls in it, a tab with the contacts and
 * a tab with the favorite. This is the container and the tabs are
 * embedded using intents.
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends Activity {
    private static final String TAG = "DialtactsActivity";

    private static final int TAB_INDEX_DIALER = 0;
    private static final int TAB_INDEX_CALL_LOG = 1;
    private static final int TAB_INDEX_CONTACTS = 2;
    private static final int TAB_INDEX_FAVORITES = 3;

    public static final String EXTRA_IGNORE_STATE = "ignore-state";

    /** Name of the dialtacts shared preferences */
    static final String PREFS_DIALTACTS = "dialtacts";
    /** If true, when handling the contacts intent the favorites tab will be shown instead */
    static final String PREF_FAVORITES_AS_CONTACTS = "favorites_as_contacts";
    static final boolean PREF_FAVORITES_AS_CONTACTS_DEFAULT = false;

    /** Last manually selected tab index */
    private static final String PREF_LAST_MANUALLY_SELECTED_TAB = "last_manually_selected_tab";
    private static final int PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT = TAB_INDEX_DIALER;

    private String mFilterText;
    private Uri mDialUri;
    private DialpadFragment mDialpadFragment;
    private CallLogFragment mCallLogFragment;
    private DefaultContactBrowseListFragment mContactsFragment;
    private DefaultContactBrowseListFragment mFavoritesFragment;

    /**
     * The index of the tab that has last been manually selected (the user clicked on a tab).
     * This value does not keep track of programmatically set Tabs (e.g. Call Log after a Call)
     */
    private int mLastManuallySelectedTab;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        fixIntent(intent);

        setContentView(R.layout.dialtacts_activity);

        final FragmentManager fragmentManager = getFragmentManager();
        mDialpadFragment = (DialpadFragment) fragmentManager
                .findFragmentById(R.id.dialpad_fragment);
        mCallLogFragment = (CallLogFragment) fragmentManager
                .findFragmentById(R.id.call_log_fragment);
        mContactsFragment = (DefaultContactBrowseListFragment) fragmentManager
                .findFragmentById(R.id.contacts_fragment);
        mFavoritesFragment = (DefaultContactBrowseListFragment) fragmentManager
                .findFragmentById(R.id.favorites_fragment);

        // Hide all tabs (the current tab will later be reshown once a tab is selected)
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.hide(mDialpadFragment);
        transaction.hide(mCallLogFragment);
        transaction.hide(mContactsFragment);
        transaction.hide(mFavoritesFragment);
        transaction.commit();

        // Setup the ActionBar tabs (the order matches the tab-index contants TAB_INDEX_*)
        setupDialer();
        setupCallLog();
        setupContacts();
        setupFavorites();
        getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);

        // Load the last manually loaded tab
        final SharedPreferences prefs = getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE);
        mLastManuallySelectedTab = prefs.getInt(PREF_LAST_MANUALLY_SELECTED_TAB,
                PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT);

        setCurrentTab(intent);

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        final int currentTabIndex = getActionBar().getSelectedTab().getPosition();
        final SharedPreferences.Editor editor =
                getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE).edit();
        if (currentTabIndex == TAB_INDEX_CONTACTS || currentTabIndex == TAB_INDEX_FAVORITES) {
            editor.putBoolean(PREF_FAVORITES_AS_CONTACTS, currentTabIndex == TAB_INDEX_FAVORITES);
        }
        editor.putInt(PREF_LAST_MANUALLY_SELECTED_TAB, mLastManuallySelectedTab);

        editor.apply();
    }

    private void fixIntent(Intent intent) {
        // This should be cleaned up: the call key used to send an Intent
        // that just said to go to the recent calls list.  It now sends this
        // abstract action, but this class hasn't been rewritten to deal with it.
        if (Intent.ACTION_CALL_BUTTON.equals(intent.getAction())) {
            intent.setDataAndType(Calls.CONTENT_URI, Calls.CONTENT_TYPE);
            intent.putExtra("call_key", true);
            setIntent(intent);
        }
    }

    private void setupDialer() {
        final Tab tab = getActionBar().newTab();
        tab.setText(R.string.dialerIconLabel);
        tab.setTabListener(new TabChangeListener(mDialpadFragment));
        tab.setIcon(R.drawable.ic_tab_dialer);
        getActionBar().addTab(tab);
        mDialpadFragment.resolveIntent();
    }

    private void setupCallLog() {
        final Tab tab = getActionBar().newTab();
        tab.setText(R.string.recentCallsIconLabel);
        tab.setIcon(R.drawable.ic_tab_recent);
        tab.setTabListener(new TabChangeListener(mCallLogFragment));
        getActionBar().addTab(tab);
    }

    private void setupContacts() {
        final Tab tab = getActionBar().newTab();
        tab.setText(R.string.contactsIconLabel);
        tab.setIcon(R.drawable.ic_tab_contacts);
        tab.setTabListener(new TabChangeListener(mContactsFragment));
        getActionBar().addTab(tab);

        // TODO: We should not artificially create Intents and put them into the Fragment.
        // It would be nicer to directly pass in the UI constant
        Intent intent = new Intent(UI.LIST_ALL_CONTACTS_ACTION);
        intent.setClass(this, ContactBrowserActivity.class);

        ContactsIntentResolver resolver = new ContactsIntentResolver(this);
        ContactsRequest request = resolver.resolveIntent(intent);
        final ContactListFilter filter = new ContactListFilter(
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
        mContactsFragment.setFilter(filter, false);
        mContactsFragment.setSearchMode(request.isSearchMode());
        mContactsFragment.setQueryString(request.getQueryString(), false);
        mContactsFragment.setContactsRequest(request);
        mContactsFragment.setDirectorySearchMode(request.isDirectorySearchEnabled()
                ? DirectoryListLoader.SEARCH_MODE_DEFAULT
                : DirectoryListLoader.SEARCH_MODE_NONE);
        mContactsFragment.setOnContactListActionListener(mListFragmentListener);
    }

    private void setupFavorites() {
        final Tab tab = getActionBar().newTab();
        tab.setText(R.string.contactsFavoritesLabel);
        tab.setIcon(R.drawable.ic_tab_starred);
        tab.setTabListener(new TabChangeListener(mFavoritesFragment));
        getActionBar().addTab(tab);

        // TODO: We should not artificially create Intents and put them into the Fragment.
        // It would be nicer to directly pass in the UI constant
        Intent intent = new Intent(UI.LIST_STREQUENT_ACTION);
        intent.setClass(this, ContactBrowserActivity.class);

        ContactsIntentResolver resolver = new ContactsIntentResolver(this);
        ContactsRequest request = resolver.resolveIntent(intent);
        final ContactListFilter filter = new ContactListFilter(
                ContactListFilter.FILTER_TYPE_STARRED);
        mFavoritesFragment.setFilter(filter, false);
        mFavoritesFragment.setSearchMode(request.isSearchMode());
        mFavoritesFragment.setQueryString(request.getQueryString(), false);
        mFavoritesFragment.setContactsRequest(request);
        mFavoritesFragment.setDirectorySearchMode(request.isDirectorySearchEnabled()
                ? DirectoryListLoader.SEARCH_MODE_DEFAULT
                : DirectoryListLoader.SEARCH_MODE_NONE);
        mFavoritesFragment.setOnContactListActionListener(mListFragmentListener);
    }

    /**
     * Returns true if the intent is due to hitting the green send key while in a call.
     *
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(final Intent intent, final boolean recentCallsRequest) {
        // If there is a call in progress go to the call screen
        if (recentCallsRequest) {
            final boolean callKey = intent.getBooleanExtra("call_key", false);

            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (callKey && phone != null && phone.showCallScreen()) {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handle send while in call", e);
            }
        }

        return false;
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
     */
    private void setCurrentTab(Intent intent) {
        // If we got here by hitting send and we're in call forward along to the in-call activity
        final boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.getType());
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }

        // Tell the children activities that they should ignore any possible saved
        // state and instead reload their state from the parent's intent
        intent.putExtra(EXTRA_IGNORE_STATE, true);

        // Remember the old manually selected tab index so that it can be restored if it is
        // overwritten by one of the programmatic tab selections
        final int savedTabIndex = mLastManuallySelectedTab;

        // Choose the tab based on the inbound intent
        if (intent.getBooleanExtra(ContactsFrontDoor.EXTRA_FRONT_DOOR, false)) {
            // Launched through the contacts front door, set the proper contacts tab (sticky
            // between favorites and contacts)
            SharedPreferences prefs = getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE);
            boolean favoritesAsContacts = prefs.getBoolean(PREF_FAVORITES_AS_CONTACTS,
                    PREF_FAVORITES_AS_CONTACTS_DEFAULT);
            if (favoritesAsContacts) {
                getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_FAVORITES));
            } else {
                getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_CONTACTS));
            }
        } else {
            // Not launched through the front door, look at the component to determine the tab
            String componentName = intent.getComponent().getClassName();
            if (getClass().getName().equals(componentName)) {
                if (recentCallsRequest) {
                    getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_CALL_LOG));
                } else {
                    getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_DIALER));
                }
            } else {
                getActionBar().selectTab(getActionBar().getTabAt(mLastManuallySelectedTab));
            }
        }

        // Restore to the previous manual selection
        mLastManuallySelectedTab = savedTabIndex;

        // Tell the children activities that they should honor their saved states
        // instead of the state from the parent's intent
        intent.putExtra(EXTRA_IGNORE_STATE, false);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        fixIntent(newIntent);
        setCurrentTab(newIntent);
        final String action = newIntent.getAction();
        if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            setupFilterText(newIntent);
        } else if (isDialIntent(newIntent)) {
            setupDialUri(newIntent);
        }
    }

    /** Returns true if the given intent contains a phone number to populate the dialer with */
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action)) {
            return true;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && "tel".equals(data.getScheme())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the filter text stored in {@link #setupFilterText(Intent)}.
     * This text originally came from a FILTER_CONTACTS_ACTION intent received
     * by this activity. The stored text will then be cleared after after this
     * method returns.
     *
     * @return The stored filter text
     */
    public String getAndClearFilterText() {
        String filterText = mFilterText;
        mFilterText = null;
        return filterText;
    }

    /**
     * Stores the filter text associated with a FILTER_CONTACTS_ACTION intent.
     * This is so child activities can check if they are supposed to display a filter.
     *
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupFilterText(Intent intent) {
        // If the intent was relaunched from history, don't apply the filter text.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        String filter = intent.getStringExtra(UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    /**
     * Retrieves the uri stored in {@link #setupDialUri(Intent)}. This uri
     * originally came from a dial intent received by this activity. The stored
     * uri will then be cleared after after this method returns.
     *
     * @return The stored uri
     */
    public Uri getAndClearDialUri() {
        Uri dialUri = mDialUri;
        mDialUri = null;
        return dialUri;
    }

    /**
     * Stores the uri associated with a dial intent. This is so child activities can
     * check if they are supposed to display new dial info.
     *
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupDialUri(Intent intent) {
        // If the intent was relaunched from history, don't reapply the intent.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        mDialUri = intent.getData();
    }

    @Override
    public void onBackPressed() {
        if (isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Pass this lifecycle event down to the fragment
        mDialpadFragment.onPostCreate();
    }

    /**
     * Tab change listener that is instantiated once for each tab. Handles showing/hiding tabs
     * and remembers manual tab selections
     */
    private class TabChangeListener implements TabListener {
        private final Fragment mFragment;

        public TabChangeListener(Fragment fragment) {
            mFragment = fragment;
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            ft.hide(mFragment);
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            ft.show(mFragment);

            // Remember this tab index. This function is also called, if the tab is set
            // automatically in which case the setter (setCurrentTab) has to set this to its old
            // value afterwards
            mLastManuallySelectedTab = tab.getPosition();
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }

    private OnContactBrowserActionListener mListFragmentListener =
            new OnContactBrowserActionListener() {
        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
        }

        @Override
        public void onSmsContactAction(Uri contactUri) {
        }

        @Override
        public void onSelectionChange() {
        }

        @Override
        public void onRemoveFromFavoritesAction(Uri contactUri) {
        }

        @Override
        public void onInvalidSelection() {
        }

        @Override
        public void onFinishAction() {
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
        }

        @Override
        public void onCreateNewContactAction() {
        }

        @Override
        public void onCallContactAction(Uri contactUri) {
        }

        @Override
        public void onAddToFavoritesAction(Uri contactUri) {
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // For now, create the menu in here. It would be nice to do this in the Fragment,
        // but that Fragment is re-used in other views.
        final ActionBar actionBar = getActionBar();
        if (actionBar == null) return false;
        final Tab tab = actionBar.getSelectedTab();
        if (tab == null) return false;
        final int tabIndex = tab.getPosition();
        if (tabIndex != TAB_INDEX_CONTACTS && tabIndex != TAB_INDEX_FAVORITES) return false;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // This is currently a copy of the equivalent code of ContactBrowserActivity (with the
        // exception of menu_add, because we do not select items in the list).
        // Should be consolidated
        switch (item.getItemId()) {
        case R.id.menu_settings: {
            final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
            startActivity(intent);
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
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
