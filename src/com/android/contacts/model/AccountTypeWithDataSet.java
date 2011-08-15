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

import com.google.common.base.Objects;

import android.text.TextUtils;


/**
 * Encapsulates an "account type" string and a "data set" string.
 */
public class AccountTypeWithDataSet {
    /** account type will never be null. */
    public final String accountType;

    /** dataSet may be null, but never be "". */
    public final String dataSet;

    private AccountTypeWithDataSet(String accountType, String dataSet) {
        if (accountType == null) throw new NullPointerException();

        this.accountType = accountType;
        this.dataSet = TextUtils.isEmpty(dataSet) ? null : dataSet;
    }

    public static AccountTypeWithDataSet get(String accountType, String dataSet) {
        return new AccountTypeWithDataSet(accountType, dataSet);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccountTypeWithDataSet)) return false;

        AccountTypeWithDataSet other = (AccountTypeWithDataSet) o;
        return Objects.equal(accountType, other.accountType)
                && Objects.equal(dataSet, other.dataSet);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accountType) ^ (dataSet == null ? 0 : Objects.hashCode(dataSet));
    }

    @Override
    public String toString() {
        return "[" + accountType + "/" + dataSet + "]";
    }
}
