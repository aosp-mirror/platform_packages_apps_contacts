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

package com.android.contacts.model;

import com.android.internal.util.Objects;

import android.accounts.Account;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

/**
 * Wrapper for an account that includes a data set (which may be null).
 */
public class AccountWithDataSet extends Account {

    public final String dataSet;
    private final AccountTypeWithDataSet mAccountTypeWithDataSet;

    private static final String[] ID_PROJECTION = new String[] {BaseColumns._ID};
    private static final Uri RAW_CONTACTS_URI_LIMIT_1 = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "1").build();


    public AccountWithDataSet(String name, String type, String dataSet) {
        super(name, type);
        this.dataSet = dataSet;
        mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
    }

    public AccountWithDataSet(Parcel in, String dataSet) {
        super(in);
        this.dataSet = dataSet;
        mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
    }

    public AccountTypeWithDataSet getAccountTypeWithDataSet() {
        return mAccountTypeWithDataSet;
    }

    /**
     * Return {@code true} if this account has any contacts in the database.
     * Touches DB.  Don't use in the UI thread.
     */
    public boolean hasData(Context context) {
        final String BASE_SELECTION =
                RawContacts.ACCOUNT_TYPE + " = ?" + " AND " + RawContacts.ACCOUNT_NAME + " = ?";
        final String selection;
        final String[] args;
        if (TextUtils.isEmpty(dataSet)) {
            selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " IS NULL";
            args = new String[] {type, name};
        } else {
            selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " = ?";
            args = new String[] {type, name, dataSet};
        }

        final Cursor c = context.getContentResolver().query(RAW_CONTACTS_URI_LIMIT_1,
                ID_PROJECTION, selection, args, null);
        if (c == null) return false;
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof AccountWithDataSet) && super.equals(o)
                && Objects.equal(((AccountWithDataSet) o).dataSet, dataSet);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode()
                + (dataSet == null ? 0 : dataSet.hashCode());
    }

    @Override
    public String toString() {
        return "AccountWithDataSet {name=" + name + ", type=" + type + ", dataSet=" + dataSet + "}";
    }
}
