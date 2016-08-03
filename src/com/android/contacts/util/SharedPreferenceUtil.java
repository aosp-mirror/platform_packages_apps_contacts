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

public class SharedPreferenceUtil {

    private static final String PREFERENCE_KEY_HAMBURGER_PROMO_DISPLAYED_BEFORE =
            "hamburgerPromoDisplayedBefore";

    private static final String PREFERENCE_KEY_HAMBURGER_MENU_CLICKED_BEFORE =
            "hamburgerMenuClickedBefore";

    private static final String PREFERENCE_KEY_HAMBURGER_PROMO_TRIGGER_ACTION_HAPPENED_BEFORE =
            "hamburgerPromoTriggerActionHappenedBefore";

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
}
