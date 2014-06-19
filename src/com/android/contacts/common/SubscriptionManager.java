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

import android.telecomm.Subscription;
import android.telephony.TelephonyManager;

import java.util.List;

/**
 * To pass current subscription information between activities/fragments.
 */
public class SubscriptionManager {
    private Subscription mCurrentSubscription = null;
    private TelephonyManager mTelephonyManager;

    public SubscriptionManager(TelephonyManager telephonyManager, Subscription subscription) {
        mTelephonyManager = telephonyManager;
        mCurrentSubscription = subscription;
    }

    public SubscriptionManager(TelephonyManager telephonyManager) {
        mTelephonyManager = telephonyManager;
    }

    public Subscription getCurrentSubscription() {
        return mCurrentSubscription;
    }

    public void setCurrentSubscription(Subscription subscription) {
        mCurrentSubscription = subscription;
    }

    public List<Subscription> getSubscriptions() {
        return mTelephonyManager.getSubscriptions();
    }
}
