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

package com.android.contacts.voicemail;

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
import com.android.contacts.voicemail.VoicemailStatusHelper.StatusMessage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract.Status;
import android.test.AndroidTestCase;

import java.util.List;

/**
 * Unit tests for {@link VoicemailStatusHelperImpl}.
 */
public class VoicemailStatusHelperImplTest extends AndroidTestCase {
    private static final String[] TEST_PACKAGES = new String[] {
        "com.test.package1",
        "com.test.package2"
    };

    private static final Uri TEST_SETTINGS_URI = Uri.parse("http://www.visual.voicemail.setup");
    private static final Uri TEST_VOICEMAIL_URI = Uri.parse("tel:901");

    private static final int ACTION_MSG_CALL_VOICEMAIL =
            R.string.voicemail_status_action_call_server;
    private static final int ACTION_MSG_CONFIGURE = R.string.voicemail_status_action_configure;

    private static final int STATUS_MSG_NONE = -1;
    private static final int STATUS_MSG_VOICEMAIL_NOT_AVAILABLE =
            R.string.voicemail_status_voicemail_not_available;
    private static final int STATUS_MSG_AUDIO_NOT_AVAIALABLE =
            R.string.voicemail_status_audio_not_available;
    private static final int STATUS_MSG_MESSAGE_WAITING = R.string.voicemail_status_messages_waiting;
    private static final int STATUS_MSG_INVITE_FOR_CONFIGURATION =
            R.string.voicemail_status_configure_voicemail;

