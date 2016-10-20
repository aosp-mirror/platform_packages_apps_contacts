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

package com.android.contacts.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SharedPreferenceUtil {

    public static final String PREFERENCE_KEY_ACCOUNT_SYNC_OFF_DISMISSES =
            "num-of-dismisses-account-sync-off";

    public static final String PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES =
            "num-of-dismisses-auto-sync-off";

    private static final String PREFERENCE_KEY_HAMBURGER_PROMO_DISPLAYED_BEFORE =
            "hamburgerPromoDisplayedBefore";

    private static final String PREFERENCE_KEY_HAMBURGER_MENU_CLICKED_BEFORE =
            "hamburgerMenuClickedBefore";

    private static final String PREFERENCE_KEY_HAMBURGER_PROMO_TRIGGER_ACTION_HAPPENED_BEFORE =
            "hamburgerPromoTriggerActionHappenedBefore";

    private static final String PREFERENCE_KEY_IMPORTED_SIM_CARDS =
            "importedSimCards";

    public static boolean getHamburgerPromoDisplayedBefore(Context context) {
        return getSharedPreferences(context)
                .getBoolean(PREFERENCE_KEY_HAMBURGER_PROMO_DISPLAYED_BEFORE, false);
    }

    public static void setHamburgerPromoDisplayedBefore(Context context) {
        getSharedPreferences(context).edit()
                .putBoolean(PREFERENCE_KEY_HAMBURGER_PROMO_DISPLAYED_BEFORE, true)
                .apply();
    }

    public static boolean getHamburgerMenuClickedBefore(Context context) {
        return getSharedPreferences(context)
                .getBoolean(PREFERENCE_KEY_HAMBURGER_MENU_CLICKED_BEFORE, false);
    }

    public static void setHamburgerMenuClickedBefore(Context context) {
        getSharedPreferences(context).edit()
                .putBoolean(PREFERENCE_KEY_HAMBURGER_MENU_CLICKED_BEFORE, true)
                .apply();
    }

    public static boolean getHamburgerPromoTriggerActionHappenedBefore(Context context) {
        return getSharedPreferences(context)
                .getBoolean(PREFERENCE_KEY_HAMBURGER_PROMO_TRIGGER_ACTION_HAPPENED_BEFORE, false);
    }

    public static void setHamburgerPromoTriggerActionHappenedBefore(Context context) {
        getSharedPreferences(context).edit()
                .putBoolean(PREFERENCE_KEY_HAMBURGER_PROMO_TRIGGER_ACTION_HAPPENED_BEFORE, true)
                .apply();
    }

    /**
     * Show hamburger promo if:
     * 1) Hamburger menu is never clicked before
     * 2) Hamburger menu promo is never displayed before
     * 3) There is at least one available user action
     *      (for now, available user actions to trigger to displayed hamburger promo are:
     *       a: QuickContact UI back to PeopleActivity
     *       b: Search action back to PeopleActivity)
     */
    public static boolean getShouldShowHamburgerPromo(Context context) {
        return !getHamburgerMenuClickedBefore(context)
                && getHamburgerPromoTriggerActionHappenedBefore(context)
                && !getHamburgerPromoDisplayedBefore(context);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
    }

    public static int getNumOfDismissesForAutoSyncOff(Context context) {
        return getSharedPreferences(context).getInt(PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES, 0);
    }

    public static void resetNumOfDismissesForAutoSyncOff(Context context) {
        final int value = getSharedPreferences(context).getInt(
                PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES, 0);
        if (value != 0) {
            getSharedPreferences(context).edit()
                    .putInt(PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES, 0).apply();
        }
    }

    public static void incNumOfDismissesForAutoSyncOff(Context context) {
        final int value = getSharedPreferences(context).getInt(
                PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES, 0);
        getSharedPreferences(context).edit()
                .putInt(PREFERENCE_KEY_GLOBAL_SYNC_OFF_DISMISSES, value + 1).apply();
    }

    private static String buildSharedPrefsName(String accountName) {
        return accountName + "-" + PREFERENCE_KEY_ACCOUNT_SYNC_OFF_DISMISSES;
    }

    public static int getNumOfDismissesforAccountSyncOff(Context context, String accountName) {
        return getSharedPreferences(context).getInt(buildSharedPrefsName(accountName), 0);
    }

    public static void resetNumOfDismissesForAccountSyncOff(Context context, String accountName) {
        final int value = getSharedPreferences(context).getInt(
                buildSharedPrefsName(accountName), 0);
        if (value != 0) {
            getSharedPreferences(context).edit()
                    .putInt(buildSharedPrefsName(accountName), 0).apply();
        }
    }

    public static void incNumOfDismissesForAccountSyncOff(Context context, String accountName) {
        final int value = getSharedPreferences(context).getInt(
                buildSharedPrefsName(accountName), 0);
        getSharedPreferences(context).edit()
                .putInt(buildSharedPrefsName(accountName), value + 1).apply();
    }

    /**
     * Persist an identifier for a SIM card which has been successfully imported.
     *
     * @param simId an identifier for the SIM card this should be one of
     * {@link TelephonyManager#getSimSerialNumber()} or {@link SubscriptionInfo#getIccId()}
     * depending on API level. The source of the value should be consistent on a particular device
     */
    public static void addImportedSim(Context context, String simId) {
        final Set<String> current = new HashSet<>(getImportedSims(context));
        current.add(simId);
        getSharedPreferences(context).edit()
                .putStringSet(PREFERENCE_KEY_IMPORTED_SIM_CARDS, current).apply();
    }

    public static Set<String> getImportedSims(Context context) {
        return getSharedPreferences(context)
                .getStringSet(PREFERENCE_KEY_IMPORTED_SIM_CARDS, Collections.<String>emptySet());
    }
}
