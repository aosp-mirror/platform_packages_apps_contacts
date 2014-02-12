/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.PinnedPositions;

/**
 * Class to hold contact information
 */
public class ContactEntry {
    public String name;
    public String status;
    public String phoneLabel;
    public String phoneNumber;
    public Uri photoUri;
    public Uri lookupUri;
    public String lookupKey;
    public Drawable presenceIcon;
    public long id;
    public int pinned = PinnedPositions.UNPINNED;
    public boolean isFavorite = false;
    public boolean isDefaultNumber = false;

    public static final ContactEntry BLANK_ENTRY = new ContactEntry();
}