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
 * limitations under the License
 */
package com.android.contacts.common;

/**
 * Meta-data for a contact group.  We load all groups associated with the contact's
 * constituent accounts.
 */
public final class GroupMetaData {
    private String mAccountName;
    private String mAccountType;
    private String mDataSet;
    private long mGroupId;
    private String mTitle;
    private boolean mDefaultGroup;
    private boolean mFavorites;

    public GroupMetaData(String accountName, String accountType, String dataSet, long groupId,
            String title, boolean defaultGroup, boolean favorites) {
        this.mAccountName = accountName;
        this.mAccountType = accountType;
        this.mDataSet = dataSet;
        this.mGroupId = groupId;
        this.mTitle = title;
        this.mDefaultGroup = defaultGroup;
        this.mFavorites = favorites;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public String getDataSet() {
        return mDataSet;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public String getTitle() {
        return mTitle;
    }

    public boolean isDefaultGroup() {
        return mDefaultGroup;
    }

    public boolean isFavorites() {
        return mFavorites;
    }
}