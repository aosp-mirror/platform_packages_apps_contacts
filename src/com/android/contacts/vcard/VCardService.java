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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    /* package */ static final int MSG_CANCEL_IMPORT_REQUEST = 3;

    /* package */ static final int IMPORT_NOTIFICATION_ID = 1000;
    /* package */ static final int EXPORT_NOTIFICATION_ID = 1001;

    /* package */ static final String CACHE_FILE_PREFIX = "import_tmp_";

    public class RequestHandler extends Handler {
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
                case MSG_CANCEL_IMPORT_REQUEST: {
                    handleCancelAllImportRequest();
                    break;
                }
                // TODO: add cancel capability for export..
                default: {
                    Log.w(LOG_TAG, "Received unknown request, ignoring it.");
                    super.hasMessages(msg.what);
                }
            }
        }
    }

    private final Handler mHandler = new RequestHandler();
    private final Messenger mMessenger = new Messenger(mHandler);
    // Should be single thread, as we don't want to simultaneously handle import and export
    // requests.
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    // Three types of map for remembering on-going jobs. Key is jobId. JobIds for import and export
    // are never overlapped each other.
    //
    // Note:
    // We don't use Future#cancel() but cancel() method in ImportProcessor/ExportProcessor,
    // so Future#isCanceled() should never be refered. Future objects in mFutureMap is used
    // just for checking each job is already finished or not (See Future#isDone()).
    //
    // Reason:
    // Future#cancel() doesn't halt the running thread. The thread keeps running even after Service
    // is stopped, while cancel() in ImporterProcessor/ExporterProcesser is expected to
    // stop the work and exit the thread appropriately.
    private int mCurrentJobId;
    private final Map<Integer, ImportProcessor> mRunningJobMapForImport =
            new HashMap<Integer, ImportProcessor>();
    private final Map<Integer, ExportProcessor> mRunningJobMapForExport =
            new HashMap<Integer, ExportProcessor>();
    private final Map<Integer, Future<?>> mFutureMap = new HashMap<Integer, Future<?>>();

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
        Log.i(LOG_TAG, "VCardService is finishing.");
        cancelRequestsAndshutdown();
        clearCache();
        super.onDestroy();
    }

    private synchronized void handleImportRequest(ImportRequest request) {
        Log.i(LOG_TAG, String.format("Received vCard import request. id: %d", mCurrentJobId));
        final ImportProcessor importProcessor =
                new ImportProcessor(this, request, mCurrentJobId);
        final Future<?> future;
        try {
            future = mExecutorService.submit(importProcessor);
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "vCard import request is rejected.", e);
            // TODO: a little unkind to show Toast in this case, which is shown just a moment.
            // Ideally we should show some persistent something users can notice more easily.
            Toast.makeText(this, getString(R.string.vcard_import_request_rejected_message),
                    Toast.LENGTH_LONG).show();
            return;
        }
        mRunningJobMapForImport.put(mCurrentJobId, importProcessor);
        mFutureMap.put(mCurrentJobId, future);
        mCurrentJobId++;
        // TODO: Ideally we should detect the current status of import/export and show "started"
        // when we can import right now and show "will start" when we cannot.
        Toast.makeText(this, getString(R.string.vcard_import_will_start_message),
                Toast.LENGTH_LONG).show();
    }

    private synchronized void handleExportRequest(ExportRequest request) {
        Log.i(LOG_TAG, String.format("Received vCard export request. id: %d", mCurrentJobId));
        final ExportProcessor exportProcessor =
                new ExportProcessor(this, request, mCurrentJobId);
        final Future<?> future;
        try {
            future = mExecutorService.submit(exportProcessor);
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "vCard export request is rejected.", e);
            Toast.makeText(this, getString(R.string.vcard_export_request_rejected_message),
                    Toast.LENGTH_LONG).show();
            return;
        }
        mRunningJobMapForExport.put(mCurrentJobId, exportProcessor);
        mFutureMap.put(mCurrentJobId, future);
        mCurrentJobId++;
        // See the comment in handleImportRequest()
        Toast.makeText(this, getString(R.string.vcard_export_will_start_message),
                Toast.LENGTH_LONG).show();
    }

    private void handleCancelAllImportRequest() {
        Log.i(LOG_TAG, "Received cancel import request.");
        cancelAllImportRequest();
    }

    private synchronized void cancelAllImportRequest() {
        for (final Map.Entry<Integer, ImportProcessor> entry :
                mRunningJobMapForImport.entrySet()) {
            final int jobId = entry.getKey();
            final ImportProcessor importProcessor = entry.getValue();
            importProcessor.cancel();
            if (mFutureMap.remove(jobId) == null) {
                Log.w(LOG_TAG, "Tried to remove unknown Future object with id: " + jobId);
            }
            Log.i(LOG_TAG, String.format("Canceling job %d", jobId));
        }
        mRunningJobMapForImport.clear();
        stopServiceWhenNoJob();
    }

    private synchronized void cancelAllExportRequest() {
        for (final Map.Entry<Integer, ExportProcessor> entry :
                mRunningJobMapForExport.entrySet()) {
            final int jobId = entry.getKey();
            final ExportProcessor exportProcessor = entry.getValue();
            exportProcessor.cancel();
            if (mFutureMap.remove(jobId) == null) {
                Log.w(LOG_TAG, "Tried to remove unknown Future object with id: " + jobId);
            }
            Log.i(LOG_TAG, String.format("Canceling job %d", jobId));
        }
        mRunningJobMapForExport.clear();
        stopServiceWhenNoJob();
    }

    /**
     * Checks job list and call {@link #stopSelf()} when there's no job now.
     * A new job cannot be submitted any more after this call.
     */
    private synchronized void stopServiceWhenNoJob() {
        // Log.v(LOG_TAG, "shutdownWhenNoJob() called");
        if (mFutureMap.size() > 0) {
            Log.v(LOG_TAG, "We have future tasks not removed from cache. Check their status.");
            for (final Map.Entry<Integer, Future<?>> entry : mFutureMap.entrySet()) {
                final int jobId = entry.getKey();
                final Future<?> future = entry.getValue();
                if (future.isDone()) {
                    // This shouldn't happen. The other methods shold handle this case correctly.
                    Log.w(LOG_TAG,
                            String.format("Cache has already finished job (id: %d). Remove it",
                                    jobId));
                    mFutureMap.remove(jobId);
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
        Log.i(LOG_TAG, String.format("Received vCard import finish notification (id: %d). "
                + "Result: %b", jobId, (successful ? "success" : "failure")));
        if (mRunningJobMapForImport.remove(jobId) == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        if (mFutureMap.remove(jobId) == null) {
            Log.w(LOG_TAG, "Tried to remove unknown Future object with id: " + jobId);
        }
        stopServiceWhenNoJob();
    }

    /* package */ synchronized void handleFinishExportNotification(
            int jobId, boolean successful) {
        Log.i(LOG_TAG, String.format("Received vCard export finish notification (id: %d). "
                + "Result: %b", jobId, (successful ? "success" : "failure")));
        if (mRunningJobMapForExport.remove(jobId) == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        if (mFutureMap.remove(jobId) == null) {
            Log.w(LOG_TAG, "Tried to remove unknown Future object with id: " + jobId);
        }
        stopServiceWhenNoJob();
    }

    /**
     * Cancels all the import/export requests and call {@link ExecutorService#shutdown()}, which
     * means this Service becomes no longer ready for import/export requests.
     *
     * Mainly called from onDestroy().
     */
    private synchronized void cancelRequestsAndshutdown() {
        if (mRunningJobMapForImport.size() > 0) {
            Log.i(LOG_TAG,
                    String.format("Cancel existing all import requests (remains: ",
                            mRunningJobMapForImport.size()));
            cancelAllImportRequest();
        }
        if (mRunningJobMapForExport.size() > 0) {
            Log.i(LOG_TAG,
                    String.format("Cancel existing all import requests (remains: ",
                            mRunningJobMapForExport.size()));
            cancelAllExportRequest();
        }
        mExecutorService.shutdown();
    }

    /**
     * Removes import caches stored locally.
     */
    private void clearCache() {
        Log.i(LOG_TAG, "start removing cache files if exist.");
        final String[] fileLists = fileList();
        for (String fileName : fileLists) {
            if (fileName.startsWith(CACHE_FILE_PREFIX)) {
                // We don't want to keep all the caches so we remove cache files old enough.
                // TODO: Ideally we should ask VCardService whether the file is being used or
                // going to be used.
                final Date now = new Date();
                final File file = getFileStreamPath(fileName);
                Log.i(LOG_TAG, "Remove a temporary file: " + fileName);
                deleteFile(fileName);
            }
        }
    }
}
