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

    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param phoneNumberHelper used to process phone number
     */
    public CallLogListItemHelper(PhoneCallDetailsHelper phoneCallDetailsHelper,
            PhoneNumberHelper phoneNumberHelper) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mPhoneNumberHelper= phoneNumberHelper;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     * @param isHighlighted whether to use the highlight text for the call
     */
    public void setPhoneCallDetails(CallLogListItemViews views, PhoneCallDetails details,
            boolean isHighlighted) {
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details,
                isHighlighted);
        boolean canCall = mPhoneNumberHelper.canPlaceCallsTo(details.number);
        boolean canPlay = details.callTypes[0] == Calls.VOICEMAIL_TYPE;

        if (canPlay) {
            // Playback action takes preference.
            views.callView.setVisibility(View.GONE);
            views.playView.setVisibility(View.VISIBLE);
            views.unheardView.setVisibility(isHighlighted ? View.VISIBLE : View.GONE);
            views.dividerView.setVisibility(View.VISIBLE);
        } else if (canCall) {
            // Call is the main action.
            views.callView.setVisibility(View.VISIBLE);
            views.playView.setVisibility(View.GONE);
            views.unheardView.setVisibility(View.GONE);
            views.dividerView.setVisibility(View.VISIBLE);
        } else {
            // No action available.
            views.callView.setVisibility(View.GONE);
            views.playView.setVisibility(View.GONE);
            views.unheardView.setVisibility(View.GONE);
            views.dividerView.setVisibility(View.GONE);
        }
    }
}
