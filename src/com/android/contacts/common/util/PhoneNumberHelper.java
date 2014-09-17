/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.contacts.common.util;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.ShortNumberInfo;

import java.util.Locale;

/**
 * This class wraps several PhoneNumberUtil calls and TelephonyManager calls. Some of them are
 * the same as the ones in the framework's code base. We can remove those once they are part of
 * the public API.
 */
public class PhoneNumberHelper {

    private static final String LOG_TAG = PhoneNumberHelper.class.getSimpleName();

    /**
     * Determines if the specified number is actually a URI (i.e. a SIP address) rather than a
     * regular PSTN phone number, based on whether or not the number contains an "@" character.
     *
     * @param number Phone number
     * @return true if number contains @
     *
     * TODO: Remove if PhoneNumberUtils.isUriNumber(String number) is made public.
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /**
     * Formats the phone number only if the given number hasn't been formatted.
     * <p>
     * The number which has only dailable character is treated as not being
     * formatted.
     *
     * @param phoneNumber the number to be formatted.
     * @param phoneNumberE164 The E164 format number whose country code is used if the given
     * phoneNumber doesn't have the country code.
     * @param defaultCountryIso The ISO 3166-1 two letters country code whose convention will
     * be used if the phoneNumberE164 is null or invalid, or if phoneNumber contains IDD.
     * @return The formatted number if the given number has been formatted, otherwise, return the
     * given number.
     *
     * TODO: Remove if PhoneNumberUtils.formatNumber(String phoneNumber, String phoneNumberE164,
     * String defaultCountryIso) is made public.
     */
    public static String formatNumber(
            String phoneNumber, String phoneNumberE164, String defaultCountryIso) {
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            if (!PhoneNumberUtils.isDialable(phoneNumber.charAt(i))) {
                return phoneNumber;
            }
        }
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        // Get the country code from phoneNumberE164
        if (phoneNumberE164 != null && phoneNumberE164.length() >= 2
                && phoneNumberE164.charAt(0) == '+') {
            try {
                // The number to be parsed is in E164 format, so the default region used doesn't
                // matter.
                PhoneNumber pn = util.parse(phoneNumberE164, "ZZ");
                String regionCode = util.getRegionCodeForNumber(pn);
                if (!TextUtils.isEmpty(regionCode) &&
                        // This makes sure phoneNumber doesn't contain an IDD
                        normalizeNumber(phoneNumber).indexOf(phoneNumberE164.substring(1)) <= 0) {
                    defaultCountryIso = regionCode;
                }
            } catch (NumberParseException e) {
                Log.w(LOG_TAG, "The number could not be parsed in E164 format!");
            }
        }

        String result = formatNumber(phoneNumber, defaultCountryIso);
        return result == null ? phoneNumber : result;
    }

    /**
     * Format a phone number.
     * <p>
     * If the given number doesn't have the country code, the phone will be
     * formatted to the default country's convention.
     *
     * @param phoneNumber The number to be formatted.
     * @param defaultCountryIso The ISO 3166-1 two letters country code whose convention will
     * be used if the given number doesn't have the country code.
     * @return The formatted number, or null if the given number is not valid.
     *
     * TODO: Remove if PhoneNumberUtils.formatNumber(String phoneNumber, String defaultCountryIso)
     * is made public.
     */
    public static String formatNumber(String phoneNumber, String defaultCountryIso) {
        // Do not attempt to format numbers that start with a hash or star symbol.
        if (phoneNumber.startsWith("#") || phoneNumber.startsWith("*")) {
            return phoneNumber;
        }

        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String result = null;
        try {
            PhoneNumber pn = util.parseAndKeepRawInput(phoneNumber, defaultCountryIso);
            result = util.formatInOriginalFormat(pn, defaultCountryIso);
        } catch (NumberParseException e) {
            Log.w(LOG_TAG, "Number could not be parsed with the given country code!");
        }
        return result;
    }

    /**
     * Normalize a phone number by removing the characters other than digits. If
     * the given number has keypad letters, the letters will be converted to
     * digits first.
     *
     * @param phoneNumber The number to be normalized.
     * @return The normalized number.
     *
     * TODO: Remove if PhoneNumberUtils.normalizeNumber(String phoneNumber) is made public.
     */
    public static String normalizeNumber(String phoneNumber) {
        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            // Character.digit() supports ASCII and Unicode digits (fullwidth, Arabic-Indic, etc.)
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (i == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(PhoneNumberUtils.convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }

    /**
     * @return the "username" part of the specified SIP address, i.e. the part before the "@"
     * character (or "%40").
     *
     * @param number SIP address of the form "username@domainname" (or the URI-escaped equivalent
     * "username%40domainname")
     *
     * TODO: Remove if PhoneNumberUtils.getUsernameFromUriNumber(String number) is made public.
     */
    public static String getUsernameFromUriNumber(String number) {
        // The delimiter between username and domain name can be
        // either "@" or "%40" (the URI-escaped equivalent.)
        int delimiterIndex = number.indexOf('@');
        if (delimiterIndex < 0) {
            delimiterIndex = number.indexOf("%40");
        }
        if (delimiterIndex < 0) {
            Log.w(LOG_TAG,
                    "getUsernameFromUriNumber: no delimiter found in SIP addr '" + number + "'");
            return number;
        }
        return number.substring(0, delimiterIndex);
    }
}
