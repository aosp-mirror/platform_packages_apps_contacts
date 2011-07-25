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
 * limitations under the License
 */
package com.android.contacts.group;

/**
 * Meta-data for a contact group.  We load all groups associated with the contact's
 * constituent accounts.
 */
public final class GroupListItem {
    private final String mAccountName;
    private final String mAccountType;
    private final long mGroupId;
    private final String mTitle;
    private final boolean mIsFirstGroupInAccount;
    private final int mMemberCount;

    /** Number of groups in the account that this group belongs to */
    private final int mGroupCountForThisAccount;

    public GroupListItem(String accountName, String accountType, long groupId, String title,
            boolean isFirstGroupInAccount, int memberCount, int groupCountForThisAccount) {
        mAccountName = accountName;
        mAccountType = accountType;
        mGroupId = groupId;
        mTitle = title;
        mIsFirstGroupInAccount = isFirstGroupInAccount;
        mMemberCount = memberCount;
        mGroupCountForThisAccount = groupCountForThisAccount;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getMemberCount() {
        return mMemberCount;
    }

    public boolean hasMemberCount() {
        return mMemberCount != -1;
    }

    public boolean isFirstGroupInAccount() {
        return mIsFirstGroupInAccount;
    }

    public int getGroupCountForThisAccount() {
        return mGroupCountForThisAccount;
    }
}