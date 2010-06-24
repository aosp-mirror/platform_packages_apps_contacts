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

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parsed form of the intent sent to the Contacts application.
 */
public class ContactsRequest implements Parcelable {

    /** Default mode: browse contacts */
    public static final int ACTION_DEFAULT = 10;

    /** Show contents of a specific group */
    public static final int ACTION_GROUP = 20;

    /** Show all starred contacts */
    public static final int ACTION_STARRED = 30;

    /** Show frequently contacted contacts */
    public static final int ACTION_FREQUENT = 40;

    /** Show starred and the frequent */
    public static final int ACTION_STREQUENT = 50;

    /** Show all contacts and pick them when clicking */
    public static final int ACTION_PICK_CONTACT = 60;

    /** Show all contacts as well as the option to create a new one */
    public static final int ACTION_PICK_OR_CREATE_CONTACT = 70;

    /** Show all contacts and pick them for edit when clicking, and allow creating a new contact */
    public static final int ACTION_INSERT_OR_EDIT_CONTACT = 80;

    /** Show all phone numbers and pick them when clicking */
    public static final int ACTION_PICK_PHONE = 90;

    /** Show all postal addresses and pick them when clicking */
    public static final int ACTION_PICK_POSTAL = 100;

    /** Show all contacts and create a shortcut for the picked contact */
    public static final int ACTION_CREATE_SHORTCUT_CONTACT = 110;

    /** Show all phone numbers and create a call shortcut for the picked number */
    public static final int ACTION_CREATE_SHORTCUT_CALL = 120;

    /** Show all phone numbers and create an SMS shortcut for the picked number */
    public static final int ACTION_CREATE_SHORTCUT_SMS = 130;

    private boolean mValid = true;
    private int mActionCode = ACTION_DEFAULT;
    private Intent mRedirectIntent;
    private CharSequence mTitle;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private String mQueryString;

    public static final int DISPLAY_ONLY_WITH_PHONES_PREFERENCE = 0;
    public static final int DISPLAY_ONLY_WITH_PHONES_ENABLED = 1;
    public static final int DISPLAY_ONLY_WITH_PHONES_DISABLED = 2;

    private int mDisplayOnlyWithPhones;
    private boolean mDisplayOnlyVisible;
    private String mGroupName;
    private boolean mLegacyCompatibilityMode;
    private boolean mDirectorySearchEnabled = true;

    /**
     * Copies all fields.
     */
    public void copyFrom(ContactsRequest request) {
        mValid = request.mValid;
        mActionCode = request.mActionCode;
        mRedirectIntent = request.mRedirectIntent;
        mTitle = request.mTitle;
        mSearchMode = request.mSearchMode;
        mSearchResultsMode = request.mSearchResultsMode;
        mQueryString = request.mQueryString;
        mDisplayOnlyWithPhones = request.mDisplayOnlyWithPhones;
        mDisplayOnlyVisible = request.mDisplayOnlyVisible;
        mGroupName = request.mGroupName;
        mLegacyCompatibilityMode = request.mLegacyCompatibilityMode;
        mDirectorySearchEnabled = request.mDirectorySearchEnabled;
    }

    public static Parcelable.Creator<ContactsRequest> CREATOR = new Creator<ContactsRequest>() {

        public ContactsRequest[] newArray(int size) {
            return new ContactsRequest[size];
        }

        public ContactsRequest createFromParcel(Parcel source) {
            ContactsRequest request = new ContactsRequest();
            request.mValid = source.readInt() != 0;
            request.mActionCode = source.readInt();
            request.mRedirectIntent = source.readParcelable(this.getClass().getClassLoader());
            request.mTitle = source.readCharSequence();
            request.mSearchMode = source.readInt() != 0;
            request.mSearchResultsMode = source.readInt() != 0;
            request.mQueryString = source.readString();
            request.mDisplayOnlyWithPhones = source.readInt();
            request.mDisplayOnlyVisible = source.readInt() != 0;
            request.mGroupName = source.readString();
            request.mLegacyCompatibilityMode  = source.readInt() != 0;
            request.mDirectorySearchEnabled = source.readInt() != 0;
            return request;
        }
    };

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mValid ? 1 : 0);
        dest.writeInt(mActionCode);
        dest.writeParcelable(mRedirectIntent, 0);
        dest.writeCharSequence(mTitle);
        dest.writeInt(mSearchMode ? 1 : 0);
        dest.writeInt(mSearchResultsMode ? 1 : 0);
        dest.writeString(mQueryString);
        dest.writeInt(mDisplayOnlyWithPhones);
        dest.writeInt(mDisplayOnlyVisible ? 1 : 0);
        dest.writeString(mGroupName);
        dest.writeInt(mLegacyCompatibilityMode ? 1 : 0);
        dest.writeInt(mDirectorySearchEnabled ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public boolean isValid() {
        return mValid;
    }

    public void setValid(boolean flag) {
        mValid = flag;
    }

    public Intent getRedirectIntent() {
        return mRedirectIntent;
    }

    public void setRedirectIntent(Intent intent) {
        mRedirectIntent = intent;
    }

    public void setActivityTitle(CharSequence title) {
        mTitle = title;
    }

    public CharSequence getActivityTitle() {
        return mTitle;
    }

    public int getActionCode() {
        return mActionCode;
    }

    public void setActionCode(int actionCode) {
        mActionCode = actionCode;
    }

    public boolean getDisplayOnlyVisible() {
        return mDisplayOnlyVisible;
    }

    public void setDisplayOnlyVisible(boolean flag) {
        mDisplayOnlyVisible = flag;
    }

    public int getDisplayWithPhonesOnlyOption() {
        return mDisplayOnlyWithPhones;
    }

    public void setDisplayWithPhonesOnlyOption(int option) {
        mDisplayOnlyWithPhones = option;
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public boolean isSearchResultsMode() {
        return mSearchResultsMode;
    }

    public void setSearchResultsMode(boolean flag) {
        mSearchResultsMode = flag;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String string) {
        mQueryString = string;
    }

    public String getGroupName() {
        return mGroupName;
    }

    public void setGroupName(String groupName) {
        mGroupName = groupName;
    }

    public boolean isLegacyCompatibilityMode() {
        return mLegacyCompatibilityMode;
    }

    public void setLegacyCompatibilityMode(boolean flag) {
        mLegacyCompatibilityMode = flag;
    }

    /**
     * Determines whether this search request should include directories or
     * is limited to local contacts only.
     */
    public boolean isDirectorySearchEnabled() {
        return mDirectorySearchEnabled;
    }

    public void setDirectorySearchEnabled(boolean flag) {
        mDirectorySearchEnabled = flag;
    }
}
