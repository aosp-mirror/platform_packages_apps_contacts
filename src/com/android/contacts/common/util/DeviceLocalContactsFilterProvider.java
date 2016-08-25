/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.common.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Keep;
import android.support.annotation.VisibleForTesting;

import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.test.NeededForReflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Get filters for device local accounts. These are "accounts" that have contacts associated
 * with them but are not returned by AccountManager. Any other account will be displayed
 * automatically so we don't worry about it.
 */
public class DeviceLocalContactsFilterProvider
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static String[] PROJECTION = new String[] {
            ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE
    };

    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;

    private final Context mContext;
    private final DeviceAccountFilter mAccountTypeFilter;

    private String[] mKnownAccountTypes;

    private List<ContactListFilter> mDeviceFilters = Collections.emptyList();

    public DeviceLocalContactsFilterProvider(Context context,
            DeviceAccountFilter accountTypeFilter) {
        mContext = context;
        mAccountTypeFilter = accountTypeFilter;
    }

    private ContactListFilter createFilterForAccount(Account account) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
                account.type, account.name, null, null);
    }

    public List<ContactListFilter> getListFilters() {
        return mDeviceFilters;
    }

    @Override
    public CursorLoader onCreateLoader(int i, Bundle bundle) {
        if (mKnownAccountTypes == null) {
            initKnownAccountTypes();
        }
        return new CursorLoader(mContext, getUri(), PROJECTION, getSelection(),
                getSelectionArgs(), null);
    }


    private List<ContactListFilter> createFiltersFromResults(Cursor cursor) {
        final Set<Account> accounts = new HashSet<>();
        boolean hasNullType = false;

        while (cursor.moveToNext()) {
            final String name = cursor.getString(COL_NAME);
            final String type = cursor.getString(COL_TYPE);
            // The case where where only one of the columns is null isn't handled specifically.
            if (mAccountTypeFilter.isDeviceAccountType(type)) {
                if (name != null && type != null) {
                    accounts.add(new Account(name, type));
                } else {
                    hasNullType = true;
                }
            }
        }

        final List<ContactListFilter> result = new ArrayList<>(accounts.size());
        if (hasNullType) {
            result.add(new ContactListFilter(ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
                    null, null, null, null));
        }
        for (Account account : accounts) {
            result.add(createFilterForAccount(account));
        }
        return result;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) return;
        mDeviceFilters = createFiltersFromResults(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Keep
    @VisibleForTesting
    public void setKnownAccountTypes(String... accountTypes) {
        mKnownAccountTypes = accountTypes;
    }

    private void initKnownAccountTypes() {
        final AccountManager accountManager = (AccountManager) mContext
                .getSystemService(Context.ACCOUNT_SERVICE);
        final Set<String> knownTypes = new HashSet<>();
        final Account[] accounts = accountManager.getAccounts();
        for (Account account : accounts) {
            if (ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) > 0) {
                knownTypes.add(account.type);
            }
        }
        mKnownAccountTypes = knownTypes.toArray(new String[knownTypes.size()]);
    }

    private Uri getUri() {
        final Uri.Builder builder = ContactsContract.RawContacts.CONTENT_URI.buildUpon();
        if (mKnownAccountTypes == null || mKnownAccountTypes.length == 0) {
            builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "1");
        }
        return builder.build();
    }

    private String getSelection() {
        final StringBuilder sb = new StringBuilder();
        sb.append(ContactsContract.RawContacts.DELETED).append(" =0 AND (")
                .append(ContactsContract.RawContacts.ACCOUNT_TYPE).append(" IS NULL");
        if (mKnownAccountTypes == null || mKnownAccountTypes.length == 0) {
            return sb.append(')').toString();
        }
        sb.append(" OR ").append(ContactsContract.RawContacts.ACCOUNT_TYPE).append(" NOT IN (");
        for (String ignored : mKnownAccountTypes) {
            sb.append("?,");
        }
        // Remove trailing ','
        sb.deleteCharAt(sb.length() - 1).append(')').append(')');

        return sb.toString();
    }

    private String[] getSelectionArgs() {
        return mKnownAccountTypes;
    }
}
