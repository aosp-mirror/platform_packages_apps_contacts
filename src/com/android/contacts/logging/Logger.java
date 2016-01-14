/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.logging;

import android.app.Activity;
import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.commonbind.analytics.AnalyticsUtil;

/**
 * Logs analytics events.
 */
public abstract class Logger {
    public static final String TAG = "Logger";

    public static Logger getInstance() {
        return null;
    }

    /**
     * Logs an event indicating that a screen was displayed.
     *
     * @param screenType integer identifier of the displayed screen
     * @param activity Parent activity of the displayed screen.
     */
    public static void logScreenView(int screenType, Activity activity) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logScreenViewImpl(screenType);
        }

        final String screenName = ScreenEvent.getScreenName(screenType);
        if (TextUtils.isEmpty(screenName)) {
            Log.w(TAG, "Unknown screenType: " + screenType);
        } else {
            AnalyticsUtil.sendScreenView(screenName, activity, /* tag */ null);
        }
    }

    public abstract void logScreenViewImpl(int screenType);
}
