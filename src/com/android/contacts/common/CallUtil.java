/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.phone.common.PhoneConstants;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;

import java.util.List;

/**
 * Utilities related to calls that can be used by non system apps. These
 * use {@link Intent#ACTION_CALL} instead of ACTION_CALL_PRIVILEGED.
 *
 * The privileged version of this util exists inside Dialer.
 */
public class CallUtil {

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallWithSubjectIntent(String number,
            PhoneAccountHandle phoneAccountHandle, String callSubject) {

        final Intent intent = getCallIntent(getCallUri(number));
        intent.putExtra(TelecomManager.EXTRA_CALL_SUBJECT, callSubject);
        if (phoneAccountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        }
        return intent;
    }

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        return getCallIntent(getCallUri(number));
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return new Intent(Intent.ACTION_CALL, uri);
    }

    /**
     * A variant of {@link #getCallIntent} for starting a video call.
     */
    public static Intent getVideoCallIntent(String number, String callOrigin) {
        final Intent intent = new Intent(Intent.ACTION_CALL, getCallUri(number));
        intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL);
        if (!TextUtils.isEmpty(callOrigin)) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }
        return intent;
    }

    /**
     * Return Uri with an appropriate scheme, accepting both SIP and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberHelper.isUriNumber(number)) {
             return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Determines if one of the call capable phone accounts defined supports video calling.
     *
     * @param context The context.
     * @return {@code true} if one of the call capable phone accounts supports video calling,
     *      {@code false} otherwise.
     */
    public static boolean isVideoEnabled(Context context) {
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }

        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
            if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if one of the call capable phone accounts defined supports calling with a subject
     * specified.
     *
     * @param context The context.
     * @return {@code true} if one of the call capable phone accounts supports calling with a
     *      subject specified, {@code false} otherwise.
     */
    public static boolean isCallWithSubjectSupported(Context context) {
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }

        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
            if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)) {
                return true;
            }
        }
        return false;
    }
}
