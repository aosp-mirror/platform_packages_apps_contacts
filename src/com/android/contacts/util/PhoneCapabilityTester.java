/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

public final class PhoneCapabilityTester {
    /**
     * Tests whether the Intent has a receiver registered. This can be used to show/hide
     * functionality (like Phone, SMS)
     */
    public static boolean isIntentRegistered(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> receiverList = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receiverList.size() > 0;
    }

    /**
     * Returns true if this device can be used to make phone calls
     */
    public static boolean isPhone(Context context) {
        // Is the device physically capabable of making phone calls?
        if (!context.getResources().getBoolean(com.android.internal.R.bool.config_voice_capable)) {
            return false;
        }

        // Is there an app registered that accepts the call intent?
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts(Constants.SCHEME_TEL, "", null));
        return isIntentRegistered(context, intent);
    }

    /**
     * Returns true if the device has an SMS application installed.
     */
    public static boolean isSmsIntentRegistered(Context context) {
        final Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts(Constants.SCHEME_SMSTO, "", null));
        return isIntentRegistered(context, intent);
    }
}
