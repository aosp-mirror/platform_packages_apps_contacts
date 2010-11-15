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
import com.android.contacts.activities.ContactBrowserActivity;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.OutputStream;

/**
 * Class for processing one export request from a user. Dropped after exporting requested Uri(s).
 * {@link VCardService} will create another object when there is another export request.
 */
public class ExportProcessor implements Runnable {
    private static final String LOG_TAG = "VCardExport";

    private final VCardService mService;

    private ContentResolver mResolver;
    private NotificationManager mNotificationManager;

    private volatile boolean mCanceled;

    private ExportRequest mExportRequest; 
    private int mJobId;

    public ExportProcessor(VCardService service, ExportRequest exportRequest, int jobId) {
        mService = service;
        mResolver = service.getContentResolver();
        mNotificationManager =
                (NotificationManager)mService.getSystemService(Context.NOTIFICATION_SERVICE);
        mExportRequest = exportRequest;
        mJobId = jobId;
    }

    @Override
    public void run() {
        // ExecutorService ignores RuntimeException, so we need to show it here.
        try {
            runInternal();
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "RuntimeException thrown during export", e);
            throw e;
        }
    }

    private void runInternal() {
        Log.i(LOG_TAG, String.format("vCard export (id: %d) has started.", mJobId));
        final ExportRequest request = mExportRequest;
        VCardComposer composer = null;
        boolean successful = false;
        try {
            final Uri uri = request.destUri;
            final OutputStream outputStream;
            try {
                outputStream = mResolver.openOutputStream(uri);
            } catch (FileNotFoundException e) {
                Log.w(LOG_TAG, "FileNotFoundException thrown", e);
                // Need concise title.

                final String errorReason =
                    mService.getString(R.string.fail_reason_could_not_open_file,
                            uri, e.getMessage());
                Log.i(LOG_TAG, "failed to export (could not open output stream)");
                doFinishNotification(errorReason, "");
                return;
            }

            final String exportType = request.exportType;
            final int vcardType;
            if (TextUtils.isEmpty(exportType)) {
                vcardType = VCardConfig.getVCardTypeFromString(
                        mService.getString(R.string.config_export_vcard_type));
            } else {
                vcardType = VCardConfig.getVCardTypeFromString(exportType);
            }

            composer = new VCardComposer(mService, vcardType, true);

            // for test
            // int vcardType = (VCardConfig.VCARD_TYPE_V21_GENERIC |
            //     VCardConfig.FLAG_USE_QP_TO_PRIMARY_PROPERTIES);
            // composer = new VCardComposer(ExportVCardActivity.this, vcardType, true);

            composer.addHandler(composer.new HandlerForOutputStream(outputStream));

            if (!composer.init()) {
                Log.w(LOG_TAG, "vCard composer init failed");
                final String errorReason = composer.getErrorReason();
                Log.e(LOG_TAG, "initialization of vCard composer failed: " + errorReason);
                final String translatedErrorReason =
                        translateComposerError(errorReason);
                final String title =
                        mService.getString(R.string.fail_reason_could_not_initialize_exporter,
                                translatedErrorReason);
                doFinishNotification(title, "");
                return;
            }

            final int total = composer.getCount();
            if (total == 0) {
                final String title =
                        mService.getString(R.string.fail_reason_no_exportable_contact);
                doFinishNotification(title, "");
                return;
            }

            int current = 1;  // 1-origin
            while (!composer.isAfterLast()) {
                if (mCanceled) {
                    return;
                }
                if (!composer.createOneEntry()) {
                    final String errorReason = composer.getErrorReason();
                    Log.e(LOG_TAG, "Failed to read a contact: " + errorReason);
                    final String translatedErrorReason =
                            translateComposerError(errorReason);
                    final String title =
                            mService.getString(R.string.fail_reason_error_occurred_during_export,
                                    translatedErrorReason);
                    doFinishNotification(title, "");
                    return;
                }
                doProgressNotification(uri, total, current);
                current++;
            }
            Log.i(LOG_TAG, "Successfully finished exporting vCard " + request.destUri);

            successful = true;
            // TODO: Show "successful"
        } finally {
            if (composer != null) {
                composer.terminate();
            }

            mService.handleFinishExportNotification(mJobId, successful);
        }
    }

    private String translateComposerError(String errorMessage) {
        final Resources resources = mService.getResources();
        if (VCardComposer.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO.equals(errorMessage)) {
            return resources.getString(R.string.composer_failed_to_get_database_infomation);
        } else if (VCardComposer.FAILURE_REASON_NO_ENTRY.equals(errorMessage)) {
            return resources.getString(R.string.composer_has_no_exportable_contact);
        } else if (VCardComposer.FAILURE_REASON_NOT_INITIALIZED.equals(errorMessage)) {
            return resources.getString(R.string.composer_not_initialized);
        } else {
            return errorMessage;
        }
    }

    private void doProgressNotification(Uri uri, int total, int current) {
        final String title = mService.getString(R.string.exporting_contact_list_title);
        final String description =
                mService.getString(R.string.exporting_contact_list_message, uri);

        /* TODO: we should show more informative Notification to users.
        final RemoteViews remoteViews = new RemoteViews(mService.getPackageName(),
                R.layout.status_bar_ongoing_event_progress_bar);
        remoteViews.setTextViewText(R.id.status_description, message);
        remoteViews.setProgressBar(R.id.status_progress_bar, total, current, (total == -1));

        final String percentage = mService.getString(R.string.percentage,
                String.valueOf((current * 100)/total));
        remoteViews.setTextViewText(R.id.status_progress_text, percentage);
        remoteViews.setImageViewResource(R.id.status_icon, android.R.drawable.stat_sys_upload);

        final Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_upload;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.tickerText = title;
        notification.contentView = remoteViews;
        notification.contentIntent =
                PendingIntent.getActivity(mService, 0,
                        new Intent(mService, ContactBrowserActivity.class), 0);*/

        final long when = System.currentTimeMillis();
        final Notification notification = new Notification(
                android.R.drawable.stat_sys_upload,
                description,
                when);

        final Context context = mService.getApplicationContext();
        final PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0,
                        new Intent(context, ContactBrowserActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(context, title, description, pendingIntent);
        mNotificationManager.notify(VCardService.EXPORT_NOTIFICATION_ID, notification);
    }

    private void doFinishNotification(final String title, final String message) {
        final Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_upload_done;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.setLatestEventInfo(mService, title, message, null);
        final Intent intent = new Intent(mService, ContactBrowserActivity.class);
        notification.contentIntent =
                PendingIntent.getActivity(mService, 0, intent, 0);
        mNotificationManager.notify(VCardService.EXPORT_NOTIFICATION_ID, notification);
    }

    public void cancel() {
        Log.i(LOG_TAG, "received cancel request");
        mCanceled = true;
    }
}
