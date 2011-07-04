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

package com.android.contacts.calllog;

import com.android.contacts.R;
import com.android.internal.telephony.CallerInfo;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;

/**
 * Helper class to fill in the views of a call log entry.
 */
/*package*/ class CallLogListItemHelper {
    /** The resources used to look up strings. */
    private final Resources mResources;
    /** The voicemail number. */
    private final String mVoiceMailNumber;
    /** Icon for incoming calls. */
    private final Drawable mDrawableIncoming;
    /** Icon for outgoing calls. */
    private final Drawable mDrawableOutgoing;
    /** Icon for missed calls. */
    private final Drawable mDrawableMissed;
    /** Icon for voicemails. */
    private final Drawable mDrawableVoicemail;
    /** Icon for the call action. */
    private final Drawable mDrawableCall;
    /** Icon for the play action. */
    private final Drawable mDrawablePlay;

    /**
     * Creates a new helper instance.
     *
     * @param resources used to look up strings
     * @param voicemailNumber the voicemail number, used to determine if a call was to voicemail
     * @param drawableIncoming the icon drawn besides an incoming call entry
     * @param drawableOutgoing the icon drawn besides an outgoing call entry
     * @param drawableMissed the icon drawn besides a missed call entry
     */
    public CallLogListItemHelper(Resources resources, String voicemailNumber,
            Drawable drawableIncoming, Drawable drawableOutgoing, Drawable drawableMissed,
            Drawable drawableVoicemail, Drawable drawableCall, Drawable drawablePlay) {
        mResources = resources;
        mVoiceMailNumber = voicemailNumber;
        mDrawableIncoming = drawableIncoming;
        mDrawableOutgoing = drawableOutgoing;
        mDrawableMissed = drawableMissed;
        mDrawableVoicemail = drawableVoicemail;
        mDrawableCall = drawableCall;
        mDrawablePlay = drawablePlay;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param name the name of the contact
     * @param number the number of the contact
     * @param numberType the type of the number as it appears in the contact, e.g.,
     *        {@link Phone#TYPE_HOME}
     * @param label the label of the number, only used if numberType is {@link Phone#TYPE_CUSTOM}
     * @param formattedNumber the formatted version of the number above
     */
    public void setContactNameLabelAndNumber(CallLogListItemViews views, String name, String number,
            int numberType, String label, String formattedNumber) {
        views.line1View.setText(name);
        views.labelView.setVisibility(View.VISIBLE);

        // "type" and "label" are currently unused for SIP addresses.
        CharSequence numberLabel = null;
        if (!PhoneNumberUtils.isUriNumber(number)) {
            numberLabel = Phone.getTypeLabel(mResources, numberType, label);
        }
        views.numberView.setVisibility(View.VISIBLE);
        views.numberView.setText(formattedNumber);
        if (!TextUtils.isEmpty(numberLabel)) {
            views.labelView.setText(numberLabel);
            views.labelView.setVisibility(View.VISIBLE);

            // Zero out the numberView's left margin (see below)
            ViewGroup.MarginLayoutParams numberLP =
                    (ViewGroup.MarginLayoutParams) views.numberView.getLayoutParams();
            numberLP.leftMargin = 0;
            views.numberView.setLayoutParams(numberLP);
        } else {
            // There's nothing to display in views.labelView, so hide it.
            // We can't set it to View.GONE, since it's the anchor for
            // numberView in the RelativeLayout, so make it INVISIBLE.
            //   Also, we need to manually *subtract* some left margin from
            // numberView to compensate for the right margin built in to
            // labelView (otherwise the number will be indented by a very
            // slight amount).
            //   TODO: a cleaner fix would be to contain both the label and
            // number inside a LinearLayout, and then set labelView *and*
            // its padding to GONE when there's no label to display.
            views.labelView.setText(null);
            views.labelView.setVisibility(View.INVISIBLE);

            ViewGroup.MarginLayoutParams labelLP =
                    (ViewGroup.MarginLayoutParams) views.labelView.getLayoutParams();
            ViewGroup.MarginLayoutParams numberLP =
                    (ViewGroup.MarginLayoutParams) views.numberView.getLayoutParams();
            // Equivalent to setting android:layout_marginLeft in XML
            numberLP.leftMargin = -labelLP.rightMargin;
            views.numberView.setLayoutParams(numberLP);
        }
    }

    /**
     * Sets the number in a call log entry.
     * <p>
     * To be used if we do not have a contact with this number.
     *
     * @param views the views to populate
     * @param number the number of the contact
     * @param formattedNumber the formatted version of the number above
     */
    public void setContactNumberOnly(final CallLogListItemViews views, String number,
            String formattedNumber) {
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = mResources.getString(R.string.unknown);
            if (views.callView != null) {
                views.callView.setVisibility(View.INVISIBLE);
            }
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = mResources.getString(R.string.private_num);
            if (views.callView != null) {
                views.callView.setVisibility(View.INVISIBLE);
            }
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = mResources.getString(R.string.payphone);
            if (views.callView != null) {
                views.callView.setVisibility(View.INVISIBLE);
            }
        } else if (PhoneNumberUtils.extractNetworkPortion(number)
                        .equals(mVoiceMailNumber)) {
            number = mResources.getString(R.string.voicemail);
        } else {
            // Just a phone number, so use the formatted version of the number.
            number = formattedNumber;
        }

        views.line1View.setText(number);
        views.numberView.setVisibility(View.GONE);
        views.labelView.setVisibility(View.GONE);
    }

    /**
     * Sets the date in the views.
     *
     * @param views the views to populate
     * @param date the date of the call log entry
     * @param now the current time relative to which the date should be formatted
     */
    public void setDate(final CallLogListItemViews views, long date, long now) {
        views.dateView.setText(
                DateUtils.getRelativeTimeSpanString(
                        date, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
    }

    /**
     * Sets the type of the call in the views.
     *
     * @param views the views to populate
     * @param type the type of call log entry, e.g., {@link Calls#INCOMING_TYPE}
     */
    public void setCallType(final CallLogListItemViews views, int type) {
        if (views.iconView != null) {
            // Set the call type icon.
            Drawable drawable = null;
            switch (type) {
                case Calls.INCOMING_TYPE:
                    drawable = mDrawableIncoming;
                    break;

                case Calls.OUTGOING_TYPE:
                    drawable = mDrawableOutgoing;
                    break;

                case Calls.MISSED_TYPE:
                    drawable = mDrawableMissed;
                    break;

                case Calls.VOICEMAIL_TYPE:
                    drawable = mDrawableVoicemail;
                    break;

                default:
                    throw new IllegalArgumentException("invalid call type: " + type);
            }
            views.iconView.setImageDrawable(drawable);
        }
        if (views.callView != null) {
            // Set the action icon.
            Drawable drawable = null;
            switch (type) {
                case Calls.INCOMING_TYPE:
                case Calls.OUTGOING_TYPE:
                case Calls.MISSED_TYPE:
                    drawable = mDrawableCall;
                    break;

                case Calls.VOICEMAIL_TYPE:
                    drawable = mDrawablePlay;
                    break;

                default:
                    throw new IllegalArgumentException("invalid call type: " + type);
            }
            views.callView.setImageDrawable(drawable);
        }
    }
}
