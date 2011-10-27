/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactTileLoaderFactory;
import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.AccountFilterUtil;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Directory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment for Phone UI's favorite screen.
 *
 * This fragment contains three kinds of contacts in one screen: "starred", "frequent", and "all"
 * contacts. To show them at once, this merges results from {@link ContactTileAdapter} and
 * {@link PhoneNumberListAdapter} into one unified list using {@link PhoneFavoriteMergedAdapter}.
 * A contact filter header is also inserted between those adapters' results.
 */
public class PhoneFavoriteFragment extends Fragment implements OnItemClickListener {
    private static final String TAG = PhoneFavoriteFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Used with LoaderManager.
     */
    private static int LOADER_ID_CONTACT_TILE = 1;
    private static int LOADER_ID_ALL_CONTACTS = 2;

    private static final String KEY_FILTER = "filter";

    private static final int REQUEST_CODE_ACCOUNT_FILTER = 1;

    public interface Listener {
        public void onContactSelected(Uri contactUri);
    }

    private class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onCreateLoader.");
            return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoadFinished");
            mContactTileAdapter.setContactCursor(data);

            if (mAllContactsForceReload) {
                mAllContactsAdapter.onDataReload();
                // Use restartLoader() to make LoaderManager to load the section again.
                getLoaderManager().restartLoader(
                        LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
            } else if (!mAllContactsLoaderStarted) {
                // Load "all" contacts if not loaded yet.
                getLoaderManager().initLoader(
                        LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
            }
            mAllContactsForceReload = false;
            mAllContactsLoaderStarted = true;

            // Show the filter header with "loading" state.
            updateFilterHeaderView();
            mAccountFilterHeader.setVisibility(View.VISIBLE);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
        }
    }

