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

import com.android.contacts.R;
import com.android.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Fragment containing a phone number list for picking.
 */
public class PhoneNumberPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener {
    private static final String TAG = PhoneNumberPickerFragment.class.getSimpleName();

    private OnPhoneNumberPickerActionListener mListener;
    private String mShortcutAction;

    private SharedPreferences mPrefs;
    private ContactListFilter mFilter;

    private TextView mAccountFilterHeaderView;
    private View mAccountFilterHeaderContainer;
    /**
     * Lives as ListView's header and is shown when {@link #mAccountFilterHeaderContainer} is set
     * to View.GONE.
     */
    private View mPaddingView;

    private static final String KEY_FILTER = "filter";

    /** true if the loader has started at least once. */
    private boolean mLoaderStarted;

    private ContactListItemView.PhotoPosition mPhotoPosition =
            ContactListItemView.DEFAULT_PHOTO_POSITION;

    // A complete copy from DefaultContactBrowserListFragment
    // TODO: should be able to share logic around filter header.
    private class FilterHeaderClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            final Activity activity = getActivity();
            if (activity != null) {
                final Intent intent = new Intent(activity, AccountFilterActivity.class);
                activity.startActivityForResult(
                        intent, AccountFilterActivity.DEFAULT_REQUEST_CODE);
            }
        }
    }
    private OnClickListener mFilterHeaderClickListener = new FilterHeaderClickListener();

    public PhoneNumberPickerFragment() {
        setQuickContactEnabled(false);
        setPhotoLoaderEnabled(true);
        setVisibleScrollbarEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DATA_SHORTCUT);

        // Show nothing instead of letting caller Activity show something.
        setHasOptionsMenu(true);
    }

    public void setOnPhoneNumberPickerActionListener(OnPhoneNumberPickerActionListener listener) {
        this.mListener = listener;
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        View paddingView = inflater.inflate(R.layout.contact_detail_list_padding, null, false);
        mPaddingView = paddingView.findViewById(R.id.contact_detail_list_padding);
        getListView().addHeaderView(paddingView);

        mAccountFilterHeaderView = (TextView) getView().findViewById(R.id.account_filter_header);
        mAccountFilterHeaderContainer =
                getView().findViewById(R.id.account_filter_header_container);
        mAccountFilterHeaderContainer.setOnClickListener(mFilterHeaderClickListener);
        updateFilterHeaderView();
    }

    @Override
    public void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        updateFilterHeaderView();
    }

    private void updateFilterHeaderView() {
        if (mAccountFilterHeaderView != null) {
            ContactListFilter filter = getFilter();
            if (filter != null && !isSearchMode()) {
                if (filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                    mAccountFilterHeaderContainer.setVisibility(View.VISIBLE);
                    mAccountFilterHeaderView.setText(getContext().getString(
                            R.string.listSingleContact));
                    mPaddingView.setVisibility(View.GONE);
                    return;
                } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                    mAccountFilterHeaderContainer.setVisibility(View.VISIBLE);
                    mAccountFilterHeaderView.setText(getContext().getString(
                            R.string.listCustomView));
                    mPaddingView.setVisibility(View.GONE);
                    return;
                } else if (filter.filterType != ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                    mAccountFilterHeaderContainer.setVisibility(View.VISIBLE);
                    mAccountFilterHeaderView.setText(getContext().getString(
                            R.string.listAllContactsInAccount, filter.accountName));
                    mPaddingView.setVisibility(View.GONE);
                    return;
                }
            }
            mAccountFilterHeaderContainer.setVisibility(View.GONE);
            mPaddingView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mPrefs = null;
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mFilter = savedState.getParcelable(KEY_FILTER);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            if (mListener != null) {
                mListener.onHomeInActionBarSelected();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * @param shortcutAction either {@link Intent#ACTION_CALL} or
     *            {@link Intent#ACTION_SENDTO} or null.
     */
    public void setShortcutAction(String shortcutAction) {
        this.mShortcutAction = shortcutAction;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri phoneUri;
        if (!isLegacyCompatibilityMode()) {
            PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
            phoneUri = adapter.getDataUri(position);

        } else {
            LegacyPhoneNumberListAdapter adapter = (LegacyPhoneNumberListAdapter) getAdapter();
            phoneUri = adapter.getPhoneUri(position);
        }

        if (phoneUri != null) {
            pickPhoneNumber(phoneUri);
        } else {
            Log.w(TAG, "Item at " + position + " was clicked before adapter is ready. Ignoring");
        }
    }

    @Override
    protected void startLoading() {
        mLoaderStarted = true;
        super.startLoading();
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        if (!isLegacyCompatibilityMode()) {
            PhoneNumberListAdapter adapter = new PhoneNumberListAdapter(getActivity());
            adapter.setDisplayPhotos(true);
            return adapter;
        } else {
            LegacyPhoneNumberListAdapter adapter = new LegacyPhoneNumberListAdapter(getActivity());
            adapter.setDisplayPhotos(true);
            return adapter;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        if (!isSearchMode() && mFilter != null) {
            adapter.setFilter(mFilter);
        }

        if (!isLegacyCompatibilityMode()) {
            ((PhoneNumberListAdapter) adapter).setPhotoPosition(mPhotoPosition);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content, null);
    }

    public void pickPhoneNumber(Uri uri) {
        if (mShortcutAction == null) {
            mListener.onPickPhoneNumberAction(uri);
        } else {
            if (isLegacyCompatibilityMode()) {
                throw new UnsupportedOperationException();
            }
            ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
            builder.createPhoneNumberShortcutIntent(uri, mShortcutAction);
        }
    }

    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickPhoneNumberAction(data.getData());
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        if ((mFilter == null && filter == null) ||
                (mFilter != null && mFilter.equals(filter))) {
            return;
        }

        mFilter = filter;
        if (mPrefs != null) {
            // Save the preference now.
            ContactListFilter.storeToPreferences(mPrefs, mFilter);
        }

        // This method can be called before {@link #onStart} where we start the loader.  In that
        // case we shouldn't start the loader yet, as we haven't done all initialization yet.
        if (mLoaderStarted) {
            reloadData();
        }
        updateFilterHeaderView();
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
        if (!isLegacyCompatibilityMode()) {
            final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
            if (adapter != null) {
                adapter.setPhotoPosition(photoPosition);
            }
        } else {
            Log.w(TAG, "setPhotoPosition() is ignored in legacy compatibility mode.");
        }
    }
}
