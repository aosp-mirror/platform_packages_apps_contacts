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

import android.database.Cursor;
import android.provider.ContactsContract.Directory;

/**
 * Model object for a {@link Directory} row.
 */
public final class DirectoryPartition {
    private long mDirectoryId;
    private int mPartitionIndex;
    private String mDirectoryType;
    private String mDisplayName;
    private boolean mShowIfEmpty;
    private boolean mLoading;

    /**
     * Directory ID, see {@link Directory}.
     */
    public long getDirectoryId() {
        return mDirectoryId;
    }

    public void setDirectoryId(long directoryId) {
        this.mDirectoryId = directoryId;
    }

    /**
     * Corresponding loader ID.
     */
    public int getPartitionIndex() {
        return mPartitionIndex;
    }

    public void setPartitionIndex(int partitionIndex) {
        this.mPartitionIndex = partitionIndex;
    }

    /**
     * Directory type resolved from {@link Directory#PACKAGE_NAME} and
     * {@link Directory#TYPE_RESOURCE_ID};
     */
    public String getDirectoryType() {
        return mDirectoryType;
    }

    public void setDirectoryType(String directoryType) {
        this.mDirectoryType = directoryType;
    }

    /**
     * See {@link Directory#DISPLAY_NAME}.
     */
    public String getDisplayName() {
        return mDisplayName;
    }

    public void setDisplayName(String displayName) {
        this.mDisplayName = displayName;
    }

    /**
     * True if the directory should be shown even if no contacts are found.
     */
    public boolean getShowIfEmpty() {
        return mShowIfEmpty;
    }

    public void setShowIfEmpty(boolean showIfEmpty) {
        this.mShowIfEmpty = showIfEmpty;
    }

    public boolean isLoading() {
        return mLoading;
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }
}
