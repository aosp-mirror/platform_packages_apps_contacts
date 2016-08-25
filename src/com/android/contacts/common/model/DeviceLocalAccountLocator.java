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
package com.android.contacts.common.model;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.VisibleForTesting;

import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.DeviceLocalAccountTypeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DeviceLocalAccountLocator attempts to create accounts for "Device" contacts by querying
 * CP2 for records with {@link android.provider.ContactsContract.RawContacts#ACCOUNT_TYPE} columns
 * that do not exist for any account returned by {@link AccountManager#getAccounts()}
 *
 * This class should be used from a background thread since it does DB queries
 */
public class DeviceLocalAccountLocator {

    @VisibleForTesting
    static String[] PROJECTION = new String[] {
            ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DATA_SET
    };

    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_DATA_SET = 2;


    private final ContentResolver mResolver;
    private final DeviceLocalAccountTypeFactory mAccountTypeFactory;
    private final Set<String> mKnownAccountTypes;


    public DeviceLocalAccountLocator(ContentResolver contentResolver,
            DeviceLocalAccountTypeFactory factory,
            List<AccountWithDataSet> knownAccounts) {
        mResolver = contentResolver;
        mAccountTypeFactory = factory;
        mKnownAccountTypes = new HashSet<>();
        for (AccountWithDataSet account : knownAccounts) {
            mKnownAccountTypes.add(account.type);
        }
    }

    public List<AccountWithDataSet> getDeviceLocalAccounts() {
        final String[] selectionArgs = getSelectionArgs();
        final Cursor cursor = mResolver.query(ContactsContract.RawContacts.CONTENT_URI, PROJECTION,
                getSelection(), selectionArgs, null);

        final Set<AccountWithDataSet> localAccounts = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                final String name = cursor.getString(COL_NAME);
                final String type = cursor.getString(COL_TYPE);
                final String dataSet = cursor.getString(COL_DATA_SET);

                if (DeviceLocalAccountTypeFactory.Util.isLocalAccountType(
                        mAccountTypeFactory, type)) {
                    localAccounts.add(new AccountWithDataSet(name, type, dataSet));
                }
            }
        } finally {
            cursor.close();
        }

        return new ArrayList<>(localAccounts);
    }

    @VisibleForTesting
    public String getSelection() {
        final StringBuilder sb = new StringBuilder();
        sb.append(ContactsContract.RawContacts.DELETED).append(" =0 AND (")
                .append(ContactsContract.RawContacts.ACCOUNT_TYPE).append(" IS NULL");
        if (mKnownAccountTypes.isEmpty()) {
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

    @VisibleForTesting
    public String[] getSelectionArgs() {
        return mKnownAccountTypes.toArray(new String[mKnownAccountTypes.size()]);
    }
}
