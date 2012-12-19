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

import com.android.contacts.common.util.CommonDateUtils;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility methods for processing dates.
 */
public class DateUtils {
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    /**
     * When parsing a date without a year, the system assumes 1970, which wasn't a leap-year.
     * Let's add a one-off hack for that day of the year
     */
    public static final String NO_YEAR_DATE_FEB29TH = "--02-29";

    // Variations of ISO 8601 date format.  Do not change the order - it does affect the
    // result in ambiguous cases.
    private static final SimpleDateFormat[] DATE_FORMATS = {
        CommonDateUtils.FULL_DATE_FORMAT,
        CommonDateUtils.DATE_AND_TIME_FORMAT,
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmssSSS'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmm'Z'", Locale.US),
    };

    static {
        for (SimpleDateFormat format : DATE_FORMATS) {
            format.setLenient(true);
            format.setTimeZone(UTC_TIMEZONE);
        }
        CommonDateUtils.NO_YEAR_DATE_FORMAT.setTimeZone(UTC_TIMEZONE);
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

    private static final Date getUtcDate(int year, int month, int dayOfMonth) {
        final Calendar calendar = Calendar.getInstance(UTC_TIMEZONE, Locale.US);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        return calendar.getTime();
    }

    /**
     * Same as {@link #formatDate(Context context, String string, boolean longForm)}, with
     * longForm set to {@code true} by default.
     *
     * @param context Valid context
     * @param string String representation of a date to parse
     * @return Returns the same date in a cleaned up format. If the supplied string does not look
     * like a date, return it unchanged.
     */

    public static String formatDate(Context context, String string) {
        return formatDate(context, string, true);
    }

    /**
     * Parses the supplied string to see if it looks like a date.
     *
     * @param context Valid context
     * @param string String representation of a date to parse
     * @param longForm If true, return the date formatted into its long string representation.
     * If false, return the date formatted using its short form representation (i.e. 12/11/2012)
     * @return Returns the same date in a cleaned up format. If the supplied string does not look
     * like a date, return it unchanged.
     */
    public static String formatDate(Context context, String string, boolean longForm) {
        if (string == null) {
            return null;
        }

        string = string.trim();
        if (string.length() == 0) {
            return string;
        }

        ParsePosition parsePosition = new ParsePosition(0);

        final boolean noYearParsed;
        Date date;

        // Unfortunately, we can't parse Feb 29th correctly, so let's handle this day seperately
        if (NO_YEAR_DATE_FEB29TH.equals(string)) {
            date = getUtcDate(0, Calendar.FEBRUARY, 29);
            noYearParsed = true;
        } else {
            synchronized (CommonDateUtils.NO_YEAR_DATE_FORMAT) {
                date = CommonDateUtils.NO_YEAR_DATE_FORMAT.parse(string, parsePosition);
            }
            noYearParsed = parsePosition.getIndex() == string.length();
        }

        if (noYearParsed) {
            final java.text.DateFormat outFormat = getLocalizedDateFormatWithoutYear(context);
            synchronized (outFormat) {
                outFormat.setTimeZone(UTC_TIMEZONE);
                return outFormat.format(date);
            }
        }

        for (int i = 0; i < DATE_FORMATS.length; i++) {
            SimpleDateFormat f = DATE_FORMATS[i];
            synchronized (f) {
                parsePosition.setIndex(0);
                date = f.parse(string, parsePosition);
                if (parsePosition.getIndex() == string.length()) {
                    final java.text.DateFormat outFormat =
                            longForm ? DateFormat.getLongDateFormat(context) :
                            DateFormat.getDateFormat(context);
                        outFormat.setTimeZone(UTC_TIMEZONE);
                        return outFormat.format(date);
                }
            }
        }
        return string;
    }

    public static boolean isMonthBeforeDay(Context context) {
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

    /**
     * Returns a SimpleDateFormat object without the year fields by using a regular expression
     * to eliminate the year in the string pattern. In the rare occurence that the resulting
     * pattern cannot be reconverted into a SimpleDateFormat, it uses the provided context to
     * determine whether the month field should be displayed before the day field, and returns
     * either "MMMM dd" or "dd MMMM" converted into a SimpleDateFormat.
     */
    public static java.text.DateFormat getLocalizedDateFormatWithoutYear(Context context) {
        final String pattern = ((SimpleDateFormat) SimpleDateFormat.getDateInstance(
                java.text.DateFormat.LONG)).toPattern();
        // Determine the correct regex pattern for year.
        // Special case handling for Spanish locale by checking for "de"
        final String yearPattern = pattern.contains(
                "de") ? "[^Mm]*[Yy]+[^Mm]*" : "[^DdMm]*[Yy]+[^DdMm]*";
        try {
         // Eliminate the substring in pattern that matches the format for that of year
            return new SimpleDateFormat(pattern.replaceAll(yearPattern, ""));
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat(
                    DateUtils.isMonthBeforeDay(context) ? "MMMM dd" : "dd MMMM");
        }
    }
}
