/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.util;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility methods for processing dates.
 */
public class DateUtils {
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    // All the SimpleDateFormats in this class use the UTC timezone
    public static final SimpleDateFormat NO_YEAR_DATE_FORMAT =
            new SimpleDateFormat("--MM-dd", Locale.US);
    public static final SimpleDateFormat FULL_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    public static final SimpleDateFormat DATE_AND_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    public static final SimpleDateFormat NO_YEAR_DATE_AND_TIME_FORMAT =
            new SimpleDateFormat("--MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    // Variations of ISO 8601 date format.  Do not change the order - it does affect the
    // result in ambiguous cases.
    private static final SimpleDateFormat[] DATE_FORMATS = {
        FULL_DATE_FORMAT,
        DATE_AND_TIME_FORMAT,
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmssSSS'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmm'Z'", Locale.US),
    };

    private static final java.text.DateFormat FORMAT_WITHOUT_YEAR_MONTH_FIRST =
            new SimpleDateFormat("MMMM dd");

    private static final java.text.DateFormat FORMAT_WITHOUT_YEAR_DATE_FIRST =
            new SimpleDateFormat("dd MMMM");

    static {
        for (SimpleDateFormat format : DATE_FORMATS) {
            format.setLenient(true);
            format.setTimeZone(UTC_TIMEZONE);
        }
        NO_YEAR_DATE_FORMAT.setTimeZone(UTC_TIMEZONE);
        FORMAT_WITHOUT_YEAR_MONTH_FIRST.setTimeZone(UTC_TIMEZONE);
        FORMAT_WITHOUT_YEAR_DATE_FIRST.setTimeZone(UTC_TIMEZONE);
    }

    /**
     * Parses the supplied string to see if it looks like a date. If so,
     * returns the date.  Otherwise, returns null.
     */
    public static Date parseDate(String string) {
        ParsePosition parsePosition = new ParsePosition(0);
        for (int i = 0; i < DATE_FORMATS.length; i++) {
            SimpleDateFormat f = DATE_FORMATS[i];
            synchronized (f) {
                parsePosition.setIndex(0);
                Date date = f.parse(string, parsePosition);
                if (parsePosition.getIndex() == string.length()) {
                    return date;
                }
            }
        }
        return null;
    }

    /**
     * Parses the supplied string to see if it looks like a date. If so,
     * returns the same date in a cleaned-up format for the user.  Otherwise, returns
     * the supplied string unchanged.
     */
    public static String formatDate(Context context, String string) {
        if (string == null) {
            return null;
        }

        string = string.trim();
        if (string.length() == 0) {
            return string;
        }

        ParsePosition parsePosition = new ParsePosition(0);

        Date date;

        synchronized (NO_YEAR_DATE_FORMAT) {
            date = NO_YEAR_DATE_FORMAT.parse(string, parsePosition);
        }

        if (parsePosition.getIndex() == string.length()) {
            java.text.DateFormat outFormat = isMonthBeforeDate(context)
                    ? FORMAT_WITHOUT_YEAR_MONTH_FIRST
                    : FORMAT_WITHOUT_YEAR_DATE_FIRST;
            synchronized (outFormat) {
                return outFormat.format(date);
            }
        }

        for (int i = 0; i < DATE_FORMATS.length; i++) {
            SimpleDateFormat f = DATE_FORMATS[i];
            synchronized (f) {
                parsePosition.setIndex(0);
                date = f.parse(string, parsePosition);
                if (parsePosition.getIndex() == string.length()) {
                    java.text.DateFormat outFormat = DateFormat.getDateFormat(context);
                    outFormat.setTimeZone(UTC_TIMEZONE);
                    return outFormat.format(date);
                }
            }
        }
        return string;
    }

    private static boolean isMonthBeforeDate(Context context) {
        char[] dateFormatOrder = DateFormat.getDateFormatOrder(context);
        for (int i = 0; i < dateFormatOrder.length; i++) {
            if (dateFormatOrder[i] == DateFormat.DATE) {
                return false;
            }
            if (dateFormatOrder[i] == DateFormat.MONTH) {
                return true;
            }
        }
        return false;
    }
}
