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

import com.android.internal.telephony.ITelephony;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Intents.UI;
import android.util.Log;
import android.view.Window;
import android.widget.TabHost;

/**
 * The dialer activity that has one tab with the virtual 12key
 * dialer, a tab with recent calls in it, a tab with the contacts and
 * a tab with the favorite. This is the container and the tabs are
 * embedded using intents.
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TabActivity implements TabHost.OnTabChangeListener {
    private static final String TAG = "Dailtacts";
    private static final String FAVORITES_ENTRY_COMPONENT =
            "com.android.contacts.DialtactsFavoritesEntryActivity";

    private static final int TAB_INDEX_DIALER = 0;
    private static final int TAB_INDEX_CALL_LOG = 1;
    private static final int TAB_INDEX_CONTACTS = 2;
    private static final int TAB_INDEX_FAVORITES = 3;

    static final String EXTRA_IGNORE_STATE = "ignore-state";

    /** If true, when handling the contacts intent the favorites tab will be shown instead */
    private static final String PREF_FAVORITES_AS_CONTACTS = "favorites_as_contacts";
    private static final boolean PREF_FAVORITES_AS_CONTACTS_DEFAULT = false;

    private TabHost mTabHost;
    private String mFilterText;
    private Uri mDialUri;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialer_activity);

        mTabHost = getTabHost();
        mTabHost.setOnTabChangedListener(this);

        // Setup the tabs
        setupDialerTab();
        setupCallLogTab();
        setupContactsTab();
        setupFavoritesTab();

        setCurrentTab(intent);

        if (intent.getAction().equals(UI.FILTER_CONTACTS_ACTION)
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        final int currentTabIndex = mTabHost.getCurrentTab();
        final SharedPreferences.Editor editor =
                getSharedPreferences(StickyTabs.PREFERENCES_NAME, MODE_PRIVATE).edit();
        if (currentTabIndex == TAB_INDEX_CONTACTS || currentTabIndex == TAB_INDEX_FAVORITES) {
            editor.putBoolean(PREF_FAVORITES_AS_CONTACTS, currentTabIndex == TAB_INDEX_FAVORITES);
        }

        editor.apply();
    }

    private void setupCallLogTab() {
        // Force the class since overriding tab entries doesn't work
        Intent intent = new Intent("com.android.phone.action.RECENT_CALLS");
        intent.setClass(this, RecentCallsListActivity.class);
        StickyTabs.setTab(intent, TAB_INDEX_CALL_LOG);

        mTabHost.addTab(mTabHost.newTabSpec("call_log")
                .setIndicator(getString(R.string.recentCallsIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_recent))
                .setContent(intent));
    }

    private void setupDialerTab() {
        Intent intent = new Intent("com.android.phone.action.TOUCH_DIALER");
        intent.setClass(this, TwelveKeyDialer.class);
        StickyTabs.setTab(intent, TAB_INDEX_DIALER);

        mTabHost.addTab(mTabHost.newTabSpec("dialer")
                .setIndicator(getString(R.string.dialerIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_dialer))
                .setContent(intent));
    }

    private void setupContactsTab() {
        Intent intent = new Intent(UI.LIST_DEFAULT);
        intent.setClass(this, ContactsListActivity.class);
        StickyTabs.setTab(intent, TAB_INDEX_CONTACTS);

        mTabHost.addTab(mTabHost.newTabSpec("contacts")
                .setIndicator(getText(R.string.contactsIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_contacts))
                .setContent(intent));
    }

    private void setupFavoritesTab() {
        Intent intent = new Intent(UI.LIST_STREQUENT_ACTION);
        intent.setClass(this, ContactsListActivity.class);
        StickyTabs.setTab(intent, TAB_INDEX_FAVORITES);

        mTabHost.addTab(mTabHost.newTabSpec("favorites")
                .setIndicator(getString(R.string.contactsFavoritesLabel),
                        getResources().getDrawable(R.drawable.ic_tab_starred))
                .setContent(intent));
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
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
        if (getClass().getName().equals(componentName)) {
            if (phoneIsInUse()) {
                // We are in a call, show the dialer tab (which allows going back to the call)
                mTabHost.setCurrentTab(TAB_INDEX_DIALER);
            } else if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
                // launched from history (long-press home) --> nothing to change
            } else if (isDialIntent(intent)) {
                // The dialer was explicitly requested
                mTabHost.setCurrentTab(TAB_INDEX_DIALER);
            } else if (Calls.CONTENT_TYPE.equals(intent.getType())) {
                // After a call, show the call log
                mTabHost.setCurrentTab(TAB_INDEX_CALL_LOG);
            } else {
                // Load the last tab used to make a phone call. default to the dialer in
                // first launch
                mTabHost.setCurrentTab(StickyTabs.loadTab(this, TAB_INDEX_DIALER));
            }
        } else if (FAVORITES_ENTRY_COMPONENT.equals(componentName)) {
            mTabHost.setCurrentTab(TAB_INDEX_FAVORITES);
        } else {
            // Launched as "Contacts" --> Go either to favorites or contacts, whichever is more
            // recent
            final SharedPreferences prefs = getSharedPreferences(StickyTabs.PREFERENCES_NAME,
                    MODE_PRIVATE);
            final boolean favoritesAsContacts = prefs.getBoolean(PREF_FAVORITES_AS_CONTACTS,
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
        if (action.equals(UI.FILTER_CONTACTS_ACTION)) {
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

    /** {@inheritDoc} */
    @Override
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

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    private boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneInUse = !phone.isIdle();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }
}
