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

import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

/**
 * Helper class to perform operations related to call types.
 */
public class CallTypeHelper {
    /** Icon for incoming calls. */
    private final Drawable mIncomingDrawable;
    /** Icon for outgoing calls. */
    private final Drawable mOutgoingDrawable;
    /** Icon for missed calls. */
    private final Drawable mMissedDrawable;
    /** Icon for voicemails. */
    private final Drawable mVoicemailDrawable;
    /** Name used to identify incoming calls. */
    private final CharSequence mIncomingName;
    /** Name used to identify outgoing calls. */
    private final CharSequence mOutgoingName;
    /** Name used to identify missed calls. */
    private final CharSequence mMissedName;
    /** Name used to identify voicemail calls. */
    private final CharSequence mVoicemailName;
    /** Name used to identify new missed calls. */
    private final CharSequence mNewMissedName;
    /** Name used to identify new voicemail calls. */
    private final CharSequence mNewVoicemailName;

    public CallTypeHelper(Resources resources, Drawable incomingDrawable, Drawable outgoingDrawable,
            Drawable missedDrawable, Drawable voicemailDrawable) {
        mIncomingDrawable = incomingDrawable;
        mOutgoingDrawable = outgoingDrawable;
        mMissedDrawable = missedDrawable;
        mVoicemailDrawable = voicemailDrawable;
        // Cache these values so that we do not need to look them up each time.
        mIncomingName = resources.getString(R.string.type_incoming);
        mOutgoingName = resources.getString(R.string.type_outgoing);
        mMissedName = resources.getString(R.string.type_missed);
        mVoicemailName = resources.getString(R.string.type_voicemail);
        mNewMissedName = addBoldAndColor(mMissedName,
                resources.getColor(R.color.call_log_missed_call_highlight_color));
        mNewVoicemailName = addBoldAndColor(mVoicemailName,
                resources.getColor(R.color.call_log_voicemail_highlight_color));
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

    /** Returns the text used to represent the given call type. */
    public CharSequence getHighlightedCallTypeText(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                // New incoming calls are not highlighted.
                return mIncomingName;

            case Calls.OUTGOING_TYPE:
                // New outgoing calls are not highlighted.
                return mOutgoingName;

            case Calls.MISSED_TYPE:
                return mNewMissedName;

            case Calls.VOICEMAIL_TYPE:
                return mNewVoicemailName;

            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }

    /** Returns the drawable of the icon associated with the given call type. */
    public Drawable getCallTypeDrawable(int callType) {
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

    /** Creates a SpannableString for the given text which is bold and in the given color. */
    private CharSequence addBoldAndColor(CharSequence text, int color) {
        int flags = Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        SpannableString result = new SpannableString(text);
        result.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), flags);
        result.setSpan(new ForegroundColorSpan(color), 0, text.length(), flags);
        return result;
    }
}
