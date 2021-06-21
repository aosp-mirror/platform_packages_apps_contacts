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
package com.android.contacts.model;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.provider.ContactsContract;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.Collections;
import java.util.List;

/**
 * Attempts to detect accounts for device contacts
 */
public final class DeviceLocalAccountLocator {

    private final Context mContext;
    private final AccountManager mAccountManager;
    private final List<AccountWithDataSet> mLocalAccount;

    public DeviceLocalAccountLocator(Context context, AccountManager accountManager) {
        mContext = context;
        mAccountManager = accountManager;
        mLocalAccount = Collections.singletonList(AccountWithDataSet.getLocalAccount(context));
    }

    /**
     * Returns a list of device local accounts
     */
    public List<AccountWithDataSet> getDeviceLocalAccounts() {
        @SuppressWarnings("MissingPermission") final Account[] accounts = mAccountManager
                .getAccountsByType(GoogleAccountType.ACCOUNT_TYPE);

        if (accounts.length > 0 && !mLocalAccount.get(0).hasData(mContext)) {
            return Collections.emptyList();
        } else {
            return mLocalAccount;
        }
    }
}
