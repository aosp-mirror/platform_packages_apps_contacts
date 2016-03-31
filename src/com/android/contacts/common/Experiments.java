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
     * Search study boolean indicating whether to order starred and frequently occurring
     * search results first.
     */
    public static final String FLAG_SEARCH_STREQUENTS_FIRST =
            "Search__order_strequent_results_first";

    private Experiments() {
    }
}
