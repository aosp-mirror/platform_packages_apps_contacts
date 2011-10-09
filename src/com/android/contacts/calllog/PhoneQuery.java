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

import android.provider.ContactsContract.PhoneLookup;

/**
 * The query to look up the {@link ContactInfo} for a given number in the Call Log.
 */
final class PhoneQuery {
    public static final String[] _PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            PhoneLookup.NORMALIZED_NUMBER,
            PhoneLookup.PHOTO_ID,
            PhoneLookup.LOOKUP_KEY,
            PhoneLookup.PHOTO_URI};

    public static final int PERSON_ID = 0;
    public static final int NAME = 1;
    public static final int PHONE_TYPE = 2;
    public static final int LABEL = 3;
    public static final int MATCHED_NUMBER = 4;
    public static final int NORMALIZED_NUMBER = 5;
    public static final int PHOTO_ID = 6;
    public static final int LOOKUP_KEY = 7;
    public static final int PHOTO_URI = 8;
}