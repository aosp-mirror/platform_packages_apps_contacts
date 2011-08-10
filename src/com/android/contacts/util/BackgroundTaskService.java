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

package com.android.contacts.util;

import android.os.AsyncTask;

import java.util.concurrent.Executor;

/**
 * Service used to submit tasks to run in the background.
 * <p>
 * BackgroundTaskService makes the same memory-visibility guarantees that AsyncTask which it
 * emulates makes, namely that fields set in the {@link BackgroundTask#doInBackground()} method
 * will be visible to the {@link BackgroundTask#onPostExecute()} method.
 * <p>
 * You are not expected to derive from this class unless you are writing your own test
 * implementation, or you are absolutely sure that the instance in
 * {@link #createAsyncTaskBackgroundTaskService()} doesn't do what you need.
 */
public abstract class BackgroundTaskService {
    public static final String BACKGROUND_TASK_SERVICE = BackgroundTaskService.class.getName();

    /**
     * Executes the given BackgroundTask with the default Executor.
     * <p>
     * All {@link BackgroundTask#doInBackground()} tasks will be guaranteed to happen serially.
     * If this is not what you want, see {@link #submit(BackgroundTask, Executor)}.
     */
    public abstract void submit(BackgroundTask task);

    /**
     * Executes the BackgroundTask with the supplied Executor.
     * <p>
     * The main use-case for this method will be to allow submitted tasks to perform their
     * {@link BackgroundTask#doInBackground()} methods concurrently.
     */
    public abstract void submit(BackgroundTask task, Executor executor);

    /**
     * Creates a concrete BackgroundTaskService whose default Executor is
     * {@link AsyncTask#SERIAL_EXECUTOR}.
     */
    public static BackgroundTaskService createAsyncTaskBackgroundTaskService() {
        return new AsyncTaskBackgroundTaskService();
    }

    private static final class AsyncTaskBackgroundTaskService extends BackgroundTaskService {
        @Override
        public void submit(BackgroundTask task) {
            submit(task, AsyncTask.SERIAL_EXECUTOR);
        }

        @Override
        public void submit(final BackgroundTask task, Executor executor) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    task.doInBackground();
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    task.onPostExecute();
                }
            }.executeOnExecutor(executor);
        }
    }
}