    // Object under test.
    private VoicemailStatusHelper mStatusHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mStatusHelper = new VoicemailStatusHelperImpl();
    }

    @Override
    protected void tearDown() throws Exception {
        for (String sourcePackage : TEST_PACKAGES) {
            deleteEntryForPackage(sourcePackage);
        }
        // Set member variables to null so that they are garbage collected across different runs
        // of the tests.
        mStatusHelper = null;
        super.tearDown();
    }


    public void testNoStatusEntries() {
        assertEquals(0, getStatusMessages().size());
    }

    public void testAllOK() {
        insertEntryForPackage(TEST_PACKAGES[0], getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGES[1], getAllOkStatusValues());
        assertEquals(0, getStatusMessages().size());
    }

    public void testNotAllOKForOnePackage() {
        insertEntryForPackage(TEST_PACKAGES[0], getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGES[1], getAllOkStatusValues());

        ContentValues values = new ContentValues();
        // Good data channel + no notification
        // action: call voicemail
        // msg: voicemail not available in call log page & none in call details page.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_OK);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkExpectedMessage(TEST_PACKAGES[1], values, STATUS_MSG_VOICEMAIL_NOT_AVAILABLE,
                STATUS_MSG_NONE, ACTION_MSG_CALL_VOICEMAIL);

        // Message waiting + good data channel - no action.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_OK);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkNoMessages(TEST_PACKAGES[1], values);

        // No data channel + no notification
        // action: call voicemail
        // msg: voicemail not available in call log page & audio not available in call details page.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_OK);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkExpectedMessage(TEST_PACKAGES[1], values, STATUS_MSG_VOICEMAIL_NOT_AVAILABLE,
                STATUS_MSG_AUDIO_NOT_AVAIALABLE, ACTION_MSG_CALL_VOICEMAIL);

        // No data channel + Notification OK
        // action: call voicemail
        // msg: voicemail not available in call log page & audio not available in call details page.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkExpectedMessage(TEST_PACKAGES[1], values, STATUS_MSG_VOICEMAIL_NOT_AVAILABLE,
                STATUS_MSG_AUDIO_NOT_AVAIALABLE, ACTION_MSG_CALL_VOICEMAIL);

        // No data channel + Notification OK
        // action: call voicemail
        // msg: message waiting in call log page & audio not available in call details page.
        values.put(NOTIFICATION_CHANNEL_STATE, NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING);
        values.put(DATA_CHANNEL_STATE, DATA_CHANNEL_STATE_NO_CONNECTION);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkExpectedMessage(TEST_PACKAGES[1], values, STATUS_MSG_MESSAGE_WAITING,
                STATUS_MSG_AUDIO_NOT_AVAIALABLE, ACTION_MSG_CALL_VOICEMAIL);

        // Not configured. No user action, so no message.
        values.put(CONFIGURATION_STATE, CONFIGURATION_STATE_NOT_CONFIGURED);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkNoMessages(TEST_PACKAGES[1], values);

        // Can be configured - invite user for configure voicemail.
        values.put(CONFIGURATION_STATE, CONFIGURATION_STATE_CAN_BE_CONFIGURED);
        updateEntryForPackage(TEST_PACKAGES[1], values);
        checkExpectedMessage(TEST_PACKAGES[1], values, STATUS_MSG_INVITE_FOR_CONFIGURATION,
                STATUS_MSG_NONE, ACTION_MSG_CONFIGURE, TEST_SETTINGS_URI);
    }

    // Test that priority of messages are handled well.
    public void testMessageOrdering() {
        insertEntryForPackage(TEST_PACKAGES[0], getAllOkStatusValues());
        insertEntryForPackage(TEST_PACKAGES[1], getAllOkStatusValues());

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
        updateEntryForPackage(TEST_PACKAGES[0], valuesNoNotificationGoodDataChannel);
        updateEntryForPackage(TEST_PACKAGES[1], valuesNoNotificationNoDataChannel);
        List<StatusMessage> messages = getStatusMessages();
        assertEquals(2, messages.size());
        assertEquals(TEST_PACKAGES[0], messages.get(1).sourcePackage);
        assertEquals(TEST_PACKAGES[1], messages.get(0).sourcePackage);

        // Now reverse the values - ordering should be reversed as well.
        updateEntryForPackage(TEST_PACKAGES[0], valuesNoNotificationNoDataChannel);
        updateEntryForPackage(TEST_PACKAGES[1], valuesNoNotificationGoodDataChannel);
        messages = getStatusMessages();
        assertEquals(2, messages.size());
        assertEquals(TEST_PACKAGES[0], messages.get(0).sourcePackage);
        assertEquals(TEST_PACKAGES[1], messages.get(1).sourcePackage);
    }

    /** Checks that the expected source status message is returned by VoicemailStatusHelper. */
    private void checkExpectedMessage(String sourcePackage, ContentValues values,
            int expectedCallLogMsg, int expectedCallDetailsMsg, int expectedActionMsg,
            Uri expectedUri) {
        List<StatusMessage> messages = getStatusMessages();
        assertEquals(1, messages.size());
        checkMessageMatches(messages.get(0), sourcePackage, expectedCallLogMsg,
                expectedCallDetailsMsg, expectedActionMsg, expectedUri);
    }

    private void checkExpectedMessage(String sourcePackage, ContentValues values,
            int expectedCallLogMsg, int expectedCallDetailsMessage, int expectedActionMsg) {
        checkExpectedMessage(sourcePackage, values, expectedCallLogMsg, expectedCallDetailsMessage,
                expectedActionMsg, TEST_VOICEMAIL_URI);
    }

    private void checkMessageMatches(StatusMessage message, String expectedSourcePackage,
            int expectedCallLogMsg, int expectedCallDetailsMsg, int expectedActionMsg,
            Uri expectedUri) {
        assertEquals(expectedSourcePackage, message.sourcePackage);
        assertEquals(expectedCallLogMsg, message.callLogMessageId);
        assertEquals(expectedCallDetailsMsg, message.callDetailsMessageId);
        assertEquals(expectedActionMsg, message.actionMessageId);
        if (expectedUri == null) {
            assertNull(message.actionUri);
        } else {
            assertEquals(expectedUri, message.actionUri);
        }
    }

    private void checkNoMessages(String sourcePackage, ContentValues values) {
        assertEquals(1, updateEntryForPackage(sourcePackage, values));
        List<StatusMessage> messages = getStatusMessages();
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
    }

    private void deleteEntryForPackage(String sourcePackage) {
        getContentResolver().delete(Status.buildSourceUri(sourcePackage), null, null);
    }

    private int updateEntryForPackage(String sourcePackage, ContentValues values) {
        return getContentResolver().update(
                Status.buildSourceUri(sourcePackage), values, null, null);
    }

    private List<StatusMessage> getStatusMessages() {
        // Restrict the cursor to only the the test packages to eliminate any side effects if there
        // are other status messages already stored on the device.
        Cursor cursor = getContentResolver().query(Status.CONTENT_URI,
                VoicemailStatusHelperImpl.PROJECTION, getTestPackageSelection(), null, null);
        return mStatusHelper.getStatusMessages(cursor);
    }

    private String getTestPackageSelection() {
        StringBuilder sb = new StringBuilder();
        for (String sourcePackage : TEST_PACKAGES) {
            if (sb.length() > 0) {
                sb.append(" OR ");
            }
            sb.append(String.format("(source_package='%s')", sourcePackage));
        }
        return sb.toString();
    }

    private ContentResolver getContentResolver() {
        return getContext().getContentResolver();
    }
}
