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
import android.content.Intent;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.common.util.AccountFilterUtil;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
    private static final String TAG = DefaultContactBrowseListFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ACCOUNT_FILTER = 1;

    private TextView mCounterHeaderView;
    private View mSearchHeaderView;
    private View mAccountFilterHeader;
    private FrameLayout mProfileHeaderContainer;
    private View mProfileHeader;
    private Button mProfileMessage;
    private FrameLayout mMessageContainer;
    private TextView mProfileTitle;
    private View mSearchProgress;
    private TextView mSearchProgressText;

    private class FilterHeaderClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            AccountFilterUtil.startAccountFilterActivityForResult(
                        DefaultContactBrowseListFragment.this,
                        REQUEST_CODE_ACCOUNT_FILTER,
                        getFilter());
        }
    }
    private OnClickListener mFilterHeaderClickListener = new FilterHeaderClickListener();

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new ProfileAndContactsLoader(context);
    }

    @Override
    protected void onItemClick(int position, long id) {
        viewContact(getAdapter().getContactUri(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        boolean showPhoto = getResources().getBoolean(R.bool.config_browse_list_show_images);
        adapter.setDisplayPhotos(showPhoto);
        if (showPhoto) {
            boolean reverse = getResources().getBoolean(R.bool.config_browse_list_reverse_images);
            adapter.setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(reverse));
        }
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        mAccountFilterHeader = getView().findViewById(R.id.account_filter_header_container);
        mAccountFilterHeader.setOnClickListener(mFilterHeaderClickListener);
        mCounterHeaderView = (TextView) getView().findViewById(R.id.contacts_count);

        // Create an empty user profile header and hide it for now (it will be visible if the
        // contacts list will have no user profile).
        addEmptyUserProfileHeader(inflater);
        showEmptyUserProfile(false);

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
        mSearchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void checkHeaderViewVisibility() {
        if (mCounterHeaderView != null) {
            mCounterHeaderView.setVisibility(isSearchMode() ? View.GONE : View.VISIBLE);
        }
        updateFilterHeaderView();

        // Hide the search header by default. See showCount().
        if (mSearchHeaderView != null) {
            mSearchHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    public void setFilter(ContactListFilter filter) {
        super.setFilter(filter);
        updateFilterHeaderView();
    }

    private void updateFilterHeaderView() {
        if (mAccountFilterHeader == null) {
            return; // Before onCreateView -- just ignore it.
        }
        final ContactListFilter filter = getFilter();
        if (filter != null && !isSearchMode()) {
            final boolean shouldShowHeader = AccountFilterUtil.updateAccountFilterTitleForPeople(
                    mAccountFilterHeader, filter, false);
            mAccountFilterHeader.setVisibility(shouldShowHeader ? View.VISIBLE : View.GONE);
        } else {
            mAccountFilterHeader.setVisibility(View.GONE);
        }
    }

    @Override
    protected void showCount(int partitionIndex, Cursor data) {
        if (!isSearchMode() && data != null) {
            int count = data.getCount();
            if (count != 0) {
                count -= (mUserProfileExists ? 1: 0);
                String format = getResources().getQuantityText(
                        R.plurals.listTotalAllContacts, count).toString();
                // Do not count the user profile in the contacts count
                if (mUserProfileExists) {
                    getAdapter().setContactsCount(String.format(format, count));
                } else {
                    mCounterHeaderView.setText(String.format(format, count));
                }
            } else {
                ContactListFilter filter = getFilter();
                int filterType = filter != null ? filter.filterType
                        : ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS;
                switch (filterType) {
                    case ContactListFilter.FILTER_TYPE_ACCOUNT:
                        mCounterHeaderView.setText(getString(
                                R.string.listTotalAllContactsZeroGroup, filter.accountName));
                        break;
                    case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                        mCounterHeaderView.setText(R.string.listTotalPhoneContactsZero);
                        break;
                    case ContactListFilter.FILTER_TYPE_STARRED:
                        mCounterHeaderView.setText(R.string.listTotalAllContactsZeroStarred);
                        break;
                    case ContactListFilter.FILTER_TYPE_CUSTOM:
                        mCounterHeaderView.setText(R.string.listTotalAllContactsZeroCustom);
                        break;
                    default:
                        mCounterHeaderView.setText(R.string.listTotalAllContactsZero);
                        break;
                }
            }
        } else {
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
            showEmptyUserProfile(false);
        }
    }

    @Override
    protected void setProfileHeader() {
        mUserProfileExists = getAdapter().hasProfile();
        showEmptyUserProfile(!mUserProfileExists && !isSearchMode());
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

    private void showEmptyUserProfile(boolean show) {
        // Changing visibility of just the mProfileHeader doesn't do anything unless
        // you change visibility of its children, hence the call to mCounterHeaderView
        // and mProfileTitle
        mProfileHeaderContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileHeader.setVisibility(show ? View.VISIBLE : View.GONE);
        mCounterHeaderView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        mMessageContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileMessage.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * This method creates a pseudo user profile contact. When the returned query doesn't have
     * a profile, this methods creates 2 views that are inserted as headers to the listview:
     * 1. A header view with the "ME" title and the contacts count.
     * 2. A button that prompts the user to create a local profile
     */
    private void addEmptyUserProfileHeader(LayoutInflater inflater) {

        ListView list = getListView();
        // Put a header with the "ME" name and a view for the number of contacts
        // The view is embedded in a frame view since you cannot change the visibility of a
        // view in a ListView without having a parent view.
        mProfileHeaderContainer = new FrameLayout(inflater.getContext());
        mProfileHeader = inflater.inflate(R.layout.user_profile_header, null, false);
        mCounterHeaderView = (TextView) mProfileHeader.findViewById(R.id.contacts_count);
        mProfileTitle = (TextView) mProfileHeader.findViewById(R.id.profile_title);
        mProfileHeaderContainer.addView(mProfileHeader);
        list.addHeaderView(mProfileHeaderContainer, null, false);

        // Add a selectable view with a message inviting the user to create a local profile
        mMessageContainer = new FrameLayout(inflater.getContext());
        mProfileMessage = (Button)inflater.inflate(R.layout.user_profile_button, null, false);
        mMessageContainer.addView(mProfileMessage);
        list.addHeaderView(mMessageContainer, null, true);

        mProfileMessage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                startActivity(intent);
            }
        });
    }
}
