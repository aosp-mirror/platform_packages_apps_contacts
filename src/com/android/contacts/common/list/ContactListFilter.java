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

package com.android.contacts.common.list;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.RawContacts;
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

    /**
     * Obsolete filter which had been used in Honeycomb. This may be stored in
     * {@link SharedPreferences}, but should be replaced with ALL filter when it is found.
     *
     * TODO: "group" filter and relevant variables are all obsolete. Remove them.
     */
    private static final int FILTER_TYPE_GROUP = 1;

    private static final String KEY_FILTER_TYPE = "filter.type";
    private static final String KEY_ACCOUNT_NAME = "filter.accountName";
    private static final String KEY_ACCOUNT_TYPE = "filter.accountType";
    private static final String KEY_DATA_SET = "filter.dataSet";

    public final int filterType;
    public final String accountType;
    public final String accountName;
    public final String dataSet;
    public final Drawable icon;
    private String mId;

    public ContactListFilter(int filterType, String accountType, String accountName, String dataSet,
            Drawable icon) {
        this.filterType = filterType;
        this.accountType = accountType;
        this.accountName = accountName;
        this.dataSet = dataSet;
        this.icon = icon;
    }

    public static ContactListFilter createFilterWithType(int filterType) {
        return new ContactListFilter(filterType, null, null, null, null);
    }

    public static ContactListFilter createAccountFilter(String accountType, String accountName,
            String dataSet, Drawable icon) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT, accountType,
                accountName, dataSet, icon);
    }

    /**
     * Returns true if this filter is based on data and may become invalid over time.
     */
    public boolean isValidationRequired() {
        return filterType == FILTER_TYPE_ACCOUNT;
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

        return filterType - another.filterType;
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

        return true;
    }

    /**
     * Store the given {@link ContactListFilter} to preferences. If the requested filter is
     * of type {@link #FILTER_TYPE_SINGLE_CONTACT} then do not save it to preferences because
     * it is a temporary state.
     */
    public static void storeToPreferences(SharedPreferences prefs, ContactListFilter filter) {
        if (filter != null && filter.filterType == FILTER_TYPE_SINGLE_CONTACT) {
            return;
        }
        prefs.edit()
            .putInt(KEY_FILTER_TYPE, filter == null ? FILTER_TYPE_DEFAULT : filter.filterType)
            .putString(KEY_ACCOUNT_NAME, filter == null ? null : filter.accountName)
            .putString(KEY_ACCOUNT_TYPE, filter == null ? null : filter.accountType)
            .putString(KEY_DATA_SET, filter == null ? null : filter.dataSet)
            .apply();
    }

    /**
     * Try to obtain ContactListFilter object saved in SharedPreference.
     * If there's no info there, return ALL filter instead.
     */
    public static ContactListFilter restoreDefaultPreferences(SharedPreferences prefs) {
        ContactListFilter filter = restoreFromPreferences(prefs);
        if (filter == null) {
            filter = ContactListFilter.createFilterWithType(FILTER_TYPE_ALL_ACCOUNTS);
        }
        // "Group" filter is obsolete and thus is not exposed anymore. The "single contact mode"
        // should also not be stored in preferences anymore since it is a temporary state.
        if (filter.filterType == FILTER_TYPE_GROUP ||
                filter.filterType == FILTER_TYPE_SINGLE_CONTACT) {
            filter = ContactListFilter.createFilterWithType(FILTER_TYPE_ALL_ACCOUNTS);
        }
        return filter;
    }

    private static ContactListFilter restoreFromPreferences(SharedPreferences prefs) {
        int filterType = prefs.getInt(KEY_FILTER_TYPE, FILTER_TYPE_DEFAULT);
        if (filterType == FILTER_TYPE_DEFAULT) {
            return null;
        }

        String accountName = prefs.getString(KEY_ACCOUNT_NAME, null);
        String accountType = prefs.getString(KEY_ACCOUNT_TYPE, null);
        String dataSet = prefs.getString(KEY_DATA_SET, null);
        return new ContactListFilter(filterType, accountType, accountName, dataSet, null);
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(filterType);
        dest.writeString(accountName);
        dest.writeString(accountType);
        dest.writeString(dataSet);
    }

    public static final Parcelable.Creator<ContactListFilter> CREATOR =
            new Parcelable.Creator<ContactListFilter>() {
        @Override
        public ContactListFilter createFromParcel(Parcel source) {
            int filterType = source.readInt();
            String accountName = source.readString();
            String accountType = source.readString();
            String dataSet = source.readString();
            return new ContactListFilter(filterType, accountType, accountName, dataSet, null);
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
            mId = sb.toString();
        }
        return mId;
    }

    /**
     * Adds the account query parameters to the given {@code uriBuilder}.
     *
     * @throws IllegalStateException if the filter type is not {@link #FILTER_TYPE_ACCOUNT}.
     */
    public Uri.Builder addAccountQueryParameterToUrl(Uri.Builder uriBuilder) {
        if (filterType != FILTER_TYPE_ACCOUNT) {
            throw new IllegalStateException("filterType must be FILTER_TYPE_ACCOUNT");
        }
        uriBuilder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
        uriBuilder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
        if (!TextUtils.isEmpty(dataSet)) {
            uriBuilder.appendQueryParameter(RawContacts.DATA_SET, dataSet);
        }
        return uriBuilder;
    }

    public String toDebugString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[filter type: " + filterType + " (" + filterTypeToString(filterType) + ")");
        if (filterType == FILTER_TYPE_ACCOUNT) {
            builder.append(", accountType: " + accountType)
                    .append(", accountName: " + accountName)
                    .append(", dataSet: " + dataSet);
        }
        builder.append(", icon: " + icon + "]");
        return builder.toString();
    }

    public static final String filterTypeToString(int filterType) {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "FILTER_TYPE_DEFAULT";
            case FILTER_TYPE_ALL_ACCOUNTS:
                return "FILTER_TYPE_ALL_ACCOUNTS";
            case FILTER_TYPE_CUSTOM:
                return "FILTER_TYPE_CUSTOM";
            case FILTER_TYPE_STARRED:
                return "FILTER_TYPE_STARRED";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "FILTER_TYPE_SINGLE_CONTACT";
            case FILTER_TYPE_ACCOUNT:
                return "FILTER_TYPE_ACCOUNT";
            default:
                return "(unknown)";
        }
    }
}
