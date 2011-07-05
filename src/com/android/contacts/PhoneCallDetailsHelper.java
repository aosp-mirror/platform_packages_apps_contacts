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
import com.android.internal.telephony.CallerInfo;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
    private final Resources mResources;
    private final String mVoicemailNumber;
    private final String mTypeIncomingText;
    private final String mTypeOutgoingText;
    private final String mTypeMissedText;

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Resources resources, String voicemailNumber) {
        mResources = resources;
        mVoicemailNumber = voicemailNumber;
        mTypeIncomingText = mResources.getString(R.string.type_incoming);
        mTypeOutgoingText = mResources.getString(R.string.type_outgoing);
        mTypeMissedText = mResources.getString(R.string.type_missed);
    }

    /**
     * Fills the call details views with content.
     *
     * @param date the date of the call, in milliseconds since the epoch
     * @param callType the type of call, as defined in the call log table
     * @param name the name of the contact, if available
     * @param number the number of the other party involved in the call
     * @param numberType the type of phone, e.g., {@link Phone#TYPE_HOME}, 0 if not available
     * @param numberLabel the custom label associated with the phone number in the contact
     */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, long date,
            int callType, CharSequence name, CharSequence number, int numberType,
            CharSequence numberLabel) {
        CharSequence callTypeText = "";
        switch (callType) {
            case Calls.INCOMING_TYPE:
                callTypeText = mTypeIncomingText;
                break;

            case Calls.OUTGOING_TYPE:
                callTypeText = mTypeOutgoingText;
                break;

            case Calls.MISSED_TYPE:
                callTypeText = mTypeMissedText;
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
            numberFormattedLabel = Phone.getTypeLabel(mResources, numberType, numberLabel);
        }

        CharSequence nameText;
        CharSequence numberText;
        if (TextUtils.isEmpty(name)) {
            nameText = getDisplayNumber(number);
            numberText = "";
        } else {
            nameText = name;
            numberText = getDisplayNumber(number);
            if (callType != 0 && numberFormattedLabel != null) {
                numberText = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                        numberFormattedLabel + " " + number, 0,
                        numberFormattedLabel.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        views.mCallTypeAndDateView.setText(callTypeAndDateText);
        views.mCallTypeAndDateView.setVisibility(View.VISIBLE);
        views.mNameView.setText(nameText);
        views.mNameView.setVisibility(View.VISIBLE);
        // Do not show the number if it is not available. This happens if we have only the number,
        // in which case the number is shown in the name field instead.
        if (!TextUtils.isEmpty(numberText)) {
            views.mNumberView.setText(numberText);
            views.mNumberView.setVisibility(View.VISIBLE);
        } else {
            views.mNumberView.setVisibility(View.GONE);
        }
    }

    private CharSequence getDisplayNumber(CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            return mResources.getString(R.string.unknown);
        }
        if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            return mResources.getString(R.string.private_num);
        }
        if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            return mResources.getString(R.string.payphone);
        }
        if (PhoneNumberUtils.extractNetworkPortion(number.toString()).equals(mVoicemailNumber)) {
            return mResources.getString(R.string.voicemail);
        }
        return number;
    }
}
