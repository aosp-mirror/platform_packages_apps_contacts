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
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.FavoritesAndContactsLoader;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
    private View mSearchHeaderView;
    private View mSearchProgress;
    private TextView mSearchProgressText;

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        // Don't use a QuickContactBadge. Just use a regular ImageView. Using a QuickContactBadge
        // inside the ListView prevents us from using MODE_FULLY_EXPANDED and messes up ripples.
        setQuickContactEnabled(false);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new FavoritesAndContactsLoader(context);
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