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

import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE;
import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_CAN_BE_CONFIGURED;
import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_NOT_CONFIGURED;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_OK;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK;

import com.android.contacts.R;
import com.android.contacts.calllog.VoicemailStatusHelper.StatusMessage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.VoicemailContract.Status;
import android.test.AndroidTestCase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link VoicemailStatusHelperImpl}.
 */
public class VoicemailStatusHelperImplTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_1 = "com.test.package1";
    private static final String TEST_PACKAGE_2 = "com.test.package2";

    private static final Uri TEST_SETTINGS_URI = Uri.parse("http://www.visual.voicemail.setup");
    private static final Uri TEST_VOICEMAIL_URI = Uri.parse("tel:901");

    private static final int ACTION_MSG_CALL_VOICEMAIL =
            R.string.voicemail_status_action_call_server;
    private static final int ACTION_MSG_CONFIGURE = R.string.voicemail_status_action_configure;

    private static final int STATUS_MSG_VOICEMAIL_NOT_AVAILABLE =
            R.string.voicemail_status_voicemail_not_available;
    private static final int STATUS_MSG_AUDIO_NOT_AVAIALABLE =
            R.string.voicemail_status_audio_not_available;
    private static final int STATUS_MSG_MESSAGE_WAITING = R.string.voicemail_status_messages_waiting;
    private static final int STATUS_MSG_INVITE_FOR_CONFIGURATION =
            R.string.voicemail_status_configure_voicemail;

    // The packages whose status entries have been added during the test and needs to be cleaned
    // up in teardown.
    private Set<String> mPackagesToCleanup = new HashSet<String>();
    // Object under test.
    private VoicemailStatusHelper mStatusHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mStatusHelper = new VoicemailStatusHelperImpl(getContentResolver());
    }

    @Override
    protected void tearDown() throws Exception {
        for (String sourcePackage : mPackagesToCleanup) {
            deleteEntryForPackage(sourcePackage);
        }
        mPackagesToCleanup.clear();
        // Set member variables to null so that they are garbage collected across different runs
        // of the tests.
        mStatusHelper = null;
        mPackagesToCleanup = null;
        super.tearDown();
    }

    public void testNoStatusEntries() {
        assertEquals(0, mStatusHelper.getStatusMessages().size());
    }

    public void testAllOK() {
        insertEntryForPackage(TEST_PACKAGE_1, getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGE_2, getAllOkStatusValues());
        assertEquals(0, mStatusHelper.getStatusMessages().size());
    }

    public void testNotAllOKForOnePackage() {
        insertEntryForPackage(TEST_PACKAGE_1, getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGE_2, getAllOkStatusValues());

        ContentValues values = new ContentValues();
        // No notification + good data channel - for now same as no connection.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_OK);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkExpectedMessage(TEST_PACKAGE_2, values, STATUS_MSG_VOICEMAIL_NOT_AVAILABLE,
                ACTION_MSG_CALL_VOICEMAIL);

        // Message waiting + good data channel - no action.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_OK);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkNoMessages(TEST_PACKAGE_2, values);

        // Notification OK + no data channel - call voicemail/no audio.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_OK);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkExpectedMessage(TEST_PACKAGE_2, values, STATUS_MSG_AUDIO_NOT_AVAIALABLE,
                ACTION_MSG_CALL_VOICEMAIL);

        // No notification + no data channel - call voicemail/no connection.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkExpectedMessage(TEST_PACKAGE_2, values, STATUS_MSG_VOICEMAIL_NOT_AVAILABLE,
                ACTION_MSG_CALL_VOICEMAIL);

        // Message waiting + no data channel - call voicemail.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkExpectedMessage(TEST_PACKAGE_2, values, STATUS_MSG_MESSAGE_WAITING,
                ACTION_MSG_CALL_VOICEMAIL);

        // Not configured. No user action, so no message.
        values.put(CONFIGURATION_STATE, CONFIGURATION_STATE_NOT_CONFIGURED);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkNoMessages(TEST_PACKAGE_2, values);

        // Can be configured - invite user for configure voicemail.
        values.put(CONFIGURATION_STATE, CONFIGURATION_STATE_CAN_BE_CONFIGURED);
        updateEntryForPackage(TEST_PACKAGE_2, values);
        checkExpectedMessage(TEST_PACKAGE_2, values, STATUS_MSG_INVITE_FOR_CONFIGURATION,
                ACTION_MSG_CONFIGURE, TEST_SETTINGS_URI);
    }

    // Test that priority of messages are handled well.
    public void testMessageOrdering() {
        insertEntryForPackage(TEST_PACKAGE_1, getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGE_2, getAllOkStatusValues());

        final ContentValues valuesNoNotificationGoodDataChannel = new ContentValues();
        valuesNoNotificationGoodDataChannel.put(NOTIFICATION_CHANNEL_STATE,
                NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        valuesNoNotificationGoodDataChannel.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_OK);

        final ContentValues valuesNoNotificationNoDataChannel = new ContentValues();
        valuesNoNotificationNoDataChannel.put(NOTIFICATION_CHANNEL_STATE,
                NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        valuesNoNotificationNoDataChannel.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);

        // Package1 with valuesNoNotificationGoodDataChannel and
        // package2 with  valuesNoNotificationNoDataChannel. Package2 should be above.
        updateEntryForPackage(TEST_PACKAGE_1, valuesNoNotificationGoodDataChannel);
        updateEntryForPackage(TEST_PACKAGE_2, valuesNoNotificationNoDataChannel);
        List<StatusMessage> messages = mStatusHelper.getStatusMessages();
        assertEquals(2, messages.size());
        assertEquals(TEST_PACKAGE_1, messages.get(1).sourcePackage);
        assertEquals(TEST_PACKAGE_2, messages.get(0).sourcePackage);

        // Now reverse the values - ordering should be reversed as well.
        updateEntryForPackage(TEST_PACKAGE_1, valuesNoNotificationNoDataChannel);
        updateEntryForPackage(TEST_PACKAGE_2, valuesNoNotificationGoodDataChannel);
        messages = mStatusHelper.getStatusMessages();
        assertEquals(2, messages.size());
        assertEquals(TEST_PACKAGE_1, messages.get(0).sourcePackage);
        assertEquals(TEST_PACKAGE_2, messages.get(1).sourcePackage);
    }

    /** Checks for the expected message with given values and actionUri as TEST_VOICEMAIL_URI. */
    private void checkExpectedMessage(String sourcePackage, ContentValues values,
            int expectedStatusMsg, int expectedActionMsg) {
        checkExpectedMessage(sourcePackage, values, expectedStatusMsg, expectedActionMsg,
                TEST_VOICEMAIL_URI);
    }

    private void checkExpectedMessage(String sourcePackage, ContentValues values,
            int expectedStatusMsg, int expectedActionMsg, Uri expectedUri) {
        List<StatusMessage> messages = mStatusHelper.getStatusMessages();
        assertEquals(1, messages.size());
        checkMessageMatches(messages.get(0), sourcePackage, expectedStatusMsg, expectedActionMsg,
                expectedUri);
    }

    private void checkMessageMatches(StatusMessage message, String expectedSourcePackage,
            int expectedStatusMsg, int expectedActionMsg, Uri expectedUri) {
        assertEquals(expectedSourcePackage, message.sourcePackage);
        assertEquals(expectedStatusMsg, message.statusMessageId);
        assertEquals(expectedActionMsg, message.actionMessageId);
        if (expectedUri == null) {
            assertNull(message.actionUri);
        } else {
            assertEquals(expectedUri, message.actionUri);
        }
    }

    private void checkNoMessages(String sourcePackage, ContentValues values) {
        assertEquals(1, updateEntryForPackage(sourcePackage, values));
        List<StatusMessage> messages = mStatusHelper.getStatusMessages();
        assertEquals(0, messages.size());
    }

    private ContentValues getAllOkStatusValues() {
        ContentValues values = new ContentValues();
        values.put(Status.SETTINGS_URI, TEST_SETTINGS_URI.toString());
        values.put(Status.VOICEMAIL_ACCESS_URI, TEST_VOICEMAIL_URI.toString());
        values.put(Status.CONFIGURATION_STATE, Status.CONFIGURATION_STATE_OK);
        values.put(Status.DATA_CHANNEL_STATE, Status.DATA_CHANNEL_STATE_OK);
        values.put(Status.NOTIFICATION_CHANNEL_STATE, Status.NOTIFICATION_CHANNEL_STATE_OK);
        return values;
    }

    private void insertEntryForPackage(String sourcePackage, ContentValues values) {
        // If insertion fails then try update as the record might already exist.
        if (getContentResolver().insert(Status.buildSourceUri(sourcePackage), values) == null) {
            updateEntryForPackage(sourcePackage, values);
        }
        mPackagesToCleanup.add(sourcePackage);
    }

    private void deleteEntryForPackage(String sourcePackage) {
        getContentResolver().delete(Status.buildSourceUri(sourcePackage), null, null);
    }

    private int updateEntryForPackage(String sourcePackage, ContentValues values) {
        return getContentResolver().update(
                Status.buildSourceUri(sourcePackage), values, null, null);
    }

    private ContentResolver getContentResolver() {
        return getContext().getContentResolver();
    }
}
