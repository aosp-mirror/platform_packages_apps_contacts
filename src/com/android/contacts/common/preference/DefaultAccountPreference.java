/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;

import java.util.HashMap;
import java.util.Map;

public class DefaultAccountPreference extends ListPreference {
    private ContactsPreferences mPreferences;
    private Map<String, AccountWithDataSet> mAccountMap;
    private int mClickedDialogEntryIndex;
    private AccountsListAdapter mListAdapter;

    public DefaultAccountPreference(Context context) {
        super(context);
        prepare();
    }

    public DefaultAccountPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    @Override
    protected View onCreateDialogView() {
        prepare();
        return super.onCreateDialogView();
    }

    private void prepare() {
        mPreferences = new ContactsPreferences(getContext());
        mAccountMap = new HashMap<>();
        mListAdapter = new AccountsListAdapter(getContext(),
                AccountsListAdapter.AccountListFilter.ACCOUNTS_CONTACT_WRITABLE);
        final String[] accountNamesArray = new String[mListAdapter.getCount()];
        for (int i = 0; i < mListAdapter.getCount(); i++) {
            final AccountWithDataSet account = mListAdapter.getItem(i);
            mAccountMap.put(account.name, account);
            accountNamesArray[i] = account.name;
        }
        setEntries(accountNamesArray);
        setEntryValues(accountNamesArray);
        final String defaultAccount = String.valueOf(mPreferences.getDefaultAccount());
        if (mListAdapter.getCount() == 1) {
            setValue(mListAdapter.getItem(0).name);
        } else if (mAccountMap.keySet().contains(defaultAccount)) {
            setValue(defaultAccount);
        } else {
            setValue(null);
        }
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        return mPreferences.getDefaultAccount();
    }

    @Override
    protected boolean persistString(String value) {
        if (value == null && mPreferences.getDefaultAccount() == null) {
            return true;
        }
        if (value == null || mPreferences.getDefaultAccount() == null
                || !value.equals(mPreferences.getDefaultAccount())) {
            mPreferences.setDefaultAccount(mAccountMap.get(value));
            notifyChanged();
        }
        return true;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        // UX recommendation is not to show cancel button on such lists.
        builder.setNegativeButton(null, null);
        // Override and do everything ListPreference does except relative to our custom adapter.
        // onDialogClosed needs to be overridden as well since mClickedDialogEntryIndex is private
        // in ListPreference.
        builder.setAdapter(mListAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
                // Clicking on an item simulates the positive button click,
                // and dismisses the dialog.
                DefaultAccountPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult && mClickedDialogEntryIndex >= 0 && getEntryValues() != null) {
            final String value = getEntryValues()[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }
}
