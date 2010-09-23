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

import com.android.common.widget.CompositeCursorAdapter;

import android.provider.ContactsContract.Directory;

/**
 * Model object for a {@link Directory} row.
 */
public final class DirectoryPartition extends CompositeCursorAdapter.Partition {

    public static final int STATUS_NOT_LOADED = 0;
    public static final int STATUS_LOADING = 1;
    public static final int STATUS_LOADED = 2;

    private long mDirectoryId;
    private String mDirectoryType;
    private String mDisplayName;
    private int mStatus;
    private boolean mPriorityDirectory;
    private boolean mPhotoSupported;

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

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public boolean isLoading() {
        return mStatus == STATUS_NOT_LOADED || mStatus == STATUS_LOADING;
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

    /**
     * Returns true if this directory supports photos.
     */
    public boolean isPhotoSupported() {
        return mPhotoSupported;
    }

    public void setPhotoSupported(boolean flag) {
        this.mPhotoSupported = flag;
    }
}
