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
package com.android.contacts.tests;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.test.InstrumentationRegistry;

import com.android.contacts.common.model.account.AccountWithDataSet;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@SuppressWarnings("MissingPermission")
public class AccountsTestHelper {
    public static final String TEST_ACCOUNT_TYPE = "com.android.contacts.tests.testauth.basic";

    private final Context mContext;
    private final AccountManager mAccountManager;
    private final ContentResolver mResolver;

    private Account mTestAccount;

    public AccountsTestHelper() {
        // Use context instead of target context because the test package has the permissions needed
        // to add and remove accounts.
        this(InstrumentationRegistry.getContext());
    }

    public AccountsTestHelper(Context context) {
        mContext = context;
        mAccountManager = AccountManager.get(mContext);
        mResolver = mContext.getContentResolver();
    }

    public AccountWithDataSet addTestAccount() {
        return addTestAccount(generateAccountName());
    }

    public String generateAccountName(String prefix) {
        return prefix + "_t" + System.nanoTime();
    }

    public String generateAccountName() {
        return generateAccountName("test");
    }

    public AccountWithDataSet addTestAccount(@NonNull String name) {
        // remember the most recent one. If the caller wants to add multiple accounts they will
        // have to keep track of them themselves.
        mTestAccount = new Account(name, TEST_ACCOUNT_TYPE);
        assertTrue(mAccountManager.addAccountExplicitly(mTestAccount, null, null));
        return convertTestAccount();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void removeTestAccount(AccountWithDataSet account) {
        final Account remove = account.getAccountOrNull();
        mAccountManager.removeAccountExplicitly(remove);
    }

    public void removeContactsForAccount() {
        // Not sure if this is necessary or if contacts are automatically cleaned up when the
        // account is removed.
        mResolver.delete(RawContacts.CONTENT_URI,
                RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?",
                new String[] { mTestAccount.name, mTestAccount.type });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void cleanup() {
        assertNotNull(mTestAccount);

        // Note that we don't need to cleanup up the contact data associated with the account.
        // CP2 will eventually do that automatically so as long as we're using unique account
        // names we should be safe. Note that cleanup is not done synchronously when the account
        // is removed so if multiple tests are using the same account name then the data should
        // be manually deleted after each test run.

        mAccountManager.removeAccountExplicitly(mTestAccount);
        mTestAccount = null;
    }

    private AccountWithDataSet convertTestAccount() {
        return new AccountWithDataSet(mTestAccount.name, mTestAccount.type, null);
    }
}
