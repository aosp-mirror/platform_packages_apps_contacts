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

import com.android.contacts.format.FormatUtils;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

/**
 * Encapsulates the views that are used to display the details of a phone call in the call log.
 */
public final class PhoneCallDetailsViews {
    public final TextView mNameView;
    public final TextView mCallTypeAndDateView;
    public final TextView mNumberView;

    /**
     * Creates a new instance and caches its views.
     *
     * @param view the view which contains the elements to fill
     */
    public PhoneCallDetailsViews(View view) {
        mNameView = (TextView) view.findViewById(R.id.name);
        mCallTypeAndDateView = (TextView) view.findViewById(R.id.call_type);
        mNumberView = (TextView) view.findViewById(R.id.number);
    }

    /**
     * Fills the call details views with content.
     *
     * @param resources used to look up strings
     * @param date the date of the call, in milliseconds since the epoch
     * @param callType the type of call, as defined in the call log table
     * @param name the name of the contact, if available
     * @param number the number of the other party involved in the call
     * @param numberType the type of phone, e.g., {@link Phone#TYPE_HOME}, 0 if not available
     * @param numberLabel the custom label associated with the phone number in the contact
     */
    public void setPhoneCallDetails(Resources resources, long date, int callType,
            CharSequence name, CharSequence number, int numberType, CharSequence numberLabel) {
        CharSequence callTypeText = "";
        switch (callType) {
            case Calls.INCOMING_TYPE:
                callTypeText = resources.getString(R.string.type_incoming);
                break;

            case Calls.OUTGOING_TYPE:
                callTypeText = resources.getString(R.string.type_outgoing);
                break;

            case Calls.MISSED_TYPE:
                callTypeText = resources.getString(R.string.type_missed);
                break;
        }

        CharSequence shortDateText =
            DateUtils.getRelativeTimeSpanString(date,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);

        CharSequence callTypeAndDateText = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                callTypeText + " " + shortDateText, 0, callTypeText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(number) && !PhoneNumberUtils.isUriNumber(number.toString())) {
            numberFormattedLabel = Phone.getTypeLabel(resources, numberType, numberLabel);
        }

        CharSequence nameText;
        CharSequence numberText;
        if (TextUtils.isEmpty(name)) {
            nameText = number;
            numberText = "";
        } else {
            nameText = name;
            numberText = number;
            if (callType != 0 && numberFormattedLabel != null) {
                numberText = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                        numberFormattedLabel + " " + number, 0,
                        numberFormattedLabel.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        mCallTypeAndDateView.setText(callTypeAndDateText);
        mCallTypeAndDateView.setVisibility(View.VISIBLE);
        mNameView.setText(nameText);
        mNameView.setVisibility(View.VISIBLE);
        // Do not show the number if it is not available. This happens if we have only the number,
        // in which case the number is shown in the name field instead.
        if (!TextUtils.isEmpty(numberText)) {
            mNumberView.setText(numberText);
            mNumberView.setVisibility(View.VISIBLE);
        } else {
            mNumberView.setVisibility(View.GONE);
        }
    }
}