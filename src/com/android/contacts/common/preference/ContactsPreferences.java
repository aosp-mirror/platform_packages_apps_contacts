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

package com.android.contacts.common.preference;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.android.contacts.common.R;

/**
 * Manages user preferences for contacts.
 */
public final class ContactsPreferences implements OnSharedPreferenceChangeListener {

    /**
     * The value for the DISPLAY_ORDER key to show the given name first.
     */
    public static final int DISPLAY_ORDER_PRIMARY = 1;

    /**
     * The value for the DISPLAY_ORDER key to show the family name first.
     */
    public static final int DISPLAY_ORDER_ALTERNATIVE = 2;

    public static final String DISPLAY_ORDER_KEY = "android.contacts.DISPLAY_ORDER";

    /**
     * The value for the SORT_ORDER key corresponding to sort by given name first.
     */
    public static final int SORT_ORDER_PRIMARY = 1;

    public static final String SORT_ORDER_KEY = "android.contacts.SORT_ORDER";

    /**
     * The value for the SORT_ORDER key corresponding to sort by family name first.
     */
    public static final int SORT_ORDER_ALTERNATIVE = 2;

    public static final String PREF_DISPLAY_ONLY_PHONES = "only_phones";
    public static final boolean PREF_DISPLAY_ONLY_PHONES_DEFAULT = false;

    private final Context mContext;
    private int mSortOrder = -1;
    private int mDisplayOrder = -1;
    private ChangeListener mListener = null;
    private Handler mHandler;
    private final SharedPreferences mPreferences;

    public ContactsPreferences(Context context) {
        mContext = context;
        mHandler = new Handler();
        mPreferences = mContext.getSharedPreferences(context.getPackageName(),
                Context.MODE_PRIVATE);
        maybeMigrateSystemSettings();
    }

    public boolean isSortOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_sort_order_user_changeable);
    }

    public int getDefaultSortOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_sort_order_primary)) {
            return SORT_ORDER_PRIMARY;
        } else {
            return SORT_ORDER_ALTERNATIVE;
        }
    }

    public int getSortOrder() {
        if (!isSortOrderUserChangeable()) {
            return getDefaultSortOrder();
        }
        if (mSortOrder == -1) {
            mSortOrder = mPreferences.getInt(SORT_ORDER_KEY, getDefaultSortOrder());
        }
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        final Editor editor = mPreferences.edit();
        editor.putInt(SORT_ORDER_KEY, sortOrder);
        editor.commit();
    }

    public boolean isDisplayOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_display_order_user_changeable);
    }

    public int getDefaultDisplayOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_display_order_primary)) {
            return DISPLAY_ORDER_PRIMARY;
        } else {
            return DISPLAY_ORDER_ALTERNATIVE;
        }
    }

    public int getDisplayOrder() {
        if (!isDisplayOrderUserChangeable()) {
            return getDefaultDisplayOrder();
        }
        if (mDisplayOrder == -1) {
            mDisplayOrder = mPreferences.getInt(DISPLAY_ORDER_KEY, getDefaultDisplayOrder());
        }
        return mDisplayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        final Editor editor = mPreferences.edit();
        editor.putInt(DISPLAY_ORDER_KEY, displayOrder);
        editor.commit();
    }

    public void registerChangeListener(ChangeListener listener) {
        if (mListener != null) unregisterChangeListener();

        mListener = listener;

        // Reset preferences to "unknown" because they may have changed while the
        // listener was unregistered.
        mDisplayOrder = -1;
        mSortOrder = -1;

        mPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void unregisterChangeListener() {
        if (mListener != null) {
            mListener = null;
        }

        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
        // This notification is not sent on the Ui thread. Use the previously created Handler
        // to switch to the Ui thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (DISPLAY_ORDER_KEY.equals(key)) {
                    mDisplayOrder = getDisplayOrder();
                } else if (SORT_ORDER_KEY.equals(key)) {
                    mSortOrder = getSortOrder();
                }
                if (mListener != null) mListener.onChange();
            }
        });
    }

    public interface ChangeListener {
        void onChange();
    }

    /**
     * If there are currently no preferences (which means this is the first time we are run),
     * check to see if there are any preferences stored in system settings (pre-L) which can be
     * copied into our own SharedPreferences.
     */
    private void maybeMigrateSystemSettings() {
        if (!mPreferences.contains(SORT_ORDER_KEY)) {
            int sortOrder = getDefaultSortOrder();
            try {
                 sortOrder = Settings.System.getInt(mContext.getContentResolver(),
                        SORT_ORDER_KEY);
            } catch (SettingNotFoundException e) {
            }
            setSortOrder(sortOrder);
        }

        if (!mPreferences.contains(DISPLAY_ORDER_KEY)) {
            int displayOrder = getDefaultDisplayOrder();
            try {
                displayOrder = Settings.System.getInt(mContext.getContentResolver(),
                        DISPLAY_ORDER_KEY);
            } catch (SettingNotFoundException e) {
            }
            setDisplayOrder(displayOrder);
        }
    }
}
