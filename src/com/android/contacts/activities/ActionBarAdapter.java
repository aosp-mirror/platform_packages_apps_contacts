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

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SearchView.OnCloseListener;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.Listener.Action;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.list.ContactsRequest;
import com.google.android.libraries.material.featurehighlight.FeatureHighlight;
import com.google.android.libraries.material.featurehighlight.appcompat.ToolbarNavigationIconFinder;

import java.util.ArrayList;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 */
public class ActionBarAdapter implements OnCloseListener {

    public interface Listener {
        public abstract class Action {
            public static final int CHANGE_SEARCH_QUERY = 0;
            public static final int START_SEARCH_MODE = 1;
            public static final int START_SELECTION_MODE = 2;
            public static final int STOP_SEARCH_AND_SELECTION_MODE = 3;
            public static final int BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE = 4;
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
    private static final String EXTRA_KEY_SELECTED_MODE = "navBar.selectionMode";

    private static final String PERSISTENT_LAST_TAB = "actionBarAdapter.lastTab";

    private boolean mSelectionMode;
    private boolean mSearchMode;
    private String mQueryString;

    private EditText mSearchView;
    private View mClearSearchView;
    /** The view that represents tabs when we are in portrait mode **/
    private View mPortraitTabs;
    /** The view that represents tabs when we are in landscape mode **/
    private View mLandscapeTabs;
    private View mSearchContainer;
    private View mSelectionContainer;

    private int mMaxPortraitTabHeight;
    private int mMaxToolbarContentInsetStart;
    private int mActionBarAnimationDuration;

    private final Activity mActivity;
    private final SharedPreferences mPrefs;

    private Listener mListener;

    private final ActionBar mActionBar;
    private final Toolbar mToolbar;
    /**
     *  Frame that contains the toolbar and draws the toolbar's background color. This is useful
     *  for placing things behind the toolbar.
     */
    private final FrameLayout mToolBarFrame;

    private boolean mShowHomeIcon;
    private boolean mShowHomeAsUp;

    private int mSearchHintResId;

    private FeatureHighlight mHamburgerFeatureHighlight;

    public interface TabState {
        public static int ALL = 0;

        public static int COUNT = 1;
        public static int DEFAULT = ALL;
    }

    private int mCurrentTab = TabState.DEFAULT;

    public ActionBarAdapter(Activity activity, Listener listener, ActionBar actionBar,
            View portraitTabs, View landscapeTabs, Toolbar toolbar) {
        this(activity, listener, actionBar, portraitTabs, landscapeTabs, toolbar,
                R.string.hint_findContacts);
    }

    public ActionBarAdapter(Activity activity, Listener listener, ActionBar actionBar,
            View portraitTabs, View landscapeTabs, Toolbar toolbar, int searchHintResId) {
        mActivity = activity;
        mListener = listener;
        mActionBar = actionBar;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mPortraitTabs = portraitTabs;
        mLandscapeTabs = landscapeTabs;
        mToolbar = toolbar;
        mToolBarFrame = (FrameLayout) mToolbar.getParent();
        mMaxToolbarContentInsetStart = mToolbar.getContentInsetStart();
        mSearchHintResId = searchHintResId;
        mActionBarAnimationDuration =
                mActivity.getResources().getInteger(R.integer.action_bar_animation_duration);

        setupSearchAndSelectionViews();
        setupTabs(mActivity);
    }

    public void setShowHomeIcon(boolean showHomeIcon) {
        mShowHomeIcon = showHomeIcon;
    }

    public void setShowHomeAsUp(boolean showHomeAsUp) {
        mShowHomeAsUp = showHomeAsUp;
    }

    public EditText getSearchView() {
        return mSearchView;
    }

    public View getSelectionContainer() {
        return mSelectionContainer;
    }

    private void setupTabs(Context context) {
        final TypedArray attributeArray = context.obtainStyledAttributes(
                new int[]{android.R.attr.actionBarSize});
        mMaxPortraitTabHeight = attributeArray.getDimensionPixelSize(0, 0);
        // Hide tabs initially
        setPortraitTabHeight(0);
    }

    private void setupSearchAndSelectionViews() {
        final LayoutInflater inflater = (LayoutInflater) mToolbar.getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        // Setup search bar
        mSearchContainer = inflater.inflate(R.layout.search_bar_expanded, mToolbar,
                /* attachToRoot = */ false);
        mSearchContainer.setVisibility(View.VISIBLE);
        mToolbar.addView(mSearchContainer);
        mSearchContainer.setBackgroundColor(mActivity.getResources().getColor(
                R.color.searchbox_background_color));
        mSearchView = (EditText) mSearchContainer.findViewById(R.id.search_view);
        mSearchView.setHint(mActivity.getString(mSearchHintResId));
        mSearchView.addTextChangedListener(new SearchTextWatcher());
        mSearchContainer.findViewById(R.id.search_back_button).setOnClickListener(
                new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onUpButtonPressed();
                }
            }
        });

