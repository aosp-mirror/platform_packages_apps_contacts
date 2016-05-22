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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.Contacts;

import com.google.common.base.Objects;

/** A member of the group currently being displayed to the user. */
public class Member implements Parcelable {

    public static final Parcelable.Creator<Member> CREATOR = new Parcelable.Creator<Member>() {
        @Override
        public Member createFromParcel(Parcel in) {
            return new Member(in);
        }

        @Override
        public Member[] newArray(int size) {
            return new Member[size];
        }
    };

    // TODO: Switch to just dealing with raw contact IDs everywhere if possible
    private final long mRawContactId;
    private final long mContactId;
    private final Uri mLookupUri;
    private final String mDisplayName;
    private final Uri mPhotoUri;
    private final String mLookupKey;
    private final long mPhotoId;

    public Member(long rawContactId, String lookupKey, long contactId, String displayName,
            String photoUri, long photoId) {
        mRawContactId = rawContactId;
        mContactId = contactId;
        mLookupKey = lookupKey;
        mLookupUri = Contacts.getLookupUri(contactId, lookupKey);
        mDisplayName = displayName;
        mPhotoUri = (photoUri != null) ? Uri.parse(photoUri) : null;
        mPhotoId = photoId;
    }

    public long getRawContactId() {
        return mRawContactId;
    }

    public long getContactId() {
        return mContactId;
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }

    public String getLookupKey() {
        return mLookupKey;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public Uri getPhotoUri() {
        return mPhotoUri;
    }

    public long getPhotoId() {
        return mPhotoId;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Member) {
            Member otherMember = (Member) object;
            return Objects.equal(mLookupUri, otherMember.getLookupUri());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mLookupUri == null ? 0 : mLookupUri.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mRawContactId);
        dest.writeLong(mContactId);
        dest.writeParcelable(mLookupUri, flags);
        dest.writeString(mLookupKey);
        dest.writeString(mDisplayName);
        dest.writeParcelable(mPhotoUri, flags);
        dest.writeLong(mPhotoId);
    }

    private Member(Parcel in) {
        mRawContactId = in.readLong();
        mContactId = in.readLong();
        mLookupUri = in.readParcelable(getClass().getClassLoader());
        mLookupKey = in.readString();
        mDisplayName = in.readString();
        mPhotoUri = in.readParcelable(getClass().getClassLoader());
        mPhotoId = in.readLong();
    }
}