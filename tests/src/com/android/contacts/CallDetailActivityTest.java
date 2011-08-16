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

import static com.android.contacts.CallDetailActivity.Tasks.UPDATE_PHONE_CALL_DETAILS;
import static com.android.contacts.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_FOR_CONTENT;
import static com.android.contacts.voicemail.VoicemailPlaybackPresenter.Tasks.PREPARE_MEDIA_PLAYER;

import com.android.contacts.util.AsyncTaskExecutors;
import com.android.contacts.util.FakeAsyncTaskExecutor;
import com.android.contacts.util.IntegrationTestUtils;
import com.android.contacts.util.LocaleTestUtils;
import com.android.internal.view.menu.ContextMenuBuilder;
import com.google.common.base.Preconditions;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.VoicemailContract;
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
    private Uri mCallLogUri;
    private Uri mVoicemailUri;
    private IntegrationTestUtils mTestUtils;
    private LocaleTestUtils mLocaleTestUtils;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;
    private CallDetailActivity mActivityUnderTest;

    public CallDetailActivityTest() {
        super(CallDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());
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
        AsyncTaskExecutors.setFactoryForTest(null);
        super.tearDown();
    }

    public void testInitialActivityStartsWithFetchingVoicemail() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        // When the activity first starts, we will show "Fetching voicemail" on the screen.
        // The duration should not be visible.
        assertHasOneTextViewContaining("Fetching voicemail");
        assertZeroTextViewsContaining("00:00");
    }

    public void testWhenCheckForContentCompletes_UiShowsBuffering() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        // There is a background check that is testing to see if we have the content available.
        // Once that task completes, we shouldn't be showing the fetching message, we should
        // be showing "Buffering".
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        assertHasOneTextViewContaining("Buffering");
        assertZeroTextViewsContaining("Fetching voicemail");
    }

    public void testInvalidVoicemailShowsErrorMessage() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        // There should be exactly one background task ready to prepare the media player.
        // Preparing the media player will have thrown an IOException since the file doesn't exist.
        // This should have put a failed to play message on screen, buffering is gone.
        mFakeAsyncTaskExecutor.runTask(PREPARE_MEDIA_PLAYER);
        assertHasOneTextViewContaining("Couldn't play voicemail");
        assertZeroTextViewsContaining("Buffering");
    }

    public void testOnResumeDoesNotCreateManyFragments() throws Throwable {
        // There was a bug where every time the activity was resumed, a new fragment was created.
        // Before the fix, this was failing reproducibly with at least 3 "Buffering" views.
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().callActivityOnPause(mActivityUnderTest);
                getInstrumentation().callActivityOnResume(mActivityUnderTest);
                getInstrumentation().callActivityOnPause(mActivityUnderTest);
                getInstrumentation().callActivityOnResume(mActivityUnderTest);
            }
        });
        assertHasOneTextViewContaining("Buffering");
    }

    /**
     * Test for bug where increase rate button with invalid voicemail causes a crash.
     * <p>
     * The repro steps for this crash were to open a voicemail that does not have an attachment,
     * then click the play button (which just reported an error), then after that try to adjust the
     * rate.  See http://b/5047879.
     */
    public void testClickIncreaseRateButtonWithInvalidVoicemailDoesNotCrash() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mTestUtils.clickButton(mActivityUnderTest, R.id.playback_start_stop);
        mTestUtils.clickButton(mActivityUnderTest, R.id.rate_increase_button);
    }

    /** Test for bug where missing Extras on intent used to start Activity causes NPE. */
    public void testCallLogUriWithMissingExtrasShouldNotCauseNPE() throws Throwable {
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
    }

    /**
     * Test for bug where voicemails should not have remove-from-call-log entry.
     * <p>
     * See http://b/5054103.
     */
    public void testVoicemailDoesNotHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
    }

    /** Test to check that I haven't broken the remove-from-call-log entry from regular calls. */
    public void testRegularCallDoesHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
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
        startActivityUnderTest();
        // Find the TextView containing the duration.  It should be initially displaying "00:00".
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, "00:00");
        assertEquals(1, views.size());
        TextView timeDisplay = views.get(0);
        // Hit the plus button.  At this point we should be displaying "fast speed".
        mTestUtils.clickButton(mActivityUnderTest, R.id.rate_increase_button);
        assertEquals("fast speed", mTestUtils.getText(timeDisplay));
        // Hit the minus button.  We should be back to "normal" speed.
        mTestUtils.clickButton(mActivityUnderTest, R.id.rate_decrease_button);
        assertEquals("normal speed", mTestUtils.getText(timeDisplay));
        // Wait for one and a half seconds.  The timer will be back.
        Thread.sleep(1500);
        assertEquals("00:00", mTestUtils.getText(timeDisplay));
    }

    private void setActivityIntentForTestCallEntry() {
        Preconditions.checkState(mCallLogUri == null, "mUri should be null");
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, "01234567890");
        values.put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE);
        mCallLogUri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
        setActivityIntent(new Intent(Intent.ACTION_VIEW, mCallLogUri));
    }

    private void setActivityIntentForTestVoicemailEntry() {
        Preconditions.checkState(mVoicemailUri == null, "mUri should be null");
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.NUMBER, "01234567890");
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
        mVoicemailUri = contentResolver.insert(VoicemailContract.Voicemails.CONTENT_URI, values);
        Uri callLogUri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                ContentUris.parseId(mVoicemailUri));
        Intent intent = new Intent(Intent.ACTION_VIEW, callLogUri);
        intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, mVoicemailUri);
        setActivityIntent(intent);
    }

    private void cleanUpUri() {
        if (mVoicemailUri != null) {
            getContentResolver().delete(VoicemailContract.Voicemails.CONTENT_URI,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mVoicemailUri)) });
            mVoicemailUri = null;
        }
        if (mCallLogUri != null) {
            getContentResolver().delete(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mCallLogUri)) });
            mCallLogUri = null;
        }
    }

    private ContentResolver getContentResolver() {
        return getInstrumentation().getTargetContext().getContentResolver();
    }

    private TextView assertHasOneTextViewContaining(String text) throws Throwable {
        Preconditions.checkNotNull(mActivityUnderTest, "forget to call startActivityUnderTest()?");
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been one TextView with text '" + text + "' but found "
                + views, 1, views.size());
        return views.get(0);
    }

    private void assertZeroTextViewsContaining(String text) throws Throwable {
        Preconditions.checkNotNull(mActivityUnderTest, "forget to call startActivityUnderTest()?");
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been no TextViews with text '" + text + "' but found "
                + views, 0,  views.size());
    }

    private void startActivityUnderTest() throws Throwable {
        Preconditions.checkState(mActivityUnderTest == null, "must only start the activity once");
        mActivityUnderTest = getActivity();
        assertNotNull("activity should not be null", mActivityUnderTest);
        // We have to run all tasks, not just one.
        // This is because it seems that we can have onResume, onPause, onResume during the course
        // of a single unit test.
        mFakeAsyncTaskExecutor.runAllTasks(UPDATE_PHONE_CALL_DETAILS);
    }
}
