/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.vcard;

import com.android.contacts.R;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryHandler;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

/**
 * {@link VCardEntryHandler} implementation letting the system update the current status of
 * vCard import.
 */
public class ImportProgressNotifier implements VCardEntryHandler {
    private static final String LOG_TAG = "VCardImport";

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final int mJobId;
    private final String mDisplayName;

    private int mCurrentCount;
    private int mTotalCount;

    public ImportProgressNotifier(
            Context context, NotificationManager notificationManager,
            int jobId, String displayName) {
        mContext = context;
        mNotificationManager = notificationManager;
        mJobId = jobId;
        mDisplayName = displayName;
    }

    public void onStart() {
    }

    public void onEntryCreated(VCardEntry contactStruct) {
        mCurrentCount++;  // 1 origin.
        if (contactStruct.isIgnorable()) {
            return;
        }

        final String totalCountString;
        synchronized (this) {
            totalCountString = String.valueOf(mTotalCount);
        }
        final String tickerText =
                mContext.getString(R.string.progress_notifier_message,
                        String.valueOf(mCurrentCount),
                        totalCountString,
                        contactStruct.getDisplayName());
        final String description = mContext.getString(R.string.importing_vcard_description,
                contactStruct.getDisplayName());

        final Notification notification = VCardService.constructProgressNotification(
                mContext.getApplicationContext(), VCardService.TYPE_IMPORT, description, tickerText,
                mJobId, mDisplayName, mTotalCount, mCurrentCount);
        mNotificationManager.notify(mJobId, notification);
    }

    public synchronized void addTotalCount(int additionalCount) {
        mTotalCount += additionalCount;
    }

    public void onEnd() {
    }
}