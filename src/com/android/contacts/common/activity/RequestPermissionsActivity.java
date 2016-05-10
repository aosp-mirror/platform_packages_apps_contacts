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

package com.android.contacts.common.activity;

import com.android.contacts.common.R;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

/**
 * Activity that requests permissions needed for activities exported from Contacts.
 */
public class RequestPermissionsActivity extends RequestPermissionsActivityBase {

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            // "Contacts" group. Without this permission, the Contacts app is useless.
            permission.GET_ACCOUNTS,
            permission.READ_CONTACTS,
            permission.WRITE_CONTACTS,
            // "Phone" group. This is only used in a few places such as QuickContactActivity and
            // ImportExportDialogFragment. We could work around missing this permission with a bit
            // of work.
            permission.CALL_PHONE,
            permission.READ_CALL_LOG,
            permission.READ_PHONE_STATE,
    };

    @Override
    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    @Override
    protected String[] getDesiredPermissions() {
        return new String[]{
                // Contacts group
                permission.GET_ACCOUNTS,
                permission.READ_CONTACTS,
                permission.WRITE_CONTACTS,
                // Phone group
                permission.CALL_PHONE,
                permission.READ_CALL_LOG,
                permission.READ_PHONE_STATE,
                // Calendar group
                permission.READ_CALENDAR,
                // SMS group
                permission.READ_SMS,
        };
    }

    public static boolean startPermissionActivity(Activity activity) {
        return startPermissionActivity(activity, REQUIRED_PERMISSIONS,
                RequestPermissionsActivity.class);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        if (permissions != null && permissions.length > 0
                && isAllGranted(permissions, grantResults)) {
            mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(mPreviousActivityIntent);
            finish();
            overridePendingTransition(0, 0);
        } else {
            Toast.makeText(this, R.string.missing_required_permission, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
