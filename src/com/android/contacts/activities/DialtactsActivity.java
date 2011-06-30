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
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DirectoryListLoader;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.PhoneNumberPickerFragment;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.internal.telephony.ITelephony;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;

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
    private static final int TAB_INDEX_FAVORITES = 2;

    private static final int TAB_INDEX_COUNT = 3;

    public static final String EXTRA_IGNORE_STATE = "ignore-state";

    /** Name of the dialtacts shared preferences */
    static final String PREFS_DIALTACTS = "dialtacts";
    static final boolean PREF_FAVORITES_AS_CONTACTS_DEFAULT = false;

    /** Last manually selected tab index */
    private static final String PREF_LAST_MANUALLY_SELECTED_TAB = "last_manually_selected_tab";
    private static final int PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT = TAB_INDEX_DIALER;

    private String mFilterText;
    private Uri mDialUri;
    private DialpadFragment mDialpadFragment;
    private CallLogFragment mCallLogFragment;
    private DefaultContactBrowseListFragment mContactsFragment;
    private StrequentContactListFragment mStrequentFragment;

    /**
     * Fragment for searching phone numbers. Unlike the other Fragments, this doesn't correspond
     * to tab but is shown by a search action.
     */
    private PhoneNumberPickerFragment mPhoneNumberPickerFragment;
    /**
     * True when this Activity is in its search UI (with a {@link SearchView} and
     * {@link PhoneNumberPickerFragment}).
     */
    private boolean mInSearchUi;

    /**
     * The index of the tab that has last been manually selected (the user clicked on a tab).
     * This value does not keep track of programmatically set Tabs (e.g. Call Log after a Call)
     */
    private int mLastManuallySelectedTab;

    private SearchView mSearchView;

    /**
     * Listener used when one of phone numbers in search UI is selected. This will initiate a
     * phone call using the phone number.
     */
    private final OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener =
            new OnPhoneNumberPickerActionListener() {
                @Override
                public void onPickPhoneNumberAction(Uri dataUri) {
                    PhoneNumberInteraction.startInteractionForPhoneCall(
                            DialtactsActivity.this, dataUri);
                }

                @Override
                public void onShortcutIntentCreated(Intent intent) {
                    Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
                }
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final OnQueryTextListener mPhoneSearchQueryTextListener =
            new OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    // Ignore.
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    // Show search result with non-empty text. Show a bare list otherwise.
                    mPhoneNumberPickerFragment.setQueryString(newText, true);
                    mPhoneNumberPickerFragment.setSearchMode(!TextUtils.isEmpty(newText));
                    return true;
                }
    };

    /**
     * Listener used to handle the "close" button on the right side of {@link SearchView}.
     * If some text is in the search view, this will clean it up. Otherwise this will exit
     * the search UI and let users go back to usual Phone UI.
     *
     * This does _not_ handle back button.
     *
     * TODO: need "up" button instead of close button
     */
    private final OnCloseListener mPhoneSearchCloseListener =
            new OnCloseListener() {
                @Override
                public boolean onClose() {
                    if (TextUtils.isEmpty(mSearchView.getQuery())) {
                        exitSearchUi();
                    } else {
                        mSearchView.setQuery(null, true);
                    }
                    return true;
                }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        fixIntent(intent);

        setContentView(R.layout.dialtacts_activity);

        final FragmentManager fragmentManager = getFragmentManager();
        mDialpadFragment = (DialpadFragment) fragmentManager
                .findFragmentById(R.id.dialpad_fragment);
        mDialpadFragment.setListener(new DialpadFragment.Listener() {
            @Override
            public void onSearchButtonPressed() {
                enterSearchUi();
            }
        });
        mCallLogFragment = (CallLogFragment) fragmentManager
                .findFragmentById(R.id.call_log_fragment);
        mContactsFragment = (DefaultContactBrowseListFragment) fragmentManager
                .findFragmentById(R.id.contacts_fragment);
        mStrequentFragment = (StrequentContactListFragment) fragmentManager
                .findFragmentById(R.id.favorites_fragment);
        mPhoneNumberPickerFragment = (PhoneNumberPickerFragment) fragmentManager
                .findFragmentById(R.id.phone_number_picker_fragment);
        mPhoneNumberPickerFragment.setOnPhoneNumberPickerActionListener(
                mPhoneNumberPickerActionListener);
        mPhoneNumberPickerFragment.setHighlightSearchPrefix(true);

        // Hide all tabs (the current tab will later be reshown once a tab is selected)
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.hide(mDialpadFragment);
        transaction.hide(mCallLogFragment);
        transaction.hide(mStrequentFragment);
        transaction.hide(mPhoneNumberPickerFragment);
        transaction.commit();

        // Setup the ActionBar tabs (the order matches the tab-index contants TAB_INDEX_*)
        setupDialer();
        setupCallLog();
        setupFavorites();
        getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);

        // Load the last manually loaded tab
        final SharedPreferences prefs = getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE);
        mLastManuallySelectedTab = prefs.getInt(PREF_LAST_MANUALLY_SELECTED_TAB,
                PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT);
        if (mLastManuallySelectedTab >= TAB_INDEX_COUNT) {
            // Stored value may have exceeded the number of current tabs. Reset it.
            mLastManuallySelectedTab = PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT;
        }

        setCurrentTab(intent);

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        final SharedPreferences.Editor editor =
                getSharedPreferences(PREFS_DIALTACTS, MODE_PRIVATE).edit();
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
        // TODO: Temporarily disable tab text labels (in all 4 tabs in this
        //   activity) so that the current tabs will all fit onscreen in
        //   portrait (bug 4520620).  (Also note we do setText("") rather
        //   leaving the text null, to work around bug 4521549.)
        tab.setText("");  // R.string.dialerIconLabel
        tab.setTabListener(new TabChangeListener(mDialpadFragment));
        tab.setIcon(R.drawable.ic_tab_dialer);
        getActionBar().addTab(tab);
        mDialpadFragment.resolveIntent();
    }

    private void setupCallLog() {
        final Tab tab = getActionBar().newTab();
        tab.setText("");  // R.string.recentCallsIconLabel
        tab.setIcon(R.drawable.ic_tab_recent);
        tab.setTabListener(new TabChangeListener(mCallLogFragment));
        getActionBar().addTab(tab);
    }

    private void setupContacts() {
        final Tab tab = getActionBar().newTab();
        tab.setText("");  // R.string.contactsIconLabel
        tab.setIcon(R.drawable.ic_tab_contacts);
        tab.setTabListener(new TabChangeListener(mContactsFragment));
        getActionBar().addTab(tab);

        // TODO: We should not artificially create Intents and put them into the Fragment.
        // It would be nicer to directly pass in the UI constant
        Intent intent = new Intent(UI.LIST_ALL_CONTACTS_ACTION);
        intent.setClass(this, PeopleActivity.class);

        ContactsIntentResolver resolver = new ContactsIntentResolver(this);
        ContactsRequest request = resolver.resolveIntent(intent);
        final ContactListFilter filter = ContactListFilter.createFilterWithType(
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
        tab.setText("");  // R.string.contactsFavoritesLabel
        tab.setIcon(R.drawable.ic_tab_starred);
        tab.setTabListener(new TabChangeListener(mStrequentFragment));
        getActionBar().addTab(tab);
        mStrequentFragment.setListener(mStrequentListener);
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

        if (DialpadFragment.phoneIsInUse()) {
            getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_DIALER));
        } else if (recentCallsRequest) {
            getActionBar().selectTab(getActionBar().getTabAt(TAB_INDEX_CALL_LOG));
        } else {
            getActionBar().selectTab(getActionBar().getTabAt(mLastManuallySelectedTab));
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
        // Fill in a phone number again.
        mDialpadFragment.resolveIntent();
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
        if (mInSearchUi) {
            // We should let the user go back to usual screens with tabs.
            exitSearchUi();
        } else if (isTaskRoot()) {
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
            ft.hide(mPhoneNumberPickerFragment);

            // During the call, we don't remember the tab position.
            if (!DialpadFragment.phoneIsInUse()) {
                // Remember this tab index. This function is also called, if the tab is set
                // automatically in which case the setter (setCurrentTab) has to set this to its old
                // value afterwards
                mLastManuallySelectedTab = tab.getPosition();
            }
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
            PhoneNumberInteraction.startInteractionForPhoneCall(
                    DialtactsActivity.this, contactUri);
        }

        @Override
        public void onAddToFavoritesAction(Uri contactUri) {
        }
    };

    private StrequentContactListFragment.Listener mStrequentListener =
            new StrequentContactListFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                    DialtactsActivity.this, contactUri);
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
        if (tabIndex != TAB_INDEX_FAVORITES) return false;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // This is currently a copy of the equivalent code of PeopleActivity (with the
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

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (mPhoneNumberPickerFragment != null && mPhoneNumberPickerFragment.isAdded()
                && !globalSearch) {
            enterSearchUi();
        } else {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        }
    }

    /**
     * Hides every tab and shows search UI for phone lookup.
     */
    private void enterSearchUi() {
        final ActionBar actionBar = getActionBar();

        final Tab tab = actionBar.getSelectedTab();

        // User can search during the call, but we don't want to remember the status.
        if (tab != null && !DialpadFragment.phoneIsInUse()) {
            mLastManuallySelectedTab = tab.getPosition();
        }

        // Instantiate or reset SearchView in ActionBar.
        if (mSearchView == null) {
            // TODO: layout is not what we want. Need "up" button instead of "close" button, etc.
            final View searchViewLayout =
                    getLayoutInflater().inflate(R.layout.custom_action_bar, null);
            mSearchView = (SearchView) searchViewLayout.findViewById(R.id.search_view);
            mSearchView.setQueryHint(getString(R.string.hint_findContacts));
            mSearchView.setOnQueryTextListener(mPhoneSearchQueryTextListener);
            mSearchView.setOnCloseListener(mPhoneSearchCloseListener);
            mSearchView.requestFocus();
            // Show soft keyboard when SearchView has a focus. Need to delay the request in order
            // to let InputMethodManager handle it correctly.
            mSearchView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewDetachedFromWindow(View v) {
                }

                @Override
                public void onViewAttachedToWindow(View v) {
                    if (mSearchView.hasFocus()) {
                        mSearchView.postDelayed(new Runnable() {
                            public void run() {
                                showInputMethod(mSearchView.findFocus());
                            }
                        }, 0);
                    }
                }
            });
            actionBar.setCustomView(searchViewLayout,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            mSearchView.setQuery(null, true);
        }

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

        // Show the search fragment and hide everything else.
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.show(mPhoneNumberPickerFragment);
        transaction.hide(mDialpadFragment);
        transaction.hide(mCallLogFragment);
        transaction.hide(mStrequentFragment);
        transaction.commit();

        mInSearchUi = true;
    }

    private void showInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    /**
     * Goes back to usual Phone UI with tags. Previously selected Tag and associated Fragment
     * should be automatically focused again.
     */
    private void exitSearchUi() {
        final ActionBar actionBar = getActionBar();

        // We want to hide SearchView and show Tabs. Also focus on previously selected one.
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Request to update option menu.
        invalidateOptionsMenu();

        mInSearchUi = false;
    }
}
