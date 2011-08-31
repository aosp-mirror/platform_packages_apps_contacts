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

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

/**
 * Contact list filter parameters.
 */
public final class ContactListFilter implements Comparable<ContactListFilter>, Parcelable {

    public static final int FILTER_TYPE_DEFAULT = -1;
    public static final int FILTER_TYPE_ALL_ACCOUNTS = -2;
    public static final int FILTER_TYPE_CUSTOM = -3;
    public static final int FILTER_TYPE_STARRED = -4;
    public static final int FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY = -5;
    public static final int FILTER_TYPE_SINGLE_CONTACT = -6;

    public static final int FILTER_TYPE_ACCOUNT = 0;
    public static final int FILTER_TYPE_GROUP = 1;

    private static final String KEY_FILTER_TYPE = "filter.type";
    private static final String KEY_ACCOUNT_NAME = "filter.accountName";
    private static final String KEY_ACCOUNT_TYPE = "filter.accountType";
    private static final String KEY_DATA_SET = "filter.dataSet";
    private static final String KEY_GROUP_ID = "filter.groupId";
    private static final String KEY_GROUP_SOURCE_ID = "filter.groupSourceId";
    private static final String KEY_GROUP_READ_ONLY = "filter.groupReadOnly";
    private static final String KEY_GROUP_TITLE = "filter.groupTitle";

    public final int filterType;
    public final String accountType;
    public final String accountName;
    public final String dataSet;
    public final Drawable icon;
    public long groupId;
    public String groupSourceId;
    public final boolean groupReadOnly;
    public final String title;
    private String mId;

    public ContactListFilter(int filterType, String accountType, String accountName, String dataSet,
            Drawable icon, long groupId, String groupSourceId, boolean groupReadOnly,
            String title) {
        this.filterType = filterType;
        this.accountType = accountType;
        this.accountName = accountName;
        this.dataSet = dataSet;
        this.icon = icon;
        this.groupId = groupId;
        this.groupSourceId = groupSourceId;
        this.groupReadOnly = groupReadOnly;
        this.title = title;
    }

    public static ContactListFilter createFilterWithType(int filterType) {
        return new ContactListFilter(filterType, null, null, null, null, 0, null, false, null);
    }

