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

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * Provides operations for managing notifications.
 * <p>
 * At the moment, it only handle {@link #ACTION_MARK_NEW_CALLS_AS_OLD}, which marks all the new
 * items in the call log as old; this is called when a notification is dismissed.
 */
public class CallLogNotificationsService extends IntentService {
    private static final String TAG = "CallLogNotificationsService";

    // Action to mark all the new calls as old. Invoked when the notifications need to be cleared.
    public static final String ACTION_MARK_NEW_CALLS_AS_OLD =
            "com.android.contacts.ACTION_MARK_NEW_CALLS_AS_OLD";

    private CallLogQueryHandler mCallLogQueryHandler;

    public CallLogNotificationsService() {
        super("CallLogNotificationsService");
        mCallLogQueryHandler = new CallLogQueryHandler(getContentResolver(), null /*listener*/);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_MARK_NEW_CALLS_AS_OLD.equals(intent.getAction())) {
            mCallLogQueryHandler.markNewCallsAsOld();
            return;
        } else {
            Log.d(TAG, "onHandleIntent: could not handle: " + intent);
        }
    }

}
