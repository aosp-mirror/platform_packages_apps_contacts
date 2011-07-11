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
import com.android.contacts.PhoneCallDetailsViews;
import com.android.internal.telephony.CallerInfo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

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
    /** A drawable to be used for incoming calls. */
    private static final Drawable TEST_INCOMING_DRAWABLE = new ColorDrawable(Color.BLACK);
    /** A drawable to be used for outgoing calls. */
    private static final Drawable TEST_OUTGOING_DRAWABLE = new ColorDrawable(Color.BLUE);
    /** A drawable to be used for missed calls. */
    private static final Drawable TEST_MISSED_DRAWABLE = new ColorDrawable(Color.RED);
    /** A drawable to be used for voicemails. */
    private static final Drawable TEST_VOICEMAIL_DRAWABLE = new ColorDrawable(Color.RED);
    /** A drawable to be used for the call action. */
    private static final Drawable TEST_CALL_DRAWABLE = new ColorDrawable(Color.RED);
    /** A drawable to be used for the play action. */
    private static final Drawable TEST_PLAY_DRAWABLE = new ColorDrawable(Color.RED);

    /** The object under test. */
    private CallLogListItemHelper mHelper;

    /** The views used in the tests. */
    private CallLogListItemViews mViews;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        Resources resources = context.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources,
                TEST_INCOMING_DRAWABLE, TEST_OUTGOING_DRAWABLE, TEST_MISSED_DRAWABLE,
                TEST_VOICEMAIL_DRAWABLE);
        PhoneNumberHelper phoneNumberHelper =
                new PhoneNumberHelper(resources, TEST_VOICEMAIL_NUMBER);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(context,
                resources, callTypeHelper, phoneNumberHelper);
        mHelper = new CallLogListItemHelper(phoneCallDetailsHelper, phoneNumberHelper,
                TEST_CALL_DRAWABLE, TEST_PLAY_DRAWABLE);
        mViews = CallLogListItemViews.createForTest(new QuickContactBadge(context),
                new ImageView(context), PhoneCallDetailsViews.createForTest(new TextView(context),
                        new LinearLayout(context), new TextView(context), new TextView(context),
                        new TextView(context), new TextView(context)));
    }

    @Override
    protected void tearDown() throws Exception {
        mHelper = null;
        mViews = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails() {
        setPhoneCallDetailsWithNumber("12125551234", "1-212-555-1234");
        assertEquals(View.VISIBLE, mViews.callView.getVisibility());
        assertEquals(TEST_CALL_DRAWABLE, mViews.callView.getDrawable());
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber(CallerInfo.UNKNOWN_NUMBER, CallerInfo.UNKNOWN_NUMBER);
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber(CallerInfo.PRIVATE_NUMBER, CallerInfo.PRIVATE_NUMBER);
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber(CallerInfo.PAYPHONE_NUMBER, CallerInfo.PAYPHONE_NUMBER);
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetPhoneCallDetails_VoicemailNumber() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER, TEST_VOICEMAIL_NUMBER);
        assertEquals(View.VISIBLE, mViews.callView.getVisibility());
        assertEquals(TEST_CALL_DRAWABLE, mViews.callView.getDrawable());
    }

    public void testSetPhoneCallDetails_Voicemail() {
        setPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.callView.getVisibility());
        assertEquals(TEST_PLAY_DRAWABLE, mViews.callView.getDrawable());
    }

    /** Sets the details of a phone call using the specified phone number. */
    private void setPhoneCallDetailsWithNumber(String number, String formattedNumber) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(number, formattedNumber, new int[]{ Calls.INCOMING_TYPE },
                        TEST_DATE, TEST_DURATION),
                true);
    }

    /** Sets the details of a phone call using the specified call type. */
    private void setPhoneCallDetailsWithTypes(int... types) {
        mHelper.setPhoneCallDetails(mViews,
                new PhoneCallDetails(
                        TEST_NUMBER, TEST_FORMATTED_NUMBER, types, TEST_DATE, TEST_DURATION),
                true);
    }
}
