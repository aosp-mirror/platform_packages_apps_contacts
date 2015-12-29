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
import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.contacts.common.compat.CompatUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class for {@link android.telecom.TelecomManager}.
 */
public class TelecomManagerCompat {
    public static final String TELECOM_MANAGER_CLASS = "android.telecom.TelecomManager";
    /**
     * Places a new outgoing call to the provided address using the system telecom service with
     * the specified intent.
     *
     * @param activity {@link Activity} used to start another activity for the given intent
     * @param telecomManager the {@link TelecomManager} used to place a call, if possible
     * @param intent the intent for the call
     */
    public static void placeCall(@Nullable Activity activity,
            @Nullable TelecomManager telecomManager, @Nullable Intent intent) {
        if (activity == null || telecomManager == null || intent == null) {
            return;
        }
        if (CompatUtils.isMarshmallowCompatible()) {
            telecomManager.placeCall(intent.getData(), intent.getExtras());
            return;
        }
        activity.startActivityForResult(intent, 0);
    }

    /**
     * Get the URI for running an adn query.
     *
     * @param telecomManager the {@link TelecomManager} used for method calls, if possible.
     * @param accountHandle The handle for the account to derive an adn query URI for or
     * {@code null} to return a URI which will use the default account.
     * @return The URI (with the content:// scheme) specific to the specified {@link PhoneAccount}
     * for the the content retrieve.
     */
    public static Uri getAdnUriForPhoneAccount(@Nullable TelecomManager telecomManager,
            PhoneAccountHandle accountHandle) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELECOM_MANAGER_CLASS, "getAdnUriForPhoneAccount",
                        PhoneAccountHandle.class))) {
            return telecomManager.getAdnUriForPhoneAccount(accountHandle);
        }
        return Uri.parse("content://icc/adn");
    }

    /**
     * Returns a list of {@link PhoneAccountHandle}s which can be used to make and receive phone
     * calls. The returned list includes only those accounts which have been explicitly enabled
     * by the user.
     *
     * @param telecomManager the {@link TelecomManager} used for method calls, if possible.
     * @return A list of PhoneAccountHandle objects.
     */
    public static List<PhoneAccountHandle> getCallCapablePhoneAccounts(
            @Nullable TelecomManager telecomManager) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELECOM_MANAGER_CLASS,
                        "getCallCapablePhoneAccounts"))) {
            return telecomManager.getCallCapablePhoneAccounts();
        }
        return new ArrayList<>();
    }

    /**
     * Used to determine the currently selected default dialer package.
     *
     * @param telecomManager the {@link TelecomManager} used for method calls, if possible.
     * @return package name for the default dialer package or null if no package has been
     *         selected as the default dialer.
     */
    @Nullable
    public static String getDefaultDialerPackage(@Nullable TelecomManager telecomManager) {
        if (telecomManager != null && CompatUtils.isDefaultDialerCompatible()) {
            return telecomManager.getDefaultDialerPackage();
        }
        return null;
    }

    /**
     * Return the {@link PhoneAccount} which will be used to place outgoing calls to addresses with
     * the specified {@code uriScheme}. This PhoneAccount will always be a member of the
     * list which is returned from invoking {@link TelecomManager#getCallCapablePhoneAccounts()}.
     * The specific account returned depends on the following priorities:
     *
     * 1. If the user-selected default PhoneAccount supports the specified scheme, it will
     * be returned.
     * 2. If there exists only one PhoneAccount that supports the specified scheme, it
     * will be returned.
     *
     * If no PhoneAccount fits the criteria above, this method will return {@code null}.
     *
     * @param telecomManager the {@link TelecomManager} used for method calls, if possible.
     * @param uriScheme The URI scheme.
     * @return The {@link PhoneAccountHandle} corresponding to the account to be used.
     */
    @Nullable
    public static PhoneAccountHandle getDefaultOutgoingPhoneAccount(
            @Nullable TelecomManager telecomManager, @Nullable String uriScheme) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELECOM_MANAGER_CLASS,
                        "getDefaultOutgoingPhoneAccount", String.class))) {
            return telecomManager.getDefaultOutgoingPhoneAccount(uriScheme);
        }
        return null;
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
     */
    @Nullable
    public static String getLine1Number(@Nullable TelecomManager telecomManager,
            @Nullable TelephonyManager telephonyManager,
            @Nullable PhoneAccountHandle phoneAccountHandle) {
        if (telecomManager != null && CompatUtils.isMarshmallowCompatible()) {
            return telecomManager.getLine1Number(phoneAccountHandle);
        }
        if (telephonyManager != null) {
            return telephonyManager.getLine1Number();
        }
        return null;
    }

    /**
     * Return whether a given phone number is the configured voicemail number for a
     * particular phone account.
     *
     * @param telecomManager the {@link TelecomManager} to use for checking the number.
     * @param accountHandle The handle for the account to check the voicemail number against
     * @param number The number to look up.
     */
    public static boolean isVoiceMailNumber(@Nullable TelecomManager telecomManager,
            @Nullable PhoneAccountHandle accountHandle, @Nullable String number) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELECOM_MANAGER_CLASS, "isVoiceMailNumber",
                        PhoneAccountHandle.class, String.class))) {
            return telecomManager.isVoiceMailNumber(accountHandle, number);
        }
        return PhoneNumberUtils.isVoiceMailNumber(number);
    }

    /**
     * Return the {@link PhoneAccount} for a specified {@link PhoneAccountHandle}. Object includes
     * resources which can be used in a user interface.
     *
     * @param telecomManager the {@link TelecomManager} used for method calls, if possible.
     * @param account The {@link PhoneAccountHandle}.
     * @return The {@link PhoneAccount} object or null if it doesn't exist.
     */
    @Nullable
    public static PhoneAccount getPhoneAccount(@Nullable TelecomManager telecomManager,
            @Nullable PhoneAccountHandle accountHandle) {
        if (telecomManager != null && (CompatUtils.isMethodAvailable(
                TELECOM_MANAGER_CLASS, "getPhoneAccount", PhoneAccountHandle.class))) {
            return telecomManager.getPhoneAccount(accountHandle);
        }
        return null;
    }

    /**
     * Return the voicemail number for a given phone account.
     *
     * @param telecomManager The {@link TelecomManager} object to use for retrieving the voicemail
     * number if accountHandle is specified.
     * @param telephonyManager The {@link TelephonyManager} object to use for retrieving the
     * voicemail number if accountHandle is null.
     * @param accountHandle The handle for the phone account.
     * @return The voicemail number for the phone account, and {@code null} if one has not been
     *         configured.
     */
    @Nullable
    public static String getVoiceMailNumber(@Nullable TelecomManager telecomManager,
            @Nullable TelephonyManager telephonyManager,
            @Nullable PhoneAccountHandle accountHandle) {
        if (telecomManager != null && (CompatUtils.isMethodAvailable(
                TELECOM_MANAGER_CLASS, "getVoiceMailNumber", PhoneAccountHandle.class))) {
            return telecomManager.getVoiceMailNumber(accountHandle);
        } else if (telephonyManager != null){
            return telephonyManager.getVoiceMailNumber();
        }
        return null;
    }

    /**
     * Processes the specified dial string as an MMI code.
     * MMI codes are any sequence of characters entered into the dialpad that contain a "*" or "#".
     * Some of these sequences launch special behavior through handled by Telephony.
     *
     * @param telecomManager The {@link TelecomManager} object to use for handling MMI.
     * @param dialString The digits to dial.
     * @return {@code true} if the digits were processed as an MMI code, {@code false} otherwise.
     */
    public static boolean handleMmi(@Nullable TelecomManager telecomManager,
            @Nullable String dialString, @Nullable PhoneAccountHandle accountHandle) {
        if (telecomManager == null || TextUtils.isEmpty(dialString)) {
            return false;
        }
        if (CompatUtils.isMarshmallowCompatible()) {
            return telecomManager.handleMmi(dialString, accountHandle);
        }

        Object handleMmiResult = CompatUtils.invokeMethod(
                telecomManager,
                "handleMmi",
                new Class<?>[] {PhoneAccountHandle.class, String.class},
                new Object[] {accountHandle, dialString});
        if (handleMmiResult != null) {
            return (boolean) handleMmiResult;
        }

        return telecomManager.handleMmi(dialString);
    }

    /**
     * Silences the ringer if a ringing call exists. Noop if {@link TelecomManager#silenceRinger()}
     * is unavailable.
     *
     * @param telecomManager the TelecomManager to use to silence the ringer.
     */
    public static void silenceRinger(@Nullable TelecomManager telecomManager) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible() || CompatUtils
                .isMethodAvailable(TELECOM_MANAGER_CLASS, "silenceRinger"))) {
            telecomManager.silenceRinger();
        }
    }

    /**
     * Returns the current SIM call manager. Apps must be prepared for this method to return null,
     * indicating that there currently exists no registered SIM call manager.
     *
     * @param telecomManager the {@link TelecomManager} to use to fetch the SIM call manager.
     * @return The phone account handle of the current sim call manager.
     */
    @Nullable
    public static PhoneAccountHandle getSimCallManager(TelecomManager telecomManager) {
        if (telecomManager != null && (CompatUtils.isMarshmallowCompatible() || CompatUtils
                .isMethodAvailable(TELECOM_MANAGER_CLASS, "getSimCallManager"))) {
            return telecomManager.getSimCallManager();
        }
        return null;
    }
}
