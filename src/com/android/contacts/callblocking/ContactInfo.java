/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.callblocking;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.util.UriUtils;

import com.google.common.base.Objects;

/**
 * Information for a contact as needed by blocked numbers.
 */
final public class ContactInfo {
    public Uri lookupUri;

    /**
     * Contact lookup key.  Note this may be a lookup key for a corp contact, in which case
     * "lookup by lookup key" doesn't work on the personal profile.
     */
    public String lookupKey;
    public String name;
    public String nameAlternative;
    public int type;
    public String label;
    public String number;
    public String formattedNumber;
    public String normalizedNumber;
    /** The photo for the contact, if available. */
    public long photoId;
    /** The high-res photo for the contact, if available. */
    public Uri photoUri;
    public boolean isBadData;
    public String objectId;
    public @UserType long userType;

    public static ContactInfo EMPTY = new ContactInfo();

    public int sourceType = 0;

    @Override
    public int hashCode() {
        // Uses only name and contactUri to determine hashcode.
        // This should be sufficient to have a reasonable distribution of hash codes.
        // Moreover, there should be no two people with the same lookupUri.
        return Objects.hashCode(lookupUri, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (Objects.equal(this, obj)) return true;
        if (obj == null) return false;
        if (obj instanceof ContactInfo) {
            ContactInfo other = (ContactInfo) obj;
            return Objects.equal(lookupUri, other.lookupUri)
                    && TextUtils.equals(name, other.name)
                    && TextUtils.equals(nameAlternative, other.nameAlternative)
                    && Objects.equal(type, other.type)
                    && TextUtils.equals(label, other.label)
                    && TextUtils.equals(number, other.number)
                    && TextUtils.equals(formattedNumber, other.formattedNumber)
                    && TextUtils.equals(normalizedNumber, other.normalizedNumber)
                    && Objects.equal(photoId, other.photoId)
                    && Objects.equal(photoUri, other.photoUri)
                    && TextUtils.equals(objectId, other.objectId)
                    && Objects.equal(userType, other.userType);
        }
        return false;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("lookupUri", lookupUri).add("name", name)
                .add("nameAlternative", nameAlternative)
                .add("type", type).add("label", label)
                .add("number", number).add("formattedNumber",formattedNumber)
                .add("normalizedNumber", normalizedNumber).add("photoId", photoId)
                .add("photoUri", photoUri).add("objectId", objectId).toString();
    }
}