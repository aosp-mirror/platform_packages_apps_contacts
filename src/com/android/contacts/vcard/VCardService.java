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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The class responsible for handling vCard import/export requests.
 *
 * This Service creates one ImportRequest/ExportRequest object (as Runnable) per request and push
 * it to {@link ExecutorService} with single thread executor. The executor handles each request
 * one by one, and notifies users when needed.
 */
// TODO: Using IntentService looks simpler than using Service + ServiceConnection though this
// works fine enough. Investigate the feasibility.
public class VCardService extends Service {
    private final static String LOG_TAG = "VCardService";

    /* package */ static final int MSG_IMPORT_REQUEST = 1;
    /* package */ static final int MSG_EXPORT_REQUEST = 2;
    /* package */ static final int MSG_CANCEL_REQUEST = 3;

    /**
     * Specifies the type of operation. Used when constructing a {@link Notification}, canceling
     * some operation, etc.
     */
    /* package */ static final int TYPE_IMPORT = 1;
    /* package */ static final int TYPE_EXPORT = 2;

    /* package */ static final String CACHE_FILE_PREFIX = "import_tmp_";

    private final Messenger mMessenger = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMPORT_REQUEST: {
                    handleImportRequest((ImportRequest)msg.obj);
                    break;
                }
                case MSG_EXPORT_REQUEST: {
                    handleExportRequest((ExportRequest)msg.obj);
                    break;
                }
                case MSG_CANCEL_REQUEST: {
                    handleCancelRequest((CancelRequest)msg.obj);
                    break;
                }
                // TODO: add cancel capability for export..
                default: {
                    Log.w(LOG_TAG, "Received unknown request, ignoring it.");
                    super.hasMessages(msg.what);
                }
            }
        }
    });

    private NotificationManager mNotificationManager;

    // Should be single thread, as we don't want to simultaneously handle import and export
    // requests.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private int mCurrentJobId;

    // Stores all unfinished import/export jobs which will be executed by mExecutorService.
    // Key is jobId.
    private final Map<Integer, ProcessorBase> mRunningJobMap =
            new HashMap<Integer, ProcessorBase>();

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "VCardService is being destroyed.");
        cancelAllRequestsAndShutdown();
        clearCache();
        super.onDestroy();
    }

    private synchronized void handleImportRequest(ImportRequest request) {
        if (tryExecute(new ImportProcessor(this, request, mCurrentJobId))) {
            final String displayName = request.originalUri.getLastPathSegment(); 
            final String message = getString(R.string.vcard_import_will_start_message,
                    displayName);
            // TODO: Ideally we should detect the current status of import/export and show
            // "started" when we can import right now and show "will start" when we cannot.
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            final Notification notification =
                    constructProgressNotification(
                            this, TYPE_IMPORT, message, message, mCurrentJobId,
                            displayName, -1, 0);
            mNotificationManager.notify(mCurrentJobId, notification);
            mCurrentJobId++;
        } else {
            // TODO: a little unkind to show Toast in this case, which is shown just a moment.
            // Ideally we should show some persistent something users can notice more easily.
            Toast.makeText(this, getString(R.string.vcard_import_request_rejected_message),
                    Toast.LENGTH_LONG).show();
        }
    }

    private synchronized void handleExportRequest(ExportRequest request) {
        if (tryExecute(new ExportProcessor(this, request, mCurrentJobId))) {
            final String displayName = request.destUri.getLastPathSegment();
            final String message = getString(R.string.vcard_export_will_start_message,
                    displayName);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            final Notification notification =
                    constructProgressNotification(this, TYPE_EXPORT, message, message,
                            mCurrentJobId, displayName, -1, 0);
            mNotificationManager.notify(mCurrentJobId, notification);
            mCurrentJobId++;
        } else {
            Toast.makeText(this, getString(R.string.vcard_export_request_rejected_message),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Tries to call {@link ExecutorService#execute(Runnable)} toward a given processor.
     * @return true when successful.
     */
    private synchronized boolean tryExecute(ProcessorBase processor) {
        try {
            mExecutorService.execute(processor);
            mRunningJobMap.put(mCurrentJobId, processor);
            return true;
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "Failed to excetute a job.", e);
            return false;
        }
    }

    private void handleCancelRequest(CancelRequest request) {
        final int jobId = request.jobId;
        Log.i(LOG_TAG, String.format("Received cancel request. (id: %d)", jobId));
        final ProcessorBase processor = mRunningJobMap.remove(jobId);

        if (processor != null) {
            processor.cancel(true);
            final String description = processor.getType() == TYPE_IMPORT ?
                    getString(R.string.importing_vcard_canceled_title, request.displayName) :
                            getString(R.string.exporting_vcard_canceled_title, request.displayName);
            final Notification notification = constructCancelNotification(this, description);
            mNotificationManager.notify(jobId, notification);
        } else {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceWhenNoJob();
    }

    /**
     * Checks job list and call {@link #stopSelf()} when there's no job now.
     * A new job cannot be submitted any more after this call.
     */
    private synchronized void stopServiceWhenNoJob() {
        if (mRunningJobMap.size() > 0) {
            for (final Map.Entry<Integer, ProcessorBase> entry : mRunningJobMap.entrySet()) {
                final int jobId = entry.getKey();
                final ProcessorBase processor = entry.getValue();
                if (processor.isDone()) {
                    mRunningJobMap.remove(jobId);
                } else {
                    Log.i(LOG_TAG, String.format("Found unfinished job (id: %d)", jobId));
                    return;
                }
            }
        }

        Log.i(LOG_TAG, "No unfinished job. Stop this service.");
        mExecutorService.shutdown();
        stopSelf();
    }

    /* package */ synchronized void handleFinishImportNotification(
            int jobId, boolean successful) {
        Log.v(LOG_TAG, String.format("Received vCard import finish notification (id: %d). "
                + "Result: %b", jobId, (successful ? "success" : "failure")));
        if (mRunningJobMap.remove(jobId) == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceWhenNoJob();
    }

    /* package */ synchronized void handleFinishExportNotification(
            int jobId, boolean successful) {
        Log.v(LOG_TAG, String.format("Received vCard export finish notification (id: %d). "
                + "Result: %b", jobId, (successful ? "success" : "failure")));
        if (mRunningJobMap.remove(jobId) == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceWhenNoJob();
    }

    /**
     * Cancels all the import/export requests and calls {@link ExecutorService#shutdown()}, which
     * means this Service becomes no longer ready for import/export requests.
     *
     * Mainly called from onDestroy().
     */
    private synchronized void cancelAllRequestsAndShutdown() {
        for (final Map.Entry<Integer, ProcessorBase> entry : mRunningJobMap.entrySet()) {
            entry.getValue().cancel(true);
        }
        mRunningJobMap.clear();
        mExecutorService.shutdown();
    }

    /**
     * Removes import caches stored locally.
     */
    private void clearCache() {
        for (final String fileName : fileList()) {
            if (fileName.startsWith(CACHE_FILE_PREFIX)) {
                // We don't want to keep all the caches so we remove cache files old enough.
                Log.i(LOG_TAG, "Remove a temporary file: " + fileName);
                deleteFile(fileName);
            }
        }
    }

    /**
     * Constructs a {@link Notification} showing the current status of import/export.
     * Users can cancel the process with the Notification.
     *
     * @param context
     * @param type import/export
     * @param description Content of the Notification.
     * @param tickerText
     * @param jobId
     * @param displayName Name to be shown to the Notification (e.g. "finished importing XXXX").
     * Typycally a file name.
     * @param totalCount The number of vCard entries to be imported. Used to show progress bar.
     * -1 lets the system show the progress bar with "indeterminate" state.
     * @param currentCount The index of current vCard. Used to show progress bar.
     */
    /* package */ static Notification constructProgressNotification(
            Context context, int type, String description, String tickerText,
            int jobId, String displayName, int totalCount, int currentCount) {
        final RemoteViews remoteViews =
                new RemoteViews(context.getPackageName(),
                        R.layout.status_bar_ongoing_event_progress_bar);
        remoteViews.setTextViewText(R.id.status_description, description);
        remoteViews.setProgressBar(R.id.status_progress_bar, totalCount, currentCount,
                totalCount == -1);
        final String percentage;
        if (totalCount > 0) {
            percentage = context.getString(R.string.percentage,
                    String.valueOf(currentCount * 100/totalCount));
        } else {
            percentage = "";
        }
        remoteViews.setTextViewText(R.id.status_progress_text, percentage);
        final int icon = (type == TYPE_IMPORT ? android.R.drawable.stat_sys_download :
                android.R.drawable.stat_sys_upload);
        remoteViews.setImageViewResource(R.id.status_icon, icon);

        final Notification notification = new Notification();
        notification.icon = icon;
        notification.tickerText = tickerText;
        notification.contentView = remoteViews;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        // Note: We cannot use extra values here (like setIntExtra()), as PendingIntent doesn't
        // preserve them across multiple Notifications. PendingIntent preserves the first extras
        // (when flag is not set), or update them when PendingIntent#getActivity() is called
        // (See PendingIntent#FLAG_UPDATE_CURRENT). In either case, we cannot preserve extras as we
        // expect (for each vCard import/export request).
        //
        // We use query parameter in Uri instead.
        // Scheme and Authority is arbitorary, assuming CancelActivity never refers them.
        final Intent intent = new Intent(context, CancelActivity.class);
        final Uri uri = (new Uri.Builder())
                .scheme("invalidscheme")
                .authority("invalidauthority")
                .appendQueryParameter(CancelActivity.JOB_ID, String.valueOf(jobId))
                .appendQueryParameter(CancelActivity.DISPLAY_NAME, displayName)
                .appendQueryParameter(CancelActivity.TYPE, String.valueOf(type)).build();
        intent.setData(uri);

        notification.contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
        return notification;
    }

    /**
     * Constructs a Notification telling users the process is canceled.
     *
     * @param context
     * @param description Content of the Notification
     */
    /* package */ static Notification constructCancelNotification(
            Context context, String description) {
        final Notification notification = new Notification();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.icon = android.R.drawable.stat_notify_error;
        notification.setLatestEventInfo(context, description, description,
                PendingIntent.getActivity(context, 0, null, 0));
        return notification;
    }

    /**
     * Constructs a Notification telling users the process is finished.
     *
     * @param context
     * @param description Content of the Notification
     * @param intent Intent to be launched when the Notification is clicked. Can be null.
     */
    /* package */ static Notification constructFinishNotification(
            Context context, String description, Intent intent) {
        final Notification notification = new Notification();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.icon = android.R.drawable.stat_sys_download_done;
        notification.setLatestEventInfo(context, description, description,
                PendingIntent.getActivity(context, 0, intent, 0));
        return notification;
    }
}
