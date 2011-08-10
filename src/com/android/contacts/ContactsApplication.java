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

package com.android.contacts;

import static com.android.contacts.util.BackgroundTaskService.createAsyncTaskBackgroundTaskService;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.test.InjectedServices;
import com.android.contacts.util.BackgroundTaskService;
import com.google.common.annotations.VisibleForTesting;

import android.app.Application;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;

public final class ContactsApplication extends Application {

    private static InjectedServices sInjectedServices;
    private AccountTypeManager mAccountTypeManager;
    private ContactPhotoManager mContactPhotoManager;
    private BackgroundTaskService mBackgroundTaskService;

    /**
     * Overrides the system services with mocks for testing.
     */
    @VisibleForTesting
    public static void injectServices(InjectedServices services) {
        sInjectedServices = services;
    }

    public static InjectedServices getInjectedServices() {
        return sInjectedServices;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (sInjectedServices != null) {
            ContentResolver resolver = sInjectedServices.getContentResolver();
            if (resolver != null) {
                return resolver;
            }
        }
        return super.getContentResolver();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (sInjectedServices != null) {
            SharedPreferences prefs = sInjectedServices.getSharedPreferences();
            if (prefs != null) {
                return prefs;
            }
        }

        return super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        if (sInjectedServices != null) {
            Object service = sInjectedServices.getSystemService(name);
            if (service != null) {
                return service;
            }
        }

        if (AccountTypeManager.ACCOUNT_TYPE_SERVICE.equals(name)) {
            if (mAccountTypeManager == null) {
                mAccountTypeManager = AccountTypeManager.createAccountTypeManager(this);
            }
            return mAccountTypeManager;
        }

        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }

        if (BackgroundTaskService.BACKGROUND_TASK_SERVICE.equals(name)) {
            if (mBackgroundTaskService == null) {
                mBackgroundTaskService = createAsyncTaskBackgroundTaskService();
            }
            return mBackgroundTaskService;
        }

        return super.getSystemService(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Priming caches to placate the StrictMode police
        Context context = getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context);
        AccountTypeManager.getInstance(context);
        LoaderManager.enableDebugLogging(true);

        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
    }
}