        mClearSearchView = mSearchContainer.findViewById(R.id.search_close_button);
        mClearSearchView.setOnClickListener(
                new OnClickListener() {
            @Override
            public void onClick(View v) {
                setQueryString(null);
            }
        });

        // Setup selection bar
        mSelectionContainer = inflater.inflate(R.layout.selection_bar, mToolbar,
                /* attachToRoot = */ false);
        // Insert the selection container into mToolBarFrame behind the Toolbar, so that
        // the Toolbar's MenuItems can appear on top of the selection container.
        mToolBarFrame.addView(mSelectionContainer, 0);
        mSelectionContainer.findViewById(R.id.selection_close).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mListener != null) {
                            mListener.onUpButtonPressed();
                        }
                    }
                });
    }

    public void initialize(Bundle savedState, ContactsRequest request) {
        if (savedState == null) {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
            mCurrentTab = loadLastTabPreference();
            mSelectionMode = false;
        } else {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mSelectionMode = savedState.getBoolean(EXTRA_KEY_SELECTED_MODE);
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
        addHamburgerFeatureHighlight();
    }

    public void setListener(Listener listener) {
        mListener = listener;
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
            mClearSearchView.setVisibility(
                    TextUtils.isEmpty(queryString) ? View.GONE : View.VISIBLE);
        }

        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    }

    /**
     * Save the current tab selection, and notify the listener.
     */
    public void setCurrentTab(int tab) {
        setCurrentTab(tab, true);
    }

    /**
     * Save the current tab selection.
     */
    public void setCurrentTab(int tab, boolean notifyListener) {
        if (tab == mCurrentTab) {
            return;
        }
        mCurrentTab = tab;

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

    /**
     * @return Whether in selection mode, i.e. if the selection view is visible/expanded.
     */
    public boolean isSelectionMode() {
        return mSelectionMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            update(false /* skipAnimation */);
            if (mSearchView == null) {
                return;
            }
            if (mSearchMode) {
                mSearchView.setEnabled(true);
                setFocusOnSearchView();
            } else {
                // Disable search view, so that it doesn't keep the IME visible.
                mSearchView.setEnabled(false);
            }
            setQueryString(null);
        } else if (flag) {
            // Everything is already set up. Still make sure the keyboard is up
            if (mSearchView != null) setFocusOnSearchView();
        }
    }

    public void setSelectionMode(boolean flag) {
        if (mSelectionMode != flag) {
            mSelectionMode = flag;
            update(false /* skipAnimation */);
        }
    }

    public String getQueryString() {
        return mSearchMode ? mQueryString : null;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        if (mSearchView != null) {
            mSearchView.setText(query);
            // When programmatically entering text into the search view, the most reasonable
            // place for the cursor is after all the text.
            mSearchView.setSelection(mSearchView.getText() == null ?
                    0 : mSearchView.getText().length());
        }
    }

    /** @return true if the "UP" icon is showing. */
    public boolean isUpShowing() {
        return mSearchMode; // Only shown on the search mode.
    }

    private void updateDisplayOptionsInner() {
        // All the flags we may change in this method.
        final int MASK = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP;

        // The current flags set to the action bar.  (only the ones that we may change here)
        final int current = mActionBar.getDisplayOptions() & MASK;

        final boolean isSearchOrSelectionMode = mSearchMode || mSelectionMode;

        // Build the new flags...
        int newFlags = 0;
        if (mShowHomeIcon && !isSearchOrSelectionMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_HOME;
            if (mShowHomeAsUp) {
                newFlags |= ActionBar.DISPLAY_HOME_AS_UP;
            }
        }
        if (mSearchMode && !mSelectionMode) {
            // The search container is placed inside the toolbar. So we need to disable the
            // Toolbar's content inset in order to allow the search container to be the width of
            // the window.
            mToolbar.setContentInsetsRelative(0, mToolbar.getContentInsetEnd());
        }
        if (!isSearchOrSelectionMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_TITLE;
            mToolbar.setContentInsetsRelative(mMaxToolbarContentInsetStart,
                    mToolbar.getContentInsetEnd());
            mToolbar.setNavigationIcon(R.drawable.ic_menu_hamburger);
        } else {
            mToolbar.setNavigationIcon(null);
        }

        if (mSelectionMode) {
            // Minimize the horizontal width of the Toolbar since the selection container is placed
            // behind the toolbar and its left hand side needs to be clickable.
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mToolbar.getLayoutParams();
            params.width = LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.END;
            mToolbar.setLayoutParams(params);
        } else {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mToolbar.getLayoutParams();
            params.width = LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.END;
            mToolbar.setLayoutParams(params);
        }

        if (current != newFlags) {
            // Pass the mask here to preserve other flags that we're not interested here.
            mActionBar.setDisplayOptions(newFlags, MASK);
        }
    }

    private void addHamburgerFeatureHighlight() {
        if (mHamburgerFeatureHighlight == null) {
            mHamburgerFeatureHighlight = FeatureHighlight.Builder
                    .forView(new ToolbarNavigationIconFinder())
                    .setHeader(mActivity.getResources().getString(
                            R.string.hamburger_feature_highlight_header))
                    .setBody(mActivity.getResources().getString(
                            R.string.hamburger_feature_highlight_body))
                    .setTargetDrawable(
                            R.drawable.ic_menu_hamburger,
                            ContextCompat.getColor(mActivity, R.color.primary_color))
                    .build();
        }
    }

    public FeatureHighlight getHamburgerFeatureHighlight() {
        return mHamburgerFeatureHighlight;
    }

    private void update(boolean skipAnimation) {
        updateStatusBarColor();

        updateOverflowButtonColor();

        final boolean isSelectionModeChanging
                = (mSelectionContainer.getParent() == null) == mSelectionMode;
        final boolean isSwitchingFromSearchToSelection =
                mSearchMode && isSelectionModeChanging || mSearchMode && mSelectionMode;
        final boolean isSearchModeChanging
                = (mSearchContainer.getParent() == null) == mSearchMode;
        final boolean isTabHeightChanging = isSearchModeChanging || isSelectionModeChanging;

        // When skipAnimation=true, it is possible that we will switch from search mode
        // to selection mode directly. So we need to remove the undesired container in addition
        // to adding the desired container.
        if (skipAnimation || isSwitchingFromSearchToSelection) {
            if (isTabHeightChanging || isSwitchingFromSearchToSelection) {
                mToolbar.removeView(mLandscapeTabs);
                mToolbar.removeView(mSearchContainer);
                mToolBarFrame.removeView(mSelectionContainer);
                if (mSelectionMode) {
                    setPortraitTabHeight(0);
                    addSelectionContainer();
                } else if (mSearchMode) {
                    setPortraitTabHeight(0);
                    addSearchContainer();
                } else {
                    setPortraitTabHeight(mMaxPortraitTabHeight);
                    addLandscapeViewPagerTabs();
                }
                updateDisplayOptions(isSearchModeChanging);
            }
            return;
        }

        // Handle a switch to/from selection mode, due to UI interaction.
        if (isSelectionModeChanging) {
            mToolbar.removeView(mLandscapeTabs);
            if (mSelectionMode) {
                addSelectionContainer();
                mSelectionContainer.setAlpha(0);
                mSelectionContainer.animate().alpha(1).setDuration(mActionBarAnimationDuration);
                animateTabHeightChange(mMaxPortraitTabHeight, 0);
                updateDisplayOptions(isSearchModeChanging);
            } else {
                if (mListener != null) {
                    mListener.onAction(Action.BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE);
                }
                mSelectionContainer.setAlpha(1);
                animateTabHeightChange(0, mMaxPortraitTabHeight);
                mSelectionContainer.animate().alpha(0).setDuration(mActionBarAnimationDuration)
                        .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplayOptions(isSearchModeChanging);
                        addLandscapeViewPagerTabs();
                        mToolBarFrame.removeView(mSelectionContainer);
                    }
                });
            }
        }

        // Handle a switch to/from search mode, due to UI interaction.
        if (isSearchModeChanging) {
            mToolbar.removeView(mLandscapeTabs);
            if (mSearchMode) {
                addSearchContainer();
                mSearchContainer.setAlpha(0);
                mSearchContainer.animate().alpha(1).setDuration(mActionBarAnimationDuration);
                animateTabHeightChange(mMaxPortraitTabHeight, 0);
                updateDisplayOptions(isSearchModeChanging);
            } else {
                mSearchContainer.setAlpha(1);
                animateTabHeightChange(0, mMaxPortraitTabHeight);
                mSearchContainer.animate().alpha(0).setDuration(mActionBarAnimationDuration)
                        .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplayOptions(isSearchModeChanging);
                        addLandscapeViewPagerTabs();
                        mToolbar.removeView(mSearchContainer);
                    }
                });
            }
        }
    }

    /**
     * Find overflow menu ImageView by its content description and update its color.
     */
    private void updateOverflowButtonColor() {
        final String overflowDescription = mActivity.getResources().getString(
                R.string.abc_action_menu_overflow_description);
        final ViewGroup decorView = (ViewGroup) mActivity.getWindow().getDecorView();
        final ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Find the overflow ImageView.
                        final ArrayList<View> outViews = new ArrayList<>();
                        decorView.findViewsWithText(outViews, overflowDescription,
                                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                        if (outViews.isEmpty()) {
                            return;
                        }
                        final ImageView overflow = (ImageView) outViews.get(0);
                        overflow.setImageResource(R.drawable.ic_more_vert);

                        // Update the overflow image color.
                        final int iconColor;
                        if (mSelectionMode) {
                            iconColor = mActivity.getResources().getColor(
                                    R.color.actionbar_color_grey_solid);
                        } else {
                            iconColor = mActivity.getResources().getColor(
                                    R.color.actionbar_text_color);
                        }
                        overflow.setImageTintList(ColorStateList.valueOf(iconColor));

                        // We're done, remove the listener.
                        decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    public void setSelectionCount(int selectionCount) {
        TextView textView = (TextView) mSelectionContainer.findViewById(R.id.selection_count_text);
        if (selectionCount == 0) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
        }
        textView.setText(String.valueOf(selectionCount));
    }

    public void setActionBarTitle(String title) {
        final TextView textView =
                (TextView) mSelectionContainer.findViewById(R.id.selection_count_text);
        textView.setVisibility(View.VISIBLE);
        textView.setText(title);
    }

    private void updateStatusBarColor() {
        if (!CompatUtils.isLollipopCompatible()) {
            return; // we can't change the status bar color prior to Lollipop
        }
        if (mSelectionMode) {
            final int cabStatusBarColor = mActivity.getResources().getColor(
                    R.color.contextual_selection_bar_status_bar_color);
            mActivity.getWindow().setStatusBarColor(cabStatusBarColor);
        } else {
            final int normalStatusBarColor = ContextCompat.getColor(
                    mActivity, R.color.primary_color_dark);
            mActivity.getWindow().setStatusBarColor(normalStatusBarColor);
        }
    }

    private void addLandscapeViewPagerTabs() {
        if (mLandscapeTabs != null) {
            mToolbar.removeView(mLandscapeTabs);
            mToolbar.addView(mLandscapeTabs);
        }
    }

    private void addSearchContainer() {
        mToolbar.removeView(mSearchContainer);
        mToolbar.addView(mSearchContainer);
        mSearchContainer.setAlpha(1);
    }

    private void addSelectionContainer() {
        mToolBarFrame.removeView(mSelectionContainer);
        mToolBarFrame.addView(mSelectionContainer, 0);
        mSelectionContainer.setAlpha(1);
    }

    private void updateDisplayOptions(boolean isSearchModeChanging) {
        if (mSearchMode && !mSelectionMode) {
            setFocusOnSearchView();
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // expanding the {@link SearchView} when a search is initiated. Note that a side effect
            // of this method is that the {@link SearchView} query text is set to empty string.
            if (isSearchModeChanging) {
                final CharSequence queryText = mSearchView.getText();
                if (!TextUtils.isEmpty(queryText)) {
                    mSearchView.setText(queryText);
                }
            }
        }
        if (mListener != null) {
            if (mSearchMode) {
                mListener.onAction(Action.START_SEARCH_MODE);
            }
            if (mSelectionMode) {
                mListener.onAction(Action.START_SELECTION_MODE);
            }
            if (!mSearchMode && !mSelectionMode) {
                mListener.onAction(Action.STOP_SEARCH_AND_SELECTION_MODE);
                mListener.onSelectedTabChanged();
            }
        }
        updateDisplayOptionsInner();
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putBoolean(EXTRA_KEY_SELECTED_MODE, mSelectionMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mCurrentTab);
    }

    public void setFocusOnSearchView() {
        mSearchView.requestFocus();
        showInputMethod(mSearchView); // Workaround for the "IME not popping up" issue.
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(
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

    private void animateTabHeightChange(int start, int end) {
        if (mPortraitTabs == null) {
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int value = (Integer) valueAnimator.getAnimatedValue();
                setPortraitTabHeight(value);
            }
        });
        animator.setDuration(100).start();
    }

    private void setPortraitTabHeight(int height) {
        if (mPortraitTabs == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mPortraitTabs.getLayoutParams();
        layoutParams.height = height;
        mPortraitTabs.setLayoutParams(layoutParams);
    }
}
