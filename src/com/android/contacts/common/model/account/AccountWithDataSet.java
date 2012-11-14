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

package com.android.contacts.common.model.account;

import android.accounts.Account;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wrapper for an account that includes a data set (which may be null).
 */
public class AccountWithDataSet extends Account {
    private static final String STRINGIFY_SEPARATOR = "\u0001";
    private static final String ARRAY_STRINGIFY_SEPARATOR = "\u0002";

    private static final Pattern STRINGIFY_SEPARATOR_PAT =
            Pattern.compile(Pattern.quote(STRINGIFY_SEPARATOR));
    private static final Pattern ARRAY_STRINGIFY_SEPARATOR_PAT =
            Pattern.compile(Pattern.quote(ARRAY_STRINGIFY_SEPARATOR));

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

    public AccountWithDataSet(Parcel in) {
        super(in);
        this.dataSet = in.readString();
        mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(dataSet);
    }

    // For Parcelable
    public static final Creator<AccountWithDataSet> CREATOR = new Creator<AccountWithDataSet>() {
        public AccountWithDataSet createFromParcel(Parcel source) {
            return new AccountWithDataSet(source);
        }

        public AccountWithDataSet[] newArray(int size) {
            return new AccountWithDataSet[size];
        }
    };

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

    private static StringBuilder addStringified(StringBuilder sb, AccountWithDataSet account) {
        sb.append(account.name);
        sb.append(STRINGIFY_SEPARATOR);
        sb.append(account.type);
        sb.append(STRINGIFY_SEPARATOR);
        if (!TextUtils.isEmpty(account.dataSet)) sb.append(account.dataSet);

        return sb;
    }

    /**
     * Pack the instance into a string.
     */
    public String stringify() {
        return addStringified(new StringBuilder(), this).toString();
    }

    /**
     * Unpack a string created by {@link #stringify}.
     *
     * @throws IllegalArgumentException if it's an invalid string.
     */
    public static AccountWithDataSet unstringify(String s) {
        final String[] array = STRINGIFY_SEPARATOR_PAT.split(s, 3);
        if (array.length < 3) {
            throw new IllegalArgumentException("Invalid string " + s);
        }
        return new AccountWithDataSet(array[0], array[1],
                TextUtils.isEmpty(array[2]) ? null : array[2]);
    }

    /**
     * Pack a list of {@link AccountWithDataSet} into a string.
     */
    public static String stringifyList(List<AccountWithDataSet> accounts) {
        final StringBuilder sb = new StringBuilder();

        for (AccountWithDataSet account : accounts) {
            if (sb.length() > 0) {
                sb.append(ARRAY_STRINGIFY_SEPARATOR);
            }
            addStringified(sb, account);
        }

        return sb.toString();
    }

    /**
     * Unpack a list of {@link AccountWithDataSet} into a string.
     *
     * @throws IllegalArgumentException if it's an invalid string.
     */
    public static List<AccountWithDataSet> unstringifyList(String s) {
        final ArrayList<AccountWithDataSet> ret = Lists.newArrayList();
        if (TextUtils.isEmpty(s)) {
            return ret;
        }

        final String[] array = ARRAY_STRINGIFY_SEPARATOR_PAT.split(s);

        for (int i = 0; i < array.length; i++) {
            ret.add(unstringify(array[i]));
        }

        return ret;
    }
}
