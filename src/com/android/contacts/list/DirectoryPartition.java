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

import com.android.contacts.widget.CompositeCursorAdapter;

import android.provider.ContactsContract.Directory;

/**
 * Model object for a {@link Directory} row.
 */
public final class DirectoryPartition extends CompositeCursorAdapter.Partition {
    private long mDirectoryId;
    private String mDirectoryType;
    private String mDisplayName;
    private boolean mLoading;
    private boolean mPriorityDirectory;

    public DirectoryPartition(boolean showIfEmpty, boolean hasHeader) {
        super(showIfEmpty, hasHeader);
    }

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

    public boolean isLoading() {
        return mLoading;
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    /**
     * Returns true if this directory should be loaded before non-priority directories.
     */
    public boolean isPriorityDirectory() {
        return mPriorityDirectory;
    }

    public void setPriorityDirectory(boolean priorityDirectory) {
        mPriorityDirectory = priorityDirectory;
    }
}
