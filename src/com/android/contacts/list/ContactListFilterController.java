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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the {@link ContactListFilter} selected by the user and saves it to
 * {@link SharedPreferences} if necessary.
 */
public class ContactListFilterController {

    public interface ContactListFilterListener {
        void onContactListFilterChanged();
    }

    private Context mContext;
    private List<ContactListFilterListener> mListeners = new ArrayList<ContactListFilterListener>();
    private ContactListFilter mFilter;

    private boolean mIsInitialized;

    public ContactListFilterController(Activity activity) {
        mContext = activity;
    }

    /**
     * @param forceFilterReload when true filter is reloaded even when there's already a cache
     * for it.
     */
    public void onStart(boolean forceFilterReload) {
        if (mFilter == null || forceFilterReload) {
            mFilter = ContactListFilter.restoreDefaultPreferences(getSharedPreferences());
        }
        mIsInitialized = true;
    }

    public boolean isInitialized() {
        return mIsInitialized;
    }

    public void addListener(ContactListFilterListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ContactListFilterListener listener) {
        mListeners.remove(listener);
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void setContactListFilter(ContactListFilter filter, boolean persistent) {
        if (!filter.equals(mFilter)) {
            mFilter = filter;
            if (persistent) {
                ContactListFilter.storeToPreferences(getSharedPreferences(), mFilter);
            }
            if (mListeners != null) {
               notifyContactListFilterChanged();
            }
        }
    }

    public void selectCustomFilter() {
        setContactListFilter(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_CUSTOM), true);
    }

    private void notifyContactListFilterChanged() {
        for (ContactListFilterListener listener : mListeners) {
            listener.onContactListFilterChanged();
        }
    }

}
