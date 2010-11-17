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
    /* package */ static final int MSG_CANCEL_IMPORT_REQUEST = 3;

    /* package */ static final int IMPORT_NOTIFICATION_ID = 1000;
    /* package */ static final int EXPORT_NOTIFICATION_ID = 1001;

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
    });

    // Should be single thread, as we don't want to simultaneously handle import and export
    // requests.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private int mCurrentJobId;

    // Stores all unfinished import/export jobs which will be executed by mExecutorService.
    // Key is jobId.
    private final Map<Integer, ProcessorBase> mRunningJobMap =
            new HashMap<Integer, ProcessorBase>();

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
        tryExecute(new ImportProcessor(this, request, mCurrentJobId),
                R.string.vcard_import_will_start_message,
                R.string.vcard_import_request_rejected_message);
    }

    private synchronized void handleExportRequest(ExportRequest request) {
        tryExecute(new ExportProcessor(this, request, mCurrentJobId),
                R.string.vcard_export_will_start_message,
                R.string.vcard_export_request_rejected_message);
    }

    /**
     * Tries to call {@link ExecutorService#execute(Runnable)} toward a given processor and
     * shows appropriate Toast using given resource ids.
     * Updates relevant instances when successful.
     */
    private synchronized void tryExecute(ProcessorBase processor,
            int successfulMessageId, int rejectedMessageId) {
        try {
            mExecutorService.execute(processor);
            mRunningJobMap.put(mCurrentJobId, processor);
            mCurrentJobId++;
            // TODO: Ideally we should detect the current status of import/export and show
            // "started" when we can import right now and show "will start" when we cannot.
            Toast.makeText(this, getString(successfulMessageId), Toast.LENGTH_LONG).show();
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "Failed to excetute a job.", e);
            // TODO: a little unkind to show Toast in this case, which is shown just a moment.
            // Ideally we should show some persistent something users can notice more easily.
            Toast.makeText(this, getString(rejectedMessageId), Toast.LENGTH_LONG).show();
        }
    }

    private void handleCancelAllImportRequest() {
        Log.i(LOG_TAG, "Received cancel import request.");
        cancelAllImportRequests();
    }

    private synchronized void cancelAllImportRequests() {
        for (final Map.Entry<Integer, ProcessorBase> entry : mRunningJobMap.entrySet()) {
            final ProcessorBase processor = entry.getValue();
            if (processor.getType() == ProcessorBase.PROCESSOR_TYPE_IMPORT) {
                final int jobId = entry.getKey();
                processor.cancel(true);
                mRunningJobMap.remove(jobId);
                Log.i(LOG_TAG, String.format("Canceling job %d", jobId));
            }
        }
        stopServiceWhenNoJob();
    }

    private synchronized void cancelAllExportRequests() {
        for (final Map.Entry<Integer, ProcessorBase> entry : mRunningJobMap.entrySet()) {
            final ProcessorBase processor = entry.getValue();
            if (processor.getType() == ProcessorBase.PROCESSOR_TYPE_EXPORT) {
                final int jobId = entry.getKey();
                processor.cancel(true);
                mRunningJobMap.remove(jobId);
                Log.i(LOG_TAG, String.format("Canceling job %d", jobId));
            }
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
}
