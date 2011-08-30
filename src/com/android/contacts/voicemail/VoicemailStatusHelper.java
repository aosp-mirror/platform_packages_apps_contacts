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

import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract.Status;

import java.util.List;

/**
 * Interface used by the call log UI to determine what user message, if any, related to voicemail
 * source status needs to be shown. The messages are returned in the order of importance.
 * <p>
 * The implementation of this interface interacts with the voicemail content provider to fetch
 * statuses of all the registered voicemail sources and determines if any status message needs to
 * be shown. The user of this interface must observe/listen to provider changes and invoke
 * this class to check if any message needs to be shown.
 */
public interface VoicemailStatusHelper {
    public class StatusMessage {
        /** Package of the source on behalf of which this message has to be shown.*/
        public final String sourcePackage;
        /**
         * The string resource id of the status message that should be shown in the call log
         * page. Set to -1, if this message is not to be shown in call log.
         */
        public final int callLogMessageId;
        /**
         * The string resource id of the status message that should be shown in the call details
         * page. Set to -1, if this message is not to be shown in call details page.
         */
        public final int callDetailsMessageId;
        /** The string resource id of the action message that should be shown. */
        public final int actionMessageId;
        /** URI for the corrective action, where applicable. Null if no action URI is available. */
        public final Uri actionUri;
        public StatusMessage(String sourcePackage, int callLogMessageId, int callDetailsMessageId,
                int actionMessageId, Uri actionUri) {
            this.sourcePackage = sourcePackage;
            this.callLogMessageId = callLogMessageId;
            this.callDetailsMessageId = callDetailsMessageId;
            this.actionMessageId = actionMessageId;
            this.actionUri = actionUri;
        }

        /** Whether this message should be shown in the call log page. */
        public boolean showInCallLog() {
            return callLogMessageId != -1;
        }

        /** Whether this message should be shown in the call details page. */
        public boolean showInCallDetails() {
            return callDetailsMessageId != -1;
        }
    }

    /**
     * Returns a list of messages, in the order or priority that should be shown to the user. An
     * empty list is returned if no message needs to be shown.
     * @param cursor The cursor pointing to the query on {@link Status#CONTENT_URI}. The projection
     *      to be used is defined by the implementation class of this interface.
     */
    public List<StatusMessage> getStatusMessages(Cursor cursor);

    /**
     * Returns the number of active voicemail sources installed.
     * <p>
     * The number of sources is counted by querying the voicemail status table.
     */
    public int getNumberActivityVoicemailSources(Cursor cursor);
}
