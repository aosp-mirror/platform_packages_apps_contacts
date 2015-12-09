/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.common.compat;

import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;

import com.android.contacts.common.ContactsUtils;

public class DirectoryCompat {

    // TODO: Use N APIs
    private static final Uri ENTERPRISE_CONTENT_URI =
            Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "directories_enterprise");
    // TODO: Use N APIs
    private static final long ENTERPRISE_LOCAL_INVISIBLE = 1000000000L + Directory.LOCAL_INVISIBLE;

    public static Uri getContentUri() {
        // TODO: Use N APIs
        if (ContactsUtils.FLAG_N_FEATURE && android.os.Build.VERSION.CODENAME.startsWith("N")) {
            return ENTERPRISE_CONTENT_URI;
        }
        return Directory.CONTENT_URI;
    }

    public static boolean isInvisibleDirectory(long directoryId) {
        return (directoryId == Directory.LOCAL_INVISIBLE
                || directoryId == ENTERPRISE_LOCAL_INVISIBLE);
    }

    public static boolean isRemoteDirectory(long directoryId) {
        // TODO: Use N APIs
        if (ContactsUtils.FLAG_N_FEATURE && android.os.Build.VERSION.CODENAME.startsWith("N")) {
            return Directory.isRemoteDirectory(directoryId);
        }
        return !(directoryId == Directory.DEFAULT || directoryId == Directory.LOCAL_INVISIBLE);
    }
}
