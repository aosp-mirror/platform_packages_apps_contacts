/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.contacts.common;

import android.telecomm.PhoneAccount;
import android.telephony.TelephonyManager;

import java.util.List;

/**
 * To pass current account information between activities/fragments.
 */
public class PhoneAccountManager {
    private PhoneAccount mCurrentAccount = null;
    private TelephonyManager mTelephonyManager;

    public PhoneAccountManager(TelephonyManager telephonyManager, PhoneAccount account) {
        mTelephonyManager = telephonyManager;
        mCurrentAccount = account;
    }

    public PhoneAccountManager(TelephonyManager telephonyManager) {
        mTelephonyManager = telephonyManager;
    }

    public PhoneAccount getCurrentAccount() {
        return mCurrentAccount;
    }

    public void setCurrentAccount(PhoneAccount account) {
        mCurrentAccount = account;
    }

    public List<PhoneAccount> getAccounts() {
        return mTelephonyManager.getAccounts();
    }
}
