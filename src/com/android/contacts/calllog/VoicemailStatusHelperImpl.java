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
 * limitations under the License
 */

package com.android.contacts.calllog;

import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_CAN_BE_CONFIGURED;
import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_OK;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_OK;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK;

import com.android.common.io.MoreCloseables;
import com.android.contacts.R;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Implementation of {@link VoicemailStatusHelper}. */
public class VoicemailStatusHelperImpl implements VoicemailStatusHelper {
    private static final int SOURCE_PACKAGE_INDEX = 0;
    private static final int CONFIGURATION_STATE_INDEX = 1;
    private static final int DATA_CHANNEL_STATE_INDEX = 2;
    private static final int NOTIFICATION_CHANNEL_STATE_INDEX = 3;
    private static final int SETTINGS_URI_INDEX = 4;
    private static final int VOICEMAIL_ACCESS_URI_INDEX = 5;
    private static final int NUM_COLUMNS = 6;
    private static final String[] PROJECTION = new String[NUM_COLUMNS];
    static {
        PROJECTION[SOURCE_PACKAGE_INDEX] = Status.SOURCE_PACKAGE;
        PROJECTION[CONFIGURATION_STATE_INDEX] = Status.CONFIGURATION_STATE;
        PROJECTION[DATA_CHANNEL_STATE_INDEX] = Status.DATA_CHANNEL_STATE;
        PROJECTION[NOTIFICATION_CHANNEL_STATE_INDEX] = Status.NOTIFICATION_CHANNEL_STATE;
        PROJECTION[SETTINGS_URI_INDEX] = Status.SETTINGS_URI;
        PROJECTION[VOICEMAIL_ACCESS_URI_INDEX] = Status.VOICEMAIL_ACCESS_URI;
    }

    /** Possible user actions. */
    public static enum Action {
        NONE(-1),
        CALL_VOICEMAIL(R.string.voicemail_status_action_call_server),
        CONFIGURE_VOICEMAIL(R.string.voicemail_status_action_configure);

        private final int mMessageId;
        private Action(int messageId) {
            mMessageId = messageId;
        }

        public int getMessageId() {
            return mMessageId;
        }
    }

    /**
     * Overall state of the source status. Each state is associated with the corresponding display
     * string and the corrective action. The states are also assigned a relative priority which is
     * used to order the messages from different sources.
     */
    private static enum OverallState {
        // TODO: Add separate string for call details and call log pages for the states that needs
        // to be shown in both.
        /** Both notification and data channel are not working. */
        NO_CONNECTION(0, Action.CALL_VOICEMAIL, R.string.voicemail_status_voicemail_not_available),
        /** Notifications working, but data channel is not working. Audio cannot be downloaded. */
        NO_DATA(1, Action.CALL_VOICEMAIL, R.string.voicemail_status_audio_not_available),
        /** Messages are known to be waiting but data channel is not working. */
        MESSAGE_WAITING(2, Action.CALL_VOICEMAIL, R.string.voicemail_status_messages_waiting),
        /** Notification channel not working, but data channel is. */
        NO_NOTIFICATIONS(3, Action.CALL_VOICEMAIL,
                R.string.voicemail_status_voicemail_not_available),
        /** Invite user to set up voicemail. */
        INVITE_FOR_CONFIGURATION(4, Action.CONFIGURE_VOICEMAIL,
                R.string.voicemail_status_configure_voicemail),
        /**
         * No detailed notifications, but data channel is working.
         * This is normal mode of operation for certain sources. No action needed.
         */
        NO_DETAILED_NOTIFICATION(5, Action.NONE, -1),
        /** Visual voicemail not yet set up. No local action needed. */
        NOT_CONFIGURED(6, Action.NONE, -1),
        /** Everything is OK. */
        OK(7, Action.NONE, -1),
        /** If one or more state value set by the source is not valid. */
        INVALID(8, Action.NONE, -1);

        private final int mPriority;
        private final Action mAction;
        private final int mMessageId;

        private OverallState(int priority, Action action, int messageId) {
            mPriority = priority;
            mAction = action;
            mMessageId = messageId;
        }

        public Action getAction() {
            return mAction;
        }

        public int getPriority() {
            return mPriority;
        }

        public int getMessageId() {
            return mMessageId;
        }
    }

    private final ContentResolver mContentResolver;

