/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.os.Bundle;
import android.preference.PreferenceActivity;

public final class ContactsPreferenceActivity extends PreferenceActivity {
    /**
     * The type of data to display in the main contacts list. 
     */
    static final String PREF_DISPLAY_TYPE = "display_system_group";

    /** Unknown display type. */
    static final int DISPLAY_TYPE_UNKNOWN = -1;
    /** Display all contacts */
    static final int DISPLAY_TYPE_ALL = 0;
    /** Display all contacts that have phone numbers */
    static final int DISPLAY_TYPE_ALL_WITH_PHONES = 1;
    /** Display a system group */
    static final int DISPLAY_TYPE_SYSTEM_GROUP = 2;
    /** Display a user group */
    static final int DISPLAY_TYPE_USER_GROUP = 3;

    /**
     * Info about what to display. If {@link #PREF_DISPLAY_TYPE}
     * is {@link #DISPLAY_TYPE_SYSTEM_GROUP} then this will be the system id.
     * If {@link #PREF_DISPLAY_TYPE} is {@link #DISPLAY_TYPE_USER_GROUP} then this will
     * be the group name.
     */ 
    static final String PREF_DISPLAY_INFO = "display_group";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

}
