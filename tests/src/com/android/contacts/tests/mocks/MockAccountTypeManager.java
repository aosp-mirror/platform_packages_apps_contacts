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
import com.android.contacts.model.AccountTypeWithDataSet;
import com.android.contacts.model.AccountWithDataSet;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import libcore.util.Objects;

/**
 * A mock {@link AccountTypeManager} class.
 */
public class MockAccountTypeManager extends AccountTypeManager {

    public AccountType[] mTypes;
    public AccountWithDataSet[] mAccounts;

    public MockAccountTypeManager(AccountType[] types, AccountWithDataSet[] accounts) {
        this.mTypes = types;
        this.mAccounts = accounts;
    }

    @Override
    public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
        for (AccountType type : mTypes) {
            if (Objects.equal(accountTypeWithDataSet.accountType, type.accountType)
                    && Objects.equal(accountTypeWithDataSet.dataSet, type.dataSet)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public List<AccountWithDataSet> getAccounts(boolean writableOnly) {
        return Arrays.asList(mAccounts);
    }

    @Override
    public List<AccountWithDataSet> getGroupWritableAccounts() {
        return Arrays.asList(mAccounts);
    }

    @Override
    public Map<AccountTypeWithDataSet, AccountType> getUsableInvitableAccountTypes() {
        return Maps.newHashMap(); // Always returns empty
    }

    @Override
    public List<AccountType> getAccountTypes(boolean writableOnly) {
        final List<AccountType> ret = Lists.newArrayList();
        synchronized (this) {
            for (AccountType type : mTypes) {
                if (!writableOnly || type.areContactsWritable()) {
                    ret.add(type);
                }
            }
        }
        return ret;
    }
}
