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

package com.android.contacts.contactSocialWidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.util.Log;

public class ContactSocialWidgetSettings {
    private static final String TAG = "ContactSocialWidgetSettings";

    private static final String PREFS_NAME = "WidgetSettings";
    private static final String CONTACT_URI = "CONTACT_URI_%";

    private static String getSettingsString(int widgetId) {
        return CONTACT_URI.replace("%", Integer.toString(widgetId));
    }

    // TODO: Think about how to remove not-used Ids...we need a way to detect removed
    // widgets

    public static Uri getContactUri(Context context, int widgetId) {
        final SharedPreferences settings =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final String resultString = settings.getString(getSettingsString(widgetId), null);
        final Uri result = resultString == null ? null : Uri.parse(resultString);
        Log.d(TAG, "getContactUri(" + widgetId + ") --> " + result);
        return result;
    }

    public static void setContactUri(Context context, int widgetId, Uri contactLookupUri) {
        Log.d(TAG, "setContactUri(" + widgetId + ", " + contactLookupUri + ")");
        final SharedPreferences settings =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final Editor editor = settings.edit();
        if (contactLookupUri == null) {
            editor.remove(getSettingsString(widgetId));
        } else {
            editor.putString(getSettingsString(widgetId), contactLookupUri.toString());
        }
        editor.commit();
    }
}
