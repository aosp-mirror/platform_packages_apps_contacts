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
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;

public class DefaultAccountPreference extends DialogPreference {
    private ContactsPreferences mPreferences;
    private AccountsListAdapter mListAdapter;
    private AccountDisplayInfoFactory mAccountDisplayInfoFactory;
    private AccountTypeManager mAccountTypeManager;
    private int mChosenIndex = -1;

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
        mListAdapter = new AccountsListAdapter(getContext(),
                AccountsListAdapter.AccountListFilter.ACCOUNTS_CONTACT_WRITABLE);
        mAccountTypeManager = AccountTypeManager.getInstance(getContext());
        mAccountDisplayInfoFactory = AccountDisplayInfoFactory.forWritableAccounts(getContext());
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        final AccountWithDataSet defaultAccount = mPreferences.getDefaultAccount();
        if (defaultAccount == null ||
                !mAccountTypeManager.getAccounts(/* writable */ true).contains(defaultAccount)) {
            return null;
        } else {
            return mAccountDisplayInfoFactory.getAccountDisplayInfo(defaultAccount).getNameLabel();
        }
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        // UX recommendation is not to show buttons on such lists.
        builder.setNegativeButton(null, null);
        builder.setPositiveButton(null, null);
        builder.setAdapter(mListAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mChosenIndex = which;
            }
        });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        final AccountWithDataSet currentDefault = mPreferences.getDefaultAccount();

        if (mChosenIndex != -1) {
            final AccountWithDataSet chosenAccount = mListAdapter.getItem(mChosenIndex);
            if (!chosenAccount.equals(currentDefault)) {
                mPreferences.setDefaultAccount(chosenAccount);
                notifyChanged();
            }
        } // else the user dismissed this dialog so leave the preference unchanged.
    }
}
