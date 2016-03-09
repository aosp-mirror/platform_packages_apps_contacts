/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.contacts.commonbind.experiments;

import android.content.Context;

/**
 * Provides getters for experiment flags.
 * This stub class is designed to be overwritten by an overlay.
 */
public final class Flags {

    private static Flags sInstance;

    public static Flags getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Flags();
        }
        return sInstance;
    }

    private Flags() {
    }

    public boolean getBoolean(String flagName, boolean defValue) {
        return defValue;
    }

    public byte[] getBytes(String flagName, byte[] defValue) {
        return defValue;
    }

    public double getDouble(String flagName, double defValue) {
        return defValue;
    }

    public int getInt(String flagName, int defValue) {
        return defValue;
    }

    public long getLong(String flagName, long defValue) {
        return defValue;
    }

    public String getString(String flagName, String defValue) {
        return defValue;
    }
}
