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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Executors;

import android.app.Instrumentation;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Simple test implementation of BackgroundTaskService.
 */
public class FakeBackgroundTaskService extends BackgroundTaskService {
    private final List<BackgroundTask> mSubmittedTasks = Lists.newArrayList();

    @Override
    public void submit(BackgroundTask task) {
        mSubmittedTasks.add(task);
    }

    public List<BackgroundTask> getSubmittedTasks() {
        return mSubmittedTasks;
    }

    public static Executor createMainSyncExecutor(final Instrumentation instrumentation) {
        return new Executor() {
            @Override
            public void execute(Runnable runnable) {
                instrumentation.runOnMainSync(runnable);
            }
        };
    }

    /**
     * Executes the background tasks, using the supplied executors.
     * <p>
     * This is most commonly used with {@link Executors#sameThreadExecutor()} for the first argument
     * and {@link #createMainSyncExecutor(Instrumentation)}, so that the test thread can directly
     * run the tasks in the background, then have the onPostExecute methods happen on the main ui
     * thread.
     */
    public void runAllBackgroundTasks(Executor doInBackgroundExecutor,
            final Executor onPostExecuteExecutor) {
        for (final BackgroundTask task : getSubmittedTasks()) {
            final Object visibilityLock = new Object();
            doInBackgroundExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (visibilityLock) {
                        task.doInBackground();
                    }
                    onPostExecuteExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (visibilityLock) {
                                task.onPostExecute();
                            }
                        }
                    });
                }
            });
        }
    }
}
