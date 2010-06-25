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
package com.android.contacts.vcard;

import android.net.Uri;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCommitter;

import java.util.ArrayList;

public class MockVCardEntryCommitter extends VCardEntryCommitter {

    private final ArrayList<Uri> mUris = new ArrayList<Uri>(); 

    public MockVCardEntryCommitter() {
        super(null);
    }

    /**
     * Exists for forcing super class to do nothing.
     */
    @Override
    public void onStart() {
    }

    /**
     * Exists for forcing super class to do nothing.
     */
    @Override
    public void onEnd() {
    }

    @Override
    public void onEntryCreated(final VCardEntry vcardEntry) {
        mUris.add(null);
    }

    @Override
    public ArrayList<Uri> getCreatedUris() {
        return mUris;
    }
}