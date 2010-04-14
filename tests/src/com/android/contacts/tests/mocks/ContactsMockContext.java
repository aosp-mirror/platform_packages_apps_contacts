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

package com.android.contacts.tests.mocks;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

/**
 * A mock context for contact activity unit tests. Forwards everything to
 * a supplied context, except content resolver operations, which are sent
 * to mock content providers.
 */
public class ContactsMockContext extends ContextWrapper {

    private MockContentResolver mContentResolver;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;

    public ContactsMockContext(Context base) {
        super(base);
        mContentResolver = new MockContentResolver();
        mContactsProvider = new MockContentProvider();
        mContentResolver.addProvider(ContactsContract.AUTHORITY, mContactsProvider);
        mSettingsProvider = new MockContentProvider();
        mContentResolver.addProvider(Settings.AUTHORITY, mSettingsProvider);
    }

    @Override
    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    public MockContentProvider getContactsProvider() {
        return mContactsProvider;
    }

    public MockContentProvider getSettingsProvider() {
        return mSettingsProvider;
    }
}
