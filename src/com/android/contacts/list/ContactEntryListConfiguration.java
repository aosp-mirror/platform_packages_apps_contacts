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

package com.android.contacts.list;

import android.content.Context;
import android.widget.ListAdapter;

/**
 * Common base class for configurations of various contact-related lists, e.g.
 * contact list, phone number list etc.
 */
public abstract class ContactEntryListConfiguration {

    private final Context mContext;
    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;

    public ContactEntryListConfiguration(Context context) {
        this.mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    public abstract ListAdapter createListAdapter();

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        mSectionHeaderDisplayEnabled = flag;
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }
}
