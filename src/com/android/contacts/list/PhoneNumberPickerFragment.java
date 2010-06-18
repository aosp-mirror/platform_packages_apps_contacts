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

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment containing a phone number list for picking.
 */
public class PhoneNumberPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener {
    private OnPhoneNumberPickerActionListener mListener;
    private String mShortcutAction;

    public PhoneNumberPickerFragment() {
        setPhotoLoaderEnabled(true);
    }

    public void setOnPhoneNumberPickerActionListener(OnPhoneNumberPickerActionListener listener) {
        this.mListener = listener;
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
        if (!isLegacyCompatibilityMode()) {
            PhoneNumberListAdapter adapter = (PhoneNumberListAdapter)getAdapter();
            pickPhoneNumber(adapter.getDataUri(position));
        } else {
            LegacyPhoneNumberListAdapter adapter = (LegacyPhoneNumberListAdapter)getAdapter();
            pickPhoneNumber(adapter.getPhoneUri(position));
        }
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
        setSectionHeaderDisplayEnabled(!isSearchMode());
        setAizyEnabled(!isSearchMode());
        super.configureAdapter();
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
    public void startSearch(String initialQuery) {
        ContactsSearchManager.startSearchForResult(getActivity(), initialQuery,
                ACTIVITY_REQUEST_CODE_FILTER, getContactsRequest());
    }
}
