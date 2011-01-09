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
package com.android.contacts.tests.mocks;

import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;

import android.accounts.Account;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A mock {@link AccountTypeManager} class.
 */
public class MockAccountTypeManager extends AccountTypeManager {

    private final AccountType[] mTypes;
    private Account[] mAccounts;

    public MockAccountTypeManager(AccountType[] types, Account[] accounts) {
        this.mTypes = types;
        this.mAccounts = accounts;
    }

    @Override
    public AccountType getAccountType(String accountType) {
        for (AccountType type : mTypes) {
            if (accountType.equals(type.accountType)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public ArrayList<Account> getAccounts(boolean writableOnly) {
        return new ArrayList<Account>(Arrays.asList(mAccounts));
    }
}
