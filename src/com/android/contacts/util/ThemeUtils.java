/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.res.Resources.Theme;
import android.util.TypedValue;

/**
 * Provides static functions to more easily resolve attributes of the current theme
 */
public class ThemeUtils {
    /**
     * Resolves the given attribute id of the theme to a resource id
     */
    public static int getAttribute(Theme theme, int attrId) {
        final TypedValue outValue = new TypedValue();
        theme.resolveAttribute(attrId, outValue, true);
        return outValue.resourceId;
    }

    /**
     * Returns the resource id of the background used for buttons to show pressed and focused state
     */
    public static int getSelectableItemBackground(Theme theme) {
        return getAttribute(theme, android.R.attr.selectableItemBackground);
    }

    /**
     * Returns the resource id of the background used for list items that show activated background
     */
    public static int getActivatedBackground(Theme theme) {
        return getAttribute(theme, android.R.attr.activatedBackgroundIndicator);
    }
}
