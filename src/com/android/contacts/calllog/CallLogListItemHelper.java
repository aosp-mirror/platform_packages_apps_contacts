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

import com.android.contacts.PhoneCallDetails;
import com.android.contacts.PhoneCallDetailsHelper;

import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.view.View;

/**
 * Helper class to fill in the views of a call log entry.
 */
/*package*/ class CallLogListItemHelper {
    /** Helper for populating the details of a phone call. */
    private final PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    /** Helper for handling phone numbers. */
    private final PhoneNumberHelper mPhoneNumberHelper;
    /** Icon for the call action. */
    private final Drawable mCallDrawable;
    /** Icon for the play action. */
    private final Drawable mPlayDrawable;

    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param callDrawable used to render the call button, for calling back a person
     * @param playDrawable used to render the play button, for playing a voicemail
     */
    public CallLogListItemHelper(PhoneCallDetailsHelper phoneCallDetailsHelper,
            PhoneNumberHelper phoneNumberHelper, Drawable callDrawable, Drawable playDrawable) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mPhoneNumberHelper= phoneNumberHelper;
        mCallDrawable = callDrawable;
        mPlayDrawable = playDrawable;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     * @param useIcons whether to use icons to show the type of the call
     * @param isHighlighted whether to use the highlight text for the call
     */
    public void setPhoneCallDetails(CallLogListItemViews views, PhoneCallDetails details,
            boolean useIcons, boolean isHighlighted) {
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details, useIcons,
                isHighlighted);
        if (views.callView != null) {
            // The type of icon, call or play, is determined by the first call in the group.
            views.callView.setImageDrawable(
                    details.callTypes[0] == Calls.VOICEMAIL_TYPE ? mPlayDrawable : mCallDrawable);
            views.callView.setVisibility(
                    mPhoneNumberHelper.canPlaceCallsTo(details.number)
                            ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
