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
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** The tag used by vCard-related notifications. */
    /* package */ static final String DEFAULT_NOTIFICATION_TAG = "VCardServiceProgress";
    /**
     * The tag used by vCard-related failure notifications.
     * <p>
     * Use a different tag from {@link #DEFAULT_NOTIFICATION_TAG} so that failures do not get
     * replaced by other notifications and vice-versa.
     */
    /* package */ static final String FAILURE_NOTIFICATION_TAG = "VCardServiceFailure";

    /* package */ final static boolean DEBUG = false;

    /* package */ static final int MSG_IMPORT_REQUEST = 1;
    /* package */ static final int MSG_EXPORT_REQUEST = 2;
    /* package */ static final int MSG_CANCEL_REQUEST = 3;
    /* package */ static final int MSG_REQUEST_AVAILABLE_EXPORT_DESTINATION = 4;
    /* package */ static final int MSG_SET_AVAILABLE_EXPORT_DESTINATION = 5;

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
                    handleImportRequest((List<ImportRequest>)msg.obj);
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
                case MSG_REQUEST_AVAILABLE_EXPORT_DESTINATION: {
                    handleRequestAvailableExportDestination(msg);
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

    private class CustomMediaScannerConnectionClient implements MediaScannerConnectionClient {
        final MediaScannerConnection mConnection;
        final String mPath;

        public CustomMediaScannerConnectionClient(String path) {
            mConnection = new MediaScannerConnection(VCardService.this, this);
            mPath = path;
        }

        public void start() {
            mConnection.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            if (DEBUG) { Log.d(LOG_TAG, "Connected to MediaScanner. Start scanning."); }
            mConnection.scanFile(mPath, null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            if (DEBUG) { Log.d(LOG_TAG, "scan completed: " + path); }
            mConnection.disconnect();
            removeConnectionClient(this);
        }
    }

    private NotificationManager mNotificationManager;

    // Should be single thread, as we don't want to simultaneously handle import and export
    // requests.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private int mCurrentJobId;

    // Stores all unfinished import/export jobs which will be executed by mExecutorService.
    // Key is jobId.
    private final Map<Integer, ProcessorBase> mRunningJobMap =
            new HashMap<Integer, ProcessorBase>();
    // Stores ScannerConnectionClient objects until they finish scanning requested files.
    // Uses List class for simplicity. It's not costly as we won't have multiple objects in
    // almost all cases.
    private final List<CustomMediaScannerConnectionClient> mRemainingScannerConnections =
            new ArrayList<CustomMediaScannerConnectionClient>();

    /* ** vCard exporter params ** */
    // If true, VCardExporter is able to emits files longer than 8.3 format.
    private static final boolean ALLOW_LONG_FILE_NAME = false;

    private String mTargetDirectory;
    private String mFileNamePrefix;
    private String mFileNameSuffix;
    private int mFileIndexMinimum;
    private int mFileIndexMaximum;
    private String mFileNameExtension;
    private Set<String> mExtensionsToConsider;
    private String mErrorReason;

    // File names currently reserved by some export job.
    private final Set<String> mReservedDestination = new HashSet<String>();
    /* ** end of vCard exporter params ** */

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(LOG_TAG, "vCard Service is being created.");
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        initExporterParams();
    }

    private void initExporterParams() {
        mTargetDirectory = getString(R.string.config_export_dir);
        mFileNamePrefix = getString(R.string.config_export_file_prefix);
        mFileNameSuffix = getString(R.string.config_export_file_suffix);
        mFileNameExtension = getString(R.string.config_export_file_extension);

        mExtensionsToConsider = new HashSet<String>();
        mExtensionsToConsider.add(mFileNameExtension);

        final String additionalExtensions =
            getString(R.string.config_export_extensions_to_consider);
        if (!TextUtils.isEmpty(additionalExtensions)) {
            for (String extension : additionalExtensions.split(",")) {
                String trimed = extension.trim();
                if (trimed.length() > 0) {
                    mExtensionsToConsider.add(trimed);
                }
            }
        }

        final Resources resources = getResources();
        mFileIndexMinimum = resources.getInteger(R.integer.config_export_file_min_index);
        mFileIndexMaximum = resources.getInteger(R.integer.config_export_file_max_index);
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
        if (DEBUG) Log.d(LOG_TAG, "VCardService is being destroyed.");
        cancelAllRequestsAndShutdown();
        clearCache();
        super.onDestroy();
    }

    private synchronized void handleImportRequest(List<ImportRequest> requests) {
        if (DEBUG) {
            final ArrayList<String> uris = new ArrayList<String>();
            final ArrayList<String> originalUris = new ArrayList<String>();
            for (ImportRequest request : requests) {
                uris.add(request.uri.toString());
                originalUris.add(request.originalUri.toString());
            }
            Log.d(LOG_TAG,
                    String.format("received multiple import request (uri: %s, originalUri: %s)",
                            uris.toString(), originalUris.toString()));
        }
        final int size = requests.size();
        for (int i = 0; i < size; i++) {
            ImportRequest request = requests.get(i);

            if (tryExecute(new ImportProcessor(this, request, mCurrentJobId))) {
                final String displayName;
                final String message;
                final String lastPathSegment = request.originalUri.getLastPathSegment();
                if ("file".equals(request.originalUri.getScheme()) &&
                        lastPathSegment != null) {
                    displayName = lastPathSegment;
                    message = getString(R.string.vcard_import_will_start_message, displayName);
                } else {
                    displayName = getString(R.string.vcard_unknown_filename);
                    message = getString(
                            R.string.vcard_import_will_start_message_with_default_name);
                }

                // We just want to show notification for the first vCard.
                if (i == 0) {
                    // TODO: Ideally we should detect the current status of import/export and show
                    // "started" when we can import right now and show "will start" when we cannot.
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }

                final Notification notification =
                        constructProgressNotification(
                                this, TYPE_IMPORT, message, message, mCurrentJobId,
                                displayName, -1, 0);
                mNotificationManager.notify(VCardService.DEFAULT_NOTIFICATION_TAG, mCurrentJobId,
                        notification);
                mCurrentJobId++;
            } else {
                // TODO: a little unkind to show Toast in this case, which is shown just a moment.
                // Ideally we should show some persistent something users can notice more easily.
                Toast.makeText(this, getString(R.string.vcard_import_request_rejected_message),
                        Toast.LENGTH_LONG).show();
                // A rejection means executor doesn't run any more. Exit.
                break;
            }
        }
    }

    private synchronized void handleExportRequest(ExportRequest request) {
        if (tryExecute(new ExportProcessor(this, request, mCurrentJobId))) {
            final String displayName = request.destUri.getLastPathSegment();
            final String message = getString(R.string.vcard_export_will_start_message,
                    displayName);

            final String path = request.destUri.getEncodedPath();
            if (DEBUG) Log.d(LOG_TAG, "Reserve the path " + path);
            if (!mReservedDestination.add(path)) {
                Log.w(LOG_TAG,
                        String.format("The path %s is already reserved. Reject export request",
                                path));
                Toast.makeText(this, getString(R.string.vcard_export_request_rejected_message),
                        Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            final Notification notification =
                    constructProgressNotification(this, TYPE_EXPORT, message, message,
                            mCurrentJobId, displayName, -1, 0);
            mNotificationManager.notify(VCardService.DEFAULT_NOTIFICATION_TAG, mCurrentJobId, notification);
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
            if (DEBUG) {
                Log.d(LOG_TAG, "Executor service status: shutdown: " + mExecutorService.isShutdown()
                        + ", terminated: " + mExecutorService.isTerminated());
            }
            mExecutorService.execute(processor);
            mRunningJobMap.put(mCurrentJobId, processor);
            return true;
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "Failed to excetute a job.", e);
            return false;
        }
    }

    private synchronized void handleCancelRequest(CancelRequest request) {
        final int jobId = request.jobId;
        if (DEBUG) Log.d(LOG_TAG, String.format("Received cancel request. (id: %d)", jobId));
        final ProcessorBase processor = mRunningJobMap.remove(jobId);

        if (processor != null) {
            processor.cancel(true);
            final String description = processor.getType() == TYPE_IMPORT ?
                    getString(R.string.importing_vcard_canceled_title, request.displayName) :
                            getString(R.string.exporting_vcard_canceled_title, request.displayName);
            final Notification notification = constructCancelNotification(this, description);
            mNotificationManager.notify(VCardService.DEFAULT_NOTIFICATION_TAG, jobId, notification);
            if (processor.getType() == TYPE_EXPORT) {
                final String path =
                        ((ExportProcessor)processor).getRequest().destUri.getEncodedPath();
                Log.i(LOG_TAG,
                        String.format("Cancel reservation for the path %s if appropriate", path));
                if (!mReservedDestination.remove(path)) {
                    Log.w(LOG_TAG, "Not reserved.");
                }
            }
        } else {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceIfAppropriate();
    }

    private synchronized void handleRequestAvailableExportDestination(Message msg) {
        if (DEBUG) Log.d(LOG_TAG, "Received available export destination request.");
        final Messenger messenger = msg.replyTo;
        final String path = getAppropriateDestination(mTargetDirectory);
        final Message message;
        if (path != null) {
            message = Message.obtain(null,
                    VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION, 0, 0, path);
        } else {
            message = Message.obtain(null,
                    VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION,
                    R.id.dialog_fail_to_export_with_reason, 0, mErrorReason);
        }
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Failed to send reply for available export destination request.", e);
        }
    }

    /**
     * Checks job list and call {@link #stopSelf()} when there's no job and no scanner connection
     * is remaining.
     * A new job (import/export) cannot be submitted any more after this call.
     */
    private synchronized void stopServiceIfAppropriate() {
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

        if (!mRemainingScannerConnections.isEmpty()) {
            Log.i(LOG_TAG, "MediaScanner update is in progress.");
            return;
        }

        Log.i(LOG_TAG, "No unfinished job. Stop this service.");
        mExecutorService.shutdown();
        stopSelf();
    }

    /* package */ synchronized void updateMediaScanner(String path) {
        if (DEBUG) {
            Log.d(LOG_TAG, "MediaScanner is being updated: " + path);
        }

        if (mExecutorService.isShutdown()) {
            Log.w(LOG_TAG, "MediaScanner update is requested after executor's being shut down. " +
                    "Ignoring the update request");
            return;
        }
        final CustomMediaScannerConnectionClient client =
                new CustomMediaScannerConnectionClient(path);
        mRemainingScannerConnections.add(client);
        client.start();
    }

    private synchronized void removeConnectionClient(
            CustomMediaScannerConnectionClient client) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Removing custom MediaScannerConnectionClient.");
        }
        mRemainingScannerConnections.remove(client);
        stopServiceIfAppropriate();
    }

    /* package */ synchronized void handleFinishImportNotification(
            int jobId, boolean successful) {
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("Received vCard import finish notification (id: %d). "
                    + "Result: %b", jobId, (successful ? "success" : "failure")));
        }
        if (mRunningJobMap.remove(jobId) == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceIfAppropriate();
    }

    /* package */ synchronized void handleFinishExportNotification(
            int jobId, boolean successful) {
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("Received vCard export finish notification (id: %d). "
                    + "Result: %b", jobId, (successful ? "success" : "failure")));
        }
        final ProcessorBase job = mRunningJobMap.remove(jobId);
        if (job == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        } else if (!(job instanceof ExportProcessor)) {
            Log.w(LOG_TAG,
                    String.format("Removed job (id: %s) isn't ExportProcessor", jobId));
        } else {
            final String path = ((ExportProcessor)job).getRequest().destUri.getEncodedPath();
            if (DEBUG) Log.d(LOG_TAG, "Remove reserved path " + path);
            mReservedDestination.remove(path);
        }

        stopServiceIfAppropriate();
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
        return new Notification.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(description)
                .setContentText(description)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0))
                .getNotification();
    }

    /**
     * Constructs a Notification telling users the process is finished.
     *
     * @param context
     * @param description Content of the Notification
     * @param intent Intent to be launched when the Notification is clicked. Can be null.
     */
    /* package */ static Notification constructFinishNotification(
            Context context, String title, String description, Intent intent) {
        return new Notification.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(description)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        (intent != null ? intent : new Intent()), 0))
                .getNotification();
    }

    /**
     * Constructs a Notification telling the vCard import has failed.
     *
     * @param context
     * @param reason The reason why the import has failed. Shown in description field.
     */
    /* package */ static Notification constructImportFailureNotification(
            Context context, String reason) {
        return new Notification.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.vcard_import_failed))
                .setContentText(reason)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0))
                .getNotification();
    }

    /**
     * Returns an appropriate file name for vCard export. Returns null when impossible.
     *
     * @return destination path for a vCard file to be exported. null on error and mErrorReason
     * is correctly set.
     */
    private String getAppropriateDestination(final String destDirectory) {
        /*
         * Here, file names have 5 parts: directory, prefix, index, suffix, and extension.
         * e.g. "/mnt/sdcard/prfx00001sfx.vcf" -> "/mnt/sdcard", "prfx", "00001", "sfx", and ".vcf"
         *      (In default, prefix and suffix is empty, so usually the destination would be
         *       /mnt/sdcard/00001.vcf.)
         *
         * This method increments "index" part from 1 to maximum, and checks whether any file name
         * following naming rule is available. If there's no file named /mnt/sdcard/00001.vcf, the
         * name will be returned to a caller. If there are 00001.vcf 00002.vcf, 00003.vcf is
         * returned.
         *
         * There may not be any appropriate file name. If there are 99999 vCard files in the
         * storage, for example, there's no appropriate name, so this method returns
         * null.
         */

        // Count the number of digits of mFileIndexMaximum
        // e.g. When mFileIndexMaximum is 99999, fileIndexDigit becomes 5, as we will count the
        int fileIndexDigit = 0;
        {
            // Calling Math.Log10() is costly.
            int tmp;
            for (fileIndexDigit = 0, tmp = mFileIndexMaximum; tmp > 0;
                fileIndexDigit++, tmp /= 10) {
            }
        }

        // %s05d%s (e.g. "p00001s")
        final String bodyFormat = "%s%0" + fileIndexDigit + "d%s";

        if (!ALLOW_LONG_FILE_NAME) {
            final String possibleBody =
                    String.format(bodyFormat, mFileNamePrefix, 1, mFileNameSuffix);
            if (possibleBody.length() > 8 || mFileNameExtension.length() > 3) {
                Log.e(LOG_TAG, "This code does not allow any long file name.");
                mErrorReason = getString(R.string.fail_reason_too_long_filename,
                        String.format("%s.%s", possibleBody, mFileNameExtension));
                Log.w(LOG_TAG, "File name becomes too long.");
                return null;
            }
        }

        for (int i = mFileIndexMinimum; i <= mFileIndexMaximum; i++) {
            boolean numberIsAvailable = true;
            String body = null;
            for (String possibleExtension : mExtensionsToConsider) {
                body = String.format(bodyFormat, mFileNamePrefix, i, mFileNameSuffix);
                final String path =
                        String.format("%s/%s.%s", destDirectory, body, possibleExtension);
                synchronized (this) {
                    if (mReservedDestination.contains(path)) {
                        if (DEBUG) {
                            Log.d(LOG_TAG, String.format("The path %s is reserved.", path));
                        }
                        numberIsAvailable = false;
                        break;
                    }
                }
                final File file = new File(path);
                if (file.exists()) {
                    numberIsAvailable = false;
                    break;
                }
            }
            if (numberIsAvailable) {
                return String.format("%s/%s.%s", destDirectory, body, mFileNameExtension);
            }
        }

        Log.w(LOG_TAG, "Reached vCard number limit. Maybe there are too many vCard in the storage");
        mErrorReason = getString(R.string.fail_reason_too_many_vcard);
        return null;
    }
}
