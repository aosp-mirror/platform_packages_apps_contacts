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
package com.android.contacts.editor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;

import java.util.List;

/**
 * This class is to fix the bug that no prompt is seen for multiple accounts while creating new
 * contacts. By registering a BroadcastReceiver statically, we detect the changes of accounts by
 * receiving the message "android.accounts.LOGIN_ACCOUNTS_CHANGED". If the BroadcastReceiver gets
 * this message, it will get the default account from the SharedPreference and compare current
 * accounts with the default account. At last, it will renew the default account in the
 * SharedPreference if necessary.
 */
public class AccountsChangedBroadcastReceiver extends BroadcastReceiver {
    final String TAG = "AccountsChanged";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context.getApplicationContext();
        final ContactEditorUtils contactEditorUtils = ContactEditorUtils.getInstance(appContext);
        final String defaultAccountKey = appContext.getResources().getString(
                R.string.contact_editor_default_account_key);
        final SharedPreferences pref = appContext.getSharedPreferences(
                appContext.getPackageName(), Context.MODE_PRIVATE);
        final String defaultAccountString = pref.getString(defaultAccountKey, null);

        if (!TextUtils.isEmpty(defaultAccountString)) {
            AccountWithDataSet defaultAccount;
            try {
                defaultAccount = AccountWithDataSet.unstringify(defaultAccountString);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid string in SharedPreference", e);
                contactEditorUtils.saveDefaultAndAllAccounts(null);
                return;
            }

            final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(
                    appContext);
            final List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(true);
            // Delete default account pref if it has been deleted.
            if (accounts == null || accounts.size() < 1 || !accounts.contains(defaultAccount)) {
                contactEditorUtils.saveDefaultAndAllAccounts(null);
            }
        }
    }
}
