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
package com.android.contacts.model.account;

import android.content.Context;
import android.content.IntentFilter;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.concurrent.ListenableFutureLoader;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/**
 * Loads the accounts from AccountTypeManager
 */
public class AccountsLoader extends ListenableFutureLoader<List<AccountInfo>> {
    private final AccountTypeManager mAccountTypeManager;
    private final Predicate<AccountInfo> mFilter;

    public AccountsLoader(Context context) {
        this(context, Predicates.<AccountInfo>alwaysTrue());
    }

    public AccountsLoader(Context context, Predicate<AccountInfo> filter) {
        super(context, new IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED));
        mAccountTypeManager = AccountTypeManager.getInstance(context);
        mFilter = filter;
    }

    @Override
    protected ListenableFuture<List<AccountInfo>> loadData() {
        return mAccountTypeManager.filterAccountsAsync(mFilter);
    }

    @Override
    protected boolean isSameData(List<AccountInfo> previous, List<AccountInfo> next) {
        return Objects.equal(AccountInfo.extractAccounts(previous),
                AccountInfo.extractAccounts(next));
    }

}
