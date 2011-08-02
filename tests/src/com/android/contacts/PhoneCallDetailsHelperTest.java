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

import com.android.contacts.calllog.CallTypeHelper;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.util.LocaleTestUtils;
import com.android.internal.telephony.CallerInfo;

import android.content.Context;
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.view.View;
import android.widget.TextView;

import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Unit tests for {@link PhoneCallDetailsHelper}.
 */
public class PhoneCallDetailsHelperTest extends AndroidTestCase {
    /** The number to be used to access the voicemail. */
    private static final String TEST_VOICEMAIL_NUMBER = "125";
    /** The date of the call log entry. */
    private static final long TEST_DATE = 1300000000;
    /** A test duration value for phone calls. */
    private static final long TEST_DURATION = 62300;
    /** The number of the caller/callee in the log entry. */
    private static final String TEST_NUMBER = "14125555555";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1-412-255-5555";
    /** The country ISO name used in the tests. */
    private static final String TEST_COUNTRY_ISO = "US";

    /** The object under test. */
    private PhoneCallDetailsHelper mHelper;
    /** The views to fill. */
    private PhoneCallDetailsViews mViews;
    private TextView mNameView;
    private PhoneNumberHelper mPhoneNumberHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        Resources resources = context.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);
        mPhoneNumberHelper = new PhoneNumberHelper(resources, TEST_VOICEMAIL_NUMBER);
        mHelper = new PhoneCallDetailsHelper(resources, callTypeHelper, mPhoneNumberHelper);
        mViews = PhoneCallDetailsViews.createForTest(context);
        mNameView = new TextView(context);
    }

    @Override
    protected void tearDown() throws Exception {
        mViews = null;
        mHelper = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber(CallerInfo.UNKNOWN_NUMBER, CallerInfo.UNKNOWN_NUMBER);
        assertNameEqualsResource(R.string.unknown);
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber(CallerInfo.PRIVATE_NUMBER, CallerInfo.PRIVATE_NUMBER);
        assertNameEqualsResource(R.string.private_num);
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber(CallerInfo.PAYPHONE_NUMBER, CallerInfo.PAYPHONE_NUMBER);
        assertNameEqualsResource(R.string.payphone);
    }

    public void testSetPhoneCallDetails_Voicemail() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER, TEST_VOICEMAIL_NUMBER);
        assertNameEqualsResource(R.string.voicemail);
    }

    public void testSetPhoneCallDetails_Normal() {
        setPhoneCallDetailsWithNumber("14125551212", "1-412-555-1212");
        assertNameEquals("1-412-555-1212");
    }

    public void testSetPhoneCallDetails_Date() {
        LocaleTestUtils localeTestUtils = new LocaleTestUtils(getContext());
        localeTestUtils.setLocale(Locale.US);
        try {
            mHelper.setCurrentTimeForTest(
                    new GregorianCalendar(2011, 5, 3, 13, 0, 0).getTimeInMillis());

            setPhoneCallDetailsWithDate(
                    new GregorianCalendar(2011, 5, 3, 13, 0, 0).getTimeInMillis());
            assertDateEquals("0 mins ago");

            setPhoneCallDetailsWithDate(
                    new GregorianCalendar(2011, 5, 3, 12, 0, 0).getTimeInMillis());
            assertDateEquals("1 hour ago");

            setPhoneCallDetailsWithDate(
                    new GregorianCalendar(2011, 5, 2, 13, 0, 0).getTimeInMillis());
            assertDateEquals("yesterday");

            setPhoneCallDetailsWithDate(
                    new GregorianCalendar(2011, 5, 1, 13, 0, 0).getTimeInMillis());
            assertDateEquals("2 days ago");
        } finally {
            localeTestUtils.restoreLocale();
        }
    }

    public void testSetPhoneCallDetails_CallTypeIcons() {
        setPhoneCallDetailsWithCallTypeIcons(Calls.INCOMING_TYPE);
        assertCallTypeIconsEquals(Calls.INCOMING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(Calls.OUTGOING_TYPE);
        assertCallTypeIconsEquals(Calls.OUTGOING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(Calls.MISSED_TYPE);
        assertCallTypeIconsEquals(Calls.MISSED_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(Calls.VOICEMAIL_TYPE);
        assertCallTypeIconsEquals(Calls.VOICEMAIL_TYPE);
    }

    public void testSetPhoneCallDetails_MultipleCallTypeIcons() {
        setPhoneCallDetailsWithCallTypeIcons(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE);
        assertCallTypeIconsEquals(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(Calls.MISSED_TYPE, Calls.MISSED_TYPE);
        assertCallTypeIconsEquals(Calls.MISSED_TYPE, Calls.MISSED_TYPE);
    }

    public void testSetPhoneCallDetails_MultipleCallTypeIconsLastOneDropped() {
        setPhoneCallDetailsWithCallTypeIcons(Calls.MISSED_TYPE, Calls.MISSED_TYPE,
                Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE);
        assertCallTypeIconsEqualsPlusOverflow(
            getContext().getString(R.string.call_log_item_count, 4),
            Calls.MISSED_TYPE, Calls.MISSED_TYPE, Calls.INCOMING_TYPE);
    }

    public void testSetPhoneCallDetails_CallTypeText() {
        LocaleTestUtils localeTestUtils = new LocaleTestUtils(getContext());
        localeTestUtils.setLocale(Locale.US);
        try {
            setPhoneCallDetailsWithCallTypeText(Calls.INCOMING_TYPE);
            assertCallTypeTextEquals("Incoming call");

            setPhoneCallDetailsWithCallTypeText(Calls.OUTGOING_TYPE);
            assertCallTypeTextEquals("Outgoing call");

            setPhoneCallDetailsWithCallTypeText(Calls.MISSED_TYPE);
            assertCallTypeTextEquals("Missed call");

            setPhoneCallDetailsWithCallTypeText(Calls.VOICEMAIL_TYPE);
            assertCallTypeTextEquals("Voicemail");
        } finally {
            localeTestUtils.restoreLocale();
        }
    }

    public void testSetPhoneCallDetails_Geocode() {
        LocaleTestUtils localeTestUtils = new LocaleTestUtils(getContext());
        localeTestUtils.setLocale(Locale.US);
        try {
            setPhoneCallDetailsWithNumber("+14125555555", "1-412-555-5555");
            assertNameEquals("1-412-555-5555");  // The phone number is shown as the name.
            assertNumberEquals("Pennsylvania");  // The geocode is shown as the number.
        } finally {
            localeTestUtils.restoreLocale();
        }
    }

    public void testSetPhoneCallDetails_NoGeocode() {
        LocaleTestUtils localeTestUtils = new LocaleTestUtils(getContext());
        localeTestUtils.setLocale(Locale.US);
        try {
            setPhoneCallDetailsWithNumber("+0", "+0");
            assertNameEquals("+0");  // The phone number is shown as the name.
            assertNumberEquals("-");  // The empty geocode is shown as the number.
        } finally {
            localeTestUtils.restoreLocale();
        }
    }

    public void testSetPhoneName_NumberOnly() {
      setPhoneCallNameWithNumberOnly();
      assertEquals(View.VISIBLE, mNameView.getVisibility());
      assertEquals(TEST_FORMATTED_NUMBER, mNameView.getText().toString());
  }

    public void testSetPhoneName() {
      setPhoneCallName("John Doe");
      assertEquals(View.VISIBLE, mNameView.getVisibility());
      assertEquals("John Doe", mNameView.getText().toString());
  }

    /** Asserts that the name text field contains the value of the given string resource. */
    private void assertNameEqualsResource(int resId) {
        assertNameEquals(getContext().getString(resId));
    }

    /** Asserts that the name text field contains the given string value. */
    private void assertNameEquals(String text) {
        assertEquals(text, mViews.nameView.getText().toString());
    }

    /** Asserts that the number text field contains the given string value. */
    private void assertNumberEquals(String text) {
        assertEquals(text, mViews.numberView.getText().toString());
    }

    /** Asserts that the date text field contains the given string value. */
    private void assertDateEquals(String text) {
        assertEquals(text, mViews.dateView.getText().toString());
    }

    /** Asserts that the call type contains the images with the given drawables. */
    private void assertCallTypeIconsEquals(int... ids) {
        assertEquals(ids.length, mViews.callTypeIcons.getCount());
        for (int index = 0; index < ids.length; ++index) {
            int id = ids[index];
            assertEquals(id, mViews.callTypeIcons.getCallType(index));
        }
        assertEquals(View.VISIBLE, mViews.callTypeIcons.getVisibility());
        assertEquals(View.GONE, mViews.callTypeText.getVisibility());
        assertEquals(View.GONE, mViews.callTypeSeparator.getVisibility());
    }

    /**
     * Asserts that the call type contains the images with the given drawables and shows the given
     * text next to the icons.
     */
    private void assertCallTypeIconsEqualsPlusOverflow(String overflowText, int... ids) {
        assertEquals(ids.length, mViews.callTypeIcons.getCount());
        for (int index = 0; index < ids.length; ++index) {
            int id = ids[index];
            assertEquals(id, mViews.callTypeIcons.getCallType(index));
        }
        assertEquals(View.VISIBLE, mViews.callTypeIcons.getVisibility());
        assertEquals(View.VISIBLE, mViews.callTypeText.getVisibility());
        assertEquals(overflowText, mViews.callTypeText.getText().toString());
        assertEquals(View.VISIBLE, mViews.callTypeSeparator.getVisibility());
    }

    /** Asserts that the call type contains the given text. */
    private void assertCallTypeTextEquals(String text) {
        assertEquals(text, mViews.callTypeText.getText().toString());
        assertEquals(View.GONE, mViews.callTypeIcons.getVisibility());
        assertEquals(View.VISIBLE, mViews.callTypeText.getVisibility());
        assertEquals(View.VISIBLE, mViews.callTypeSeparator.getVisibility());
    }

    /** Sets the phone call details with default values and the given number. */
    private void setPhoneCallDetailsWithNumber(String number, String formattedNumber) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(number, formattedNumber, TEST_COUNTRY_ISO,
                        new int[]{ Calls.INCOMING_TYPE }, TEST_DATE, TEST_DURATION),
                false, false);
    }

    /** Sets the phone call details with default values and the given date. */
    private void setPhoneCallDetailsWithDate(long date) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        new int[]{ Calls.INCOMING_TYPE }, date, TEST_DURATION),
                false, false);
    }

    /** Sets the phone call details with default values and the given call types using icons. */
    private void setPhoneCallDetailsWithCallTypeIcons(int... callTypes) {
        setPhoneCallDetailsWithCallTypes(true, callTypes);
    }

    /** Sets the phone call details with default values and the given call types using text. */
    private void setPhoneCallDetailsWithCallTypeText(int... callTypes) {
        setPhoneCallDetailsWithCallTypes(false, callTypes);
    }

    private void setPhoneCallDetailsWithCallTypes(boolean useIcons, int... callTypes) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        callTypes, TEST_DATE, TEST_DURATION),
                useIcons, false);
    }

    private void setPhoneCallNameWithNumberOnly() {
        mHelper.setPhoneCallName(mNameView,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        new int[]{ Calls.INCOMING_TYPE }, TEST_DATE, TEST_DURATION));
    }

    private void setPhoneCallName(String name) {
        mHelper.setPhoneCallName(mNameView,
                new PhoneCallDetails(TEST_NUMBER, TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO,
                        new int[]{ Calls.INCOMING_TYPE }, TEST_DATE, TEST_DURATION,
                        name, 0, "", 1, null));
    }
}
