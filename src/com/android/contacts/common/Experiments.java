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
 * limitations under the License
 */
package com.android.contacts.common;

/**
 * Experiment flag names.
 */
public final class Experiments {

    /**
     * Experiment to enable assistant in left navigation drawer.
     */
    public static final String ASSISTANT = "Assistant__enable_assistant";

    /**
     * Experiment to show the restore assistant on the assistants view.
     */
    public static final String ASSISTANT_RESTORE = "Assistant__restore";

    /**
     * Whether to open contact sheet (aka smart profile) instead of our own QuickContact.
     */
    public static final String CONTACT_SHEET = "QuickContact__contact_sheet";

    /**
     * Flags for maximum content update time
     */
    public static final String DYNAMIC_MAX_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_max_content_change_update_delay_millis";

    /**
     * Flags for minimum content update time
     */
    public static final String DYNAMIC_MIN_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_min_content_change_update_delay_millis";

    /**
     * Experiment to enable dynamic strequent shortcuts.
     */
    public static final String DYNAMIC_SHORTCUTS = "Shortcuts__dynamic_shortcuts";

    /**
     * Experiment to toggle contacts sync using the pull to refresh gesture.
     */
    public static final String PULL_TO_REFRESH = "PullToRefresh__pull_to_refresh";

    /**
     * Flags for maximum time to show spinner for a contacts sync.
     */
    public static final String PULL_TO_REFRESH_CANCEL_REFRESH_MILLIS =
            "PullToRefresh__cancel_refresh_millis";

    /**
     * Search study boolean indicating whether to inject yenta search results before CP2 results.
     */
    public static final String SEARCH_YENTA = "Search__yenta";

    /**
     * The time to wait for Yenta search results before giving up.
     */
    public static final String SEARCH_YENTA_TIMEOUT_MILLIS = "Search__yenta_timeout";

    private Experiments() {
    }
}
