package com.android.contacts;

import static com.android.contacts.util.PermissionsUtil.hasPermission;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.filters.Suppress;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Make sure the app doesn't crash when it is started without permissions. Note: this won't
 * run in most environments because permissions will already have been granted.
 *
 * To exercise this run:
 *
 * $ adb shell pm revoke com.android.contacts android.permission.READ_CONTACTS
 * $ adb shell pm revoke com.android.contacts android.permission.WRITE_CONTACTS
 * $ adb shell pm revoke com.android.contacts android.permission.GET_ACCOUNTS
 * $ adb shell pm revoke com.android.contacts android.permission.READ_PHONE_STATE
 * $ adb shell pm revoke com.android.contacts android.permission.CALL_PHONE
 * $ adb shell am instrument -w \
 *     com.google.android.contacts.tests/androidx.test.runner.AndroidJUnitRunner \
 *     -e class com.android.contacts.NoPermissionsLaunchSmokeTest
 */
@MediumTest
// suppressed because failed assumptions are reported as test failures by the build server
@Suppress
@RunWith(AndroidJUnit4.class)
public class NoPermissionsLaunchSmokeTest {
    private static final long TIMEOUT = 5000;

    private Context mTargetContext;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.READ_CONTACTS));
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.WRITE_CONTACTS));
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.GET_ACCOUNTS));
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.READ_PHONE_STATE));
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.CALL_PHONE));
        assumeTrue(!hasPermission(mTargetContext, Manifest.permission.READ_CALL_LOG));

        // remove state that might exist outside of the app
        // (e.g. launcher shortcuts and scheduled jobs)
        DynamicShortcuts.reset(mTargetContext);
    }

    @Test
    public void launchingMainActivityDoesntCrash() throws Exception {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Launch the main activity
        InstrumentationRegistry.getContext().startActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_DEFAULT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .setPackage(InstrumentationRegistry.getTargetContext().getPackageName()));

        device.waitForIdle();

        final PackageManager packageManager = mTargetContext.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            device.wait(Until.hasObject(By.textEndsWith("your phone call logs?")), TIMEOUT);
            final UiObject2 grantCallLogPermissionButton = device.findObject(By.text("ALLOW"));
            grantCallLogPermissionButton.click();
        }

        device.wait(Until.hasObject(By.textEndsWith("access your contacts?")), TIMEOUT);
        final UiObject2 grantContactsPermissionButton = device.findObject(By.text("ALLOW"));

        grantContactsPermissionButton.click();

        device.wait(Until.hasObject(By.textEndsWith("make and manage phone calls?")), TIMEOUT);

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            device.waitForIdle();
            return;
        }

        final UiObject2 grantPhonePermissionButton = device.findObject(By.text("ALLOW"));

        grantPhonePermissionButton.clickAndWait(Until.newWindow(), TIMEOUT);

        // Not sure if this actually waits for the load to complete or not.
        device.waitForIdle();
    }

    // TODO: it would be good to have similar tests for other entry points that might be reached
    // without required permissions.
}
