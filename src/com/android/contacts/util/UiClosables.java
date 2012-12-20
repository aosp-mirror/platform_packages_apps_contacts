/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.widget.ListPopupWindow;

/**
 * Methods for closing various objects
 */
public class UiClosables {

    /**
     * Close a {@link ListPopupWindow}.
     *
     * @param popup The popup window to close.
     * @return {@code true} if the popup was showing. {@code false} otherwise.
     */
    public static boolean closeQuietly(ListPopupWindow popup) {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return true;
        }
        return false;
    }
}
