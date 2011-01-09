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

package com.android.contacts.test;

import com.google.android.collect.Maps;

import android.content.ContentResolver;

import java.util.HashMap;

/**
 * A mechanism for providing alternative (mock) services to the application
 * while running tests. Activities, Services and the Application should check
 * with this class to see if a particular service has been overridden.
 */
public class InjectedServices {

    private ContentResolver mContentResolver;
    private HashMap<String, Object> mSystemServices;

    public void setContentResolver(ContentResolver mContentResolver) {
        this.mContentResolver = mContentResolver;
    }

    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    public void setSystemService(String name, Object service) {
        if (mSystemServices == null) {
            mSystemServices = Maps.newHashMap();
        }

        mSystemServices.put(name, service);
    }

    public Object getSystemService(String name) {
        if (mSystemServices != null) {
            return mSystemServices.get(name);
        }
        return null;
    }
}
