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

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

/**
 * Contact list filter parameters.
 */
public final class ContactListFilter implements Comparable<ContactListFilter> {

    public static final int FILTER_TYPE_ALL_ACCOUNTS = -1;
    public static final int FILTER_TYPE_CUSTOM = -2;
    public static final int FILTER_TYPE_ACCOUNT = 0;
    public static final int FILTER_TYPE_GROUP = 1;

    public int filterType;
    public String accountType;
    public String accountName;
    public Drawable icon;
    public long groupId;
    public String title;

    public ContactListFilter(int filterType) {
        this.filterType = filterType;
    }

    public ContactListFilter(
            String accountType, String accountName, Drawable icon, String title) {
        this.filterType = ContactListFilter.FILTER_TYPE_ACCOUNT;
        this.accountType = accountType;
        this.accountName = accountName;
        this.icon = icon;
        this.title = title;
    }

    public ContactListFilter(
            String accountType, String accountName, long groupId, String title) {
        this.filterType = ContactListFilter.FILTER_TYPE_GROUP;
        this.accountType = accountType;
        this.accountName = accountName;
        this.groupId = groupId;
        this.title = title;
    }

    @Override
    public String toString() {
        switch (filterType) {
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
                return "account: " + accountType + " " + accountName;
            case ContactListFilter.FILTER_TYPE_GROUP:
                return "group: " + accountType + " " + accountName + " " + title + "(" + groupId
                        + ")";
        }
        return super.toString();
    }

    @Override
    public int compareTo(ContactListFilter another) {
        int res = accountType.compareTo(another.accountType);
        if (res != 0) {
            return res;
        }

        res = accountName.compareTo(another.accountName);
        if (res != 0) {
            return res;
        }

        if (filterType != another.filterType) {
            return filterType - another.filterType;
        }

        String title1 = title != null ? title : "";
        String title2 = another.title != null ? another.title : "";
        return title1.compareTo(title2);
    }

    @Override
    public int hashCode() {
        int code = filterType;
        if (accountType != null) {
            code = code * 31 + accountType.hashCode();
            code = code * 31 + accountName.hashCode();
        }
        if (groupId != 0) {
            code = code * 31 + (int) groupId;
        }
        return code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ContactListFilter)) {
            return false;
        }

        ContactListFilter otherFilter = (ContactListFilter) other;
        return filterType == otherFilter.filterType
                && TextUtils.equals(accountName, otherFilter.accountName)
                && TextUtils.equals(accountType, otherFilter.accountType)
                && groupId == otherFilter.groupId;
    }
}
