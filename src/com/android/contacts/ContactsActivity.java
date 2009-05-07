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

package com.android.contacts;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.Intents.UI;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.TabHost;

/**
 * The contacts activity that has one tab with social activity stream and
 * another with contact list. This is the container and the tabs are embedded
 * using intents.
 */
public class ContactsActivity extends TabActivity implements TabHost.OnTabChangeListener {
    private static final String TAG = "Contacts";
    private static final String FAVORITES_ENTRY_COMPONENT =
            "com.android.contacts.DialtactsFavoritesEntryActivity";

    private static final int TAB_INDEX_CONTACTS = 0;
    private static final int TAB_INDEX_FAVORITES = 1;

    static final String EXTRA_IGNORE_STATE = "ignore-state";

    /** Name of the dialtacts shared preferences */
    static final String PREFS_DIALTACTS = "dialtacts";
    /** If true, when handling the contacts intent the favorites tab will be shown instead */
    static final String PREF_FAVORITES_AS_CONTACTS = "favorites_as_contacts";
    static final boolean PREF_FAVORITES_AS_CONTACTS_DEFAULT = false;

    private TabHost mTabHost;
    private String mFilterText;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialer_activity);

        mTabHost = getTabHost();
        mTabHost.setOnTabChangedListener(this);

        // Setup the tabs
        setupActivityStreamTab();
        setupContactsTab();
        setupFavoritesTab();

        setCurrentTab(intent);

        if (intent.getAction().equals(Contacts.Intents.UI.FILTER_CONTACTS_ACTION)
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        int currentTabIndex = mTabHost.getCurrentTab();
        if (currentTabIndex == TAB_INDEX_CONTACTS || currentTabIndex == TAB_INDEX_FAVORITES) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE)
                    .edit();
            editor.putBoolean(PREF_FAVORITES_AS_CONTACTS, currentTabIndex == TAB_INDEX_FAVORITES);
            editor.commit();
        }
    }

    private void setupActivityStreamTab() {
        // Just a placeholder for now
        Intent intent = new Intent("com.android.contacts.action.LIST_DEFAULT");
        intent.setClass(this, ActivityStreamActivity.class);

        mTabHost.addTab(mTabHost.newTabSpec("stream")
                .setIndicator(getText(R.string.activityStreamIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_contacts))
                .setContent(intent));
    }

    private void setupContactsTab() {
        Intent intent = new Intent(UI.LIST_DEFAULT);
        intent.setClass(this, ContactsListActivity.class);

        mTabHost.addTab(mTabHost.newTabSpec("contacts")
                .setIndicator(getText(R.string.contactsIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_contacts))
                .setContent(intent));
    }

    private void setupFavoritesTab() {
        Intent intent = new Intent(UI.LIST_STREQUENT_ACTION);
        intent.setClass(this, ContactsListActivity.class);

        mTabHost.addTab(mTabHost.newTabSpec("favorites")
                .setIndicator(getString(R.string.contactsFavoritesLabel),
                        getResources().getDrawable(R.drawable.ic_tab_starred))
                .setContent(intent));
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param recentCallsRequest true is the recent calls tab is desired, false otherwise
     */
    private void setCurrentTab(Intent intent) {

        // Dismiss menu provided by any children activities
        Activity activity = getLocalActivityManager().
                getActivity(mTabHost.getCurrentTabTag());
        if (activity != null) {
            activity.closeOptionsMenu();
        }

        // Tell the children activities that they should ignore any possible saved
        // state and instead reload their state from the parent's intent
        intent.putExtra(EXTRA_IGNORE_STATE, true);

        // Choose the tab based on the inbound intent
        String componentName = intent.getComponent().getClassName();
        if (FAVORITES_ENTRY_COMPONENT.equals(componentName)) {
            mTabHost.setCurrentTab(TAB_INDEX_FAVORITES);
        } else if (Contacts.Intents.UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())) {
            mTabHost.setCurrentTab(TAB_INDEX_CONTACTS);
        } else {
            SharedPreferences prefs = getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE);
            boolean favoritesAsContacts = prefs.getBoolean(PREF_FAVORITES_AS_CONTACTS,
                    PREF_FAVORITES_AS_CONTACTS_DEFAULT);
            if (favoritesAsContacts) {
                mTabHost.setCurrentTab(TAB_INDEX_FAVORITES);
            } else {
                mTabHost.setCurrentTab(TAB_INDEX_CONTACTS);
            }
        }

        // Tell the children activities that they should honor their saved states
        // instead of the state from the parent's intent
        intent.putExtra(EXTRA_IGNORE_STATE, false);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        setCurrentTab(newIntent);
        final String action = newIntent.getAction();
        if (action.equals(Contacts.Intents.UI.FILTER_CONTACTS_ACTION)) {
            setupFilterText(newIntent);
        }
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
        String filter = intent.getStringExtra(Contacts.Intents.UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle BACK
        if (keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /** {@inheritDoc} */
    public void onTabChanged(String tabId) {
        // Because we're using Activities as our tab children, we trigger
        // onWindowFocusChanged() to let them know when they're active.  This may
        // seem to duplicate the purpose of onResume(), but it's needed because
        // onResume() can't reliably check if a keyguard is active.
        Activity activity = getLocalActivityManager().getActivity(tabId);
        if (activity != null) {
            activity.onWindowFocusChanged(true);
        }
    }
}