    public VoicemailStatusHelperImpl(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    /** A wrapper on {@link Message} which additionally stores the priority of the message. */
    private static class MessageWrapper {
        private final Message mMessage;
        private final int mPriority;

        public MessageWrapper(Message message, int priority) {
            mMessage = message;
            mPriority = priority;
        }
    }

    @Override
    public List<Message> getStatusMessages() {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(Status.CONTENT_URI, PROJECTION, null, null, null);
            List<MessageWrapper> messages =
                    new ArrayList<VoicemailStatusHelperImpl.MessageWrapper>();
            while(cursor.moveToNext()) {
                MessageWrapper message = getMessageForStatusEntry(cursor);
                if (message != null) {
                    messages.add(message);
                }
            }
            // Finally reorder the messages by their priority.
            return reorderMessages(messages);
        } finally {
            MoreCloseables.closeQuietly(cursor);
        }
    }

    private List<Message> reorderMessages(List<MessageWrapper> messageWrappers) {
        Collections.sort(messageWrappers, new Comparator<MessageWrapper>() {
            @Override
            public int compare(MessageWrapper msg1, MessageWrapper msg2) {
                return msg1.mPriority - msg2.mPriority;
            }
        });
        List<Message> reorderMessages = new ArrayList<VoicemailStatusHelper.Message>();
        // Copy the ordered message objects into the final list.
        for (MessageWrapper messageWrapper : messageWrappers) {
            reorderMessages.add(messageWrapper.mMessage);
        }
        return reorderMessages;
    }

    /**
     * Returns the message for the status entry pointed to by the cursor.
     */
    private MessageWrapper getMessageForStatusEntry(Cursor cursor) {
        final String sourcePackage = cursor.getString(SOURCE_PACKAGE_INDEX);
        if (sourcePackage == null) {
            return null;
        }
        final OverallState overallState = getOverallState(cursor.getInt(CONFIGURATION_STATE_INDEX),
                cursor.getInt(DATA_CHANNEL_STATE_INDEX),
                cursor.getInt(NOTIFICATION_CHANNEL_STATE_INDEX));
        final Action action = overallState.getAction();

        // No source package or no action, means no message shown.
        if (action == Action.NONE) {
            return null;
        }

        Uri actionUri = null;
        if (action == Action.CALL_VOICEMAIL) {
            actionUri = Uri.parse(cursor.getString(VOICEMAIL_ACCESS_URI_INDEX));
        } else if (action == Action.CONFIGURE_VOICEMAIL) {
            actionUri = Uri.parse(cursor.getString(SETTINGS_URI_INDEX));
        }
        return new MessageWrapper(
                new Message(sourcePackage, overallState.getMessageId(), action.getMessageId(),
                        actionUri),
                overallState.getPriority());
    }

    private OverallState getOverallState(int configurationState, int dataChannelState,
            int notificationChannelState) {
        if (configurationState == CONFIGURATION_STATE_OK) {
            // Voicemail is configured. Let's see how is the data channel.
            if (dataChannelState == DATA_CHANNEL_STATE_OK) {
                // Data channel is fine. What about notification channel?
                if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_OK) {
                    return OverallState.OK;
                } else if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING) {
                    return OverallState.NO_DETAILED_NOTIFICATION;
                } else if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_NO_CONNECTION) {
                    return OverallState.NO_NOTIFICATIONS;
                }
            } else if (dataChannelState == DATA_CHANNEL_STATE_NO_CONNECTION) {
                // Data channel is not working. What about notification channel?
                if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_OK) {
                    return OverallState.NO_DATA;
                } else if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING) {
                    return OverallState.MESSAGE_WAITING;
                } else if (notificationChannelState == NOTIFICATION_CHANNEL_STATE_NO_CONNECTION) {
                    return OverallState.NO_CONNECTION;
                }
            }
        } else if (configurationState == CONFIGURATION_STATE_CAN_BE_CONFIGURED) {
            // Voicemail not configured. data/notification channel states are irrelevant.
            return OverallState.INVITE_FOR_CONFIGURATION;
        } else if (configurationState == Status.CONFIGURATION_STATE_NOT_CONFIGURED) {
            // Voicemail not configured. data/notification channel states are irrelevant.
            return OverallState.NOT_CONFIGURED;
        }
        // Will reach here only if the source has set an invalid value.
        return OverallState.INVALID;
    }
}
