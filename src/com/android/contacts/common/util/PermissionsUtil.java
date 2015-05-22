/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.common.util;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Utility class to help with runtime permissions.
 */
public class PermissionsUtil {
    // Each permission in this list is a cherry-picked permission from a particular permission
    // group. Granting a permission group enables access to all permissions in that group so we
    // only need to check a single permission in each group.
    // Note: This assumes that the app has correctly requested for all the relevant permissions
    // in its Manifest file.
    public static final String PHONE = permission.CALL_PHONE;
    public static final String CONTACTS = permission.READ_CONTACTS;
    public static final String LOCATION = permission.ACCESS_FINE_LOCATION;

    private static Boolean sHasPhonePermissions;
    private static Boolean sHasContactsPermissions;
    private static Boolean sHasLocationPermissions;

    public static boolean hasPhonePermissions(Context context) {
        if (sHasPhonePermissions == null) {
            sHasPhonePermissions = hasPermission(context, PHONE);
        }
        return sHasPhonePermissions;
    }

    public static boolean hasContactsPermissions(Context context) {
        if (sHasContactsPermissions == null) {
            sHasContactsPermissions = hasPermission(context, CONTACTS);
        }
        return sHasContactsPermissions;
    }

    public static boolean hasLocationPermissions(Context context) {
        if (sHasLocationPermissions == null) {
            sHasLocationPermissions = hasPermission(context, LOCATION);
        }
        return sHasLocationPermissions;
    }

    /**
     * To be called during various activity lifecycle events to update the cached versions of the
     * permissions.
     *
     * @param context A valid context.
     */
    public static void updateCachedPermissions(Context context) {
        hasPermission(context, PHONE);
        hasPermission(context, CONTACTS);
        hasPermission(context, LOCATION);
    }

    public static boolean hasPermission(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
}
