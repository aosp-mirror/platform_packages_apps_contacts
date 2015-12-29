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
 * limitations under the License.
 */
package com.android.contacts.common.compat.telecom;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.android.contacts.common.compat.CompatUtils;

/**
 * Compatibility class for {@link android.telecom.TelecomManager}
 */
public class TelecomManagerCompat {

    /**
     * Places a new outgoing call to the provided address using the system telecom service with
     * the specified intent.
     *
     * @param activity {@link Activity} used to start another activity for the given intent
     * @param telecomManager the {@link TelecomManager} used to place a call, if possible
     * @param intent the intent for the call
     * @throws NullPointerException if activity, telecomManager, or intent are null
     */
    public static void placeCall(Activity activity, TelecomManager telecomManager, Intent intent) {
        if (CompatUtils.isMarshmallowCompatible()) {
            telecomManager.placeCall(intent.getData(), intent.getExtras());
            return;
        }
        activity.startActivityForResult(intent, 0);
    }

    /**
     * Return the line 1 phone number for given phone account.
     *
     * @param telecomManager the {@link TelecomManager} to use in the event that
     *    {@link TelecomManager#getLine1Number(PhoneAccountHandle)} is available
     * @param telephonyManager the {@link TelephonyManager} to use if TelecomManager#getLine1Number
     *    is unavailable
     * @param phoneAccountHandle the phoneAccountHandle upon which to check the line one number
     * @return the line one number
     * @throws NullPointerException if telecomManager or telephonyManager are null
     */
    public static String getLine1Number(TelecomManager telecomManager,
            TelephonyManager telephonyManager, @Nullable PhoneAccountHandle phoneAccountHandle) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return telecomManager.getLine1Number(phoneAccountHandle);
        }
        return telephonyManager.getLine1Number();
    }

    /**
     * Return whether a given phone number is the configured voicemail number for a
     * particular phone account.
     *
     * @param telecomManager the {@link TelecomManager} to use
     * @param accountHandle The handle for the account to check the voicemail number against
     * @param number The number to look up.
     * @throws NullPointerException if telecomManager is null
     */
    public static boolean isVoiceMailNumber(TelecomManager telecomManager,
            @Nullable PhoneAccountHandle accountHandle, @Nullable String number) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return telecomManager.isVoiceMailNumber(accountHandle, number);
        }
        return PhoneNumberUtils.isVoiceMailNumber(number);
    }

    /**
     * Silences the ringer if a ringing call exists. Noop if {@link TelecomManager#silenceRinger()}
     * is unavailable.
     *
     * @param telecomManager the {@link TelecomManager} to use to silence the ringer
     * @throws NullPointerException if telecomManager is null
     */
    public static void silenceRinger(TelecomManager telecomManager) {
        if (CompatUtils.isMarshmallowCompatible() || CompatUtils
                .isMethodAvailable("android.telecom.TelecomManager", "silenceRinger")) {
            telecomManager.silenceRinger();
        }
    }
}
