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

package com.android.contacts;

import com.android.internal.telephony.CallerInfo;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.widget.TextView;

/**
 * Unit tests for {@link PhoneCallDetailsHelper}.
 */
public class PhoneCallDetailsHelperTest extends AndroidTestCase {
    /** The number to be used to access the voicemail. */
    private static final String TEST_VOICEMAIL_NUMBER = "125";
    /** The date of the call log entry. */
    private static final long TEST_DATE = 1300000000;

    /** The object under test. */
    private PhoneCallDetailsHelper mHelper;
    /** The views to fill. */
    private PhoneCallDetailsViews mViews;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        mHelper = new PhoneCallDetailsHelper(context.getResources(), TEST_VOICEMAIL_NUMBER);
        mViews = PhoneCallDetailsViews.createForTest(new TextView(context), new TextView(context),
                new TextView(context));
    }

    @Override
    protected void tearDown() throws Exception {
        mViews = null;
        mHelper = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber(CallerInfo.UNKNOWN_NUMBER);
        assertNameEqualsResource(R.string.unknown);
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber(CallerInfo.PRIVATE_NUMBER);
        assertNameEqualsResource(R.string.private_num);
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber(CallerInfo.PAYPHONE_NUMBER);
        assertNameEqualsResource(R.string.payphone);
    }

    public void testSetPhoneCallDetails_Voicemail() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER);
        assertNameEqualsResource(R.string.voicemail);
    }

    public void testSetPhoneCallDetails_Normal() {
        setPhoneCallDetailsWithNumber("1-412-555-1212");
        assertNameEquals("1-412-555-1212");
    }

    /** Asserts that the name text field contains the value of the given string resource. */
    private void assertNameEqualsResource(int resId) {
        assertNameEquals(getContext().getString(resId));
    }

    /** Asserts that the name text field contains the given string value. */
    private void assertNameEquals(String text) {
        assertEquals(text, mViews.mNameView.getText().toString());
    }

    /** Sets the phone call details with default values and the given number. */
    private void setPhoneCallDetailsWithNumber(String number) {
        mHelper.setPhoneCallDetails(mViews, TEST_DATE, Calls.INCOMING_TYPE, "", number, 0, "");
    }
}
