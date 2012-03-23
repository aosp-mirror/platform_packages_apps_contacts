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

import android.content.Context;
import android.content.Intent;

/**
 * Utilities for managing CallerInfoCache.
 *
 * The cache lives in Phone package and is used as fallback storage when database lookup is slower
 * than expected. It remembers some information necessary for responding to incoming calls
 * (e.g. custom ringtone settings, send-to-voicemail).
 *
 * Even though the cache will be updated periodically, Contacts app can request the cache update
 * via broadcast Intent. This class provides that mechanism, and possibly other misc utilities
 * for the update mechanism.
 */
public final class CallerInfoCacheUtils {
    private static final String UPDATE_CALLER_INFO_CACHE =
            "com.android.phone.UPDATE_CALLER_INFO_CACHE";

    private CallerInfoCacheUtils() {
    }

    /**
     * Sends an Intent, notifying CallerInfo cache should be updated.
     *
     * Note: CallerInfo is *not* part of public API, and no guarantee is available around its
     * specific behavior. In practice this will only be used by Phone package, but may change
     * in the future.
     *
     * See also CallerInfoCache in Phone package for more information.
     */
    public static void sendUpdateCallerInfoCacheIntent(Context context) {
        context.sendBroadcast(new Intent(UPDATE_CALLER_INFO_CACHE));
    }
}