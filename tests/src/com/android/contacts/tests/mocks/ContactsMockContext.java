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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import com.android.contacts.model.AccountTypeManager;

/**
 * A mock context for contacts unit tests. Forwards everything to
 * a supplied context, except content resolver operations, which are sent
 * to mock content providers.
 */
public class ContactsMockContext extends ContextWrapper {
    private ContactsMockPackageManager mPackageManager;
    private MockContentResolver mContentResolver;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;
    private MockAccountTypeManager mMockAccountTypeManager;
    private Intent mIntentForStartActivity;

    public ContactsMockContext(Context base) {
        super(base);
        mPackageManager = new ContactsMockPackageManager();
        mContentResolver = new MockContentResolver();
        mContactsProvider = new MockContentProvider();
        mContentResolver.addProvider(ContactsContract.AUTHORITY, mContactsProvider);
        mContactsProvider.attachInfo(this, new ProviderInfo());
        mSettingsProvider = new MockContentProvider();
        mSettingsProvider.attachInfo(this, new ProviderInfo());
        mContentResolver.addProvider(Settings.AUTHORITY, mSettingsProvider);
    }

    public void setMockAccountTypeManager(MockAccountTypeManager mockAccountTypeManager) {
        mMockAccountTypeManager = mockAccountTypeManager;
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

    @Override
    public PackageManager getPackageManager() {
        return mPackageManager;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    /**
     * Instead of actually sending Intent, this method just remembers what Intent was supplied last.
     * You can check the content via {@link #getIntentForStartActivity()} for verification.
     */
    @Override
    public void startActivity(Intent intent) {
        mIntentForStartActivity = intent;
    }

    public Intent getIntentForStartActivity() {
        return mIntentForStartActivity;
    }

    public void verify() {
        mContactsProvider.verify();
        mSettingsProvider.verify();
    }

    @Override
    public Object getSystemService(String name) {
        if (AccountTypeManager.ACCOUNT_TYPE_SERVICE.equals(name)) {
           return mMockAccountTypeManager;
        }
        return super.getSystemService(name);
    }
}
