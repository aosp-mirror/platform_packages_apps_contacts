/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class AccountFilterActivity extends ContactsActivity
        implements AdapterView.OnItemClickListener {

    private static final String TAG = AccountFilterActivity.class.getSimpleName();

    public static final String KEY_EXTRA_CONTACT_LIST_FILTER = "contactListFilter";

    private ListView mListView;

    private List<ContactListFilter> mFilters = new ArrayList<ContactListFilter>();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_list_filter);

        mListView = (ListView) findViewById(com.android.internal.R.id.list);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                finishAndSetResult(mFilters.get(position));
            }
        });

        ActionBar actionBar =  getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        loadAccountFilters();
    }

    private void loadAccountFilters() {
        ArrayList<ContactListFilter> accountFilters = new ArrayList<ContactListFilter>();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        ArrayList<Account> accounts = accountTypes.getAccounts(false);
        for (Account account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type);
            Drawable icon = accountType != null ? accountType.getDisplayIcon(this) : null;
            accountFilters.add(ContactListFilter.createAccountFilter(account.type, account.name,
                    icon, account.name));
        }
        int count = accountFilters.size();

        if (count >= 1) {
            // If we only have one account, don't show it as "account", instead show it as "all"
            mFilters.add(ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            if (count > 1) {
                mFilters.addAll(accountFilters);
                mFilters.add(ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_CUSTOM));
            }
        }

        mListView.setAdapter(new FilterListAdapter(this));
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        finishAndSetResult(mFilters.get(position));
    }

    private void finishAndSetResult(ContactListFilter filter) {
        final Intent intent = new Intent();
        intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    private class FilterListAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        public FilterListAdapter(Context context) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public ContactListFilter getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.filter_spinner_item, parent, false);
            }
            view.setSingleAccount(mFilters.size() == 1);
            ContactListFilter filter = mFilters.get(position);
            view.setContactListFilter(filter);
            view.bindView(true);
            return view;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
