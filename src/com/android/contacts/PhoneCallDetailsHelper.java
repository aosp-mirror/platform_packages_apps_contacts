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

import com.android.contacts.calllog.CallTypeHelper;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.format.FormatUtils;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
    /** The maximum number of icons will be shown to represent the call types in a group. */
    private static final int MAX_CALL_TYPE_ICONS = 3;

    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;
    // Helper classes.
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneNumberHelper mPhoneNumberHelper;

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Resources resources, CallTypeHelper callTypeHelper,
            PhoneNumberHelper phoneNumberHelper) {
        mResources = resources;
        mCallTypeHelper = callTypeHelper;
        mPhoneNumberHelper = phoneNumberHelper;
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details,
            boolean useIcons, boolean isHighlighted) {
        if (useIcons) {
            views.callTypeIcons.clear();
            int count = details.callTypes.length;
            for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
                views.callTypeIcons.add(details.callTypes[index]);
            }
            views.callTypeIcons.setVisibility(View.VISIBLE);
            if (count > MAX_CALL_TYPE_ICONS) {
                views.callTypeText.setVisibility(View.VISIBLE);
                views.callTypeSeparator.setVisibility(View.VISIBLE);
                views.callTypeText.setText(
                        mResources.getString(R.string.call_log_item_count, count));
            } else {
                views.callTypeText.setVisibility(View.GONE);
                views.callTypeSeparator.setVisibility(View.GONE);
            }
        } else {
            // Use the name of the first call type.
            // TODO: We should update this to handle the text for multiple calls as well.
            int callType = details.callTypes[0];
            views.callTypeText.setText(
                    isHighlighted ? mCallTypeHelper.getHighlightedCallTypeText(callType)
                            : mCallTypeHelper.getCallTypeText(callType));
            views.callTypeIcons.clear();

            views.callTypeText.setVisibility(View.VISIBLE);
            views.callTypeSeparator.setVisibility(View.VISIBLE);
            views.callTypeIcons.setVisibility(View.GONE);
        }

        CharSequence shortDateText =
            DateUtils.getRelativeTimeSpanString(details.date,
                    getCurrentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberUtils.isUriNumber(details.number.toString())) {
            numberFormattedLabel = Phone.getTypeLabel(mResources, details.numberType,
                    details.numberLabel);
        }

        final CharSequence nameText;
        final CharSequence numberText;
        final CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.number, details.formattedNumber);
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
            String geocode = mPhoneNumberHelper.getGeocodeForNumber(
                    details.number.toString(), details.countryIso);
            if (TextUtils.isEmpty(geocode)) {
                numberText = mResources.getString(R.string.call_log_empty_gecode);
            } else {
                numberText = geocode;
            }
        } else {
            nameText = details.name;
            if (numberFormattedLabel != null) {
                numberText = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                        numberFormattedLabel + " " + displayNumber, 0,
                        numberFormattedLabel.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                numberText = displayNumber;
            }
        }

        views.dateView.setText(shortDateText);
        views.nameView.setText(nameText);
        views.numberView.setText(numberText);
    }

    /** Sets the name in the text view for the given phone call. */
    public void setPhoneCallName(TextView nameView, PhoneCallDetails details) {
        final CharSequence nameText;
        final CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.number, details.formattedNumber);
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
        } else {
            nameText = details.name;
        }

        nameView.setText(nameText);
    }

    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }
}
