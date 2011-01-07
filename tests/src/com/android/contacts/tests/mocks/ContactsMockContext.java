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

//import com.android.providers.contacts.ContactsMockPackageManager;

import android.app.Activity;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

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

    public void verify() {
        mContactsProvider.verify();
        mSettingsProvider.verify();
    }

    /**
     * Waits for the specified loaders to complete loading.
     */
    public void waitForLoaders(final Activity activity, int... loaderIds) {
        // We want to wait for each loader using a separate thread, so that we can
        // simulate race conditions.
        Thread[] waitThreads = new Thread[loaderIds.length];
        for (int i = 0; i < loaderIds.length; i++) {
            final int loaderId = loaderIds[i];
            waitThreads[i] = new Thread("LoaderWaitingThread" + i) {
                @Override
                public void run() {
                    AsyncTaskLoader<?> loader =
                            (AsyncTaskLoader<?>) activity.getLoaderManager().getLoader(loaderId);
                    loader.waitForLoader();
                }
            };
            waitThreads[i].start();
        }

        // Now we wait for all these threads to finish
        for (Thread thread : waitThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

}
