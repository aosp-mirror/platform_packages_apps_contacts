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
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.Listener.Action;
import com.android.contacts.list.ContactsRequest;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnCloseListener {

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

        void onUpButtonPressed();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";
    private static final String EXTRA_KEY_SELECTED_TAB = "navBar.selectedTab";

    private static final String PERSISTENT_LAST_TAB = "actionBarAdapter.lastTab";

    private boolean mSearchMode;
    private String mQueryString;

    private EditText mSearchView;
    private View mSearchContainer;

    private final Context mContext;
    private final SharedPreferences mPrefs;

    private Listener mListener;

    private final ActionBar mActionBar;
    private final int mActionBarNavigationMode;
    private final MyTabListener mTabListener;

    private boolean mShowHomeIcon;

    public interface TabState {
        public static int FAVORITES = 0;
        public static int ALL = 1;

        public static int COUNT = 2;
        public static int DEFAULT = ALL;
    }

    private int mCurrentTab = TabState.DEFAULT;

    public ActionBarAdapter(Context context, Listener listener, ActionBar actionBar) {
        mContext = context;
        mListener = listener;
        mActionBar = actionBar;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mShowHomeIcon = mContext.getResources().getBoolean(R.bool.show_home_icon);

        mActionBarNavigationMode = ActionBar.NAVIGATION_MODE_TABS;
        mTabListener = new MyTabListener();

        setupSearchView();
        setupTabs();
    }

    private void setupTabs() {
        addTab(TabState.FAVORITES, R.string.favorites_tab_label);
        addTab(TabState.ALL, R.string.all_contacts_tab_label);
    }

    private void setupSearchView() {
        mActionBar.setCustomView(R.layout.search_bar_expanded);
        mSearchContainer = mActionBar.getCustomView();
        mSearchContainer.setBackgroundColor(mContext.getResources().getColor(
                R.color.searchbox_background_color));
        mSearchView = (EditText) mSearchContainer.findViewById(R.id.search_view);
        mSearchView.setHint(mContext.getString(R.string.hint_findContacts));
        mSearchView.addTextChangedListener(new SearchTextWatcher());
        mSearchContainer.findViewById(R.id.search_close_button).setOnClickListener(
                new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchView.setText(null);
            }
        });
        mSearchContainer.findViewById(R.id.search_back_button).setOnClickListener(
                new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onUpButtonPressed();
                }
            }
        });
        mActionBar.setCustomView(mSearchContainer);
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
        if (mCurrentTab >= TabState.COUNT || mCurrentTab < 0) {
            // Invalid tab index was saved (b/12938207). Restore the default.
            mCurrentTab = TabState.DEFAULT;
        }
        // Show tabs or the expanded {@link SearchView}, depending on whether or not we are in
        // search mode.
        update(true /* skipAnimation */);
        // Expanding the {@link SearchView} clears the query, so set the query from the
        // {@link ContactsRequest} after it has been expanded, if applicable.
        if (mSearchMode && !TextUtils.isEmpty(mQueryString)) {
            setQueryString(mQueryString);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void addTab(int expectedTabIndex, int description) {
        final Tab tab = mActionBar.newTab();
        tab.setTabListener(mTabListener);
        tab.setText(description);
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

    private class SearchTextWatcher implements TextWatcher {

        @Override
        public void onTextChanged(CharSequence queryString, int start, int before, int count) {
            if (queryString.equals(mQueryString)) {
                return;
            }
            mQueryString = queryString.toString();
            if (!mSearchMode) {
                if (!TextUtils.isEmpty(queryString)) {
                    setSearchMode(true);
                }
            } else if (mListener != null) {
                mListener.onAction(Action.CHANGE_SEARCH_QUERY);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
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
        if (mCurrentTab != actionBarSelectedNavIndex) {
            mActionBar.setSelectedNavigationItem(mCurrentTab);
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
            update(false /* skipAnimation */);
            if (mSearchView == null) {
                return;
            }
            if (mSearchMode) {
                setFocusOnSearchView();
            } else {
                mSearchView.setText(null);
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
            mSearchView.setText(query);
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
        if (mShowHomeIcon && !mSearchMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_HOME;
        }
        if (mSearchMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_CUSTOM;
        } else {
            newFlags |= ActionBar.DISPLAY_SHOW_TITLE;
        }

        if (current != newFlags) {
            // Pass the mask here to preserve other flags that we're not interested here.
            mActionBar.setDisplayOptions(newFlags, MASK);
        }
    }

    private void update(boolean skipAnimation) {
        final boolean isIconifiedChanging
                = (mSearchContainer.getVisibility() == View.VISIBLE) != mSearchMode;
        if (isIconifiedChanging && !skipAnimation) {
            if (mSearchMode) {
                mSearchContainer.setVisibility(View.VISIBLE);
                mSearchContainer.setAlpha(0);
                mSearchContainer.animate().alpha(1);
                updateDisplayOptionsAndNavigationMode(isIconifiedChanging);
            } else {
                mSearchContainer.setAlpha(1);
                mSearchContainer.animate().alpha(0).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplayOptions();
                        mSearchContainer.setVisibility(View.GONE);
                        updateDisplayOptionsAndNavigationMode(isIconifiedChanging);
                    }
                });
            }
            return;
        }
        if (isIconifiedChanging && skipAnimation) {
            if (mSearchMode) {
                mSearchContainer.setVisibility(View.VISIBLE);
            } else {
                mSearchContainer.setVisibility(View.GONE);
            }
        }
        updateDisplayOptionsAndNavigationMode(isIconifiedChanging);
    }

    private void updateDisplayOptionsAndNavigationMode(boolean isIconifiedChanging) {
        if (mSearchMode) {
            setFocusOnSearchView();
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // expanding the {@link SearchView} when a search is initiated. Note that a side effect
            // of this method is that the {@link SearchView} query text is set to empty string.
            if (isIconifiedChanging) {
                final CharSequence queryText = mSearchView.getText();
                if (!TextUtils.isEmpty(queryText)) {
                    mSearchView.setText(queryText);
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
            }
            if (mListener != null) {
                mListener.onAction(Action.STOP_SEARCH_MODE);
                mListener.onSelectedTabChanged();
            }
        }
        updateDisplayOptions();
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
        showInputMethod(mSearchView); // Workaround for the "IME not popping up" issue.
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) mContext.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
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
