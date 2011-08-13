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
 * limitations under the License.
 */

package com.android.contacts.calllog;

import android.net.Uri;
import android.text.TextUtils;

/**
 * Information for a contact as needed by the Call Log.
 */
public final class ContactInfo {
    public long personId = -1;
    public String name;
    public int type;
    public String label;
    public String number;
    public String formattedNumber;
    public String normalizedNumber;
    public Uri thumbnailUri;
    public String lookupKey;

    public static ContactInfo EMPTY = new ContactInfo();

    @Override
    public int hashCode() {
        // Uses only name and personId to determine hashcode.
        // This should be sufficient to have a reasonable distribution of hash codes.
        // Moreover, there should be no two people with the same personId.
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (personId ^ (personId >>> 32));
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ContactInfo other = (ContactInfo) obj;
        if (personId != other.personId) return false;
        if (!TextUtils.equals(name, other.name)) return false;
        if (type != other.type) return false;
        if (!TextUtils.equals(label, other.label)) return false;
        if (!TextUtils.equals(number, other.number)) return false;
        // Ignore formatted number.
        if (!TextUtils.equals(normalizedNumber, other.normalizedNumber)) return false;
        if (!uriEquals(thumbnailUri, other.thumbnailUri)) return false;
        if (!TextUtils.equals(lookupKey, other.lookupKey)) return false;
        return true;
    }

    private static boolean uriEquals(Uri thumbnailUri1, Uri thumbnailUri2) {
        if (thumbnailUri1 == thumbnailUri2) return true;
        if (thumbnailUri1 == null) return false;
        return thumbnailUri1.equals(thumbnailUri2);
    }
}