    public static ContactListFilter createGroupFilter(long groupId) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_GROUP, null, null, null, null,
                groupId, null, false, null);
    }

    public static ContactListFilter createGroupFilter(String accountType, String accountName,
            String dataSet, long groupId, String groupSourceId, boolean groupReadOnly,
            String title) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_GROUP, accountType, accountName,
                dataSet, null, groupId, groupSourceId, groupReadOnly, title);
    }

    public static ContactListFilter createAccountFilter(String accountType, String accountName,
            String dataSet, Drawable icon, String title) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT, accountType,
                accountName, dataSet, icon, 0, null, false, title);
    }

    /**
     * Returns true if this filter is based on data and may become invalid over time.
     */
    public boolean isValidationRequired() {
        return filterType == FILTER_TYPE_ACCOUNT || filterType == FILTER_TYPE_GROUP;
    }

    @Override
    public String toString() {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "default";
            case FILTER_TYPE_ALL_ACCOUNTS:
                return "all_accounts";
            case FILTER_TYPE_CUSTOM:
                return "custom";
            case FILTER_TYPE_STARRED:
                return "starred";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "with_phones";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "single";
            case FILTER_TYPE_ACCOUNT:
                return "account: " + accountType + (dataSet != null ? "/" + dataSet : "")
                        + " " + accountName;
            case FILTER_TYPE_GROUP:
                return "group: " + accountType + (dataSet != null ? "/" + dataSet : "")
                        + " " + accountName + " " + title + "(" + groupId + ")";
        }
        return super.toString();
    }

    @Override
    public int compareTo(ContactListFilter another) {
        int res = accountName.compareTo(another.accountName);
        if (res != 0) {
            return res;
        }

        res = accountType.compareTo(another.accountType);
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
        if (dataSet != null) {
            code = code * 31 + dataSet.hashCode();
        }
        if (groupSourceId != null) {
            code = code * 31 + groupSourceId.hashCode();
        } else if (groupId != 0) {
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
        if (filterType != otherFilter.filterType
                || !TextUtils.equals(accountName, otherFilter.accountName)
                || !TextUtils.equals(accountType, otherFilter.accountType)
                || !TextUtils.equals(dataSet, otherFilter.dataSet)) {
            return false;
        }

        if (groupSourceId != null && otherFilter.groupSourceId != null) {
            return groupSourceId.equals(otherFilter.groupSourceId);
        }

        return groupId == otherFilter.groupId;
    }

    public static void storeToPreferences(SharedPreferences prefs, ContactListFilter filter) {
        prefs.edit()
            .putInt(KEY_FILTER_TYPE, filter == null ? FILTER_TYPE_DEFAULT : filter.filterType)
            .putString(KEY_ACCOUNT_NAME, filter == null ? null : filter.accountName)
            .putString(KEY_ACCOUNT_TYPE, filter == null ? null : filter.accountType)
            .putString(KEY_DATA_SET, filter == null ? null : filter.dataSet)
            .putLong(KEY_GROUP_ID, filter == null ? -1 : filter.groupId)
            .putString(KEY_GROUP_SOURCE_ID, filter == null ? null : filter.groupSourceId)
            .putBoolean(KEY_GROUP_READ_ONLY, filter == null ? false : filter.groupReadOnly)
            .putString(KEY_GROUP_TITLE, filter == null ? null : filter.title)
            .apply();
    }

    /**
     * Try to obtain ContactListFilter object saved in SharedPreference.
     * If there's no info there, return ALL filter instead.
     */
    public static ContactListFilter restoreDefaultPreferences(SharedPreferences prefs) {
        ContactListFilter filter = restoreFromPreferences(prefs);
        if (filter == null) {
            filter = ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
        }
        return filter;
    }

    public static ContactListFilter restoreFromPreferences(SharedPreferences prefs) {
        int filterType = prefs.getInt(KEY_FILTER_TYPE, FILTER_TYPE_DEFAULT);
        if (filterType == FILTER_TYPE_DEFAULT) {
            return null;
        }

        String accountName = prefs.getString(KEY_ACCOUNT_NAME, null);
        String accountType = prefs.getString(KEY_ACCOUNT_TYPE, null);
        String dataSet = prefs.getString(KEY_DATA_SET, null);
        long groupId = prefs.getLong(KEY_GROUP_ID, -1);
        String groupSourceId = prefs.getString(KEY_GROUP_SOURCE_ID, null);
        boolean groupReadOnly = prefs.getBoolean(KEY_GROUP_READ_ONLY, false);
        String title = prefs.getString(KEY_GROUP_TITLE, "group");
        return new ContactListFilter(filterType, accountType, accountName, dataSet, null, groupId,
                groupSourceId, groupReadOnly, title);
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(filterType);
        dest.writeString(accountName);
        dest.writeString(accountType);
        dest.writeString(dataSet);
        dest.writeLong(groupId);
        dest.writeString(groupSourceId);
        dest.writeInt(groupReadOnly ? 1 : 0);
    }

    public static final Parcelable.Creator<ContactListFilter> CREATOR =
            new Parcelable.Creator<ContactListFilter>() {
        @Override
        public ContactListFilter createFromParcel(Parcel source) {
            int filterType = source.readInt();
            String accountName = source.readString();
            String accountType = source.readString();
            String dataSet = source.readString();
            long groupId = source.readLong();
            String groupSourceId = source.readString();
            boolean groupReadOnly = source.readInt() != 0;
            return new ContactListFilter(filterType, accountType, accountName, dataSet, null,
                    groupId, groupSourceId, groupReadOnly, null);
        }

        @Override
        public ContactListFilter[] newArray(int size) {
            return new ContactListFilter[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns a string that can be used as a stable persistent identifier for this filter.
     */
    public String getId() {
        if (mId == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(filterType);
            if (accountType != null) {
                sb.append('-').append(accountType);
            }
            if (dataSet != null) {
                sb.append('/').append(dataSet);
            }
            if (accountName != null) {
                sb.append('-').append(accountName.replace('-', '_'));
            }
            if (groupSourceId != null) {
                sb.append('-').append(groupSourceId);
            } else if (groupId != 0) {
                sb.append('-').append(groupId);
            }
            mId = sb.toString();
        }
        return mId;
    }
}
