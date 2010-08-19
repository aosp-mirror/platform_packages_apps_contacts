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

package com.android.contacts.util;

import java.text.Collator;
import java.util.Locale;

/**
 * Returns the collator that can be used to sort contact list entries. This
 * collator is the same as the one that is used in sqlite.
 */
public final class PhonebookCollatorFactory {
    public static final Collator getCollator() {
        final Locale defaultLocale = Locale.getDefault();
        final String defaultLocaleString = defaultLocale.toString();
        // For Japanese we use a special collator that puts japanese characters before foreign
        // ones (this is called a dictionary collator)
        // Warning: This function has to match the behavior in sqlite3_android.cpp (located in
        // the framework)
        final Locale locale;
        if ("ja".equals(defaultLocaleString) || "ja_JP".equals(defaultLocaleString)) {
            locale = new Locale("ja@collation=phonebook");
        } else {
            locale = defaultLocale;
        }

        return Collator.getInstance(locale);
    }
}
