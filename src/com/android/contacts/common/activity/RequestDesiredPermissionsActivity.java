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

package com.android.contacts.common.activity;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Requests permissions that are not absolutely required by the calling Activity;
 * if permissions are denied, the calling Activity is still restarted.
 *
 * Activities that have a set of permissions that must be granted in order for the Activity to
 * function propertly should call
 * {@link RequestPermissionsActivity#startPermissionActivity(Activity, String[], Class)}
 * before calling {@link RequestDesiredPermissionsActivity#startPermissionActivity(Activity)}.
 */
public class RequestDesiredPermissionsActivity extends RequestPermissionsActivityBase {

    private static final String[] DESIRED_PERMISSIONS = new String[] {
            permission.ACCESS_FINE_LOCATION,
            permission.READ_CALENDAR,
            permission.READ_SMS,
    };

    @Override
    protected String[] getRequiredPermissions() {
        return DESIRED_PERMISSIONS;
    }

    @Override
    protected String[] getDesiredPermissions() {
        return DESIRED_PERMISSIONS;
    }

    /**
     * If any desired permissions the Contacts app needs are missing, open an Activity
     * to prompt the user for these permissions.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     */
    public static boolean startPermissionActivity(Activity activity) {
        final Bundle extras = activity.getIntent().getExtras();
        if (extras != null && extras.getBoolean(STARTED_PERMISSIONS_ACTIVITY, false)) {
            return false;
        }
        return startPermissionActivity(activity, DESIRED_PERMISSIONS,
                RequestDesiredPermissionsActivity.class);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(mPreviousActivityIntent);
        overridePendingTransition(0, 0);

        finish();
        overridePendingTransition(0, 0);
    }
}