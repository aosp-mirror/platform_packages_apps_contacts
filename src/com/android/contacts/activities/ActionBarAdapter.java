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

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.Listener.Action;
import com.android.contacts.list.ContactsRequest;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnQueryTextListener, OnCloseListener {

    public interface Listener {
        public abstract class Action {
            public static final int CHANGE_SEARCH_QUERY = 0;
            public static final int START_SEARCH_MODE = 1;
            public static final int STOP_SEARCH_MODE = 2;
        }

        void onAction(int action);

        /**
         * Called when the user selects a tab.  The new tab can be obtained using
         * {@link #getCurrentTab}.
         */
        void onSelectedTabChanged();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";
    private static final String EXTRA_KEY_SELECTED_TAB = "navBar.selectedTab";

    private static final String PERSISTENT_LAST_TAB = "actionBarAdapter.lastTab";

    private boolean mSearchMode;
    private String mQueryString;

    private SearchView mSearchView;

    private final Context mContext;
    private final SharedPreferences mPrefs;

    private Listener mListener;

    private final ActionBar mActionBar;
    private final int mActionBarNavigationMode;
    private final MyTabListener mTabListener;
    private final MyNavigationListener mNavigationListener;

    private boolean mShowHomeIcon;
    private boolean mShowTabsAsText;

    public interface TabState {
        public static int FAVORITES = 0;
        public static int ALL = 1;
        public static int GROUPS = 2;

        public static int COUNT = 3;
        public static int DEFAULT = ALL;
    }

    private int mCurrentTab = TabState.DEFAULT;

    /**
     * Extension of ArrayAdapter to be used for the action bar navigation drop list.  It is not
     * possible to change the text appearance of a text item that is in the spinner header or
     * in the drop down list using a selector xml file.  The only way to differentiate the two
     * is if the view is gotten via {@link #getView(int, View, ViewGroup)} or
     * {@link #getDropDownView(int, View, ViewGroup)}.
     */
    private class CustomArrayAdapter extends ArrayAdapter<String> {

        public CustomArrayAdapter(Context context, int textResId) {
            super(context, textResId);
        }

        public View getView (int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);
            textView.setTextAppearance(mContext,
                    R.style.PeopleNavigationDropDownHeaderTextAppearance);
            return textView;
        }

        public View getDropDownView (int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
            textView.setTextAppearance(mContext,
                    R.style.PeopleNavigationDropDownTextAppearance);
            return textView;
        }
    }

    public ActionBarAdapter(Context context, Listener listener, ActionBar actionBar,
            boolean isUsingTwoPanes) {
        mContext = context;
        mListener = listener;
        mActionBar = actionBar;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mShowHomeIcon = mContext.getResources().getBoolean(R.bool.show_home_icon);

        // On wide screens, show the tabs as text (instead of icons)
        mShowTabsAsText = isUsingTwoPanes;
        if (isUsingTwoPanes) {
            mActionBarNavigationMode = ActionBar.NAVIGATION_MODE_LIST;
            mTabListener = null;
            mNavigationListener = new MyNavigationListener();
        } else {
            mActionBarNavigationMode = ActionBar.NAVIGATION_MODE_TABS;
            mTabListener = new MyTabListener();
            mNavigationListener = null;
        }

        // Set up search view.
        View customSearchView = LayoutInflater.from(mActionBar.getThemedContext()).inflate(
                R.layout.custom_action_bar, null);
        int searchViewWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.search_view_width);
        if (searchViewWidth == 0) {
            searchViewWidth = LayoutParams.MATCH_PARENT;
        }
        LayoutParams layoutParams = new LayoutParams(searchViewWidth, LayoutParams.WRAP_CONTENT);
        mSearchView = (SearchView) customSearchView.findViewById(R.id.search_view);
        // Since the {@link SearchView} in this app is "click-to-expand", set the below mode on the
        // {@link SearchView} so that the magnifying glass icon appears inside the editable text
        // field. (In the "click-to-expand" search pattern, the user must explicitly expand the
        // search field and already knows a search is being conducted, so the icon is redundant
        // and can go away once the user starts typing.)
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setQueryHint(mContext.getString(R.string.hint_findContacts));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setQuery(mQueryString, false);
        mActionBar.setCustomView(customSearchView, layoutParams);

        // Set up tabs or navigation list
        switch(mActionBarNavigationMode) {
            case ActionBar.NAVIGATION_MODE_TABS:
                setupTabs();
                break;
            case ActionBar.NAVIGATION_MODE_LIST:
                setupNavigationList();
                break;
        }
    }

    private void setupTabs() {
        addTab(TabState.FAVORITES, R.drawable.ic_tab_starred, R.string.contactsFavoritesLabel);
        addTab(TabState.ALL, R.drawable.ic_tab_all, R.string.contactsAllLabel);
        addTab(TabState.GROUPS, R.drawable.ic_tab_groups, R.string.contactsGroupsLabel);
    }

    private void setupNavigationList() {
        ArrayAdapter<String> navAdapter = new CustomArrayAdapter(mContext,
                R.layout.people_navigation_item);
        navAdapter.add(mContext.getString(R.string.contactsFavoritesLabel));
        navAdapter.add(mContext.getString(R.string.contactsAllLabel));
        navAdapter.add(mContext.getString(R.string.contactsGroupsLabel));
        mActionBar.setListNavigationCallbacks(navAdapter, mNavigationListener);
    }

    /**
     * Because the navigation list items are in a different order than tab items, this returns
     * the appropriate tab from the navigation item position.
     */
    private int getTabPositionFromNavigationItemPosition(int navItemPos) {
        switch(navItemPos) {
            case 0:
                return TabState.FAVORITES;
            case 1:
                return TabState.ALL;
            case 2:
                return TabState.GROUPS;
        }
        throw new IllegalArgumentException(
                "Parameter must be between 0 and " + Integer.toString(TabState.COUNT-1)
                + " inclusive.");
    }

    /**
     * This is the inverse of {@link getTabPositionFromNavigationItemPosition}.
     */
    private int getNavigationItemPositionFromTabPosition(int tabPos) {
        switch(tabPos) {
            case TabState.FAVORITES:
                return 0;
            case TabState.ALL:
                return 1;
            case TabState.GROUPS:
                return 2;
        }
        throw new IllegalArgumentException(
                "Parameter must be between 0 and " + Integer.toString(TabState.COUNT-1)
                + " inclusive.");
    }

    public void initialize(Bundle savedState, ContactsRequest request) {
        if (savedState == null) {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
            mCurrentTab = loadLastTabPreference();
        } else {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);

            // Just set to the field here.  The listener will be notified by update().
            mCurrentTab = savedState.getInt(EXTRA_KEY_SELECTED_TAB);
        }
        // Show tabs or the expanded {@link SearchView}, depending on whether or not we are in
        // search mode.
        update();
        // Expanding the {@link SearchView} clears the query, so set the query from the
        // {@link ContactsRequest} after it has been expanded, if applicable.
        if (mSearchMode && !TextUtils.isEmpty(mQueryString)) {
            setQueryString(mQueryString);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void addTab(int expectedTabIndex, int icon, int description) {
        final Tab tab = mActionBar.newTab();
        tab.setTabListener(mTabListener);
        if (mShowTabsAsText) {
            tab.setText(description);
        } else {
            tab.setIcon(icon);
            tab.setContentDescription(description);
        }
        mActionBar.addTab(tab);
        if (expectedTabIndex != tab.getPosition()) {
            throw new IllegalStateException("Tabs must be created in the right order");
        }
    }

    private class MyTabListener implements ActionBar.TabListener {
        /**
         * If true, it won't call {@link #setCurrentTab} in {@link #onTabSelected}.
         * This flag is used when we want to programmatically update the current tab without
         * {@link #onTabSelected} getting called.
         */
        public boolean mIgnoreTabSelected;

        @Override public void onTabReselected(Tab tab, FragmentTransaction ft) { }
        @Override public void onTabUnselected(Tab tab, FragmentTransaction ft) { }

        @Override public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (!mIgnoreTabSelected) {
                setCurrentTab(tab.getPosition());
            }
        }
    }

    private class MyNavigationListener implements ActionBar.OnNavigationListener {
        public boolean mIgnoreNavigationItemSelected;

        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            if (!mIgnoreNavigationItemSelected) {
                setCurrentTab(getTabPositionFromNavigationItemPosition(itemPosition));
            }
            return true;
        }
    }

    /**
     * Change the current tab, and notify the listener.
     */
    public void setCurrentTab(int tab) {
        setCurrentTab(tab, true);
    }

    /**
     * Change the current tab
     */
    public void setCurrentTab(int tab, boolean notifyListener) {
        if (tab == mCurrentTab) {
            return;
        }
        mCurrentTab = tab;

        final int actionBarSelectedNavIndex = mActionBar.getSelectedNavigationIndex();
        switch(mActionBar.getNavigationMode()) {
            case ActionBar.NAVIGATION_MODE_TABS:
                if (mCurrentTab != actionBarSelectedNavIndex) {
                    mActionBar.setSelectedNavigationItem(mCurrentTab);
                }
                break;
            case ActionBar.NAVIGATION_MODE_LIST:
                if (mCurrentTab != getTabPositionFromNavigationItemPosition(
                        actionBarSelectedNavIndex)) {
                    mActionBar.setSelectedNavigationItem(
                            getNavigationItemPositionFromTabPosition(mCurrentTab));
                }
                break;
        }

        if (notifyListener && mListener != null) mListener.onSelectedTabChanged();
        saveLastTabPreference(mCurrentTab);
    }

    public int getCurrentTab() {
        return mCurrentTab;
    }

    /**
     * @return Whether in search mode, i.e. if the search view is visible/expanded.
     *
     * Note even if the action bar is in search mode, if the query is empty, the search fragment
     * will not be in search mode.
     */
    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            update();
            if (mSearchView == null) {
                return;
            }
            if (mSearchMode) {
                setFocusOnSearchView();
            } else {
                mSearchView.setQuery(null, false);
            }
        } else if (flag) {
            // Everything is already set up. Still make sure the keyboard is up
            if (mSearchView != null) setFocusOnSearchView();
        }
    }

    public String getQueryString() {
        return mSearchMode ? mQueryString : null;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        if (mSearchView != null) {
            mSearchView.setQuery(query, false);
        }
    }

    /** @return true if the "UP" icon is showing. */
    public boolean isUpShowing() {
        return mSearchMode; // Only shown on the search mode.
    }

    private void updateDisplayOptions() {
        // All the flags we may change in this method.
        final int MASK = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM;

        // The current flags set to the action bar.  (only the ones that we may change here)
        final int current = mActionBar.getDisplayOptions() & MASK;

        // Build the new flags...
        int newFlags = 0;
        newFlags |= ActionBar.DISPLAY_SHOW_TITLE;
        if (mShowHomeIcon) {
            newFlags |= ActionBar.DISPLAY_SHOW_HOME;
        }
        if (mSearchMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_HOME;
            newFlags |= ActionBar.DISPLAY_HOME_AS_UP;
            newFlags |= ActionBar.DISPLAY_SHOW_CUSTOM;
        }
        mActionBar.setHomeButtonEnabled(mSearchMode);

        if (current != newFlags) {
            // Pass the mask here to preserve other flags that we're not interested here.
            mActionBar.setDisplayOptions(newFlags, MASK);
        }
    }

    private void update() {
        boolean isIconifiedChanging = mSearchView.isIconified() == mSearchMode;
        if (mSearchMode) {
            setFocusOnSearchView();
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // expanding the {@link SearchView} when a search is initiated. Note that a side effect
            // of this method is that the {@link SearchView} query text is set to empty string.
            if (isIconifiedChanging) {
                final CharSequence queryText = mSearchView.getQuery();
                mSearchView.onActionViewExpanded();
                if (!TextUtils.isEmpty(queryText)) {
                    mSearchView.setQuery(queryText, false);
                }
            }
            if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_STANDARD) {
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            }
            if (mListener != null) {
                mListener.onAction(Action.START_SEARCH_MODE);
            }
        } else {
            final int currentNavigationMode = mActionBar.getNavigationMode();
            if (mActionBarNavigationMode == ActionBar.NAVIGATION_MODE_TABS
                    && currentNavigationMode != ActionBar.NAVIGATION_MODE_TABS) {
                // setNavigationMode will trigger onTabSelected() with the tab which was previously
                // selected.
                // The issue is that when we're first switching to the tab navigation mode after
                // screen orientation changes, onTabSelected() will get called with the first tab
                // (i.e. favorite), which would results in mCurrentTab getting set to FAVORITES and
                // we'd lose restored tab.
                // So let's just disable the callback here temporarily.  We'll notify the listener
                // after this anyway.
                mTabListener.mIgnoreTabSelected = true;
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                mActionBar.setSelectedNavigationItem(mCurrentTab);
                mTabListener.mIgnoreTabSelected = false;
            } else if (mActionBarNavigationMode == ActionBar.NAVIGATION_MODE_LIST
                    && currentNavigationMode != ActionBar.NAVIGATION_MODE_LIST) {
                mNavigationListener.mIgnoreNavigationItemSelected = true;
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
                mActionBar.setSelectedNavigationItem(
                        getNavigationItemPositionFromTabPosition(mCurrentTab));
                mNavigationListener.mIgnoreNavigationItemSelected = false;
            }
            mActionBar.setTitle(null);
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // collapsing the {@link SearchView} when search mode is exited.
            if (isIconifiedChanging) {
                mSearchView.onActionViewCollapsed();
            }
            if (mListener != null) {
                mListener.onAction(Action.STOP_SEARCH_MODE);
                mListener.onSelectedTabChanged();
            }
        }
        updateDisplayOptions();
    }

    @Override
    public boolean onQueryTextChange(String queryString) {
        // TODO: Clean up SearchView code because it keeps setting the SearchView query,
        // invoking onQueryChanged, setting up the fragment again, invalidating the options menu,
        // storing the SearchView again, and etc... unless we add in the early return statements.
        if (queryString.equals(mQueryString)) {
            return false;
        }
        mQueryString = queryString;
        if (!mSearchMode) {
            if (!TextUtils.isEmpty(queryString)) {
                setSearchMode(true);
            }
        } else if (mListener != null) {
            mListener.onAction(Action.CHANGE_SEARCH_QUERY);
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        // When the search is "committed" by the user, then hide the keyboard so the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
        }
        return true;
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mCurrentTab);
    }

    /**
     * Clears the focus from the {@link SearchView} if we are in search mode.
     * This will suppress the IME if it is visible.
     */
    public void clearFocusOnSearchView() {
        if (isSearchMode()) {
            if (mSearchView != null) {
                mSearchView.clearFocus();
            }
        }
    }

    public void setFocusOnSearchView() {
        mSearchView.requestFocus();
        mSearchView.setIconified(false); // Workaround for the "IME not popping up" issue.
    }

    private void saveLastTabPreference(int tab) {
        mPrefs.edit().putInt(PERSISTENT_LAST_TAB, tab).apply();
    }

    private int loadLastTabPreference() {
        try {
            return mPrefs.getInt(PERSISTENT_LAST_TAB, TabState.DEFAULT);
        } catch (IllegalArgumentException e) {
            // Preference is corrupt?
            return TabState.DEFAULT;
        }
    }
}
