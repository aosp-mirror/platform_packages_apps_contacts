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

package com.android.contacts.ui;

import com.android.contacts.R;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

/**
 * Manages user preferences for contacts.
 */
public final class ContactsPreferences  {

    private Context mContext;
    private ContentResolver mContentResolver;
    private int mSortOrder = -1;
    private int mDisplayOrder = -1;
    private SettingsObserver mSettingsObserver;

    public ContactsPreferences(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();

        mSettingsObserver = new SettingsObserver();
        mSettingsObserver.register();
    }

    public boolean isSortOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_sort_order_user_changeable);
    }

    private int getDefaultSortOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_sort_order_primary)) {
            return ContactsContract.Preferences.SORT_ORDER_PRIMARY;
        } else {
            return ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE;
        }
    }

    public int getSortOrder() {
        if (!isSortOrderUserChangeable()) {
            return getDefaultSortOrder();
        }

        if (mSortOrder == -1) {
            try {
                mSortOrder = Settings.System.getInt(mContext.getContentResolver(),
                        ContactsContract.Preferences.SORT_ORDER);
            } catch (SettingNotFoundException e) {
                mSortOrder = getDefaultSortOrder();
            }
        }
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        Settings.System.putInt(mContext.getContentResolver(),
                ContactsContract.Preferences.SORT_ORDER, sortOrder);
    }

    public boolean isDisplayOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_display_order_user_changeable);
    }

    private int getDefaultDisplayOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_display_order_primary)) {
            return ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY;
        } else {
            return ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE;
        }
    }

    public int getDisplayOrder() {
        if (!isDisplayOrderUserChangeable()) {
            return getDefaultDisplayOrder();
        }

        if (mDisplayOrder == -1) {
            try {
                mDisplayOrder = Settings.System.getInt(mContext.getContentResolver(),
                        ContactsContract.Preferences.DISPLAY_ORDER);
            } catch (SettingNotFoundException e) {
                mDisplayOrder = getDefaultDisplayOrder();
            }
        }
        return mDisplayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        Settings.System.putInt(mContext.getContentResolver(),
                ContactsContract.Preferences.DISPLAY_ORDER, displayOrder);
    }

    private class SettingsObserver extends ContentObserver {

        public SettingsObserver() {
            super(null);
        }

        public void register() {
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(
                            ContactsContract.Preferences.SORT_ORDER), false, this);
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(
                            ContactsContract.Preferences.DISPLAY_ORDER), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mSortOrder = -1;
            mDisplayOrder = -1;

            // TODO send a message to parent context to notify of the change
        }
    }
}
