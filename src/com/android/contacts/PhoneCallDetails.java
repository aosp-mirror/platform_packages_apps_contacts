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

package com.android.contacts;

import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;

/**
 * The details of a phone call to be shown in the UI.
 */
public class PhoneCallDetails {
    /** The number of the other party involved in the call. */
    public final CharSequence number;
    /** The formatted version of {@link #number}. */
    public final CharSequence formattedNumber;
    /** The type of call, as defined in the call log table, e.g., {@link Calls#INCOMING_TYPE}. */
    public final int callType;
    /** The date of the call, in milliseconds since the epoch. */
    public final long date;
    /** The name of the contact, or the empty string. */
    public final CharSequence name;
    /** The type of phone, e.g., {@link Phone#TYPE_HOME}, 0 if not available. */
    public final int numberType;
    /** The custom label associated with the phone number in the contact, or the empty string. */
    public final CharSequence numberLabel;

    /** Create the details for a call with a number not associated with a contact. */
    public PhoneCallDetails(CharSequence number, CharSequence formattedNumber, int callType,
            long date) {
        this(number, formattedNumber, callType, date, "", 0, "");
    }

    /** Create the details for a call with a number associated with a contact. */
    public PhoneCallDetails(CharSequence number, CharSequence formattedNumber, int callType,
            long date, CharSequence name, int numberType, CharSequence numberLabel) {
        this.number = number;
        this.formattedNumber = formattedNumber;
        this.callType = callType;
        this.date = date;
        this.name = name;
        this.numberType = numberType;
        this.numberLabel = numberLabel;
    }
}
