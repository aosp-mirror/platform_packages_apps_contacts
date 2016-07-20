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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.Experiments;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.FavoritesAndContactsLoader;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.commonbind.experiments.Flags;
import com.android.contacts.util.SyncUtil;

import java.util.List;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
    private View mSearchHeaderView;
    private View mSearchProgress;
    private TextView mSearchProgressText;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        // Don't use a QuickContactBadge. Just use a regular ImageView. Using a QuickContactBadge
        // inside the ListView prevents us from using MODE_FULLY_EXPANDED and messes up ripples.
        setQuickContactEnabled(false);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
        setDisplayDirectoryHeader(false);
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new FavoritesAndContactsLoader(context);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        bindListHeader(data.getCount());
        super.onLoadFinished(loader, data);
    }

    private void bindListHeader(int numberOfContacts) {
        final View accountFilterContainer = getView().findViewById(
                R.id.account_filter_header_container);
        final ContactListFilterController contactListFilterController =
                ContactListFilterController.getInstance(getContext());
        final ContactListFilter filter = contactListFilterController.getFilter();
        if (isSearchMode()) {
            hideHeaderAndAddPadding(getContext(), getListView(), accountFilterContainer);
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            bindListHeaderCustom(getListView(), accountFilterContainer);
        } else if (filter.filterType != ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    filter.accountName, filter.accountType, filter.dataSet);
            bindListHeader(getContext(), getListView(), accountFilterContainer,
                    accountWithDataSet, numberOfContacts);
        } else {
            hideHeaderAndAddPadding(getContext(), getListView(), accountFilterContainer);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        }
        viewContact(position, uri, getAdapter().isEnterpriseContact(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        if (Flags.getInstance(getActivity()).getBoolean(Experiments.PULL_TO_REFRESH)) {
            initSwipeRefreshLayout();

        }
        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mSearchHeaderView = inflater.inflate(R.layout.search_header, null, false);
        headerContainer.addView(mSearchHeaderView);
        getListView().addHeaderView(headerContainer, null, false);
        checkHeaderViewVisibility();

        mSearchProgress = getView().findViewById(R.id.search_progress);
        mSearchProgressText = (TextView) mSearchHeaderView.findViewById(R.id.totalContactsText);
    }

    private void initSwipeRefreshLayout() {
        mSwipeRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipe_refresh);
        if (mSwipeRefreshLayout == null) {
            return;
        }

        mSwipeRefreshLayout.setEnabled(true);
        // Request sync contacts
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                syncContacts(mFilter);
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.swipe_refresh_color1,
                R.color.swipe_refresh_color2,
                R.color.swipe_refresh_color3,
                R.color.swipe_refresh_color4);
        mSwipeRefreshLayout.setDistanceToTriggerSync(
                (int) getResources().getDimension(R.dimen.pull_to_refresh_distance));
    }

    /** Request sync for Google accounts(not include Google+ accounts) in filter. */
    private void syncContacts(ContactListFilter filter) {
        if (filter == null) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        final List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(
                getActivity()).getAccounts(/* contactsWritableOnly */ true);
        final List<Account> syncableAccounts = filter.getSyncableAccounts(accounts);
        if (syncableAccounts != null && syncableAccounts.size() > 0) {
            for (Account account : syncableAccounts) {
                if (!SyncUtil.isSyncStatusPendingOrActive(account)) {
                    ContentResolver.requestSync(account, ContactsContract.AUTHORITY, bundle);
                }
            }
        }
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
        if (!flag) showSearchProgress(false);
    }

    /** Show or hide the directory-search progress spinner. */
    private void showSearchProgress(boolean show) {
        if (mSearchProgress != null) {
            mSearchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void checkHeaderViewVisibility() {
        // Hide the search header by default.
        if (mSearchHeaderView != null) {
            mSearchHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void setListHeader() {
        if (!isSearchMode()) {
            return;
        }
        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        // In search mode we only display the header if there is nothing found
        if (TextUtils.isEmpty(getQueryString()) || !adapter.areAllPartitionsEmpty()) {
            mSearchHeaderView.setVisibility(View.GONE);
            showSearchProgress(false);
        } else {
            mSearchHeaderView.setVisibility(View.VISIBLE);
            if (adapter.isLoading()) {
                mSearchProgressText.setText(R.string.search_results_searching);
                showSearchProgress(true);
            } else {
                mSearchProgressText.setText(R.string.listFoundAllContactsZero);
                mSearchProgressText.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_SELECTED);
                showSearchProgress(false);
            }
        }
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return mSwipeRefreshLayout;
    }
}