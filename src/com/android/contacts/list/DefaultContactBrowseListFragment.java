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

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.FavoritesAndContactsLoader;
import com.android.contacts.common.model.account.AccountWithDataSet;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
    private View mSearchHeaderView;
    private View mSearchProgress;
    private View mEmptyAccountView;
    private View mEmptyHomeView;
    private View mAccountFilterContainer;
    private TextView mSearchProgressText;

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
        final ContactListFilter filter = getFilter();
        if (!isSearchMode() && numberOfContacts <= 0) {
            if (filter != null && filter.isContactsFilterType()) {
                makeViewVisible(mEmptyHomeView);
            } else {
                makeViewVisible(mEmptyAccountView);
            }
            return;
        }
        makeViewVisible(mAccountFilterContainer);
        if (isSearchMode()) {
            hideHeaderAndAddPadding(getContext(), getListView(), mAccountFilterContainer);
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            bindListHeaderCustom(getListView(), mAccountFilterContainer);
        } else if (filter.filterType != ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    filter.accountName, filter.accountType, filter.dataSet);
            bindListHeader(getContext(), getListView(), mAccountFilterContainer,
                    accountWithDataSet, numberOfContacts);
        } else {
            hideHeaderAndAddPadding(getContext(), getListView(), mAccountFilterContainer);
        }
    }

    // Show the view that's specified by id and hide the other two.
    private void makeViewVisible(View view) {
        mEmptyAccountView.setVisibility(view == mEmptyAccountView ? View.VISIBLE : View.GONE);
        mEmptyHomeView.setVisibility(view == mEmptyHomeView ? View.VISIBLE : View.GONE);
        mAccountFilterContainer.setVisibility(
                view == mAccountFilterContainer ? View.VISIBLE : View.GONE);
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
        final View view = inflater.inflate(R.layout.contact_list_content, null);

        mAccountFilterContainer = view.findViewById(R.id.account_filter_header_container);

        // Add empty main view and account view to list.
        final FrameLayout contactListLayout = (FrameLayout) view.findViewById(R.id.contact_list);
        mEmptyAccountView = getEmptyAccountView(inflater);
        mEmptyHomeView = getEmptyHomeView(inflater);
        contactListLayout.addView(mEmptyAccountView);
        contactListLayout.addView(mEmptyHomeView);

        return view;
    }

    private View getEmptyHomeView(LayoutInflater inflater) {
        final View emptyHomeView = inflater.inflate(R.layout.empty_home_view, null);
        // Set image margins.
        final ImageView image = (ImageView) emptyHomeView.findViewById(R.id.empty_home_image);
        final LayoutParams params = (LayoutParams) image.getLayoutParams();
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        final int marginTop = screenHeight / 2 -
                getResources().getDimensionPixelSize(R.dimen.empty_home_view_image_offset) ;
        params.setMargins(0, marginTop, 0, 0);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(params);

        // Set up add contact button.
        final Button addContactButton =
                (Button) emptyHomeView.findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((PeopleActivity) getActivity()).onFabClicked();
            }
        });
        return emptyHomeView;
    }

    private View getEmptyAccountView(LayoutInflater inflater) {
        final View emptyAccountView = inflater.inflate(R.layout.empty_account_view, null);
        // Set image margins.
        final ImageView image = (ImageView) emptyAccountView.findViewById(R.id.empty_account_image);
        final LayoutParams params = (LayoutParams) image.getLayoutParams();
        final int height = getResources().getDisplayMetrics().heightPixels;
        final int divisor =
                getResources().getInteger(R.integer.empty_account_view_image_margin_divisor);
        final int offset =
                getResources().getDimensionPixelSize(R.dimen.empty_account_view_image_offset);
        params.setMargins(0, height / divisor + offset, 0, 0);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(params);

        // Set up add contact button.
        final Button addContactButton =
                (Button) emptyAccountView.findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((PeopleActivity) getActivity()).onFabClicked();
            }
        });
        return emptyAccountView;
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

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
}