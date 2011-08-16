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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Executors;

import android.app.Instrumentation;
import android.os.AsyncTask;

import junit.framework.Assert;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Test implementation of AsyncTaskExecutor.
 * <p>
 * This class is thread-safe. As per the contract of the AsyncTaskExecutor, the submit methods must
 * be called from the main ui thread, however the other public methods may be called from any thread
 * (most commonly the test thread).
 * <p>
 * Tasks submitted to this executor will not be run immediately. Rather they will be stored in a
 * list of submitted tasks, where they can be examined. They can also be run on-demand using the run
 * methods, so that different ordering of AsyncTask execution can be simulated.
 */
@ThreadSafe
public class FakeAsyncTaskExecutor implements AsyncTaskExecutor {
    private static final long DEFAULT_TIMEOUT_MS = 10000;
    private static final Executor DEFAULT_EXECUTOR = Executors.sameThreadExecutor();

    /** The maximum length of time in ms to wait for tasks to execute during tests. */
    private final long mTimeoutMs = DEFAULT_TIMEOUT_MS;
    /** The executor for the background part of our test tasks. */
    private final Executor mExecutor = DEFAULT_EXECUTOR;

    private final Object mLock = new Object();
    @GuardedBy("mLock") private final List<SubmittedTask<?>> mSubmittedTasks = Lists.newArrayList();

    private final Instrumentation mInstrumentation;

    /** Create a fake AsyncTaskExecutor for use in unit tests. */
    public FakeAsyncTaskExecutor(Instrumentation instrumentation) {
        mInstrumentation = Preconditions.checkNotNull(instrumentation);
    }

    /** Encapsulates an async task with the params and identifier it was submitted with. */
    public interface SubmittedTask<T> {
        AsyncTask<T, ?, ?> getTask();
        T[] getParams();
        Object getIdentifier();
    }

    private static final class SubmittedTaskImpl<T> implements SubmittedTask<T> {
        private final Object mIdentifier;
        private final AsyncTask<T, ?, ?> mTask;
        private final T[] mParams;

        public SubmittedTaskImpl(Object identifier, AsyncTask<T, ?, ?> task, T[] params) {
            mIdentifier = identifier;
            mTask = task;
            mParams = params;
        }

        @Override
        public Object getIdentifier() {
            return mIdentifier;
        }

        @Override
        public AsyncTask<T, ?, ?> getTask() {
            return mTask;
        }

        @Override
        public T[] getParams() {
            return mParams;
        }

        @Override
        public String toString() {
            return "SubmittedTaskImpl [mIdentifier=" + mIdentifier + "]";
        }
    }

    @Override
    public <T> AsyncTask<T, ?, ?> submit(Object identifier, AsyncTask<T, ?, ?> task, T... params) {
        AsyncTaskExecutors.checkCalledFromUiThread();
        synchronized (mLock) {
            mSubmittedTasks.add(new SubmittedTaskImpl<T>(identifier, task, params));
            return task;
        }
    }

    /**
     * Runs a single task matching the given identifier.
     * <p>
     * Removes the matching task from the list of submitted tasks, then runs it. The executor used
     * to execute this async task will be a same-thread executor.
     * <p>
     * Fails if there was not exactly one task matching the given identifier.
     * <p>
     * This method blocks until the AsyncTask has completely finished executing.
     */
    public void runTask(Enum<?> identifier) throws InterruptedException {
        List<SubmittedTask<?>> tasks = getSubmittedTasksByIdentifier(identifier, true);
        Assert.assertEquals("Expected one task " + identifier + ", got " + tasks, 1, tasks.size());
        runTask(tasks.get(0));
    }

    /**
     * Runs all tasks whose identifier matches the given identifier.
     * <p>
     * Removes all matching tasks from the list of submitted tasks, and runs them. The executor used
     * to execute these async tasks will be a same-thread executor.
     * <p>
     * Fails if there were no tasks matching the given identifier.
     * <p>
     * This method blocks until the AsyncTask objects have completely finished executing.
     */
    public void runAllTasks(Enum<?> identifier) throws InterruptedException {
        List<SubmittedTask<?>> tasks = getSubmittedTasksByIdentifier(identifier, true);
        Assert.assertTrue("There were no tasks with identifier " + identifier, tasks.size() > 0);
        for (SubmittedTask<?> task : tasks) {
            runTask(task);
        }
    }

    /**
     * Executes a single {@link AsyncTask} using the supplied executors.
     * <p>
     * Blocks until the task has completed running.
     */
    private <T> void runTask(SubmittedTask<T> submittedTask) throws InterruptedException {
        final AsyncTask<T, ?, ?> task = submittedTask.getTask();
        task.executeOnExecutor(mExecutor, submittedTask.getParams());
        // Block until the task has finished running in the background.
        try {
            task.get(mTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("waited too long");
        }
        // Block until the onPostExecute or onCancelled has finished.
        // Unfortunately we can't be sure when the AsyncTask will have posted its result handling
        // code to the main ui thread, the best we can do is wait for the Status to be FINISHED.
        final CountDownLatch latch = new CountDownLatch(1);
        class AsyncTaskHasFinishedRunnable implements Runnable {
            @Override
            public void run() {
                if (task.getStatus() == AsyncTask.Status.FINISHED) {
                    latch.countDown();
                } else {
                    mInstrumentation.waitForIdle(this);
                }
            }
        }
        mInstrumentation.waitForIdle(new AsyncTaskHasFinishedRunnable());
        Assert.assertTrue(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    private List<SubmittedTask<?>> getSubmittedTasksByIdentifier(
            Enum<?> identifier, boolean remove) {
        Preconditions.checkNotNull(identifier, "can't lookup tasks by 'null' identifier");
        List<SubmittedTask<?>> results = Lists.newArrayList();
        synchronized (mLock) {
            Iterator<SubmittedTask<?>> iter = mSubmittedTasks.iterator();
            while (iter.hasNext()) {
                SubmittedTask<?> task = iter.next();
                if (identifier.equals(task.getIdentifier())) {
                    results.add(task);
                    iter.remove();
                }
            }
        }
        return results;
    }

    /** Get a factory that will return this instance - useful for testing. */
    public AsyncTaskExecutors.AsyncTaskExecutorFactory getFactory() {
        return new AsyncTaskExecutors.AsyncTaskExecutorFactory() {
            @Override
            public AsyncTaskExecutor createAsyncTaskExeuctor() {
                return FakeAsyncTaskExecutor.this;
            }
        };
    }
}
