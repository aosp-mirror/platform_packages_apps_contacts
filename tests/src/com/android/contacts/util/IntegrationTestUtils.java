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

import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.PowerManager.FULL_WAKE_LOCK;
import static android.os.PowerManager.ON_AFTER_RELEASE;

import com.google.common.base.Preconditions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.PowerManager;
import android.view.View;

import junit.framework.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/** Some utility methods for making integration testing smoother. */
@ThreadSafe
public class IntegrationTestUtils {
    private static final String TAG = "IntegrationTestUtils";

    private final Instrumentation mInstrumentation;
    private final Object mLock = new Object();
    @GuardedBy("mLock") private PowerManager.WakeLock mWakeLock;

    public IntegrationTestUtils(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    /**
     * Find a view by a given resource id, from the given activity, and click it, iff it is
     * enabled according to {@link View#isEnabled()}.
     */
    public void clickButton(final Activity activity, final int buttonResourceId) throws Throwable {
        runOnUiThreadAndGetTheResult(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                View view = activity.findViewById(buttonResourceId);
                Assert.assertNotNull(view);
                if (view.isEnabled()) {
                    view.performClick();
                }
                return null;
            }
        });
    }

    // TODO: Move this class and the appropriate documentation into a test library, having checked
    // first to see if exactly this code already exists or not.
    /**
     * Execute a callable on the ui thread, returning its result synchronously.
     * <p>
     * Waits for an idle sync on the main thread (see {@link Instrumentation#waitForIdle(Runnable)})
     * before executing this callable.
     */
    private <T> T runOnUiThreadAndGetTheResult(Callable<T> callable) throws Throwable {
        FutureTask<T> future = new FutureTask<T>(callable);
        mInstrumentation.waitForIdle(future);
        try {
            return future.get();
        } catch (ExecutionException e) {
            // Unwrap the cause of the exception and re-throw it.
            throw e.getCause();
        }
    }

    /**
     * Wake up the screen, useful in tests that want or need the screen to be on.
     * <p>
     * This is usually called from setUp() for tests that require it.  After calling this method,
     * {@link #releaseScreenWakeLock()} must be called, this is usually done from tearDown().
     */
    public void acquireScreenWakeLock(Context context) {
        synchronized (mLock) {
            Preconditions.checkState(mWakeLock == null, "mWakeLock was already held");
            mWakeLock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE | FULL_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
    }

    /** Release the wake lock previously acquired with {@link #acquireScreenWakeLock(Context)}. */
    public void releaseScreenWakeLock() {
        synchronized (mLock) {
            // We don't use Preconditions to force you to have acquired before release.
            // This is because we don't want unnecessary exceptions in tearDown() since they'll
            // typically mask the actual exception that happened during the test.
            // The other reason is that this method is most likely to be called from tearDown(),
            // which is invoked within a finally block, so it's not infrequently the case that
            // the setUp() method fails before getting the lock, at which point we don't want
            // to fail in tearDown().
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;
            }
        }
    }
}
