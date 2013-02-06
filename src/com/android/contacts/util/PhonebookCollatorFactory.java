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
        return Collator.getInstance(Locale.getDefault());
    }
}
