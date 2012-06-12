/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

/** Static methods that are useful for scheduling actions to occur at a later time. */
public class SchedulingUtils {


    /** Runs a piece of code after the next layout run */
    public static void doAfterLayout(final View view, final Runnable runnable) {
        final OnGlobalLayoutListener listener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Layout pass done, unregister for further events
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                runnable.run();
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /** Runs a piece of code just before the next draw. */
    public static void doAfterDraw(final View view, final Runnable runnable) {
        final OnDrawListener listener = new OnDrawListener() {
            @Override
            public void onDraw() {
                view.getViewTreeObserver().removeOnDrawListener(this);
                runnable.run();
            }
        };
        view.getViewTreeObserver().addOnDrawListener(listener);
    }
}
