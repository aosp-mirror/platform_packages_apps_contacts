/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.contacts.commonbind.analytics;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.text.TextUtils;

public class AnalyticsUtil {

    /**
     * Initialize this class and setup automatic activity tracking.
     */
    public static void initialize(Application application) { }

    /**
     * Log a screen view for {@param fragment}.
     */
    public static void sendScreenView(Fragment fragment) {}

    public static void sendScreenView(Fragment fragment, Activity activity) {}

    public static void sendScreenView(Fragment fragment, Activity activity, String tag) {}

    public static void sendScreenView(String fragmentName, Activity activity, String tag) {}

    /**
     * Logs a event to the analytics server.
     *
     * @param application The application the tracker is stored in.
     * @param category The category for the event.
     * @param action The event action.
     * @param label The event label.
     * @param value The value associated with the event.
     */
    public static void sendEvent(Application application, String category, String action,
            String label, long value) { }
}