    private class AllContactsLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onCreateLoader");
            CursorLoader loader = new CursorLoader(getActivity(), null, null, null, null, null);
            mAllContactsAdapter.configureLoader(loader, Directory.DEFAULT);
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onLoadFinished");
            mAllContactsAdapter.changeCursor(0, data);
            updateFilterHeaderView();
            mAccountFilterHeaderContainer.setVisibility(View.VISIBLE);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onLoaderReset. ");
        }
    }

    private class ContactTileAdapterListener implements ContactTileAdapter.Listener {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mListener != null) {
                mListener.onContactSelected(contactUri);
            }
        }
    }

    private class FilterHeaderClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            AccountFilterUtil.startAccountFilterActivityForResult(
                    PhoneFavoriteFragment.this, REQUEST_CODE_ACCOUNT_FILTER);
        }
    }

    private class ContactsPreferenceChangeListener
            implements ContactsPreferences.ChangeListener {
        @Override
        public void onChange() {
            if (loadContactsPreferences()) {
                requestReloadAllContacts();
            }
        }
    }

    private class ScrollListener implements ListView.OnScrollListener {
        private boolean mShouldShowFastScroller;
        @Override
        public void onScroll(AbsListView view,
                int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // FastScroller should be visible only when the user is seeing "all" contacts section.
            final boolean shouldShow = mAdapter.shouldShowFirstScroller(firstVisibleItem);
            if (shouldShow != mShouldShowFastScroller) {
                mListView.setVerticalScrollBarEnabled(shouldShow);
                mListView.setFastScrollEnabled(shouldShow);
                mListView.setFastScrollAlwaysVisible(shouldShow);
                mShouldShowFastScroller = shouldShow;
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
    }

    private Listener mListener;
    private PhoneFavoriteMergedAdapter mAdapter;
    private ContactTileAdapter mContactTileAdapter;
    private PhoneNumberListAdapter mAllContactsAdapter;

    /**
     * true when the loader for {@link PhoneNumberListAdapter} has started already.
     */
    private boolean mAllContactsLoaderStarted;
    /**
     * true when the loader for {@link PhoneNumberListAdapter} must reload "all" contacts again.
     * It typically happens when {@link ContactsPreferences} has changed its settings
     * (display order and sort order)
     */
    private boolean mAllContactsForceReload;

    private ContactsPreferences mContactsPrefs;
    private ContactListFilter mFilter;

    private TextView mEmptyView;
    private ListView mListView;
    /**
     * Layout containing {@link #mAccountFilterHeader}. Used to limit area being "pressed".
     */
    private FrameLayout mAccountFilterHeaderContainer;
    private View mAccountFilterHeader;

    private final ContactTileAdapter.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mAllContactsLoaderListener =
            new AllContactsLoaderListener();
    private final OnClickListener mFilterHeaderClickListener = new FilterHeaderClickListener();
    private final ContactsPreferenceChangeListener mContactsPreferenceChangeListener =
            new ContactsPreferenceChangeListener();
    private final ScrollListener mScrollListener = new ScrollListener();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState != null) {
            mFilter = savedState.getParcelable(KEY_FILTER);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mContactsPrefs = new ContactsPreferences(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View listLayout = inflater.inflate(R.layout.contact_tile_list, container, false);

        mListView = (ListView) listLayout.findViewById(R.id.contact_tile_list);
        mListView.setItemsCanFocus(true);
        mListView.setOnItemClickListener(this);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

        initAdapters(getActivity(), inflater);

        mListView.setAdapter(mAdapter);

        mListView.setOnScrollListener(mScrollListener);
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        mEmptyView = (TextView) listLayout.findViewById(R.id.contact_tile_list_empty);
        mEmptyView.setText(getString(R.string.listTotalAllContactsZero));
        mListView.setEmptyView(mEmptyView);

        updateFilterHeaderView();

        return listLayout;
    }

    /**
     * Constructs and initializes {@link #mContactTileAdapter}, {@link #mAllContactsAdapter}, and
     * {@link #mAllContactsAdapter}.
     *
     * TODO: Move all the code here to {@link PhoneFavoriteMergedAdapter} if possible.
     * There are two problems: account header (whose content changes depending on filter settings)
     * and OnClickListener (which initiates {@link Activity#startActivityForResult(Intent, int)}).
     * See also issue 5429203, 5269692, and 5432286. If we are able to have a singleton for filter,
     * this work will become easier.
     */
    private void initAdapters(Context context, LayoutInflater inflater) {
        mContactTileAdapter = new ContactTileAdapter(context, mContactTileAdapterListener,
                getResources().getInteger(R.integer.contact_tile_column_count),
                ContactTileAdapter.DisplayType.STREQUENT_PHONE_ONLY);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(context));

        // Setup the "all" adapter manually. See also the setup logic in ContactEntryListFragment.
        mAllContactsAdapter = new PhoneNumberListAdapter(context);
        mAllContactsAdapter.setDisplayPhotos(true);
        mAllContactsAdapter.setQuickContactEnabled(true);
        mAllContactsAdapter.setSearchMode(false);
        mAllContactsAdapter.setIncludeProfile(false);
        mAllContactsAdapter.setSelectionVisible(false);
        mAllContactsAdapter.setDarkTheme(true);
        mAllContactsAdapter.setPhotoLoader(ContactPhotoManager.getInstance(context));
        // Disable directory header.
        mAllContactsAdapter.setHasHeader(0, false);
        // Show A-Z section index.
        mAllContactsAdapter.setSectionHeaderDisplayEnabled(true);
        // Disable pinned header. It doesn't work with this fragment.
        mAllContactsAdapter.setPinnedPartitionHeadersEnabled(false);
        // Put photos on left for consistency with "frequent" contacts section.
        mAllContactsAdapter.setPhotoPosition(ContactListItemView.PhotoPosition.LEFT);

        if (mFilter != null) {
            mAllContactsAdapter.setFilter(mFilter);
        }

        // Create the account filter header but keep it hidden until "all" contacts are loaded.
        mAccountFilterHeaderContainer = new FrameLayout(context, null);
        mAccountFilterHeader = inflater.inflate(R.layout.account_filter_header_for_phone_favorite,
                mListView, false);
        mAccountFilterHeader.setOnClickListener(mFilterHeaderClickListener);
        mAccountFilterHeaderContainer.addView(mAccountFilterHeader);
        mAccountFilterHeaderContainer.setVisibility(View.GONE);

        mAdapter = new PhoneFavoriteMergedAdapter(context,
                mContactTileAdapter, mAccountFilterHeaderContainer, mAllContactsAdapter);

    }

    @Override
    public void onStart() {
        super.onStart();

        mContactsPrefs.registerChangeListener(mContactsPreferenceChangeListener);

        // If ContactsPreferences has changed, we need to reload "all" contacts with the new
        // settings. If mAllContactsFoarceReload is already true, it should be kept.
        if (loadContactsPreferences()) {
            mAllContactsForceReload = true;
        }

        // Use initLoader() instead of reloadLoader() to refraing unnecessary reload.
        // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
        // be called, on which we'll check if "all" contacts should be reloaded again or not.
        getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        mContactsPrefs.unregisterChangeListener();
    }

    /**
     * {@inheritDoc}
     *
     * This is only effective for elements provided by {@link #mContactTileAdapter}.
     * {@link #mContactTileAdapter} has its own logic for click events.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        if (position <= contactTileAdapterCount) {
            Log.e(TAG, "onItemClick() event for unexpected position. "
                    + "The position " + position + " is before \"all\" section. Ignored.");
        } else {
            final int localPosition = position - mContactTileAdapter.getCount() - 1;
            if (mListener != null) {
                mListener.onContactSelected(mAllContactsAdapter.getDataUri(localPosition));
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ACCOUNT_FILTER) {
            if (getActivity() != null) {
                AccountFilterUtil.handleAccountFilterResult(
                        ContactListFilterController.getInstance(getActivity()), resultCode, data);
            } else {
                Log.e(TAG, "getActivity() returns null during Fragment#onActivityResult()");
            }
        }
    }

    private boolean loadContactsPreferences() {
        if (mContactsPrefs == null || mAllContactsAdapter == null) {
            return false;
        }

        boolean changed = false;
        if (mAllContactsAdapter.getContactNameDisplayOrder() != mContactsPrefs.getDisplayOrder()) {
            mAllContactsAdapter.setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
            changed = true;
        }

        if (mAllContactsAdapter.getSortOrder() != mContactsPrefs.getSortOrder()) {
            mAllContactsAdapter.setSortOrder(mContactsPrefs.getSortOrder());
            changed = true;
        }

        return changed;
    }

    /**
     * Requests to reload "all" contacts. If the section is already loaded, this method will
     * force reloading it now. If the section isn't loaded yet, the actual load may be done later
     * (on {@link #onStart()}.
     */
    private void requestReloadAllContacts() {
        if (DEBUG) {
            Log.d(TAG, "requestReloadAllContacts()"
                    + " mAllContactsAdapter: " + mAllContactsAdapter
                    + ", mAllContactsLoaderStarted: " + mAllContactsLoaderStarted);
        }

        if (mAllContactsAdapter == null || !mAllContactsLoaderStarted) {
            // Remember this request until next load on onStart().
            mAllContactsForceReload = true;
            return;
        }

        if (DEBUG) Log.d(TAG, "Reload \"all\" contacts now.");

        mAllContactsAdapter.onDataReload();
        // Use restartLoader() to make LoaderManager to load the section again.
        getLoaderManager().restartLoader(LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
    }

    private void updateFilterHeaderView() {
        final ContactListFilter filter = getFilter();
        if (mAccountFilterHeader == null || mAllContactsAdapter == null || filter == null) {
            return;
        }
        AccountFilterUtil.updateAccountFilterTitleForPhone(
                mAccountFilterHeader, filter, mAllContactsAdapter.isLoading(), true);
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        if ((mFilter == null && filter == null) || (mFilter != null && mFilter.equals(filter))) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "setFilter(). old filter (" + mFilter
                    + ") will be replaced with new filter (" + filter + ")");
        }

        mFilter = filter;

        if (mAllContactsAdapter != null) {
            mAllContactsAdapter.setFilter(mFilter);
            requestReloadAllContacts();
            updateFilterHeaderView();
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
}