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

import android.content.res.Resources;
import android.provider.CallLog.Calls;

import com.android.contacts.R;

/**
 * Helper class to perform operations related to call types.
 */
public class CallTypeHelper {
    /** Name used to identify incoming calls. */
    private final CharSequence mIncomingName;
    /** Name used to identify outgoing calls. */
    private final CharSequence mOutgoingName;
    /** Name used to identify missed calls. */
    private final CharSequence mMissedName;
    /** Name used to identify voicemail calls. */
    private final CharSequence mVoicemailName;
    /** Color used to identify new missed calls. */
    private final int mNewMissedColor;
    /** Color used to identify new voicemail calls. */
    private final int mNewVoicemailColor;

    public CallTypeHelper(Resources resources) {
        // Cache these values so that we do not need to look them up each time.
        mIncomingName = resources.getString(R.string.type_incoming);
        mOutgoingName = resources.getString(R.string.type_outgoing);
        mMissedName = resources.getString(R.string.type_missed);
        mVoicemailName = resources.getString(R.string.type_voicemail);
        mNewMissedColor = resources.getColor(R.color.call_log_missed_call_highlight_color);
        mNewVoicemailColor = resources.getColor(R.color.call_log_voicemail_highlight_color);
    }

    /** Returns the text used to represent the given call type. */
    public CharSequence getCallTypeText(int callType) {
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

    /** Returns the color used to highlight the given call type, null if not highlight is needed. */
    public Integer getHighlightedColor(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                // New incoming calls are not highlighted.
                return null;

            case Calls.OUTGOING_TYPE:
                // New outgoing calls are not highlighted.
                return null;

            case Calls.MISSED_TYPE:
                return mNewMissedColor;

            case Calls.VOICEMAIL_TYPE:
                return mNewVoicemailColor;

            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }
}
