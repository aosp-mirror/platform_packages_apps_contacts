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

package com.android.contacts.common.compat;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.style.TtsSpan;

/**
 * This class contains static utility methods extracted from PhoneNumberUtils, and the
 * methods were added in API level 23. In this way, we could enable the corresponding functionality
 * for pre-M devices. We need maintain this class and keep it synced with PhoneNumberUtils.
 * Another thing to keep in mind is that we use com.google.i18n rather than com.android.i18n in
 * here, so we need make sure the application behavior is preserved.
 */
public class PhoneNumberUtilsCompat {
    private PhoneNumberUtilsCompat() {}

    public static CharSequence createTtsSpannable(CharSequence phoneNumber) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M) {
            return PhoneNumberUtils.createTtsSpannable(phoneNumber);
        } else {
            return createTtsSpannableInternal(phoneNumber);
        }
    }

    public static TtsSpan createTtsSpan(String phoneNumber) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M) {
            return PhoneNumberUtils.createTtsSpan(phoneNumber);
        } else {
            return createTtsSpanInternal(phoneNumber);
        }
    }

    /**
     * Copied from {@link PhoneNumberUtils#createTtsSpannable}
     */
    private static CharSequence createTtsSpannableInternal(CharSequence phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(phoneNumber);
        addTtsSpan(spannable, 0, spannable.length());
        return spannable;
    }

    /**
     * Attach a {@link TtsSpan} to the supplied {@code Spannable} at the indicated location,
     * annotating that location as containing a phone number.
     *
     * @param s A {@code Spannable} to annotate.
     * @param start The starting character position of the phone number in {@code s}.
     * @param endExclusive The position after the ending character in the phone number {@code s}.
     */
    private static void addTtsSpan(Spannable s, int start, int endExclusive) {
        s.setSpan(createTtsSpan(s.subSequence(start, endExclusive).toString()),
                start,
                endExclusive,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Copied from {@link PhoneNumberUtils#createTtsSpan}
     */
    private static TtsSpan createTtsSpanInternal(String phoneNumberString) {
        if (phoneNumberString == null) {
            return null;
        }

        // Parse the phone number
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        PhoneNumber phoneNumber = null;
        try {
            // Don't supply a defaultRegion so this fails for non-international numbers because
            // we don't want to TalkBalk to read a country code (e.g. +1) if it is not already
            // present
            phoneNumber = phoneNumberUtil.parse(phoneNumberString, /* defaultRegion */ null);
        } catch (NumberParseException ignored) {
        }

        // Build a telephone tts span
        final TtsSpan.TelephoneBuilder builder = new TtsSpan.TelephoneBuilder();
        if (phoneNumber == null) {
            // Strip separators otherwise TalkBack will be silent
            // (this behavior was observed with TalkBalk 4.0.2 from their alpha channel)
            builder.setNumberParts(splitAtNonNumerics(phoneNumberString));
        } else {
            if (phoneNumber.hasCountryCode()) {
                builder.setCountryCode(Integer.toString(phoneNumber.getCountryCode()));
            }
            builder.setNumberParts(Long.toString(phoneNumber.getNationalNumber()));
        }
        return builder.build();
    }



    /**
     * Split a phone number using spaces, ignoring anything that is not a digit
     * @param number A {@code CharSequence} before splitting, e.g., "+20(123)-456#"
     * @return A {@code String} after splitting, e.g., "20 123 456".
     */
    private static String splitAtNonNumerics(CharSequence number) {
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            sb.append(PhoneNumberUtils.isISODigit(number.charAt(i))
                    ? number.charAt(i)
                    : " ");
        }
        // It is very important to remove extra spaces. At time of writing, any leading or trailing
        // spaces, or any sequence of more than one space, will confuse TalkBack and cause the TTS
        // span to be non-functional!
        return sb.toString().replaceAll(" +", " ").trim();
    }

}
