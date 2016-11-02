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
package com.android.contacts.common.model;

import android.content.Context;

import com.android.contacts.common.Experiments;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contactsbind.ObjectFactory;
import com.android.contactsbind.experiments.Flags;

import java.util.Collections;
import java.util.List;

/**
 * Attempts to detect accounts for device contacts
 */
public abstract class DeviceLocalAccountLocator {

    /**
     * Returns a list of device local accounts
     */
    public abstract List<AccountWithDataSet> getDeviceLocalAccounts();

    // This works on Nexus and AOSP because the local device account is the null account but most
    // OEMs have a special account name and type for their device account.
    public static final DeviceLocalAccountLocator NULL_ONLY = new DeviceLocalAccountLocator() {
        @Override
        public List<AccountWithDataSet> getDeviceLocalAccounts() {
            return Collections.singletonList(AccountWithDataSet.getNullAccount());
        }
    };

    public static DeviceLocalAccountLocator create(Context context,
            List<AccountWithDataSet> knownAccounts) {
        if (Flags.getInstance().getBoolean(Experiments.OEM_CP2_DEVICE_ACCOUNT_DETECTION_ENABLED)) {
            return new Cp2DeviceLocalAccountLocator(context.getContentResolver(),
                    ObjectFactory.getDeviceLocalAccountTypeFactory(context), knownAccounts);
        }
        return NULL_ONLY;
    }
}
