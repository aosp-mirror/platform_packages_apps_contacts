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
import android.os.Parcel;

/**
 * Wrapper for an account that includes a data set (which may be null).
 */
public class AccountWithDataSet extends Account {

    public final String dataSet;

    public AccountWithDataSet(String name, String type, String dataSet) {
        super(name, type);
        this.dataSet = dataSet;
    }

    public AccountWithDataSet(Parcel in, String dataSet) {
        super(in);
        this.dataSet = dataSet;
    }

    public String getAccountTypeWithDataSet() {
        return dataSet == null ? type : AccountType.getAccountTypeAndDataSet(type, dataSet);
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
