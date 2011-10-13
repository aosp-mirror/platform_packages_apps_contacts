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
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.AccountFilterActivity;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactListFilterController.ContactListFilterListener;
import com.android.contacts.list.ContactListItemView;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.PhoneFavoriteFragment;
import com.android.contacts.list.PhoneNumberPickerFragment;
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
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.UI;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
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

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";

    /**
     * Copied from PhoneApp. See comments in Phone app for more detail.
     */
    public static final String EXTRA_CALL_ORIGIN = "com.android.phone.CALL_ORIGIN";
    public static final String CALL_ORIGIN_DIALTACTS =
            "com.android.contacts.activities.DialtactsActivity";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

    /** Used both by {@link ActionBar} and {@link ViewPagerAdapter} */
    private static final int TAB_INDEX_DIALER = 0;
    private static final int TAB_INDEX_CALL_LOG = 1;
    private static final int TAB_INDEX_FAVORITES = 2;

    private static final int TAB_INDEX_COUNT = 3;

    private SharedPreferences mPrefs;

    /** Last manually selected tab index */
    private static final String PREF_LAST_MANUALLY_SELECTED_TAB =
            "DialtactsActivity_last_manually_selected_tab";
    private static final int PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT = TAB_INDEX_DIALER;

    /**
     * Listener interface for Fragments accommodated in {@link ViewPager} enabling them to know
     * when it becomes visible or invisible inside the ViewPager.
     */
    public interface ViewPagerVisibilityListener {
        public void onVisibilityChanged(boolean visible);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        private DialpadFragment mDialpadFragment;
        private CallLogFragment mCallLogFragment;
        private PhoneFavoriteFragment mPhoneFavoriteFragment;

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_DIALER:
                    if (mDialpadFragment == null) {
                        mDialpadFragment = new DialpadFragment();
                    }
                    return mDialpadFragment;
                case TAB_INDEX_CALL_LOG:
                    if (mCallLogFragment == null) {
                        mCallLogFragment = new CallLogFragment();
                    }
                    return mCallLogFragment;
                case TAB_INDEX_FAVORITES:
                    if (mPhoneFavoriteFragment == null) {
                        mPhoneFavoriteFragment = new PhoneFavoriteFragment();
                    }
                    return mPhoneFavoriteFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT;
        }
    }

    private class PageChangeListener implements OnPageChangeListener {
        private int mCurrentPosition = -1;
        /**
         * Used during page migration, to remember the next position {@link #onPageSelected(int)}
         * specified.
         */
        private int mNextPosition = -1;

        @Override
        public void onPageScrolled(
                int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            final ActionBar actionBar = getActionBar();
            if (mCurrentPosition == position) {
                Log.w(TAG, "Previous position and next position became same (" + position + ")");
            }

            actionBar.selectTab(actionBar.getTabAt(position));
            mNextPosition = position;
        }

        public void setCurrentPosition(int position) {
            mCurrentPosition = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            switch (state) {
                case ViewPager.SCROLL_STATE_IDLE: {
                    if (mCurrentPosition >= 0) {
                        sendFragmentVisibilityChange(mCurrentPosition, false);
                    }
                    if (mNextPosition >= 0) {
                        sendFragmentVisibilityChange(mNextPosition, true);
                    }
                    invalidateOptionsMenu();

                    mCurrentPosition = mNextPosition;
                    break;
                }
                case ViewPager.SCROLL_STATE_DRAGGING:
                case ViewPager.SCROLL_STATE_SETTLING:
                default:
                    break;
            }
        }
    }

    private String mFilterText;

    /** Enables horizontal swipe between Fragments. */
    private ViewPager mViewPager;
    private final PageChangeListener mPageChangeListener = new PageChangeListener();
    private DialpadFragment mDialpadFragment;
    private CallLogFragment mCallLogFragment;
    private PhoneFavoriteFragment mPhoneFavoriteFragment;

    private final TabListener mTabListener = new TabListener() {
        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mViewPager.getCurrentItem() != tab.getPosition()) {
                mViewPager.setCurrentItem(tab.getPosition(), true);
            }

            // During the call, we don't remember the tab position.
            if (!DialpadFragment.phoneIsInUse()) {
                // Remember this tab index. This function is also called, if the tab is set
                // automatically in which case the setter (setCurrentTab) has to set this to its old
                // value afterwards
                mLastManuallySelectedFragment = tab.getPosition();
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    };

    /**
     * Fragment for searching phone numbers. Unlike the other Fragments, this doesn't correspond
     * to tab but is shown by a search action.
     */
    private PhoneNumberPickerFragment mSearchFragment;
    /**
     * True when this Activity is in its search UI (with a {@link SearchView} and
     * {@link PhoneNumberPickerFragment}).
     */
    private boolean mInSearchUi;
    private SearchView mSearchView;

    private final OnClickListener mFilterOptionClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            final PopupMenu popupMenu = new PopupMenu(DialtactsActivity.this, view);
            final Menu menu = popupMenu.getMenu();
            popupMenu.inflate(R.menu.dialtacts_search_options);
            final MenuItem filterOptionMenuItem = menu.findItem(R.id.filter_option);
            filterOptionMenuItem.setOnMenuItemClickListener(mFilterOptionsMenuItemClickListener);
            final MenuItem addContactOptionMenuItem = menu.findItem(R.id.add_contact);
            addContactOptionMenuItem.setIntent(
                    new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
            popupMenu.show();
        }
    };

    /**
     * The index of the Fragment (or, the tab) that has last been manually selected.
     * This value does not keep track of programmatically set Tabs (e.g. Call Log after a Call)
     */
    private int mLastManuallySelectedFragment;

    private ContactListFilterController mContactListFilterController;
    private OnMenuItemClickListener mFilterOptionsMenuItemClickListener =
            new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final Intent intent =
                    new Intent(DialtactsActivity.this, AccountFilterActivity.class);
            ContactListFilter filter = mContactListFilterController.getFilter();
            startActivityForResult(intent, AccountFilterActivity.DEFAULT_REQUEST_CODE);
            return true;
        }
    };

    private OnMenuItemClickListener mSearchMenuItemClickListener =
            new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            enterSearchUi();
            return true;
        }
    };

    /**
     * Listener used when one of phone numbers in search UI is selected. This will initiate a
     * phone call using the phone number.
     */
    private final OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener =
            new OnPhoneNumberPickerActionListener() {
                @Override
                public void onPickPhoneNumberAction(Uri dataUri) {
                    // Specify call-origin so that users will see the previous tab instead of
                    // CallLog screen (search UI will be automatically exited).
                    PhoneNumberInteraction.startInteractionForPhoneCall(
                            DialtactsActivity.this, dataUri,
                            CALL_ORIGIN_DIALTACTS);
                }

                @Override
                public void onShortcutIntentCreated(Intent intent) {
                    Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
                }

                @Override
                public void onHomeInActionBarSelected() {
                    exitSearchUi();
                }
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final OnQueryTextListener mPhoneSearchQueryTextListener =
            new OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    View view = getCurrentFocus();
                    if (view != null) {
                        hideInputMethod(view);
                        view.clearFocus();
                    }
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    // Show search result with non-empty text. Show a bare list otherwise.
                    mSearchFragment.setQueryString(newText, true);
                    mSearchFragment.setSearchMode(!TextUtils.isEmpty(newText));
                    return true;
                }
    };

    /**
     * Listener used to handle the "close" button on the right side of {@link SearchView}.
     * If some text is in the search view, this will clean it up. Otherwise this will exit
     * the search UI and let users go back to usual Phone UI.
     *
     * This does _not_ handle back button.
     */
    private final OnCloseListener mPhoneSearchCloseListener =
            new OnCloseListener() {
                @Override
                public boolean onClose() {
                    if (!TextUtils.isEmpty(mSearchView.getQuery())) {
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

        mContactListFilterController = new ContactListFilterController(this);
        mContactListFilterController.addListener(new ContactListFilterListener() {
            @Override
            public void onContactListFilterChanged() {
                boolean doInvalidateOptionsMenu = false;

                if (mPhoneFavoriteFragment != null && mPhoneFavoriteFragment.isAdded()) {
                    mPhoneFavoriteFragment.setFilter(mContactListFilterController.getFilter());
                    doInvalidateOptionsMenu = true;
                }

                if (mSearchFragment != null && mSearchFragment.isAdded()) {
                    mSearchFragment.setFilter(mContactListFilterController.getFilter());
                    doInvalidateOptionsMenu = true;
                } else {
                    Log.w(TAG, "Search Fragment isn't available when ContactListFilter is changed");
                }

                if (doInvalidateOptionsMenu) {
                    invalidateOptionsMenu();
                }
            }
        });

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(new ViewPagerAdapter(getFragmentManager()));
        mViewPager.setOnPageChangeListener(mPageChangeListener);

        prepareSearchView();

        // Setup the ActionBar tabs (the order matches the tab-index contants TAB_INDEX_*)
        setupDialer();
        setupCallLog();
        setupFavorites();
        getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);

        // Load the last manually loaded tab
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mLastManuallySelectedFragment = mPrefs.getInt(PREF_LAST_MANUALLY_SELECTED_TAB,
                PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT);
        if (mLastManuallySelectedFragment >= TAB_INDEX_COUNT) {
            // Stored value may have exceeded the number of current tabs. Reset it.
            mLastManuallySelectedFragment = PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT;
        }

        setCurrentTab(intent);

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Force filter reload to reflect possible filter changes done via People UI.
        //
        // Ideally both (People/Phone) UI should share the same instance for
        // ContactListFilterController and they should be able to receive filter change event
        // from the same controller (Bug 5165507)
        mContactListFilterController.onStart(true);
        if (mPhoneFavoriteFragment != null) {
            mPhoneFavoriteFragment.setFilter(mContactListFilterController.getFilter());
        }
        if (mSearchFragment != null) {
            mSearchFragment.setFilter(mContactListFilterController.getFilter());
        }
    }

    private void prepareSearchView() {
        final View searchViewLayout =
                getLayoutInflater().inflate(R.layout.dialtacts_custom_action_bar, null);
        mSearchView = (SearchView) searchViewLayout.findViewById(R.id.search_view);
        mSearchView.setOnQueryTextListener(mPhoneSearchQueryTextListener);
        mSearchView.setOnCloseListener(mPhoneSearchCloseListener);
        // Since we're using a custom layout for showing SearchView instead of letting the
        // search menu icon do that job, we need to manually configure the View so it looks
        // "shown via search menu".
        // - it should be iconified by default
        // - it should not be iconified at this time
        // See also comments for onActionViewExpanded()/onActionViewCollapsed()
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setQueryHint(getString(R.string.hint_findContacts));
        mSearchView.setIconified(false);
        mSearchView.setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showInputMethod(view.findFocus());
                }
            }
        });

        if (!ViewConfiguration.get(this).hasPermanentMenuKey()) {
            // Filter option menu should be shown on the right side of SearchView.
            final View filterOptionView = searchViewLayout.findViewById(R.id.search_option);
            filterOptionView.setVisibility(View.VISIBLE);
            filterOptionView.setOnClickListener(mFilterOptionClickListener);
        }

        getActionBar().setCustomView(searchViewLayout,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        // This method can be called before onCreate(), at which point we cannot rely on ViewPager.
        // In that case, we will setup the "current position" soon after the ViewPager is ready.
        final int currentPosition = mViewPager != null ? mViewPager.getCurrentItem() : -1;

        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
            mDialpadFragment.setListener(mDialpadListener);
            if (currentPosition == TAB_INDEX_DIALER) {
                mDialpadFragment.onVisibilityChanged(true);
            }
        } else if (fragment instanceof CallLogFragment) {
            mCallLogFragment = (CallLogFragment) fragment;
            if (currentPosition == TAB_INDEX_CALL_LOG) {
                mCallLogFragment.onVisibilityChanged(true);
            }
        } else if (fragment instanceof PhoneFavoriteFragment) {
            mPhoneFavoriteFragment = (PhoneFavoriteFragment) fragment;
            mPhoneFavoriteFragment.setListener(mPhoneFavoriteListener);
            if (mContactListFilterController != null
                    && mContactListFilterController.getFilter() != null) {
                mPhoneFavoriteFragment.setFilter(mContactListFilterController.getFilter());
            }
        } else if (fragment instanceof PhoneNumberPickerFragment) {
            mSearchFragment = (PhoneNumberPickerFragment) fragment;
            mSearchFragment.setOnPhoneNumberPickerActionListener(mPhoneNumberPickerActionListener);
            mSearchFragment.setQuickContactEnabled(true);
            mSearchFragment.setDarkTheme(true);
            mSearchFragment.setPhotoPosition(ContactListItemView.PhotoPosition.LEFT);
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            if (mInSearchUi) {
                transaction.show(mSearchFragment);
            } else {
                transaction.hide(mSearchFragment);
            }
            transaction.commitAllowingStateLoss();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mPrefs.edit().putInt(PREF_LAST_MANUALLY_SELECTED_TAB, mLastManuallySelectedFragment)
                .apply();
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
        tab.setContentDescription(R.string.dialerIconLabel);
        tab.setTabListener(mTabListener);
        tab.setIcon(R.drawable.ic_tab_dialer);
        getActionBar().addTab(tab);
    }

    private void setupCallLog() {
        final Tab tab = getActionBar().newTab();
        tab.setContentDescription(R.string.recentCallsIconLabel);
        tab.setIcon(R.drawable.ic_tab_recent);
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    private void setupFavorites() {
        final Tab tab = getActionBar().newTab();
        tab.setContentDescription(R.string.contactsFavoritesLabel);
        tab.setIcon(R.drawable.ic_tab_all);
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    /**
     * Returns true if the intent is due to hitting the green send key while in a call.
     *
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(final Intent intent,
            final boolean recentCallsRequest) {
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

        // Remember the old manually selected tab index so that it can be restored if it is
        // overwritten by one of the programmatic tab selections
        final int savedTabIndex = mLastManuallySelectedFragment;

        final int tabIndex;
        if (DialpadFragment.phoneIsInUse() || isDialIntent(intent)) {
            tabIndex = TAB_INDEX_DIALER;
        } else if (recentCallsRequest) {
            tabIndex = TAB_INDEX_CALL_LOG;
        } else {
            tabIndex = mLastManuallySelectedFragment;
        }

        final int previousItemIndex = mViewPager.getCurrentItem();
        mViewPager.setCurrentItem(tabIndex, false /* smoothScroll */);
        if (previousItemIndex != tabIndex) {
            sendFragmentVisibilityChange(previousItemIndex, false);
        }
        mPageChangeListener.setCurrentPosition(tabIndex);
        sendFragmentVisibilityChange(tabIndex, true);

        // Restore to the previous manual selection
        mLastManuallySelectedFragment = savedTabIndex;
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        fixIntent(newIntent);
        setCurrentTab(newIntent);
        final String action = newIntent.getAction();
        if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            setupFilterText(newIntent);
        }
        if (mInSearchUi || mSearchFragment.isVisible()) {
            exitSearchUi();
        }

        if (mViewPager.getCurrentItem() == TAB_INDEX_DIALER) {
            if (mDialpadFragment != null) {
                mDialpadFragment.configureScreenFromIntent(newIntent);
            } else {
                Log.e(TAG, "DialpadFragment isn't ready yet when the tab is already selected.");
            }
        }
        invalidateOptionsMenu();
    }

    /** Returns true if the given intent contains a phone number to populate the dialer with */
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
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

    private DialpadFragment.Listener mDialpadListener = new DialpadFragment.Listener() {
        @Override
        public void onSearchButtonPressed() {
            enterSearchUi();
        }
    };

    private PhoneFavoriteFragment.Listener mPhoneFavoriteListener =
            new PhoneFavoriteFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                    DialtactsActivity.this, contactUri,
                    CALL_ORIGIN_DIALTACTS);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dialtacts_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem searchMenuItem = menu.findItem(R.id.search_on_action_bar);
        final MenuItem filterOptionMenuItem = menu.findItem(R.id.filter_option);
        final MenuItem addContactOptionMenuItem = menu.findItem(R.id.add_contact);
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings);
        Tab tab = getActionBar().getSelectedTab();
        if (mInSearchUi) {
            searchMenuItem.setVisible(false);
            if (ViewConfiguration.get(this).hasPermanentMenuKey()) {
                filterOptionMenuItem.setVisible(true);
                filterOptionMenuItem.setOnMenuItemClickListener(
                        mFilterOptionsMenuItemClickListener);
                addContactOptionMenuItem.setVisible(true);
                addContactOptionMenuItem.setIntent(
                        new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
            } else {
                // Filter option menu should be not be shown as a overflow menu.
                filterOptionMenuItem.setVisible(false);
                addContactOptionMenuItem.setVisible(false);
            }
            callSettingsMenuItem.setVisible(false);
        } else {
            final boolean showCallSettingsMenu;
            if (tab != null && tab.getPosition() == TAB_INDEX_DIALER) {
                searchMenuItem.setVisible(false);
                // When permanent menu key is _not_ available, the call settings menu should be
                // available via DialpadFragment.
                showCallSettingsMenu = ViewConfiguration.get(this).hasPermanentMenuKey();
            } else {
                searchMenuItem.setVisible(true);
                searchMenuItem.setOnMenuItemClickListener(mSearchMenuItemClickListener);
                showCallSettingsMenu = true;
            }
            if (tab != null && tab.getPosition() == TAB_INDEX_FAVORITES) {
                filterOptionMenuItem.setVisible(true);
                filterOptionMenuItem.setOnMenuItemClickListener(
                        mFilterOptionsMenuItemClickListener);
                addContactOptionMenuItem.setVisible(true);
                addContactOptionMenuItem.setIntent(
                        new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
            } else {
                filterOptionMenuItem.setVisible(false);
                addContactOptionMenuItem.setVisible(false);
            }

            if (showCallSettingsMenu) {
                callSettingsMenuItem.setVisible(true);
                callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
            } else {
                callSettingsMenuItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (mSearchFragment != null && mSearchFragment.isAdded() && !globalSearch) {
            if (mInSearchUi) {
                if (mSearchView.hasFocus()) {
                    showInputMethod(mSearchView.findFocus());
                } else {
                    mSearchView.requestFocus();
                }
            } else {
                enterSearchUi();
            }
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
            mLastManuallySelectedFragment = tab.getPosition();
        }

        mSearchView.setQuery(null, true);

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        sendFragmentVisibilityChange(mViewPager.getCurrentItem(), false);

        // Show the search fragment and hide everything else.
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.show(mSearchFragment);
        transaction.commitAllowingStateLoss();
        mViewPager.setVisibility(View.GONE);

        // We need to call this and onActionViewCollapsed() manually, since we are using a custom
        // layout instead of asking the search menu item to take care of SearchView.
        mSearchView.onActionViewExpanded();
        mInSearchUi = true;
    }

    private void showInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(view, 0)) {
                Log.w(TAG, "Failed to show soft input method.");
            }
        }
    }

    private void hideInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Goes back to usual Phone UI with tags. Previously selected Tag and associated Fragment
     * should be automatically focused again.
     */
    private void exitSearchUi() {
        final ActionBar actionBar = getActionBar();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.hide(mSearchFragment);
        transaction.commitAllowingStateLoss();

        // We want to hide SearchView and show Tabs. Also focus on previously selected one.
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        sendFragmentVisibilityChange(mViewPager.getCurrentItem(), true);

        mViewPager.setVisibility(View.VISIBLE);

        hideInputMethod(getCurrentFocus());

        // Request to update option menu.
        invalidateOptionsMenu();

        // See comments in onActionViewExpanded()
        mSearchView.onActionViewCollapsed();
        mInSearchUi = false;
    }

    private Fragment getFragmentAt(int position) {
        switch (position) {
            case TAB_INDEX_DIALER:
                return mDialpadFragment;
            case TAB_INDEX_CALL_LOG:
                return mCallLogFragment;
            case TAB_INDEX_FAVORITES:
                return mPhoneFavoriteFragment;
            default:
                throw new IllegalStateException("Unknown fragment index: " + position);
        }
    }

    private void sendFragmentVisibilityChange(int position, boolean visibility) {
        final Fragment fragment = getFragmentAt(position);
        if (fragment instanceof ViewPagerVisibilityListener) {
            ((ViewPagerVisibilityListener) fragment).onVisibilityChanged(visibility);
        }
    }

    /** Returns an Intent to launch Call Settings screen */
    public static Intent getCallSettingsIntent() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case AccountFilterActivity.DEFAULT_REQUEST_CODE: {
                ContactListFilter filter = (ContactListFilter) data.getParcelableExtra(
                        AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
                if (filter == null) {
                    return;
                }
                if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                    mContactListFilterController.selectCustomFilter();
                } else {
                    mContactListFilterController.setContactListFilter(filter, true);
                }
            }
            break;
        }
    }
}
