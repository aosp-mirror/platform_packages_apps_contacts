/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.preference;

import com.android.contacts.R;

import android.content.Context;
import android.preference.PreferenceActivity;

import java.util.List;

/**
 * Contacts settings.
 */
public final class ContactsPreferenceActivity extends PreferenceActivity {

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    /**
     * Returns true if there are no preferences to display and therefore the
     * corresponding menu item can be removed.
     */
    public static boolean isEmpty(Context context) {
        return !context.getResources().getBoolean(R.bool.config_sort_order_user_changeable)
                && !context.getResources().getBoolean(R.bool.config_display_order_user_changeable);
    }
}
