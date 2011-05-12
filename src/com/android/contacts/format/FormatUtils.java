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
package com.android.contacts.format;

import android.database.CharArrayBuffer;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.StyleSpan;

import java.util.Arrays;

/**
 * Assorted utility methods related to text formatting in Contacts.
 */
public class FormatUtils {

    /**
     * Finds the earliest point in buffer1 at which the first part of buffer2 matches.  For example,
     * overlapPoint("abcd", "cdef") == 2.
     */
    public static int overlapPoint(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {
        if (buffer1 == null || buffer2 == null) {
            return -1;
        }
        return overlapPoint(Arrays.copyOfRange(buffer1.data, 0, buffer1.sizeCopied),
                Arrays.copyOfRange(buffer2.data, 0, buffer2.sizeCopied));
    }

    /**
     * Finds the earliest point in string1 at which the first part of string2 matches.  For example,
     * overlapPoint("abcd", "cdef") == 2.
     */
    public static int overlapPoint(String string1, String string2) {
        if (string1 == null || string2 == null) {
            return -1;
        }
        return overlapPoint(string1.toCharArray(), string2.toCharArray());
    }

    /**
     * Finds the earliest point in array1 at which the first part of array2 matches.  For example,
     * overlapPoint("abcd", "cdef") == 2.
     */
    public static int overlapPoint(char[] array1, char[] array2) {
        if (array1 == null || array2 == null) {
            return -1;
        }
        int count1 = array1.length;
        int count2 = array2.length;

        // Ignore matching tails of the two arrays.
        while (count1 > 0 && count2 > 0 && array1[count1 - 1] == array2[count2 - 1]) {
            count1--;
            count2--;
        }

        int size = count2;
        for (int i = 0; i < count1; i++) {
            if (i + size > count1) {
                size = count1 - i;
            }
            int j;
            for (j = 0; j < size; j++) {
                if (array1[i+j] != array2[j]) {
                    break;
                }
            }
            if (j == size) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Applies the given style to a range of the input CharSequence.
     * @param style The style to apply (see the style constants in {@link Typeface}).
     * @param input The CharSequence to style.
     * @param start Starting index of the range to style (will be clamped to be a minimum of 0).
     * @param end Ending index of the range to style (will be clamped to a maximum of the input
     *     length).
     * @param flags Bitmask for configuring behavior of the span.  See {@link android.text.Spanned}.
     * @return The styled CharSequence.
     */
    public static CharSequence applyStyleToSpan(int style, CharSequence input, int start, int end,
            int flags) {
        // Enforce bounds of the char sequence.
        start = Math.max(0, start);
        end = Math.min(input.length(), end);
        SpannableString text = new SpannableString(input);
        text.setSpan(new StyleSpan(style), start, end, flags);
        return text;
    }
}
