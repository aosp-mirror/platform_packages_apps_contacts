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

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.contacts.common.database.SimContactDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds data for a SIM card in the device.
 */
public class SimCard {

    private static final String TAG = "SimCard";

    public static final int NO_SUBSCRIPTION_ID = -1;

    // This state is created from the info we get from the system
    private final String mSimId;
    private final int mSubscriptionId;
    private final CharSequence mCarrierName;
    private final CharSequence mDisplayName;
    private final String mPhoneNumber;
    private final String mCountryCode;

    // This is our own state that we associate with SIM cards. Currently these are only used
    // in the GoogleContacts app.
    // Note: these are logically immutable but are not final to reduce required constructor
    // parameters
    private boolean mDismissed = false;
    private boolean mImported = false;

    private List<SimContact> mContacts;

    public SimCard(SimCard other) {
        mSimId = other.mSimId;
        mSubscriptionId = other.mSubscriptionId;
        mCarrierName = other.mCarrierName;
        mDisplayName = other.mDisplayName;
        mPhoneNumber = other.mPhoneNumber;
        mCountryCode = other.mCountryCode;
        mDismissed = other.mDismissed;
        mImported = other.mImported;
        mContacts = other.mContacts;
    }

    public SimCard(String simId, int subscriptionId, CharSequence carrierName,
            CharSequence displayName, String phoneNumber, String countryCode) {
        mSimId = simId;
        mSubscriptionId = subscriptionId;
        mCarrierName = carrierName;
        mDisplayName = displayName;
        mPhoneNumber = phoneNumber;
        mCountryCode = countryCode;
    }

    public SimCard(String simId, CharSequence carrierName,
            CharSequence displayName, String phoneNumber, String countryCode) {
        this(simId, NO_SUBSCRIPTION_ID, carrierName, displayName, phoneNumber, countryCode);
    }

    public String getSimId() {
        return mSimId;
    }

    public int getSubscriptionId() {
        return mSubscriptionId;
    }

    public boolean hasContacts() {
        if (mContacts == null) {
            throw new IllegalStateException("Contacts not loaded.");
        }
        return !mContacts.isEmpty();
    }

    public int getContactCount() {
        if (mContacts == null) {
            throw new IllegalStateException("Contacts not loaded.");
        }
        return mContacts.size();
    }

    public boolean isDismissed() {
        return mDismissed;
    }

    public boolean isImported() {
        return mImported;
    }

    public boolean isImportable() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "isImportable: isDismissed? " + isDismissed() +
                    " isImported? " + isImported() + " contacts=" + mContacts);
        }
        return !isDismissed() && !isImported() && hasContacts();
    }

    public List<SimContact> getContacts() {
        if (mContacts == null) {
            throw new IllegalStateException("Contacts not loaded.");
        }
        return mContacts;
    }

    public synchronized void loadContacts(SimContactDao dao) {
        if (mSubscriptionId == NO_SUBSCRIPTION_ID) {
            // Load from the default SIM card.
            mContacts = dao.loadSimContacts();
        } else {
            mContacts = dao.loadSimContacts(mSubscriptionId);
        }
        if (mContacts == null) {
            mContacts = Collections.emptyList();
        }
    }

    public SimCard withImportAndDismissStates(boolean imported, boolean dismissed) {
        SimCard copy = new SimCard(this);
        copy.mImported = imported;
        copy.mDismissed = dismissed;
        return copy;
    }

    public SimCard withImportedState(boolean imported) {
        return withImportAndDismissStates(imported, mDismissed);
    }

    public SimCard withDismissedState(boolean dismissed) {
        return withImportAndDismissStates(dismissed, mImported);
    }

    public SimCard withContacts(SimContact... contacts) {
        final SimCard copy = new SimCard(this);
        copy.mContacts = Arrays.asList(contacts);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimCard simCard = (SimCard) o;

        return mSubscriptionId == simCard.mSubscriptionId && mDismissed == simCard.mDismissed &&
                mImported == simCard.mImported && Objects.equals(mSimId, simCard.mSimId) &&
                Objects.equals(mPhoneNumber, simCard.mPhoneNumber) &&
                Objects.equals(mCountryCode, simCard.mCountryCode);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mSimId, mPhoneNumber, mCountryCode);
        result = 31 * result + mSubscriptionId;
        result = 31 * result + (mDismissed ? 1 : 0);
        result = 31 * result + (mImported ? 1 : 0);
        return result;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static SimCard create(SubscriptionInfo info) {
        return new SimCard(info.getIccId(), info.getSubscriptionId(),
                info.getCarrierName(), info.getDisplayName(), info.getNumber(),
                info.getCountryIso());
    }

    public static SimCard create(TelephonyManager telephony) {
        SimCard simCard = new SimCard(telephony.getSimSerialNumber(),
                telephony.getNetworkOperatorName(), "SIM", telephony.getLine1Number(),
                telephony.getNetworkCountryIso());
        return simCard;
    }
}
