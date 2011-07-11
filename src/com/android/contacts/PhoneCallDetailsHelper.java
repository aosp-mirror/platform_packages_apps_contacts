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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
    private final Context mContext;
    private final Resources mResources;
    private final String mVoicemailNumber;
    /** Icon for incoming calls. */
    private final Drawable mIncomingDrawable;
    /** Icon for outgoing calls. */
    private final Drawable mOutgoingDrawable;
    /** Icon for missed calls. */
    private final Drawable mMissedDrawable;
    /** Icon for voicemails. */
    private final Drawable mVoicemailDrawable;
    /** Name used to identify incoming calls. */
    private final String mIncomingName;
    /** Name used to identify outgoing calls. */
    private final String mOutgoingName;
    /** Name used to identify missed calls. */
    private final String mMissedName;
    /** Name used to identify voicemail calls. */
    private final String mVoicemailName;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Context context, Resources resources, String voicemailNumber,
            Drawable incomingDrawable, Drawable outgoingDrawable, Drawable missedDrawable,
            Drawable voicemailDrawable) {
        mContext = context;
        mResources = resources;
        mVoicemailNumber = voicemailNumber;
        mIncomingDrawable = incomingDrawable;
        mOutgoingDrawable = outgoingDrawable;
        mMissedDrawable = missedDrawable;
        mVoicemailDrawable = voicemailDrawable;
        // Cache these values so that we do not need to look them up each time.
        mIncomingName = mResources.getString(R.string.type_incoming);
        mOutgoingName = mResources.getString(R.string.type_outgoing);
        mMissedName = mResources.getString(R.string.type_missed);
        mVoicemailName = mResources.getString(R.string.type_voicemail);
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details,
            boolean useIcons) {
        if (useIcons) {
            views.callTypeIcons.removeAllViews();
            int count = details.callTypes.length;
            for (int callType : details.callTypes) {
                ImageView callTypeImage = new ImageView(mContext);
                callTypeImage.setImageDrawable(getCallTypeDrawable(callType));
                views.callTypeIcons.addView(callTypeImage);
            }
            views.callTypeIcons.setVisibility(View.VISIBLE);
            views.callTypeText.setVisibility(View.GONE);
            views.callTypeSeparator.setVisibility(View.GONE);
        } else {
            String callTypeName;
            // Use the name of the first call type.
            // TODO: We should update this to handle the text for multiple calls as well.
            int callType = details.callTypes[0];
            views.callTypeText.setText(getCallTypeText(callType));
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
        if (TextUtils.isEmpty(details.name)) {
            nameText = getDisplayNumber(details.number, details.formattedNumber);
            numberText = "";
        } else {
            nameText = details.name;
            CharSequence displayNumber = getDisplayNumber(details.number, details.formattedNumber);
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
    }

    /** Returns the text used to represent the given call type. */
    private String getCallTypeText(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return mIncomingName;

            case Calls.OUTGOING_TYPE:
                return mOutgoingName;

            case Calls.MISSED_TYPE:
                return mMissedName;

            case Calls.VOICEMAIL_TYPE:
                return mVoicemailName;

            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }

    /** Returns the drawable of the icon associated with the given call type. */
    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return mIncomingDrawable;

            case Calls.OUTGOING_TYPE:
                return mOutgoingDrawable;

            case Calls.MISSED_TYPE:
                return mMissedDrawable;

            case Calls.VOICEMAIL_TYPE:
                return mVoicemailDrawable;

            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }

    private CharSequence getDisplayNumber(CharSequence number, CharSequence formattedNumber) {
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
        if (TextUtils.isEmpty(formattedNumber)) {
            return number;
        } else {
            return formattedNumber;
        }
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
