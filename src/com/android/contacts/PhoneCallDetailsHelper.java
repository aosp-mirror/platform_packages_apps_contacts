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
            boolean useIcons, boolean isHighlighted, boolean nameOnly) {
        if (useIcons) {
            views.callTypeIcons.removeAllViews();
            int count = details.callTypes.length;
            for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
                mCallTypeHelper.inflateCallTypeIcon(details.callTypes[index], views.callTypeIcons);
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
            String callTypeName;
            // Use the name of the first call type.
            // TODO: We should update this to handle the text for multiple calls as well.
            int callType = details.callTypes[0];
            views.callTypeText.setText(
                    isHighlighted ? mCallTypeHelper.getHighlightedCallTypeText(callType)
                            : mCallTypeHelper.getCallTypeText(callType));
            views.callTypeIcons.removeAllViews();

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
            numberText = mPhoneNumberHelper.getGeocodeForNumber(
                    details.number.toString(), details.countryIso);
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
        views.dateView.setVisibility(View.VISIBLE);
        views.nameView.setText(nameText);
        views.nameView.setVisibility(View.VISIBLE);
        // Do not show the number if it is not available. This happens if we have only the number,
        // in which case the number is shown in the name field instead.
        if (!TextUtils.isEmpty(numberText)) {
            views.numberView.setText(numberText);
            views.numberView.setVisibility(View.VISIBLE);
        } else {
            views.numberView.setVisibility(View.GONE);
        }

        // Hide the rest if not visible.
        views.callTypeView.setVisibility(nameOnly ? View.GONE : View.VISIBLE);
        views.numberView.setVisibility(nameOnly ? View.GONE : View.VISIBLE);
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
