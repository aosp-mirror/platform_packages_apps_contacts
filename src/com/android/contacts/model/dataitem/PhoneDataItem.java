/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;

import com.android.contacts.model.RawContact;

/**
 * Represents a phone data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.Phone}.
 */
public class PhoneDataItem extends DataItem {

    public static final String KEY_FORMATTED_PHONE_NUMBER = "formattedPhoneNumber";

    /* package */ PhoneDataItem(RawContact rawContact, ContentValues values) {
        super(rawContact, values);
    }

    public String getNumber() {
        return getContentValues().getAsString(Phone.NUMBER);
    }

    /**
     * Returns the normalized phone number in E164 format.
     */
    public String getNormalizedNumber() {
        return getContentValues().getAsString(Phone.NORMALIZED_NUMBER);
    }

    public String getFormattedPhoneNumber() {
        return getContentValues().getAsString(KEY_FORMATTED_PHONE_NUMBER);
    }

    /**
     * Values are Phone.TYPE_*
     */
    public int getType() {
        return getContentValues().getAsInteger(Phone.TYPE);
    }

    public String getLabel() {
        return getContentValues().getAsString(Phone.LABEL);
    }

    public void computeFormattedPhoneNumber(String defaultCountryIso) {
        final String phoneNumber = getNumber();
        if (phoneNumber != null) {
            final String formattedPhoneNumber = PhoneNumberUtils.formatNumber(phoneNumber,
                    getNormalizedNumber(), defaultCountryIso);
            getContentValues().put(KEY_FORMATTED_PHONE_NUMBER, formattedPhoneNumber);
        }
    }

    /**
     * Returns the formatted phone number (if already computed using {@link
     * #computeFormattedPhoneNumber}). Otherwise this method returns the unformatted phone number.
     */
    @Override
    public String buildDataStringForDisplay() {
        final String formatted = getFormattedPhoneNumber();
        if (formatted != null) {
            return formatted;
        } else {
            return getNumber();
        }
    }
}
