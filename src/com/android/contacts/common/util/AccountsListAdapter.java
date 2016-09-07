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

package com.android.contacts.common.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * List-Adapter for Account selection
 */
public final class AccountsListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final List<AccountDisplayInfo> mAccountDisplayInfoList;
    private final Context mContext;
    private int mCustomLayout = -1;

    /**
     * Filters that affect the list of accounts that is displayed by this adapter.
     */
    public enum AccountListFilter {
        ALL_ACCOUNTS,                   // All read-only and writable accounts
        ACCOUNTS_CONTACT_WRITABLE,      // Only where the account type is contact writable
        ACCOUNTS_GROUP_WRITABLE         // Only accounts where the account type is group writable
    }

    public AccountsListAdapter(Context context, AccountListFilter accountListFilter) {
        this(context, accountListFilter, null);
    }

    /**
     * @param currentAccount the Account currently selected by the user, which should come
     * first in the list. Can be null.
     */
    public AccountsListAdapter(Context context, AccountListFilter accountListFilter,
            AccountWithDataSet currentAccount) {
        mContext = context;
        final List<AccountWithDataSet> accounts = getAccounts(accountListFilter);
        if (currentAccount != null
                && !accounts.isEmpty()
                && !accounts.get(0).equals(currentAccount)
                && accounts.remove(currentAccount)) {
            accounts.add(0, currentAccount);
        }

        final AccountDisplayInfoFactory factory = new AccountDisplayInfoFactory(context,
                accounts);
        mAccountDisplayInfoList = new ArrayList<>(accounts.size());
        for (AccountWithDataSet account : accounts) {
            mAccountDisplayInfoList.add(factory.getAccountDisplayInfo(account));
        }
        mInflater = LayoutInflater.from(context);
    }

    private List<AccountWithDataSet> getAccounts(AccountListFilter accountListFilter) {
        final AccountTypeManager typeManager = AccountTypeManager.getInstance(mContext);
        if (accountListFilter == AccountListFilter.ACCOUNTS_GROUP_WRITABLE) {
            return new ArrayList<AccountWithDataSet>(typeManager.getGroupWritableAccounts());
        }
        return new ArrayList<AccountWithDataSet>(typeManager.getAccounts(
                accountListFilter == AccountListFilter.ACCOUNTS_CONTACT_WRITABLE));
    }

    public void setCustomLayout(int customLayout) {
        mCustomLayout = customLayout;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View resultView = convertView != null ? convertView :
                mInflater.inflate(mCustomLayout > 0 ? mCustomLayout :
                        R.layout.account_selector_list_item_condensed, parent, false);

        final TextView text1 = (TextView) resultView.findViewById(android.R.id.text1);
        final TextView text2 = (TextView) resultView.findViewById(android.R.id.text2);
        final ImageView icon = (ImageView) resultView.findViewById(android.R.id.icon);

        text1.setText(mAccountDisplayInfoList.get(position).getTypeLabel());
        text2.setText(mAccountDisplayInfoList.get(position).getNameLabel());

        icon.setImageDrawable(mAccountDisplayInfoList.get(position).getIcon());

        return resultView;
    }

    @Override
    public int getCount() {
        return mAccountDisplayInfoList.size();
    }

    @Override
    public AccountWithDataSet getItem(int position) {
        return mAccountDisplayInfoList.get(position).getSource();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}

