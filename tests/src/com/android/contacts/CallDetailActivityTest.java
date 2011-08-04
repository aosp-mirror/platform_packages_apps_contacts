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

package com.android.contacts;

import com.android.contacts.util.IntegrationTestUtils;
import com.android.contacts.util.LocaleTestUtils;
import com.android.internal.view.menu.ContextMenuBuilder;
import com.google.common.base.Preconditions;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.Menu;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Unit tests for the {@link CallDetailActivity}.
 */
@LargeTest
public class CallDetailActivityTest extends ActivityInstrumentationTestCase2<CallDetailActivity> {
    private static final String FAKE_VOICEMAIL_URI_STRING = "content://fake_uri";
    private Uri mUri;
    private IntegrationTestUtils mTestUtils;
    private LocaleTestUtils mLocaleTestUtils;

    public CallDetailActivityTest() {
        super(CallDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // I don't like the default of focus-mode for tests, the green focus border makes the
        // screenshots look weak.
        setActivityInitialTouchMode(true);
        mTestUtils = new IntegrationTestUtils(getInstrumentation());
        // Some of the tests rely on the text that appears on screen - safest to force a
        // specific locale.
        mLocaleTestUtils = new LocaleTestUtils(getInstrumentation().getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        mLocaleTestUtils.restoreLocale();
        mLocaleTestUtils = null;
        cleanUpUri();
        mTestUtils = null;
        super.tearDown();
    }

    /**
     * Test for bug where increase rate button with invalid voicemail causes a crash.
     * <p>
     * The repro steps for this crash were to open a voicemail that does not have an attachment,
     * then click the play button (which just reported an error), then after that try to adjust the
     * rate.  See http://b/5047879.
     */
    @Suppress
    public void testClickIncreaseRateButtonWithInvalidVoicemailDoesNotCrash() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        Activity activity = getActivity();
        mTestUtils.clickButton(activity, R.id.playback_start_stop);
        mTestUtils.clickButton(activity, R.id.rate_increase_button);
    }

    /** Test for bug where missing Extras on intent used to start Activity causes NPE. */
    @Suppress
    public void testCallLogUriWithMissingExtrasShouldNotCauseNPE() throws Exception {
        setActivityIntentForTestCallEntry();
        getActivity();
    }

    /**
     * Test for bug where voicemails should not have remove-from-call-log entry.
     * <p>
     * See http://b/5054103.
     */
    @Suppress
    public void testVoicemailDoesNotHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        CallDetailActivity activity = getActivity();
        Menu menu = new ContextMenuBuilder(activity);
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
    }

    /** Test to check that I haven't broken the remove-from-call-log entry from regular calls. */
    public void testRegularCallDoesHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestCallEntry();
        CallDetailActivity activity = getActivity();
        Menu menu = new ContextMenuBuilder(activity);
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
    }

    /**
     * Test to show that we are correctly displaying playback rate on the ui.
     * <p>
     * See bug http://b/5044075.
     */
    @Suppress
    public void testVoicemailPlaybackRateDisplayedOnUi() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        CallDetailActivity activity = getActivity();
        // Find the TextView containing the duration.  It should be initially displaying "00:00".
        List<TextView> views = mTestUtils.getTextViewsWithString(activity, "00:00");
        assertEquals(1, views.size());
        TextView timeDisplay = views.get(0);
        // Hit the plus button.  At this point we should be displaying "fast speed".
        mTestUtils.clickButton(activity, R.id.rate_increase_button);
        assertEquals("fast speed", mTestUtils.getText(timeDisplay));
        // Hit the minus button.  We should be back to "normal" speed.
        mTestUtils.clickButton(activity, R.id.rate_decrease_button);
        assertEquals("normal speed", mTestUtils.getText(timeDisplay));
        // Wait for one and a half seconds.  The timer will be back.
        Thread.sleep(1500);
        assertEquals("00:00", mTestUtils.getText(timeDisplay));
    }

    private void setActivityIntentForTestCallEntry() {
        createTestCallEntry(false);
        setActivityIntent(new Intent(Intent.ACTION_VIEW, mUri));
    }

    private void setActivityIntentForTestVoicemailEntry() {
        createTestCallEntry(true);
        Intent intent = new Intent(Intent.ACTION_VIEW, mUri);
        Uri voicemailUri = Uri.parse(FAKE_VOICEMAIL_URI_STRING);
        intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, voicemailUri);
        setActivityIntent(intent);
    }

    /** Inserts an entry into the call log. */
    private void createTestCallEntry(boolean isVoicemail) {
        Preconditions.checkState(mUri == null, "mUri should be null");
        ContentResolver contentResolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(CallLog.Calls.NUMBER, "01234567890");
        if (isVoicemail) {
            contentValues.put(CallLog.Calls.TYPE, CallLog.Calls.VOICEMAIL_TYPE);
            contentValues.put(CallLog.Calls.VOICEMAIL_URI, FAKE_VOICEMAIL_URI_STRING);
        } else {
            contentValues.put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE);
        }
        contentValues.put(CallLog.Calls.VOICEMAIL_URI, FAKE_VOICEMAIL_URI_STRING);
        mUri = contentResolver.insert(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL, contentValues);
    }

    private void cleanUpUri() {
        if (mUri != null) {
            getContentResolver().delete(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mUri)) });
            mUri = null;
        }
    }

    private ContentResolver getContentResolver() {
        return getInstrumentation().getTargetContext().getContentResolver();
    }
}
