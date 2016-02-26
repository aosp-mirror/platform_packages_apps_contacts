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
package com.android.contacts.common.logging;

import android.app.Activity;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;

/**
 * Stores constants identifying individual screens/dialogs/fragments in the application, and also
 * provides a mapping of integer id -> screen name mappings for analytics purposes.
 */
public final class ScreenEvent {

    // Should match ContactsExtension.ScreenEvent.ScreenType values in
    // http://cs/google3/logs/proto/wireless/android/contacts/contacts_extensions.proto
    public static final class ScreenType {
        public static final int UNKNOWN = 0;
        public static final int SEARCH = 1;
        public static final int SEARCH_EXIT = 2;
        public static final int FAVORITES = 3;
        public static final int ALL_CONTACTS = 4;
        public static final int QUICK_CONTACT = 5;
        public static final int EDITOR = 6;

        private ScreenType() {
        }

        public static String getFriendlyName(int screenType) {
            switch (screenType) {
                case SEARCH: // fall-through
                case SEARCH_EXIT: return "Search";
                case FAVORITES: return "Favorites";
                case ALL_CONTACTS: return "AllContacts";
                case QUICK_CONTACT: return "QuickContact";
                case EDITOR: return "Editor";
                case UNKNOWN: // fall-through
                default: return null;
            }
        }
    }

    private ScreenEvent() {
    }
}
