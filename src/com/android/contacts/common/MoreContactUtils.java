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

import android.content.Context;
import android.graphics.Rect;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

/**
 * Shared static contact utility methods.
 */
public class MoreContactUtils {

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link android.telephony.PhoneNumberUtils#compare(android.content.Context, String, String)}
     * instead
     */
    public static boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
            CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                mimetype1)) {
            return false;
        }

        return shouldCollapsePhoneNumbers(data1.toString(), data2.toString());
    }

    private static boolean shouldCollapsePhoneNumbers(
            String number1WithLetters, String number2WithLetters) {
        final String number1 = PhoneNumberUtils.convertKeypadLettersToDigits(number1WithLetters);
        final String number2 = PhoneNumberUtils.convertKeypadLettersToDigits(number2WithLetters);

        int index1 = 0;
        int index2 = 0;
        for (;;) {
            // Skip formatting characters.
            while (index1 < number1.length() &&
                    !PhoneNumberUtils.isNonSeparator(number1.charAt(index1))) {
                index1++;
            }
            while (index2 < number2.length() &&
                    !PhoneNumberUtils.isNonSeparator(number2.charAt(index2))) {
                index2++;
            }
            // If both have finished, match.  If only one has finished, not match.
            final boolean number1End = (index1 == number1.length());
            final boolean number2End = (index2 == number2.length());
            if (number1End) {
                return number2End;
            }
            if (number2End) return false;

            // If the non-formatting characters are different, not match.
            if (number1.charAt(index1) != number2.charAt(index2)) return false;

            // Go to the next characters.
            index1++;
            index2++;
        }
    }

    /**
     * Returns the {@link android.graphics.Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link android.view.View}'s bounds. This is equivalent to
     * how the target {@link android.graphics.Rect} is calculated in
     * {@link android.provider.ContactsContract.QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(Context context, View view) {
        final float appScale = context.getResources().getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + view.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + view.getHeight()) * appScale + 0.5f);
        return rect;
    }

    /**
     * Returns a header view based on the R.layout.list_separator, where the
     * containing {@link android.widget.TextView} is set using the given textResourceId.
     */
    public static View createHeaderView(Context context, int textResourceId) {
        View view = View.inflate(context, R.layout.list_separator, null);
        TextView textView = (TextView) view.findViewById(R.id.title);
        textView.setText(context.getString(textResourceId));
        return view;
    }
}
