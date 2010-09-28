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

import com.android.common.widget.CompositeCursorAdapter.Partition;
import com.android.contacts.ContactListEmptyView;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.IContentService;
import android.content.Intent;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ContactEntryListFragment<T extends ContactEntryListAdapter>
        extends Fragment
        implements OnItemClickListener, OnScrollListener, OnFocusChangeListener, OnTouchListener,
                LoaderCallbacks<Cursor> {

    // TODO: Make this protected. This should not be used from the ContactBrowserActivity but
    // instead use the new startActivityWithResultFromFragment API
    public static final int ACTIVITY_REQUEST_CODE_PICKER = 1;

    private static final String TAG = "ContactEntryListFragment";

    private static final String KEY_LIST_STATE = "liststate";
    private static final String KEY_SECTION_HEADER_DISPLAY_ENABLED = "sectionHeaderDisplayEnabled";
    private static final String KEY_PHOTO_LOADER_ENABLED = "photoLoaderEnabled";
    private static final String KEY_QUICK_CONTACT_ENABLED = "quickContactEnabled";
    private static final String KEY_SEARCH_MODE = "searchMode";
    private static final String KEY_AIZY_ENABLED = "aizyEnabled";
    private static final String KEY_QUERY_STRING = "queryString";
    private static final String KEY_DIRECTORY_SEARCH_ENABLED = "directorySearchEnabled";
    private static final String KEY_SELECTION_VISIBLE = "selectionVisible";
    private static final String KEY_REQUEST = "request";
    private static final String KEY_LEGACY_COMPATIBILITY = "legacyCompatibility";
    private static final String KEY_DIRECTORY_RESULT_LIMIT = "directoryResultLimit";

    private static final String DIRECTORY_ID_ARG_KEY = "directoryId";

    private static final int DIRECTORY_LOADER_ID = -1;

    private static final int DIRECTORY_SEARCH_DELAY_MILLIS = 300;
    private static final int DIRECTORY_SEARCH_MESSAGE = 1;

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mQuickContactEnabled = true;
    private boolean mSearchMode;
    private boolean mAizyEnabled;
    private String mQueryString;
    private boolean mDirectorySearchEnabled;
    private boolean mSelectionVisible;
    private ContactsRequest mRequest;
    private boolean mLegacyCompatibility;

    private T mAdapter;
    private View mView;
    private ListView mListView;
    private ContactListAizyView mAizy;

    /**
     * Used for keeping track of the scroll state of the list.
     */
    private Parcelable mListState;

    private int mDisplayOrder;
    private int mSortOrder;
    private int mDirectoryResultLimit;

    private ContextMenuAdapter mContextMenuAdapter;
    private ContactPhotoLoader mPhotoLoader;
    private ContactListEmptyView mEmptyView;
    private ProviderStatusLoader mProviderStatusLoader;
    private ContactsPreferences mContactsPrefs;

    private int mProviderStatus = ProviderStatus.STATUS_NORMAL;

    private boolean mForceLoad;
    private boolean mLoadDirectoryList;

    /**
     * Indicates whether we are doing the initial complete load of data (false) or
     * a refresh caused by a change notification (true)
     */
    private boolean mLoadPriorityDirectoriesOnly;

    private Context mContext;

    private LoaderManager mLoaderManager;

    private Handler mDelayedDirectorySearchHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == DIRECTORY_SEARCH_MESSAGE) {
                loadDirectoryPartition(msg.arg1, (DirectoryPartition) msg.obj);
            }
        }
    };

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract T createListAdapter();

    /**
     * @param position Please note that the position is already adjusted for
     *            header views, so "0" means the first list item below header
     *            views.
     */
    protected abstract void onItemClick(int position, long id);

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        setContext(activity);
        setLoaderManager(super.getLoaderManager());
    }

    /**
     * Sets a context for the fragment in the unit test environment.
     */
    public void setContext(Context context) {
        mContext = context;
        configurePhotoLoader();
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * Overrides a loader manager for use in unit tests.
     */
    public void setLoaderManager(LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    @Override
    public LoaderManager getLoaderManager() {
        return mLoaderManager;
    }

    public T getAdapter() {
        return mAdapter;
    }

    @Override
    public View getView() {
        return mView;
    }

    public ListView getListView() {
        return mListView;
    }

    public ContactListEmptyView getEmptyView() {
        return mEmptyView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED, mSectionHeaderDisplayEnabled);
        outState.putBoolean(KEY_PHOTO_LOADER_ENABLED, mPhotoLoaderEnabled);
        outState.putBoolean(KEY_QUICK_CONTACT_ENABLED, mQuickContactEnabled);
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode);
        outState.putBoolean(KEY_AIZY_ENABLED, mAizyEnabled);
        outState.putBoolean(KEY_DIRECTORY_SEARCH_ENABLED, mDirectorySearchEnabled);
        outState.putBoolean(KEY_SELECTION_VISIBLE, mSelectionVisible);
        outState.putBoolean(KEY_LEGACY_COMPATIBILITY, mLegacyCompatibility);
        outState.putString(KEY_QUERY_STRING, mQueryString);
        outState.putInt(KEY_DIRECTORY_RESULT_LIMIT, mDirectoryResultLimit);
        outState.putParcelable(KEY_REQUEST, mRequest);

        if (mListView != null) {
            mListState = mListView.onSaveInstanceState();
            outState.putParcelable(KEY_LIST_STATE, mListState);
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mContactsPrefs = new ContactsPreferences(mContext);
        restoreSavedState(savedState);
    }

    public void restoreSavedState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        mSectionHeaderDisplayEnabled = savedState.getBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED);
        mPhotoLoaderEnabled = savedState.getBoolean(KEY_PHOTO_LOADER_ENABLED);
        mQuickContactEnabled = savedState.getBoolean(KEY_QUICK_CONTACT_ENABLED);
        mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE);
        mAizyEnabled = savedState.getBoolean(KEY_AIZY_ENABLED);
        mDirectorySearchEnabled = savedState.getBoolean(KEY_DIRECTORY_SEARCH_ENABLED);
        mSelectionVisible = savedState.getBoolean(KEY_SELECTION_VISIBLE);
        mLegacyCompatibility = savedState.getBoolean(KEY_LEGACY_COMPATIBILITY);
        mQueryString = savedState.getString(KEY_QUERY_STRING);
        mDirectoryResultLimit = savedState.getInt(KEY_DIRECTORY_RESULT_LIMIT);
        mRequest = savedState.getParcelable(KEY_REQUEST);

        // Retrieve list state. This will be applied in onLoadFinished
        mListState = savedState.getParcelable(KEY_LIST_STATE);
    }

    /**
     * Returns the parsed intent that started the activity hosting this fragment.
     */
    public ContactsRequest getContactsRequest() {
        return mRequest;
    }

    /**
     * Sets a parsed intent that started the activity hosting this fragment.
     */
    public void setContactsRequest(ContactsRequest request) {
        mRequest = request;
    }

    @Override
    public void onStart() {
        mContactsPrefs.registerChangeListener(mPreferencesChangeListener);

        if (mProviderStatusLoader == null) {
            mProviderStatusLoader = new ProviderStatusLoader(mContext);
        }

        loadPreferences();

        if (mListView instanceof ContactEntryListView) {
            ContactEntryListView listView = (ContactEntryListView)mListView;
            listView.setSelectionVisible(isSelectionVisible());
        }

        mForceLoad = false;
        mLoadDirectoryList = true;
        mLoadPriorityDirectoriesOnly = true;

        startLoading();
        super.onStart();
    }

    protected void startLoading() {
        configureAdapter();
        int partitionCount = mAdapter.getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            Partition partition = mAdapter.getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (directoryPartition.getStatus() == DirectoryPartition.STATUS_NOT_LOADED) {
                    if (directoryPartition.isPriorityDirectory() || !mLoadPriorityDirectoriesOnly) {
                        startLoadingDirectoryPartition(i);
                    }
                }
            } else {
                getLoaderManager().initLoader(i, null, this);
            }
        }

        // Next time this method is called, we should start loading non-priority directories
        mLoadPriorityDirectoriesOnly = false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == DIRECTORY_LOADER_ID) {
            DirectoryListLoader loader = new DirectoryListLoader(mContext);
            mAdapter.configureDirectoryLoader(loader);
            return loader;
        } else {
            CursorLoader loader = new CursorLoader(mContext, null, null, null, null, null);
            long directoryId = args != null && args.containsKey(DIRECTORY_ID_ARG_KEY)
                    ? args.getLong(DIRECTORY_ID_ARG_KEY)
                    : Directory.DEFAULT;
            mAdapter.configureLoader(loader, directoryId);
            return loader;
        }
    }

    private void startLoadingDirectoryPartition(int partitionIndex) {
        DirectoryPartition partition = (DirectoryPartition)mAdapter.getPartition(partitionIndex);
        partition.setStatus(DirectoryPartition.STATUS_LOADING);
        long directoryId = partition.getDirectoryId();
        if (mForceLoad) {
            if (directoryId == Directory.DEFAULT) {
                loadDirectoryPartition(partitionIndex, partition);
            } else {
                loadDirectoryPartitionDelayed(partitionIndex, partition);
            }
        } else {
            Bundle args = new Bundle();
            args.putLong(DIRECTORY_ID_ARG_KEY, directoryId);
            getLoaderManager().initLoader(partitionIndex, args, this);
        }
    }

    /**
     * Queues up a delayed request to search the specified directory. Since
     * directory search will likely introduce a lot of network traffic, we want
     * to wait for a pause in the user's typing before sending a directory request.
     */
    private void loadDirectoryPartitionDelayed(int partitionIndex, DirectoryPartition partition) {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE, partition);
        Message msg = mDelayedDirectorySearchHandler.obtainMessage(
                DIRECTORY_SEARCH_MESSAGE, partitionIndex, 0, partition);
        mDelayedDirectorySearchHandler.sendMessageDelayed(msg, DIRECTORY_SEARCH_DELAY_MILLIS);
    }

    /**
     * Loads the directory partition.
     */
    protected void loadDirectoryPartition(int partitionIndex, DirectoryPartition partition) {
        Bundle args = new Bundle();
        args.putLong(DIRECTORY_ID_ARG_KEY, partition.getDirectoryId());
        getLoaderManager().restartLoader(partitionIndex, args, this);
    }

    /**
     * Cancels all queued directory loading requests.
     */
    private void removePendingDirectorySearchRequests() {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!checkProviderStatus(false)) {
            if (data != null) {
                data.close();
            }
            return;
        }

        int loaderId = loader.getId();
        if (loaderId == DIRECTORY_LOADER_ID) {
            mAdapter.changeDirectories(data);
            startLoading();
        } else {
            onPartitionLoaded(loaderId, data);
            if (isDirectorySearchEnabled()) {
                if (mLoadDirectoryList) {
                    mLoadDirectoryList = false;
                    getLoaderManager().initLoader(DIRECTORY_LOADER_ID, null, this);
                } else {
                    startLoading();
                }
            }

        }

