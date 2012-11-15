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

import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.dialpad.UnicodeDialerKeyListener;

import junit.framework.TestCase;
/**
 * Test case for {@link UnicodeDialerKeyListener}.
 *
 * adb shell am instrument -w -e class com.android.contacts.dialpad.UnicodeDialerKeyListenerTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class UnicodeDialerKeyListenerTest extends TestCase {
    private static UnicodeDialerKeyListener mUnicodeDialerKeyListener;

    // Pasted numeric digits should remain unchanged
    public void testNumericDigits() {
        // The last 3 arguments don't matter because {@link NumberKeyListener} doesn't care
        // about dest, dstart, dend in
        // public CharSequence filter (CharSequence source, int start, int end,
        //         Spanned dest, int dstart, int dend)
        // anyway. This applies to all tests.
        assertEquals(null, mUnicodeDialerKeyListener.filter("111222333", 0, 9, null, 0, 0));
    }

    // Pasted Arabic digits should be converted to ascii digits
    public void testArabicDigits() {
        assertEquals("0123456789", mUnicodeDialerKeyListener.filter("٠١٢٣٤٥٦٧٨٩", 0, 10,
                null, 0, 0));
    }

    // Pasted Farsi(Persian) digits should be converted to ascii digits
    // Note the difference in digits 4, 5 and 6 when compared to arabic. The rest of the digits
    // look the same compared to the Arabic digits but they actually have different unicode codes.
    public void testFarsiDigits() {
        assertEquals("0123456789", mUnicodeDialerKeyListener.filter("۰۱۲۳۴۵۶۷۸۹", 0, 10,
                null, 0, 0));
    }

    // This is a rare use case but we should make sure it works all the same.
    public void testCombinationDigits() {
        assertEquals("15102849177", mUnicodeDialerKeyListener.filter("۱510٢٨٤۹۱۷۷", 0, 11,
                null, 0, 0));
    }

    // Test that a normal digit string with dashes is returned unfiltered
    public void testDashes() {
        assertEquals(null, mUnicodeDialerKeyListener.filter("1510-284-9177", 0, 13,
                null, 0, 0));
    }

    @Override
    protected void setUp() throws Exception {
        mUnicodeDialerKeyListener = UnicodeDialerKeyListener.INSTANCE;
    }
}
