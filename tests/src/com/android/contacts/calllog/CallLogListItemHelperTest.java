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

package com.android.contacts.calllog;

import com.android.contacts.PhoneCallDetails;
import com.android.contacts.PhoneCallDetailsHelper;
import com.android.internal.telephony.CallerInfo;

import android.content.Context;
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.view.View;

/**
 * Unit tests for {@link CallLogListItemHelper}.
 */
public class CallLogListItemHelperTest extends AndroidTestCase {
    /** A test phone number for phone calls. */
    private static final String TEST_NUMBER = "14125555555";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1-412-255-5555";
    /** A test date value for phone calls. */
    private static final long TEST_DATE = 1300000000;
    /** A test duration value for phone calls. */
    private static final long TEST_DURATION = 62300;
    /** A test voicemail number. */
    private static final String TEST_VOICEMAIL_NUMBER = "123";
    /** The country ISO name used in the tests. */
    private static final String TEST_COUNTRY_ISO = "US";
    /** The geocoded location used in the tests. */
    private static final String TEST_GEOCODE = "United States";

    /** The object under test. */
    private CallLogListItemHelper mHelper;

    /** The views used in the tests. */
    private CallLogListItemViews mViews;
    private PhoneNumberHelper mPhoneNumberHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        Resources resources = context.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);
        mPhoneNumberHelper = new TestPhoneNumberHelper(resources, TEST_VOICEMAIL_NUMBER);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                resources, callTypeHelper, mPhoneNumberHelper);
        mHelper = new CallLogListItemHelper(phoneCallDetailsHelper, mPhoneNumberHelper, resources);
        mViews = CallLogListItemViews.createForTest(context);
    }

    @Override
    protected void tearDown() throws Exception {
        mHelper = null;
        mViews = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails() {
        setPhoneCallDetailsWithNumber("12125551234", "1-212-555-1234");
        assertEquals(View.VISIBLE, mViews.secondaryActionView.getVisibility());
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber(CallerInfo.UNKNOWN_NUMBER, CallerInfo.UNKNOWN_NUMBER);
        assertNoCallButton();
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber(CallerInfo.PRIVATE_NUMBER, CallerInfo.PRIVATE_NUMBER);
        assertNoCallButton();
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber(CallerInfo.PAYPHONE_NUMBER, CallerInfo.PAYPHONE_NUMBER);
        assertNoCallButton();
    }

    public void testSetPhoneCallDetails_VoicemailNumber() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER, TEST_VOICEMAIL_NUMBER);
        assertEquals(View.VISIBLE, mViews.secondaryActionView.getVisibility());
    }

    public void testSetPhoneCallDetails_ReadVoicemail() {
        setPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.secondaryActionView.getVisibility());
    }

    public void testSetPhoneCallDetails_UnreadVoicemail() {
        setUnreadPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.secondaryActionView.getVisibility());
    }

    public void testSetPhoneCallDetails_VoicemailFromUnknown() {
        setPhoneCallDetailsWithNumberAndType(CallerInfo.UNKNOWN_NUMBER, CallerInfo.UNKNOWN_NUMBER,
                Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.secondaryActionView.getVisibility());
    }

    /** Asserts that the whole call area is gone. */
    private void assertNoCallButton() {
        assertEquals(View.GONE, mViews.secondaryActionView.getVisibility());
        assertEquals(View.GONE, mViews.dividerView.getVisibility());
    }

    /** Sets the details of a phone call using the specified phone number. */
    private void setPhoneCallDetailsWithNumber(String number, String formattedNumber) {
        setPhoneCallDetailsWithNumberAndType(number, formattedNumber, Calls.INCOMING_TYPE);
    }

    /** Sets the details of a phone call using the specified phone number. */
    private void setPhoneCallDetailsWithNumberAndType(String number, String formattedNumber,
            int callType) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(number, formattedNumber, TEST_COUNTRY_ISO, TEST_GEOCODE,
                        new int[]{ callType }, TEST_DATE, TEST_DURATION),
                false);
    }

    /** Sets the details of a phone call using the specified call type. */
    private void setPhoneCallDetailsWithTypes(int... types) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        TEST_GEOCODE, types, TEST_DATE, TEST_DURATION),
                false);
    }

    /** Sets the details of a phone call using the specified call type. */
    private void setUnreadPhoneCallDetailsWithTypes(int... types) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        TEST_GEOCODE, types, TEST_DATE, TEST_DURATION),
                true);
    }
}
