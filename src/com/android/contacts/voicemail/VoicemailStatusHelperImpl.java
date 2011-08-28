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

package com.android.contacts.voicemail;

import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_CAN_BE_CONFIGURED;
import static android.provider.VoicemailContract.Status.CONFIGURATION_STATE_OK;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.DATA_CHANNEL_STATE_OK;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;
import static android.provider.VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK;

import com.android.contacts.R;
import com.android.contacts.util.UriUtils;

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
    /** Projection on the voicemail_status table used by this class. */
    public static final String[] PROJECTION = new String[NUM_COLUMNS];
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
        NO_CONNECTION(0, Action.CALL_VOICEMAIL, R.string.voicemail_status_voicemail_not_available,
                R.string.voicemail_status_audio_not_available),
        /** Notifications working, but data channel is not working. Audio cannot be downloaded. */
        NO_DATA(1, Action.CALL_VOICEMAIL, R.string.voicemail_status_voicemail_not_available,
                R.string.voicemail_status_audio_not_available),
        /** Messages are known to be waiting but data channel is not working. */
        MESSAGE_WAITING(2, Action.CALL_VOICEMAIL, R.string.voicemail_status_messages_waiting,
                R.string.voicemail_status_audio_not_available),
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
        private final int mCallLogMessageId;
        private final int mCallDetailsMessageId;

        private OverallState(int priority, Action action, int callLogMessageId) {
            this(priority, action, callLogMessageId, -1);
        }

        private OverallState(int priority, Action action, int callLogMessageId,
                int callDetailsMessageId) {
            mPriority = priority;
            mAction = action;
            mCallLogMessageId = callLogMessageId;
            mCallDetailsMessageId = callDetailsMessageId;
        }

        public Action getAction() {
            return mAction;
        }

        public int getPriority() {
            return mPriority;
        }

        public int getCallLogMessageId() {
            return mCallLogMessageId;
        }

        public int getCallDetailsMessageId() {
            return mCallDetailsMessageId;
        }
    }

    /** A wrapper on {@link StatusMessage} which additionally stores the priority of the message. */
    private static class MessageStatusWithPriority {
        private final StatusMessage mMessage;
        private final int mPriority;

        public MessageStatusWithPriority(StatusMessage message, int priority) {
            mMessage = message;
            mPriority = priority;
        }
    }

    @Override
    public List<StatusMessage> getStatusMessages(Cursor cursor) {
        List<MessageStatusWithPriority> messages =
            new ArrayList<VoicemailStatusHelperImpl.MessageStatusWithPriority>();
        cursor.moveToPosition(-1);
        while(cursor.moveToNext()) {
            MessageStatusWithPriority message = getMessageForStatusEntry(cursor);
            if (message != null) {
                messages.add(message);
            }
        }
        // Finally reorder the messages by their priority.
        return reorderMessages(messages);
    }

    @Override
    public int getNumberActivityVoicemailSources(Cursor cursor) {
        int count = 0;
        cursor.moveToPosition(-1);
        while(cursor.moveToNext()) {
            if (isVoicemailSourceActive(cursor)) {
                ++count;
            }
        }
        return count;
    }

    /** Returns whether the source status in the cursor corresponds to an active source. */
    private boolean isVoicemailSourceActive(Cursor cursor) {
        return cursor.getString(SOURCE_PACKAGE_INDEX) != null
                &&  cursor.getInt(CONFIGURATION_STATE_INDEX) == Status.CONFIGURATION_STATE_OK;
    }

    private List<StatusMessage> reorderMessages(List<MessageStatusWithPriority> messageWrappers) {
        Collections.sort(messageWrappers, new Comparator<MessageStatusWithPriority>() {
            @Override
            public int compare(MessageStatusWithPriority msg1, MessageStatusWithPriority msg2) {
                return msg1.mPriority - msg2.mPriority;
            }
        });
        List<StatusMessage> reorderMessages = new ArrayList<VoicemailStatusHelper.StatusMessage>();
        // Copy the ordered message objects into the final list.
        for (MessageStatusWithPriority messageWrapper : messageWrappers) {
            reorderMessages.add(messageWrapper.mMessage);
        }
        return reorderMessages;
    }

    /**
     * Returns the message for the status entry pointed to by the cursor.
     */
    private MessageStatusWithPriority getMessageForStatusEntry(Cursor cursor) {
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
            actionUri = UriUtils.parseUriOrNull(cursor.getString(VOICEMAIL_ACCESS_URI_INDEX));
            // Even if actionUri is null, it is still be useful to show the notification.
        } else if (action == Action.CONFIGURE_VOICEMAIL) {
            actionUri = UriUtils.parseUriOrNull(cursor.getString(SETTINGS_URI_INDEX));
            // If there is no settings URI, there is no point in showing the notification.
            if (actionUri == null) {
                return null;
            }
        }
        return new MessageStatusWithPriority(
                new StatusMessage(sourcePackage, overallState.getCallLogMessageId(),
                        overallState.getCallDetailsMessageId(), action.getMessageId(),
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
