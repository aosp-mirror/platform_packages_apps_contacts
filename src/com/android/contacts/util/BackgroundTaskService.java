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

/**
 * Service used to submit tasks to run in the background.
 * <p>
 * BackgroundTaskService makes the same memory-visibility guarantees that AsyncTask which it
 * emulates makes, namely that fields set in the {@link BackgroundTask#doInBackground()} method
 * will be visible to the {@link BackgroundTask#onPostExecute()} method.
 */
public abstract class BackgroundTaskService {
    public static final String BACKGROUND_TASK_SERVICE = BackgroundTaskService.class.getName();

    public abstract void submit(BackgroundTask task);

    public static BackgroundTaskService createBackgroundTaskService() {
        return new AsyncTaskBackgroundTaskService();
    }

    private static final class AsyncTaskBackgroundTaskService extends BackgroundTaskService {
        @Override
        public void submit(final BackgroundTask task) {
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
            }.execute();
        }
    }
}
