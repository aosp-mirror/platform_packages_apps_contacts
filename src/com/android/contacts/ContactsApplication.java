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

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.test.InjectedServices;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.os.StrictMode;
import android.preference.PreferenceManager;

public final class ContactsApplication extends Application {

    private static InjectedServices sInjectedServices;

    /**
     * Overrides the system services with mocks for testing.
     */
    public static void injectContentResolver(InjectedServices services) {
        sInjectedServices = services;
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
    public void onCreate() {
        super.onCreate();

        // Priming caches to placate the StrictMode police
        Context context = getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context);
        AccountTypeManager.getInstance(context);

        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
    }
}
