package com.android.contacts.common.activity;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;

/**
 * Activity that requests permissions needed for ImportVCardActivity.
 */
public class RequestImportVCardPermissionsActivity extends Activity {
    public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";

    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;
    private static String[] sPermissions = new String[]{
            permission.READ_CONTACTS,
            permission.WRITE_CONTACTS,
            permission.WRITE_EXTERNAL_STORAGE,
    };

    private Intent mPreviousActivityIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreviousActivityIntent = (Intent) getIntent().getExtras().get(PREVIOUS_ACTIVITY_INTENT);
        requestPermissions();
    }

    public static boolean startPermissionActivity(Activity activity) {
        if (!hasPermissions(activity)) {
            final Intent intent = new Intent(activity,
                    RequestImportVCardPermissionsActivity.class);
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
            finish();
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
        requestPermissions(sPermissions, PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        Trace.endSection();
    }

    public static boolean hasPermissions(Context context) {
        Trace.beginSection("hasPermission");
        try {
            for (String permission : sPermissions) {
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