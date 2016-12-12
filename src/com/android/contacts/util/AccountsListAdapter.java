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

package com.android.contacts.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountDisplayInfoFactory;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * List-Adapter for Account selection
 */
public final class AccountsListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final List<AccountDisplayInfo> mAccountDisplayInfoList;
    private final List<AccountWithDataSet> mAccounts;
    private final Context mContext;
    private int mCustomLayout = -1;

    public enum AccountListFilter {
        ALL_ACCOUNTS {
            @Override
            public List<AccountWithDataSet> getAccounts(Context context) {
                return AccountTypeManager.getInstance(context).getAccounts(false);
            }
        },
        ACCOUNTS_CONTACT_WRITABLE {
            @Override
            public List<AccountWithDataSet> getAccounts(Context context) {
                return AccountTypeManager.getInstance(context).getAccounts(true);
            }
        },
        ACCOUNTS_GROUP_WRITABLE {
            @Override
            public List<AccountWithDataSet> getAccounts(Context context) {
                return AccountTypeManager.getInstance(context).getGroupWritableAccounts();
            }
        };

        public abstract List<AccountWithDataSet> getAccounts(Context context);
    }

    public AccountsListAdapter(Context context, AccountListFilter filter) {
        this(context, filter.getAccounts(context), null);
    }

    public AccountsListAdapter(Context context, AccountListFilter filter,
            AccountWithDataSet currentAccount) {
        this(context, filter.getAccounts(context), currentAccount);
    }

    public AccountsListAdapter(Context context, List<AccountWithDataSet> accounts) {
        this(context, accounts, null);
    }

    /**
     * @param currentAccount the Account currently selected by the user, which should come
     * first in the list. Can be null.
     */
    public AccountsListAdapter(Context context, List<AccountWithDataSet> accounts,
            AccountWithDataSet currentAccount) {
        mContext = context;
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

        mAccounts = accounts;
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