// TODO fix the empty view
//            if (mEmptyView != null && (data == null || data.getCount() == 0)) {
//                prepareEmptyView();
//            }
    }

    protected void onPartitionLoaded(int partitionIndex, Cursor data) {
        mAdapter.changeCursor(partitionIndex, data);
        showCount(partitionIndex, data);
        if (partitionIndex == mAdapter.getIndexedPartition()) {
            mAizy.readFromIndexer(mAdapter.getIndexer());
        }

        // TODO should probably only restore instance state after all directories are loaded
        completeRestoreInstanceState();
    }

    @Override
    public void onStop() {
        super.onStop();
        mContactsPrefs.unregisterChangeListener();
        mAdapter.clearPartitions();
    }

    protected void reloadData() {
        removePendingDirectorySearchRequests();
        mAdapter.onDataReload();
        mLoadPriorityDirectoriesOnly = true;
        mForceLoad = true;
        startLoading();
    }

    /**
     * Configures the empty view. It is called when we are about to populate
     * the list with an empty cursor.
     */
    protected void prepareEmptyView() {
    }

    /**
     * Shows the count of entries included in the list. The default
     * implementation does nothing.
     */
    protected void showCount(int partitionIndex, Cursor data) {
    }

    /**
     * Provides logic that dismisses this fragment. The default implementation
     * does nothing.
     */
    protected void finish() {
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        mSectionHeaderDisplayEnabled = flag;
        if (mAdapter != null) {
            mAdapter.setSectionHeaderDisplayEnabled(flag);
        }
        configureAizy();
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setAizyEnabled(boolean flag) {
        mAizyEnabled = flag;
        configureAizy();
    }

    public boolean isAizyEnabled() {
        return mAizyEnabled;
    }

    private void configureAizy() {
        boolean hasAisy = isAizyEnabled() && isSectionHeaderDisplayEnabled();

        if (mListView != null) {
            mListView.setFastScrollEnabled(!hasAisy);
            mListView.setVerticalScrollBarEnabled(!hasAisy);
        }
        if (mAizy != null) {
            if (hasAisy) {
                mAizy.setVisibility(View.VISIBLE);
            } else if (isAizyEnabled()) {
                mAizy.setVisibility(View.INVISIBLE);
            } else {
                mAizy.setVisibility(View.GONE);
            }
        }
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
        configurePhotoLoader();
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }

    /**
     * Returns true if the list is supposed to visually highlight the selected item.
     */
    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
    }

    public void setQuickContactEnabled(boolean flag) {
        this.mQuickContactEnabled = flag;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            setSectionHeaderDisplayEnabled(!mSearchMode);

            if (mAdapter != null) {
                mAdapter.clearPartitions();
                mAdapter.setSearchMode(flag);
                mAdapter.setPinnedPartitionHeadersEnabled(flag);
                mAdapter.configureDefaultPartition(flag, flag);
                reloadData();
            }

            if (mListView != null) {
                mListView.setFastScrollEnabled(!flag);
            }
        }
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        if (!TextUtils.equals(mQueryString, queryString)) {
            mQueryString = queryString;
            if (mAdapter != null) {
                mAdapter.setQueryString(queryString);
                reloadData();
            }
        }
    }

    public boolean isDirectorySearchEnabled() {
        return mDirectorySearchEnabled;
    }

    public void setDirectorySearchEnabled(boolean flag) {
        mDirectorySearchEnabled = flag;
    }

    public boolean isLegacyCompatibilityMode() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibilityMode(boolean flag) {
        mLegacyCompatibility = flag;
    }

    protected int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    protected void setContactNameDisplayOrder(int displayOrder) {
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

    public void setDirectoryResultLimit(int limit) {
        mDirectoryResultLimit = limit;
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

    protected void loadPreferences() {
        setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
        setSortOrder(mContactsPrefs.getSortOrder());
        if (mListView instanceof ContactEntryListView) {
            ContactEntryListView listView = (ContactEntryListView)mListView;
            listView.setHighlightNamesWhenScrolling(isNameHighlighingEnabled());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        onCreateView(inflater, container);

        mAdapter = createListAdapter();

        boolean searchMode = isSearchMode();
        mAdapter.setSearchMode(searchMode);
        mAdapter.configureDefaultPartition(searchMode, searchMode);
        mAdapter.setPhotoLoader(mPhotoLoader);
        mListView.setAdapter(mAdapter);

        return mView;
    }

    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
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
            if (emptyView instanceof ContactListEmptyView) {
                mEmptyView = (ContactListEmptyView)emptyView;
            }
        }

        mListView.setOnItemClickListener(this);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mListView.setFastScrollEnabled(!isSearchMode());

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        mListView.setDividerHeight(0);

        // We manually save/restore the listview state
        mListView.setSaveEnabled(false);

        if (mContextMenuAdapter != null) {
            mListView.setOnCreateContextMenuListener(mContextMenuAdapter);
        }

        mAizy = (ContactListAizyView) mView.findViewById(R.id.contacts_list_aizy);
        mAizy.setListener(new ContactListAizyView.Listener() {
            @Override
            public void onScroll(int position) {
                mListView.smoothScrollToPositionFromTop(
                        position + mListView.getHeaderViewsCount(),
                        0);
            }
        });

        configureAizy();
        configurePhotoLoader();
    }

    protected void configurePhotoLoader() {
        if (isPhotoLoaderEnabled() && mContext != null) {
            if (mPhotoLoader == null) {
                mPhotoLoader = new ContactPhotoLoader(mContext, R.drawable.ic_contact_list_picture);
            }
            if (mListView != null) {
                mListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoLoader);
            }
        }
    }

    protected void configureAdapter() {
        if (mAdapter == null) {
            return;
        }

        mAdapter.setQuickContactEnabled(mQuickContactEnabled);
        mAdapter.setQueryString(mQueryString);
        mAdapter.setDirectorySearchEnabled(mDirectorySearchEnabled);
        mAdapter.setPinnedPartitionHeadersEnabled(mSearchMode);
        mAdapter.setContactNameDisplayOrder(mDisplayOrder);
        mAdapter.setSortOrder(mSortOrder);
        mAdapter.setNameHighlightingEnabled(isNameHighlighingEnabled());
        mAdapter.setSectionHeaderDisplayEnabled(mSectionHeaderDisplayEnabled);
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setDirectoryResultLimit(mDirectoryResultLimit);
    }

    protected boolean isNameHighlighingEnabled() {
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

    @Override
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

        registerProviderStatusObserver();

        if (isPhotoLoaderEnabled()) {
            mPhotoLoader.resume();
        }
    }

    @Override
    public void onDestroy() {
        if (isPhotoLoaderEnabled()) {
            mPhotoLoader.stop();
        }
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
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
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

    @Override
    public void onPause() {
        super.onPause();
        removePendingDirectorySearchRequests();
        unregisterProviderStatusObserver();
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    public void onClose() {
        hideSoftKeyboard();
        finish();
    }

    /**
     * Restore the list state after the adapter is populated.
     */
    protected void completeRestoreInstanceState() {
        if (mListState != null) {
            mListView.onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

    private ContentObserver mProviderStatusObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            checkProviderStatus(true);
        }
    };

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void registerProviderStatusObserver() {
        mContext.getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI,
                false, mProviderStatusObserver);
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void unregisterProviderStatusObserver() {
        mContext.getContentResolver().unregisterContentObserver(mProviderStatusObserver);
    }

    /**
     * Obtains the contacts provider status and configures the UI accordingly.
     *
     * @param loadData true if the method needs to start a query when the
     *            provider is in the normal state
     * @return true if the provider status is normal
     */
    private boolean checkProviderStatus(boolean loadData) {
        View importFailureView = findViewById(R.id.import_failure);
        if (importFailureView == null) {
            return true;
        }

        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = mContext.getContentResolver().query(ProviderStatus.CONTENT_URI,
                new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(0);
                    if (status != mProviderStatus) {
                        mProviderStatus = status;
                        switch (status) {
                            case ProviderStatus.STATUS_NORMAL:
                                mAdapter.notifyDataSetInvalidated();
                                if (loadData) {
                                    reloadData();
                                }
                                break;

                            case ProviderStatus.STATUS_CHANGING_LOCALE:
                                setEmptyText(R.string.locale_change_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADING:
                                setEmptyText(R.string.upgrade_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                                long size = cursor.getLong(1);
                                String message = mContext.getResources().getString(
                                        R.string.upgrade_out_of_memory, new Object[] {size});
                                TextView messageView = (TextView) findViewById(R.id.emptyText);
                                messageView.setText(message);
                                messageView.setVisibility(View.VISIBLE);
                                configureImportFailureView(importFailureView);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        importFailureView.setVisibility(
                mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
                        ? View.VISIBLE
                        : View.GONE);
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    private void configureImportFailureView(View importFailureView) {

        OnClickListener listener = new OnClickListener(){

            public void onClick(View v) {
                switch(v.getId()) {
                    case R.id.import_failure_uninstall_apps: {
                        // TODO break into a separate method
                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                        break;
                    }
                    case R.id.import_failure_retry_upgrade: {
                        // Send a provider status update, which will trigger a retry
                        ContentValues values = new ContentValues();
                        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
                        mContext.getContentResolver().update(ProviderStatus.CONTENT_URI,
                                values, null, null);
                        break;
                    }
                }
            }};

        Button uninstallApps = (Button) findViewById(R.id.import_failure_uninstall_apps);
        uninstallApps.setOnClickListener(listener);

        Button retryUpgrade = (Button) findViewById(R.id.import_failure_retry_upgrade);
        retryUpgrade.setOnClickListener(listener);
    }

    private View findViewById(int id) {
        return mView.findViewById(id);
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return mContext.getString(zeroResourceId);
        } else {
            String format = mContext.getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    protected void setEmptyText(int resourceId) {
        TextView empty = (TextView) getEmptyView().findViewById(R.id.emptyText);
        empty.setText(mContext.getText(resourceId));
        empty.setVisibility(View.VISIBLE);
    }

    // TODO redesign into an async task or loader
    protected boolean isSyncActive() {
        Account[] accounts = AccountManager.get(mContext).getAccounts();
        if (accounts != null && accounts.length > 0) {
            IContentService contentService = ContentResolver.getContentService();
            for (Account account : accounts) {
                try {
                    if (contentService.isSyncActive(account, ContactsContract.AUTHORITY)) {
                        return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not get the sync status");
                }
            }
        }
        return false;
    }

    protected boolean hasIccCard() {
        TelephonyManager telephonyManager =
                (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.hasIccCard();
    }

    /**
     * Processes a user request to start search. This may be triggered by the
     * search key, a menu item or some other user action.
     */
    public void startSearch(String initialQuery) {
        ContactsSearchManager.startSearch(getActivity(), initialQuery, mRequest);
    }

    /**
     * Processes a result returned by the contact picker.
     */
    public void onPickerResult(Intent data) {
        throw new UnsupportedOperationException("Picker result handler is not implemented.");
    }

    // TODO integrate into picker fragments
//    protected Uri buildCallingPackageUri(Uri uri) {
//        String callingPackage = getContext().getCallingPackage();
//        if (!TextUtils.isEmpty(callingPackage)) {
//            uri = uri.buildUpon().appendQueryParameter(
//                    ContactsContract.REQUESTING_PACKAGE_PARAM_KEY, callingPackage).build();
//        }
//    }
    private ContactsPreferences.ChangeListener mPreferencesChangeListener =
            new ContactsPreferences.ChangeListener() {
        @Override
        public void onChange() {
            loadPreferences();
            reloadData();
        }
    };
}
