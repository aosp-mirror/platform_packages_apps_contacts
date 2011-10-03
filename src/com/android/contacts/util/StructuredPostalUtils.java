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

import android.content.Intent;
import android.net.Uri;

public class StructuredPostalUtils {
    private StructuredPostalUtils() {
    }

    public static Intent getViewPostalAddressIntent(String postalAddress) {
        return new Intent(Intent.ACTION_VIEW, getPostalAddressUri(postalAddress));
    }

    public static Uri getPostalAddressUri(String postalAddress) {
        return Uri.parse("geo:0,0?q=" + Uri.encode(postalAddress));
    }
}
