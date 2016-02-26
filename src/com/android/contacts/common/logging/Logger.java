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
package com.android.contacts.common.logging;

import android.app.Activity;
import android.app.Fragment;
import android.text.TextUtils;

import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.commonbind.ObjectFactory;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;

/**
 * Logs analytics events.
 */
public abstract class Logger {
    public static final String TAG = "Logger";

    private static Logger getInstance() {
        return ObjectFactory.getLogger();
    }

    /**
     * Logs an event indicating that a screen was displayed.
     *
     * @param screenType integer identifier of the displayed screen
     * @param activity Parent activity of the displayed screen.
     */
    public static void logScreenView(Activity activity, int screenType) {
        logScreenView(activity, screenType, ScreenType.UNKNOWN);
    }

    /**
     * @param previousScreenType integer identifier of the displayed screen the user came from.
     */
    public static void logScreenView(Activity activity, int screenType, int previousScreenType) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logScreenViewImpl(screenType, previousScreenType);
        }
        // We prepend the friendly screen name with "From" and use it as the tag to indicate the
        // screen where the user was previously when they initiated the screen view being logged
        String tag = ScreenType.getFriendlyName(previousScreenType);
        if (!TextUtils.isEmpty(tag)) {
            tag = "From" + tag;
        }
        AnalyticsUtil.sendScreenView(/* fragmentName */ (String) null, activity, tag);
    }

    /**
     * Logs the results of a user search for a particular contact.
     */
    public static void logSearchEvent(SearchState searchState) {
        final Logger logger = getInstance();
         if (logger != null) {
            logger.logSearchEventImpl(searchState);
        }
    }

    public abstract void logScreenViewImpl(int screenType, int previousScreenType);
    public abstract void logSearchEventImpl(SearchState searchState);
}
