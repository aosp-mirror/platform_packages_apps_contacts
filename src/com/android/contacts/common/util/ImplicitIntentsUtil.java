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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.QuickContact;

import java.util.List;

/**
 * Utility for forcing intents to be started inside the current app. This is useful for avoiding
 * senseless disambiguation dialogs. Ie, if a user clicks a contact inside Contacts we assume
 * they want to view the contact inside the Contacts app as opposed to a 3rd party contacts app.
 *
 * Methods are designed to replace the use of startActivity() for implicit intents. This class isn't
 * necessary for explicit intents. No attempt is made to replace startActivityForResult(), since
 * startActivityForResult() is always used with explicit intents in this project.
 *
 * Why not just always use explicit intents? The Contacts/Dialer app implements standard intent
 * actions used by others apps. We want to continue exercising these intent filters to make sure
 * they still work. Plus we sometimes don't know an explicit intent would work. See
 * {@link #startActivityInAppIfPossible}.
 *
 * Some ContactsCommon code that is only used by Dialer doesn't use ImplicitIntentsUtil.
 */
public class ImplicitIntentsUtil {

    /**
     * Start an intent. If it is possible for this app to handle the intent, force this app's
     * activity to handle the intent. Sometimes it is impossible to know whether this app
     * can handle an intent while coding since the code is used inside both Dialer and Contacts.
     * This method is particularly useful in such circumstances.
     *
     * On a Nexus 5 with a small number of apps, this method consistently added 3-16ms of delay
     * in order to talk to the package manager.
     */
    public static void startActivityInAppIfPossible(Context context, Intent intent) {
        final Intent appIntent = getIntentInAppIfExists(context, intent);
        if (appIntent != null) {
            context.startActivity(appIntent);
        } else {
            context.startActivity(intent);
        }
    }

    /**
     * Start intent using an activity inside this app. This method is useful if you are certain
     * that the intent can be handled inside this app, and you care about shaving milliseconds.
     */
    public static void startActivityInApp(Context context, Intent intent) {
        String packageName = context.getPackageName();
        intent.setPackage(packageName);
        context.startActivity(intent);
    }

    /**
     * Start an intent normally. Assert that the intent can't be opened inside this app.
     */
    public static void startActivityOutsideApp(Context context, Intent intent) {
        final boolean isPlatformDebugBuild = Build.TYPE.equals("eng")
                || Build.TYPE.equals("userdebug");
        if (isPlatformDebugBuild) {
            if (getIntentInAppIfExists(context, intent) != null) {
                throw new AssertionError("startActivityOutsideApp() was called for an intent" +
                        " that can be handled inside the app");
            }
        }
        context.startActivity(intent);
    }

    /**
     * Returns an implicit intent for opening QuickContacts.
     */
    public static Intent composeQuickContactIntent(Uri contactLookupUri,
            int extraMode) {
        final Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
        intent.setData(contactLookupUri);
        intent.putExtra(QuickContact.EXTRA_MODE, extraMode);
        // Make sure not to show QuickContacts on top of another QuickContacts.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Returns a copy of {@param intent} with a class name set, if a class inside this app
     * has a corresponding intent filter.
     */
    private static Intent getIntentInAppIfExists(Context context, Intent intent) {
        final Intent intentCopy = new Intent(intent);
        intentCopy.setPackage(context.getPackageName());
        final List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(
                intentCopy, 0);
        if (list != null && list.size() != 0) {
            intentCopy.setClass(context, list.get(0).getClass());
            return intentCopy;
        }
        return null;
    }
}
