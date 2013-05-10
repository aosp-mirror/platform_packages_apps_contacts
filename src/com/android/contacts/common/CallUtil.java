/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;

import com.android.phone.common.PhoneConstants;

/**
 * Utilities related to calls.
 */
public class CallUtil {

    public static final String SCHEME_TEL = "tel";
    public static final String SCHEME_SMSTO = "smsto";
    public static final String SCHEME_MAILTO = "mailto";
    public static final String SCHEME_IMTO = "imto";
    public static final String SCHEME_SIP = "sip";

    public static final ComponentName CALL_INTENT_DESTINATION = new ComponentName(
            "com.android.phone", "com.android.phone.PrivilegedOutgoingCallBroadcaster");

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        return getCallIntent(number, null);
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return getCallIntent(uri, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(String number, String callOrigin) {
        return getCallIntent(getCallUri(number), callOrigin);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(Uri uri, String callOrigin) {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (callOrigin != null) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }

        // Set phone as an explicit component of CALL_PRIVILEGED intent.
        // Setting destination explicitly prevents other apps from capturing this Intent since,
        // unlike SendBroadcast, there is no API for specifying a permission on startActivity.
        intent.setComponent(CALL_INTENT_DESTINATION);

        return intent;
    }

    /**
     * Return Uri with an appropriate scheme, accepting Voicemail, SIP, and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberUtils.isUriNumber(number)) {
             return Uri.fromParts(SCHEME_SIP, number, null);
        }
        return Uri.fromParts(SCHEME_TEL, number, null);
     }
}
