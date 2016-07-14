/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.app.Fragment;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.R;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for account filter manipulation.
 */
public class AccountFilterUtil {
    private static final String TAG = AccountFilterUtil.class.getSimpleName();

     /**
      * Launches account filter setting Activity using
      * {@link Fragment#startActivityForResult(Intent, int)}.
      *
      * @param requestCode requestCode for {@link Activity#startActivityForResult(Intent, int)}
      * @param currentFilter currently-selected filter, so that it can be displayed as activated.
      */
     public static void startAccountFilterActivityForResult(
             Fragment fragment, int requestCode, ContactListFilter currentFilter) {
         final Activity activity = fragment.getActivity();
         if (activity != null) {
             final Intent intent = new Intent(activity, AccountFilterActivity.class);
             fragment.startActivityForResult(intent, requestCode);
         } else {
             Log.w(TAG, "getActivity() returned null. Ignored");
         }
     }

    /**
     * Useful method to handle onActivityResult() for
     * {@link #startAccountFilterActivityForResult(Fragment, int, ContactListFilter)}.
     *
     * This will update filter via a given ContactListFilterController.
     */
    public static void handleAccountFilterResult(
            ContactListFilterController filterController, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            final ContactListFilter filter = (ContactListFilter)
                    data.getParcelableExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER);
            if (filter == null) {
                return;
            }
            if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                filterController.selectCustomFilter();
            } else {
                filterController.setContactListFilter(filter, /* persistent */
                        filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
            }
        }
    }

    /**
     * Loads a list of contact list filters
     */
    public static class FilterLoader extends AsyncTaskLoader<List<ContactListFilter>> {
        private Context mContext;

        public FilterLoader(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public List<ContactListFilter> loadInBackground() {
            return loadAccountFilters(mContext);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        protected void onReset() {
            onStopLoading();
        }
    }

    private static List<ContactListFilter> loadAccountFilters(Context context) {
        final ArrayList<ContactListFilter> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(context);
        accountTypeManager.sortAccounts(/* defaultAccount */ getDefaultAccount(context));
        final List<AccountWithDataSet> accounts =
                accountTypeManager.getAccounts(/* contactWritableOnly */ false);
        final List<AccountWithDataSet> writableAccounts =
                accountTypeManager.getAccounts(/* contactWritableOnly */ true);
        final AccountWithDataSet localAccount = AccountWithDataSet.getLocalAccount();
        if ((writableAccounts == null || writableAccounts.size() == 0)
                && !accounts.contains(localAccount)) {
            // Add "local account" if there is no writable account.
            accounts.add(localAccount);
        }

        for (AccountWithDataSet account : accounts) {
            final AccountType accountType =
                    accountTypeManager.getAccountType(account.type, account.dataSet);
            if ((accountType.isExtension() || !accountType.areContactsWritable())
                    && !account.hasData(context)) {
                // Hide extensions and read-only accounts with no raw_contacts.
                continue;
            }
            final Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
            if (account.isLocalAccount()) {
                accountFilters.add(ContactListFilter.createDeviceContactsFilter(icon));
            } else {
                accountFilters.add(ContactListFilter.createAccountFilter(
                        account.type, account.name, account.dataSet, icon));
            }
        }

        final ArrayList<ContactListFilter> result = Lists.newArrayList();
        result.addAll(accountFilters);
        return result;
    }

    private static AccountWithDataSet getDefaultAccount(Context context) {
        final SharedPreferences prefs =
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        final String defaultAccountKey =
                context.getResources().getString(R.string.contact_editor_default_account_key);
        final String defaultAccountString = prefs.getString(defaultAccountKey, null);
        if (TextUtils.isEmpty(defaultAccountString)) {
            return null;
        }
        try {
            return AccountWithDataSet.unstringify(defaultAccountString);
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Error with retrieving default account " + exception.toString(), exception);
            return null;
        }
    }
}
