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

import android.net.Uri;

/**
 * Handles notifications for voicemails.
 */
public interface VoicemailNotifier {
    /**
     * Notifies the user of a new voicemail.
     *
     * @param newVoicemailUri URI of the new voicemail record just inserted
     */
    public void notifyNewVoicemail(Uri newVoicemailUri);

    /**
     * Updates the notification and clears it if there are no new voicemails. Called when the phone
     * just rebooted to put back notifications for anything the user has not acknowledged.
     */
    public void updateNotification();

    /** Clears the new voicemail notification. */
    public void clearNotification();
}
