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
 * limitations under the License.
 */

package com.android.contacts.dialpad;

import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.method.DialerKeyListener;

/**
 * {@link DialerKeyListener} with Unicode support. Converts any Unicode(e.g. Arabic) characters
 * that represent digits into digits before filtering the results so that we can support
 * pasted digits from Unicode languages.
 */
public class UnicodeDialerKeyListener extends DialerKeyListener {
    public static final UnicodeDialerKeyListener INSTANCE = new UnicodeDialerKeyListener();

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
            Spanned dest, int dstart, int dend) {
        final String converted = PhoneNumberUtils.convertKeypadLettersToDigits(
                PhoneNumberUtils.replaceUnicodeDigits(source.toString()));
        // PhoneNumberUtils.replaceUnicodeDigits performs a character for character replacement,
        // so we can assume that start and end positions should remain unchanged.
        CharSequence result = super.filter(converted, start, end, dest, dstart, dend);
        if (result == null) {
            if (source.equals(converted)) {
                // There was no conversion or filtering performed. Just return null according to
                // the behavior of DialerKeyListener.
                return null;
            } else {
                // filter returns null if the charsequence is to be returned unchanged/unfiltered.
                // But in this case we do want to return a modified character string (even if
                // none of the characters in the modified string are filtered). So if
                // result == null we return the unfiltered but converted numeric string instead.
                return converted.subSequence(start, end);
            }
        }
        return result;
    }
}
