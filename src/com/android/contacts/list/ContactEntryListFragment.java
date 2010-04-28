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

import com.android.contacts.ContactPhotoLoader;
import com.android.contacts.ContactsApplicationController;
import com.android.contacts.ContactsListActivity;
import com.android.contacts.R;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.PinnedHeaderListView;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnCloseListener;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
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
import android.widget.Filter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ContactEntryListFragment extends Fragment implements OnItemClickListener,
        OnScrollListener, TextWatcher, OnEditorActionListener, OnCloseListener,
        OnFocusChangeListener, OnTouchListener {

    private static final String LIST_STATE_KEY = "liststate";

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private String mQueryString;

    private ContactsApplicationController mAppController;
    private ContactEntryListAdapter mAdapter;
    private ListView mListView;

    /**
     * Used for keeping track of the scroll state of the list.
     */
    private Parcelable mListState;

    private boolean mLegacyCompatibility;
    private int mDisplayOrder;
    private ContextMenuAdapter mContextMenuAdapter;
    private ContactPhotoLoader mPhotoLoader;
    private SearchEditText mSearchEditText;

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract ContactEntryListAdapter createListAdapter();
    protected abstract void onItemClick(int position, long id);

    public ContactEntryListAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Override to provide logic that dismisses this fragment.
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
    }

    public boolean isLegacyCompatibility() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibility(boolean flag) {
        mLegacyCompatibility = flag;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        if (mAdapter != null) {
            mAdapter.setContactNameDisplayOrder(displayOrder);
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
        View view = inflateView(inflater, container);
        mAdapter = createListAdapter();
        configureView(view);
        return view;
    }

    protected void configureView(View view) {
        mListView = (ListView)view.findViewById(android.R.id.list);
        if (mListView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }

        View emptyView = view.findViewById(com.android.internal.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }

        mListView.setAdapter(mAdapter);
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

        mAdapter.setContactNameDisplayOrder(mDisplayOrder);

        configurePinnedHeader();

        if (isPhotoLoaderEnabled()) {
            mPhotoLoader =
                new ContactPhotoLoader(getActivity(), R.drawable.ic_contact_list_picture);
            mAdapter.setPhotoLoader(mPhotoLoader);
            mListView.setOnScrollListener(this);
        }

        if (isSearchMode()) {
            mSearchEditText = (SearchEditText)view.findViewById(R.id.search_src_text);
            mSearchEditText.setText(getQueryString());
            mSearchEditText.addTextChangedListener(this);
            mSearchEditText.setOnEditorActionListener(this);
            mSearchEditText.setOnCloseListener(this);
            mAdapter.setQueryString(getQueryString());
        }

        if (isSearchResultsMode()) {
            TextView titleText = (TextView)view.findViewById(R.id.search_results_for);
            if (titleText != null) {
                titleText.setText(Html.fromHtml(getActivity().getString(R.string.search_results_for,
                        "<b>" + getQueryString() + "</b>")));
            }
        }

        ((ContactsListActivity)getActivity()).setupListView(mAdapter, mListView);
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

    private void configurePinnedHeader() {
        if (!mSectionHeaderDisplayEnabled) {
            return;
        }

        if (mListView instanceof PinnedHeaderListView) {
            PinnedHeaderListView pinnedHeaderList = (PinnedHeaderListView)mListView;
            View headerView = mAdapter.createPinnedHeaderView(pinnedHeaderList);
            pinnedHeaderList.setPinnedHeaderView(headerView);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideSoftKeyboard();

        onItemClick(position, id);
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
        mAdapter.setQueryString(query);
        Filter filter = mAdapter.getFilter();
        filter.filter(query);
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
}
