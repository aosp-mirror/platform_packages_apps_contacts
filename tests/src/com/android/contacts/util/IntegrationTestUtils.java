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

import android.app.Activity;
import android.app.Instrumentation;
import android.view.View;

import junit.framework.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Some utility methods for making integration testing smoother. */
public class IntegrationTestUtils {
    private final Instrumentation mInstrumentation;

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
}
