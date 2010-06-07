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
package com.android.contacts.list;

import android.provider.ContactsContract.Directory;

/**
 * Model object for a {@link Directory} row.
 */
public final class DirectoryPartition {

    /**
     * Directory ID, see {@link Directory}.
     */
    long directoryId;

    /**
     * Corresponding loader ID.
     */
    int partitionIndex;

    /**
     * Directory type resolved from {@link Directory#PACKAGE_NAME} and
     * {@link Directory#TYPE_RESOURCE_ID};
     */
    public String directoryType;

    /**
     * See {@link Directory#DISPLAY_NAME}.
     */
    public String displayName;

    /**
     * True if the directory should be shown even if no contacts are found.
     */
    public boolean showIfEmpty;

}