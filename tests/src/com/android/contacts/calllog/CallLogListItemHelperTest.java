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

import com.android.contacts.R;
import com.android.contacts.util.LocaleTestUtils;
import com.android.internal.telephony.CallerInfo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Unit tests for {@link CallLogListItemHelper}.
 */
public class CallLogListItemHelperTest extends AndroidTestCase {
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
        mHelper = new CallLogListItemHelper(context.getResources(), TEST_VOICEMAIL_NUMBER,
                TEST_INCOMING_DRAWABLE, TEST_OUTGOING_DRAWABLE, TEST_MISSED_DRAWABLE,
                TEST_VOICEMAIL_DRAWABLE, TEST_CALL_DRAWABLE, TEST_PLAY_DRAWABLE);
        mViews = new CallLogListItemViews();
        // Only set the views that are needed in the tests.
        mViews.iconView = new ImageView(context);
        mViews.dateView = new TextView(context);
        mViews.callView = new ImageView(context);
        mViews.line1View = new TextView(context);
        mViews.labelView = new TextView(context);
        mViews.numberView = new TextView(context);
    }

    @Override
    protected void tearDown() throws Exception {
        mHelper = null;
        mViews = null;
        super.tearDown();
    }

    public void testSetContactNumberOnly() {
        mHelper.setContactNumberOnly(mViews, "12125551234", "1-212-555-1234");
        assertEquals("1-212-555-1234", mViews.line1View.getText());
        assertEquals(View.GONE, mViews.labelView.getVisibility());
        assertEquals(View.GONE, mViews.numberView.getVisibility());
        assertEquals(View.VISIBLE, mViews.callView.getVisibility());
    }

    public void testSetContactNumberOnly_Unknown() {
        mHelper.setContactNumberOnly(mViews, CallerInfo.UNKNOWN_NUMBER, "");
        assertEquals(getContext().getString(R.string.unknown), mViews.line1View.getText());
        assertEquals(View.GONE, mViews.labelView.getVisibility());
        assertEquals(View.GONE, mViews.numberView.getVisibility());
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetContactNumberOnly_Private() {
        mHelper.setContactNumberOnly(mViews, CallerInfo.PRIVATE_NUMBER, "");
        assertEquals(getContext().getString(R.string.private_num), mViews.line1View.getText());
        assertEquals(View.GONE, mViews.labelView.getVisibility());
        assertEquals(View.GONE, mViews.numberView.getVisibility());
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetContactNumberOnly_Payphone() {
        mHelper.setContactNumberOnly(mViews, CallerInfo.PAYPHONE_NUMBER, "");
        assertEquals(getContext().getString(R.string.payphone), mViews.line1View.getText());
        assertEquals(View.GONE, mViews.labelView.getVisibility());
        assertEquals(View.GONE, mViews.numberView.getVisibility());
        assertEquals(View.INVISIBLE, mViews.callView.getVisibility());
    }

    public void testSetContactNumberOnly_Voicemail() {
        mHelper.setContactNumberOnly(mViews, TEST_VOICEMAIL_NUMBER, "");
        assertEquals(getContext().getString(R.string.voicemail), mViews.line1View.getText());
        assertEquals(View.GONE, mViews.labelView.getVisibility());
        assertEquals(View.GONE, mViews.numberView.getVisibility());
        assertEquals(View.VISIBLE, mViews.callView.getVisibility());
    }

    public void testSetDate() {
        // This test requires the locale to be set to US.
        LocaleTestUtils localeTestUtils = new LocaleTestUtils(getContext());
        localeTestUtils.setLocale(Locale.US);
        try {
            mHelper.setDate(mViews,
                    new GregorianCalendar(2011, 5, 1, 12, 0, 0).getTimeInMillis(),
                    new GregorianCalendar(2011, 5, 1, 13, 0, 0).getTimeInMillis());
            assertEquals("1 hour ago", mViews.dateView.getText());
            mHelper.setDate(mViews,
                    new GregorianCalendar(2010, 5, 1, 12, 0, 0).getTimeInMillis(),
                    new GregorianCalendar(2011, 5, 1, 13, 0, 0).getTimeInMillis());
            assertEquals("June 1, 2010", mViews.dateView.getText());
        } finally {
            localeTestUtils.restoreLocale();
        }
    }

    public void testSetCallType_Icon() {
        mHelper.setCallType(mViews, Calls.INCOMING_TYPE);
        assertEquals(TEST_INCOMING_DRAWABLE, mViews.iconView.getDrawable());
        mHelper.setCallType(mViews, Calls.OUTGOING_TYPE);
        assertEquals(TEST_OUTGOING_DRAWABLE, mViews.iconView.getDrawable());
        mHelper.setCallType(mViews, Calls.MISSED_TYPE);
        assertEquals(TEST_MISSED_DRAWABLE, mViews.iconView.getDrawable());
    }
}
