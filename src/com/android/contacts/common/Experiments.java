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
     * Experiment to enable dynamic strequent shortcuts.
     */
    public static final String DYNAMIC_SHORTCUTS = "Shortcuts__dynamic_shortcuts";

    /**
     * Flags for minimum content update time
     */
    public static final String DYNAMIC_MIN_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_min_content_change_update_delay_millis";

    /**
     * Flags for maximum content update time
     */
    public static final String DYNAMIC_MAX_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_max_content_change_update_delay_millis";


    private Experiments() {
    }
}
