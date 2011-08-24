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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import android.app.Instrumentation;
import android.os.AsyncTask;

import junit.framework.Assert;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
 * <p>
 * The onPreExecute method of the submitted AsyncTask will be called synchronously during the
 * call to {@link #submit(Object, AsyncTask, Object...)}.
 */
@ThreadSafe
public class FakeAsyncTaskExecutor implements AsyncTaskExecutor {
    private static final long DEFAULT_TIMEOUT_MS = 10000;

    /** The maximum length of time in ms to wait for tasks to execute during tests. */
    private final long mTimeoutMs = DEFAULT_TIMEOUT_MS;

    private final Object mLock = new Object();
    @GuardedBy("mLock") private final List<SubmittedTask> mSubmittedTasks = Lists.newArrayList();

    private final DelayedExecutor mBlockingExecutor = new DelayedExecutor();
    private final Instrumentation mInstrumentation;

    /** Create a fake AsyncTaskExecutor for use in unit tests. */
    public FakeAsyncTaskExecutor(Instrumentation instrumentation) {
        mInstrumentation = checkNotNull(instrumentation);
    }

    /** Encapsulates an async task with the params and identifier it was submitted with. */
    public interface SubmittedTask {
        Runnable getRunnable();
        Object getIdentifier();
        AsyncTask<?, ?, ?> getAsyncTask();
    }

    private static final class SubmittedTaskImpl implements SubmittedTask {
        private final Object mIdentifier;
        private final Runnable mRunnable;
        private final AsyncTask<?, ?, ?> mAsyncTask;

        public SubmittedTaskImpl(Object identifier, Runnable runnable,
                AsyncTask<?, ?, ?> asyncTask) {
            mIdentifier = identifier;
            mRunnable = runnable;
            mAsyncTask = asyncTask;
        }

        @Override
        public Object getIdentifier() {
            return mIdentifier;
        }

        @Override
        public Runnable getRunnable() {
            return mRunnable;
        }

        @Override
        public AsyncTask<?, ?, ?> getAsyncTask() {
            return mAsyncTask;
        }

        @Override
        public String toString() {
            return "SubmittedTaskImpl [mIdentifier=" + mIdentifier + "]";
        }
    }

    private class DelayedExecutor implements Executor {
        private final Object mNextLock = new Object();
        @GuardedBy("mNextLock") private Object mNextIdentifier;
        @GuardedBy("mNextLock") private AsyncTask<?, ?, ?> mNextTask;

        @Override
        public void execute(Runnable command) {
            synchronized (mNextLock) {
                mSubmittedTasks.add(new SubmittedTaskImpl(mNextIdentifier,
                        command, checkNotNull(mNextTask)));
                mNextIdentifier = null;
                mNextTask = null;
            }
        }

        public <T> AsyncTask<T, ?, ?> submit(Object identifier,
                AsyncTask<T, ?, ?> task, T... params) {
            synchronized (mNextLock) {
                checkState(mNextIdentifier == null);
                checkState(mNextTask == null);
                mNextIdentifier = identifier;
                mNextTask = checkNotNull(task, "Already had a valid task.\n"
                        + "Are you calling AsyncTaskExecutor.submit(...) from within the "
                        + "onPreExecute() method of another task being submitted?\n"
                        + "Sorry!  Not that's not supported.");
            }
            return task.executeOnExecutor(this, params);
        }
    }

    @Override
    public <T> AsyncTask<T, ?, ?> submit(Object identifier, AsyncTask<T, ?, ?> task, T... params) {
        AsyncTaskExecutors.checkCalledFromUiThread();
        return mBlockingExecutor.submit(identifier, task, params);
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
    public void runTask(Object identifier) throws InterruptedException {
        List<SubmittedTask> tasks = getSubmittedTasksByIdentifier(identifier, true);
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
    public void runAllTasks(Object identifier) throws InterruptedException {
        List<SubmittedTask> tasks = getSubmittedTasksByIdentifier(identifier, true);
        Assert.assertTrue("There were no tasks with identifier " + identifier, tasks.size() > 0);
        for (SubmittedTask task : tasks) {
            runTask(task);
        }
    }

    /**
     * Executes a single {@link SubmittedTask}.
     * <p>
     * Blocks until the task has completed running.
     */
    private <T> void runTask(final SubmittedTask submittedTask) throws InterruptedException {
        submittedTask.getRunnable().run();
        // Block until the onPostExecute or onCancelled has finished.
        // Unfortunately we can't be sure when the AsyncTask will have posted its result handling
        // code to the main ui thread, the best we can do is wait for the Status to be FINISHED.
        final CountDownLatch latch = new CountDownLatch(1);
        class AsyncTaskHasFinishedRunnable implements Runnable {
            @Override
            public void run() {
                if (submittedTask.getAsyncTask().getStatus() == AsyncTask.Status.FINISHED) {
                    latch.countDown();
                } else {
                    mInstrumentation.waitForIdle(this);
                }
            }
        }
        mInstrumentation.waitForIdle(new AsyncTaskHasFinishedRunnable());
        Assert.assertTrue(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    private List<SubmittedTask> getSubmittedTasksByIdentifier(
            Object identifier, boolean remove) {
        Preconditions.checkNotNull(identifier, "can't lookup tasks by 'null' identifier");
        List<SubmittedTask> results = Lists.newArrayList();
        synchronized (mLock) {
            Iterator<SubmittedTask> iter = mSubmittedTasks.iterator();
            while (iter.hasNext()) {
                SubmittedTask task = iter.next();
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
