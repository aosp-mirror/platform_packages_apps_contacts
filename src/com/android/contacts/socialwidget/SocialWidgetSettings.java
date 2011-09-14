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

package com.android.contacts.socialwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class SocialWidgetSettings {
    private static final String TAG = "SocialWidgetSettings";

    // To migrate from earlier versions...
    private static final String LEGACY_PREFS_NAME = "WidgetSettings";

    // Prefix to use for all preferences used by this class.
    private static final String PREFERENCES_PREFIX = "SocialWidgetSettings_";

    private static final String CONTACT_URI_PREFIX = "CONTACT_URI_";

    private static final String KEY_MIGRATED = PREFERENCES_PREFIX + "settings_migrated";

    private static final SocialWidgetSettings sInstance = new SocialWidgetSettings();

    public static SocialWidgetSettings getInstance() {
        return sInstance;
    }

    private final String getPreferenceKey(int widgetId) {
        return PREFERENCES_PREFIX + CONTACT_URI_PREFIX + Integer.toString(widgetId);
    }

    public void remove(Context context, int[] widgetIds) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final Editor editor = settings.edit();
        for (int widgetId : widgetIds) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "remove(" + widgetId + ")");
            }
            editor.remove(getPreferenceKey(widgetId));
        }
        editor.apply();
    }

    public Uri getContactUri(Context context, int widgetId) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        ensureMigrated(context, settings);

        final String resultString = settings.getString(getPreferenceKey(widgetId), null);
        final Uri result = resultString == null ? null : Uri.parse(resultString);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getContactUri(" + widgetId + ") --> " + result);
        }
        return result;
    }

    public void setContactUri(Context context, int widgetId, Uri contactLookupUri) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setContactUri(" + widgetId + ", " + contactLookupUri + ")");
        }
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final Editor editor = settings.edit();
        if (contactLookupUri == null) {
            editor.remove(getPreferenceKey(widgetId));
        } else {
            editor.putString(getPreferenceKey(widgetId), contactLookupUri.toString());
        }
        editor.apply();
    }

    private void ensureMigrated(Context context, SharedPreferences settings) {
        if (settings.getBoolean(KEY_MIGRATED, false)) {
            return; // Migrated already
        }

        Log.i(TAG, "Migrating widget settings...");

        // Old preferences only had the "CONTACT_URI_" prefix.
        // New preferences have the "SocialWidgetSettings_CONTACT_URI_" prefix.
        // So just copy all the entries with adding "SocialWidgetSettings_" to their key names.

        final SharedPreferences.Editor editor = settings.edit();

        final SharedPreferences legacySettings =
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        for (String key : legacySettings.getAll().keySet()) {
            final String value = legacySettings.getString(key, null);
            if (value == null) continue; // Just in case.

            Log.i(TAG, "Found: " + key + ": " + value);

            editor.putString(PREFERENCES_PREFIX + key, value);
        }

        editor.apply();
        settings.edit().putBoolean(KEY_MIGRATED, true).apply();
    }
}
