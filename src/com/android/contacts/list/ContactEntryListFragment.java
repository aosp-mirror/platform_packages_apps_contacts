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

package com.android.contacts.list;

import com.android.contacts.ContactEntryListView;
import com.android.contacts.ContactPhotoLoader;
import com.android.contacts.ContactsApplicationController;
import com.android.contacts.ContactsListActivity;
import com.android.contacts.R;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnCloseListener;

import android.app.patterns.CursorLoader;
import android.app.patterns.Loader;
import android.app.patterns.LoaderManagingFragment;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView.OnEditorActionListener;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ContactEntryListFragment<T extends ContactEntryListAdapter>
        extends LoaderManagingFragment<Cursor>
        implements OnItemClickListener,
        OnScrollListener, TextWatcher, OnEditorActionListener, OnCloseListener,
        OnFocusChangeListener, OnTouchListener {

    private static final String LIST_STATE_KEY = "liststate";

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private String mQueryString;

    private ContactsApplicationController mAppController;
    private CursorLoader mLoader;
    private T mAdapter;
    private View mView;
    private ListView mListView;

    /**
     * Used for keeping track of the scroll state of the list.
     */
    private Parcelable mListState;

    private boolean mLegacyCompatibility;
    private int mDisplayOrder;
    private int mSortOrder;

    private ContextMenuAdapter mContextMenuAdapter;
    private ContactPhotoLoader mPhotoLoader;
    private SearchEditText mSearchEditText;

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract T createListAdapter();

    /**
     * @param position Please note that the position is already adjusted for
     *            header views, so "0" means the first list item below header
     *            views.
     */
    protected abstract void onItemClick(int position, long id);

    public CursorLoader getLoader() {
        return mLoader;
    }

    public T getAdapter() {
        return mAdapter;
    }

    public void setListAdapter(T adapter) {
        mAdapter = adapter;
        mListView.setAdapter(mAdapter);
        if (isPhotoLoaderEnabled()) {
            mAdapter.setPhotoLoader(mPhotoLoader);
        }
        if (isNameHighlighingEnabled()) {
            mAdapter.setNameHighlightingEnabled(true);
        }

        ((ContactsListActivity)getActivity()).setupListView(mAdapter, mListView);
    }

    public View getView() {
        return mView;
    }

    public ListView getListView() {
        return mListView;
    }

    @Override
    protected Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), null, null, null, null, null);
    }

    @Override
    protected void onInitializeLoaders() {
        mLoader = (CursorLoader)startLoading(0, null);
    }

    @Override
    protected void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }

        mAdapter.changeCursor(data);
        showCount(data);
    }

    protected void reloadData() {
        mAdapter.configureLoader(mLoader);
        mLoader.forceLoad();
    }

    /**
     * Shows the count of entries included in the list. The default
     * implementation does nothing.
     */
    protected void showCount(Cursor data) {
    }

    /**
     * Provides logic that dismisses this fragment. The default implementation
     * does nothing.
     */
    protected void finish() {
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        mSectionHeaderDisplayEnabled = flag;
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchResultsMode(boolean flag) {
        mSearchResultsMode = flag;
    }

    public boolean isSearchResultsMode() {
        return mSearchResultsMode;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
        if (mAdapter != null) {
            mAdapter.setQueryString(queryString);
        }
    }

    public boolean isLegacyCompatibility() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibility(boolean flag) {
        mLegacyCompatibility = flag;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        if (mAdapter != null) {
            mAdapter.setContactNameDisplayOrder(displayOrder);
        }
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        if (mAdapter != null) {
            mAdapter.setSortOrder(sortOrder);
        }
    }

    @Deprecated
    public void setContactsApplicationController(ContactsApplicationController controller) {
        mAppController = controller;
    }

    @Deprecated
    public ContactsApplicationController getContactsApplicationController() {
        return mAppController;
    }

    public void setContextMenuAdapter(ContextMenuAdapter adapter) {
        mContextMenuAdapter = adapter;
        if (mListView != null) {
            mListView.setOnCreateContextMenuListener(adapter);
        }
    }

    public ContextMenuAdapter getContextMenuAdapter() {
        return mContextMenuAdapter;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        mView = createView(inflater, container);
        mAdapter = createListAdapter();
        mAdapter.configureLoader(mLoader);
        setListAdapter(mAdapter);
        return mView;
    }

    protected View createView(LayoutInflater inflater, ViewGroup container) {
        mView = inflateView(inflater, container);

        mListView = (ListView)mView.findViewById(android.R.id.list);
        if (mListView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }

        View emptyView = mView.findViewById(com.android.internal.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }

        mListView.setOnItemClickListener(this);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        mListView.setDividerHeight(0);

        // We manually save/restore the listview state
        mListView.setSaveEnabled(false);

        if (mContextMenuAdapter != null) {
            mListView.setOnCreateContextMenuListener(mContextMenuAdapter);
        }

        ContactEntryListView listView = (ContactEntryListView)mListView;
        listView.setHighlightNamesWhenScrolling(isNameHighlighingEnabled());

        if (isPhotoLoaderEnabled()) {
            mPhotoLoader =
                new ContactPhotoLoader(getActivity(), R.drawable.ic_contact_list_picture);
            mListView.setOnScrollListener(this);
        }

        if (isSearchMode()) {
            mSearchEditText = (SearchEditText)mView.findViewById(R.id.search_src_text);
            mSearchEditText.setText(getQueryString());
            mSearchEditText.addTextChangedListener(this);
            mSearchEditText.setOnEditorActionListener(this);
            mSearchEditText.setOnCloseListener(this);
        }

        if (isSearchResultsMode()) {
            TextView titleText = (TextView)mView.findViewById(R.id.search_results_for);
            if (titleText != null) {
                titleText.setText(Html.fromHtml(getActivity().getString(R.string.search_results_for,
                        "<b>" + getQueryString() + "</b>")));
            }
        }
        return mView;
    }

    private boolean isNameHighlighingEnabled() {
        // When sort order and display order contradict each other, we want to
        // highlight the part of the name used for sorting.
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            return true;
        } else if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return true;
        } else {
            return false;
        }
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoLoader.pause();
        } else if (isPhotoLoaderEnabled()) {
            mPhotoLoader.resume();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPhotoLoaderEnabled()) {
            mPhotoLoader.resume();
        }
        if (isSearchMode()) {
            mSearchEditText.requestFocus();
        }
    }

    @Override
    public void onDestroy() {
        mPhotoLoader.stop();
        super.onDestroy();
    }


    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideSoftKeyboard();

        int adjPosition = position - mListView.getHeaderViewsCount();
        if (adjPosition >= 0) {
            onItemClick(adjPosition, id);
        }
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }

    /**
     * Event handler for search UI.
     */
    public void afterTextChanged(Editable s) {
        String query = s.toString().trim();
        setQueryString(query);
        reloadData();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /**
     * Event handler for search UI.
     */
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            if (TextUtils.isEmpty(getQueryString())) {
                finish();
            }
            return true;
        }
        return false;
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            hideSoftKeyboard();
        }
        return false;
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    public void onClose() {
        hideSoftKeyboard();
        finish();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the QueryHandler has run
        if (mListView != null) {
            icicle.putParcelable(LIST_STATE_KEY, mListView.onSaveInstanceState());
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mListState = icicle.getParcelable(LIST_STATE_KEY);
    }

    /**
     * Restore the list state after the adapter is populated.
     */
    public void completeRestoreInstanceState() {
        if (mListState != null) {
            mListView.onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getActivity().getString(zeroResourceId);
        } else {
            String format = getActivity().getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }
}
