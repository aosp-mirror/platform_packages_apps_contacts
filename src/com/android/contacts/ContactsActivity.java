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

import com.android.contacts.test.InjectedServices;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;

/**
 * A common superclass for Contacts activities that handles application-wide services.
 */
public abstract class ContactsActivity extends Activity {

    private ContentResolver mContentResolver;

    @Override
    public ContentResolver getContentResolver() {
        if (mContentResolver == null) {
            InjectedServices services = ContactsApplication.getInjectedServices();
            if (services != null) {
                mContentResolver = services.getContentResolver();
            }
            if (mContentResolver == null) {
                mContentResolver = super.getContentResolver();
            }
        }
        return mContentResolver;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        InjectedServices services = ContactsApplication.getInjectedServices();
        if (services != null) {
            SharedPreferences prefs = services.getSharedPreferences();
            if (prefs != null) {
                return prefs;
            }
        }

        return super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }
}
