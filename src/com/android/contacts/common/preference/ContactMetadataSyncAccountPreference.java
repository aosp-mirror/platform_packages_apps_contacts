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
 * limitations under the License
 */

package com.android.contacts.common.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountTypeWithDataSet;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactMetadataSyncAccountPreference extends ListPreference {
    private ContactsPreferences mPreferences;
    private Map<String, AccountWithDataSet> mAccountMap;

    public ContactMetadataSyncAccountPreference(Context context) {
        super(context);
        prepare();
    }

    public ContactMetadataSyncAccountPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    private void prepare() {
        mPreferences = new ContactsPreferences(getContext());
        mAccountMap = new HashMap<>();
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(getContext());
        List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(true);
        mAccountMap.put(ContactsPreferences.DO_NOT_SYNC_CONTACT_METADATA_MSG, null);
        for (AccountWithDataSet account : accounts) {
            if (GoogleAccountType.ACCOUNT_TYPE.equals(account.type) && account.dataSet == null) {
                mAccountMap.put(account.name, account);
            }
        }
        final Set<String> accountNames = mAccountMap.keySet();
        final String[] accountNamesArray = accountNames.toArray(new String[accountNames.size()]);
        setEntries(accountNamesArray);
        setEntryValues(accountNamesArray);
        setValue(mPreferences.getContactMetadataSyncAccount());
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        return mPreferences.getContactMetadataSyncAccount();
    }

    @Override
    protected boolean persistString(String value) {
        if (value == null && mPreferences.getContactMetadataSyncAccount() == null) {
            return true;
        }
        if (value == null || mPreferences.getContactMetadataSyncAccount() == null
                || !value.equals(mPreferences.getContactMetadataSyncAccount())) {
            mPreferences.setContactMetadataSyncAccount(mAccountMap.get(value));
            notifyChanged();
        }
        return true;
    }

    @Override
    // UX recommendation is not to show cancel button on such lists.
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null);
    }
}
