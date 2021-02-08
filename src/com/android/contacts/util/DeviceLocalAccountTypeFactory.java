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
package com.android.contacts.util;

import android.content.Context;
import android.provider.ContactsContract;

import androidx.annotation.IntDef;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.DeviceLocalAccountType;
import com.android.contacts.model.account.SimAccountType;

import java.lang.annotation.Retention;
import java.util.Objects;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Reports whether a value from RawContacts.ACCOUNT_TYPE should be considered a "Device"
 * account
 */
public interface DeviceLocalAccountTypeFactory {

    @Retention(SOURCE)
    @IntDef({TYPE_OTHER, TYPE_DEVICE, TYPE_SIM})
    @interface LocalAccountType {}
    static final int TYPE_OTHER = 0;
    static final int TYPE_DEVICE = 1;
    static final int TYPE_SIM = 2;

    @DeviceLocalAccountTypeFactory.LocalAccountType int classifyAccount(String accountType);

    AccountType getAccountType(String accountType);

    class Util {
        private Util() { }

        public static boolean isLocalAccountType(@LocalAccountType int type) {
            return type == TYPE_SIM || type == TYPE_DEVICE;
        }

        public static boolean isLocalAccountType(DeviceLocalAccountTypeFactory factory,
                String type) {

            return isLocalAccountType(factory.classifyAccount(type));
        }
    }

    class Default implements DeviceLocalAccountTypeFactory {
        private Context mContext;

        public Default(Context context) {
            mContext = context;
        }

        @Override
        public int classifyAccount(String accountType) {
            for (ContactsContract.SimAccount simAccount :
                    ContactsContract.SimContacts.getSimAccounts(
                            mContext.getContentResolver())) {
                if (accountType != null && Objects.equals(accountType,
                        simAccount.getAccountType())) {
                    return TYPE_SIM;
                }
            }
            return accountType == null ||
                    Objects.equals(AccountWithDataSet.getLocalAccount(mContext).type, accountType)
                    ? TYPE_DEVICE : TYPE_OTHER;
        }

        @Override
        public AccountType getAccountType(String accountType) {
            if (classifyAccount(accountType) == TYPE_SIM) {
                return new SimAccountType(mContext);
            }
            if (accountType != null && !Objects.equals(
                    AccountWithDataSet.getLocalAccount(mContext).type, accountType)) {
                throw new IllegalArgumentException(accountType + " is not a device account type.");
            }
            return new DeviceLocalAccountType(mContext, true);
        }
    }
}
