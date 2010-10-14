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

package com.android.contacts;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.ContactsContract.QuickContact;
import android.util.Log;

/**
 * Utility class to annotate Intents with extra data required for the Sticky-Tab behavior, which
 * allows storing the app to go to the last tab that was used to make a call. Also handles saving
 * and restoring the tab index
 */
public final class StickyTabs {
    private static final String TAG = "StickyTabs";
    private static final boolean LOGV = false;

    private static final String EXTRA_TAB_INDEX =
            QuickContact.EXTRA_SELECTED_CONTACTS_APP_TAB_INDEX;

    /**
     * Name of the shared setting. We are using the same name as in FroYo to prevent
     * having an orphan here
     */
    public static final String PREFERENCES_NAME = "dialtacts";

    /**
     * Name of the shared setting. We are using the same name as in FroYo to prevent
     * having an orphan there
     */
    private static final String PREF_LAST_PHONECALL_TAB = "last_manually_selected_tab";

    /**
     * Writes the selected tab to the passed intent
     * @param intent The intent to modify.
     * @param tabIndex The tab index to write to the intent
     * @return Returns the modified intent. Notice that this is not a new instance (the passed-in
     * intent is modified)
     */
    public static Intent setTab(Intent intent, int tabIndex) {
        if (LOGV) Log.v(TAG, "*********** Setting tab index of intent to " + tabIndex);

        if (tabIndex == -1) {
            intent.removeExtra(EXTRA_TAB_INDEX);
        } else {
            intent.putExtra(EXTRA_TAB_INDEX, tabIndex);
        }
        return intent;
    }

    /**
     * Writes the selected tab to the passed intent by retrieving it from the originalIntent that
     * was passed in
     * @param intent The intent to modify.
     * @param originalIntent The intent where the tab index should be read from
     * @return Returns the modified intent. Notice that this is not a new instance (the passed-in
     * intent is modified)
     */
    public static Intent setTab(Intent intent, Intent originalIntent) {
        return setTab(intent, getTab(originalIntent));
    }

    /**
     * Returns the selected tab or -1 if no tab is stored
     */
    public static int getTab(Intent intent) {
        if (intent.getExtras() == null) return -1;
        return intent.getExtras().getInt(EXTRA_TAB_INDEX, -1);
    }

    /**
     * Persists the given tabIndex. If the value is -1, the previously persisted value is not
     * overriden
     */
    public static void saveTab(Context context, int tabIndex) {
        if (LOGV) Log.v(TAG, "*********** Persisting tab index " + tabIndex);
        if (tabIndex == -1) return;

        final SharedPreferences.Editor editor =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(PREF_LAST_PHONECALL_TAB, tabIndex);
        editor.apply();
    }

    /**
     * Persists the tab as it is stored in the Intent. If the intent does not have a tab index,
     * the persisted value is not overriden
     */
    public static void saveTab(Context context, Intent intent) {
        saveTab(context, getTab(intent));
    }

    /**
     * Returns the previously persisted tab or defaultValue if nothing is saved
     */
    public static int loadTab(Context context, int defaultValue) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        final int result = prefs.getInt(PREF_LAST_PHONECALL_TAB, defaultValue);
        if (LOGV) Log.v(TAG, "*********** Loaded tab index: " + result);
        return result;
    }
}
