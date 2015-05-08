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

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;

/**
 * Repeatedly ask the user for runtime permissions, until they grant all the permissions.
 * For now only handles activities that are only designed for use in Contacts.
 */
public class RequestPermissionsActivity extends Activity {
    public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";

    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;
    private static String[] permissions = new String[]{
            permission.CALL_PHONE,
            permission.READ_CONTACTS,
            permission.WRITE_CONTACTS,
            permission.MANAGE_ACCOUNTS,
            permission.GET_ACCOUNTS,
            permission.ACCESS_FINE_LOCATION,
            permission.ACCESS_COARSE_LOCATION,
            permission.READ_PROFILE,
            permission.WRITE_PROFILE,
            permission.INTERNET,
            permission.NFC,
            permission.READ_PHONE_STATE,
            permission.WAKE_LOCK,
            permission.WRITE_EXTERNAL_STORAGE,
            permission.WRITE_SETTINGS,
            permission.USE_CREDENTIALS,
            permission.VIBRATE,
            permission.READ_SYNC_SETTINGS,
            permission.INSTALL_SHORTCUT,
            permission.READ_CALL_LOG,
            permission.READ_SMS,
            permission.READ_CALENDAR,
            // This following permission can't be requested as a runtime permission.
            //permission.READ_VOICEMAIL
    };

    private Intent mPreviousActivityIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreviousActivityIntent = (Intent) getIntent().getExtras().get(PREVIOUS_ACTIVITY_INTENT);
        requestPermissions();
    }

    /**
     * If any permissions the Contacts app needs are missing, open an Activity
     * to prompt the user for these permissions. Moreover, finish the current activity.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     */
    public static boolean startPermissionActivity(Activity activity) {
        if (!RequestPermissionsActivity.hasPermissions(activity)) {
            final Intent intent = new Intent(activity,  RequestPermissionsActivity.class);
            intent.putExtra(PREVIOUS_ACTIVITY_INTENT, activity.getIntent());
            activity.startActivity(intent);
            activity.finish();
            return true;
        }
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        if (isAllGranted(grantResults)) {
            mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(mPreviousActivityIntent);
            finish();
            overridePendingTransition(0, 0);
        } else {
            requestPermissions();
        }
    }

    private boolean isAllGranted(int[] grantResult) {
        for (int result : grantResult) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        Trace.beginSection("requestPermissions");
        requestPermissions(permissions, PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        Trace.endSection();
    }

    public static boolean hasPermissions(Context context) {
        Trace.beginSection("hasPermission");
        try {
            for (String permission : permissions) {
                if (context.checkSelfPermission(permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        } finally {
            Trace.endSection();
        }

    }
}