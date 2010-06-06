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
package com.android.contacts;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryHandler;

/**
 * {@link VCardEntryHandler} implementation which lets the system update
 * the current status of vCard import.
 */
public class ImportProgressNotifier implements VCardEntryHandler {
    private Context mContext;
    private NotificationManager mNotificationManager;

    private int mCurrentCount;
    private int mTotalCount;

    public void init(Context context, NotificationManager notificationManager) {
        mContext = context;
        mNotificationManager = notificationManager;
    }

    public void onStart() {
    }

    public void onEntryCreated(VCardEntry contactStruct) {
        mCurrentCount++;  // 1 origin.
        if (contactStruct.isIgnorable()) {
            return;
        }

        // We don't use startEntry() since:
        // - We cannot know name there but here.
        // - There's high probability where name comes soon after the beginning of entry, so
        //   we don't need to hurry to show something.
        final String packageName = "com.android.contacts";
        final RemoteViews remoteViews = new RemoteViews(packageName,
                R.layout.status_bar_ongoing_event_progress_bar);
        final String title = mContext.getString(R.string.reading_vcard_title);
        String totalCountString;
        synchronized (this) {
            totalCountString = String.valueOf(mTotalCount);
        }
        final String text = mContext.getString(R.string.progress_notifier_message,
                String.valueOf(mCurrentCount),
                totalCountString,
                contactStruct.getDisplayName());

        // TODO: uploading image does not work correctly. (looks like a static image).
        remoteViews.setTextViewText(R.id.description, text);
        remoteViews.setProgressBar(R.id.progress_bar, mTotalCount, mCurrentCount,
                mTotalCount == -1);
        final String percentage =
                mContext.getString(R.string.percentage,
                        String.valueOf(mCurrentCount * 100/mTotalCount));
        remoteViews.setTextViewText(R.id.progress_text, percentage);
        remoteViews.setImageViewResource(R.id.appIcon, android.R.drawable.stat_sys_download);

        final Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_download;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.contentView = remoteViews;

        notification.contentIntent =
                PendingIntent.getActivity(mContext, 0,
                        new Intent(mContext, ContactsListActivity.class), 0);
        mNotificationManager.notify(ImportVCardService.NOTIFICATION_ID, notification);
    }

    public synchronized void addTotalCount(int additionalCount) {
        mTotalCount += additionalCount;
    }

    public synchronized void resetTotalCount() {
        mTotalCount = 0;
    }

    public void onEnd() {
    }
}