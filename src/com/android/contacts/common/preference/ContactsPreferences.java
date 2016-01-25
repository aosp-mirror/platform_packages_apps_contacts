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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;

import com.android.contacts.common.R;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.model.AccountTypeManager;

import java.util.ArrayList;
import java.util.List;

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

    public static final String DO_NOT_SYNC_CONTACT_METADATA_MSG = "Do not sync metadata";

    public static final String CONTACT_METADATA_AUTHORITY = "com.android.contacts.metadata";

    /**
     * Value to use when a preference is unassigned and needs to be read from the shared preferences
     */
    private static final int PREFERENCE_UNASSIGNED = -1;

    private final Context mContext;
    private int mSortOrder = PREFERENCE_UNASSIGNED;
    private int mDisplayOrder = PREFERENCE_UNASSIGNED;
    private String mDefaultAccount = null;
    private ChangeListener mListener = null;
    private Handler mHandler;
    private final SharedPreferences mPreferences;
    private String mDefaultAccountKey;
    private String mDefaultAccountSavedKey;

    public ContactsPreferences(Context context) {
        mContext = context;
        mHandler = new Handler();
        mPreferences = mContext.getSharedPreferences(context.getPackageName(),
                Context.MODE_PRIVATE);
        mDefaultAccountKey = mContext.getResources().getString(
                R.string.contact_editor_default_account_key);
        mDefaultAccountSavedKey = mContext.getResources().getString(
                R.string.contact_editor_anything_saved_key);
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
        if (mSortOrder == PREFERENCE_UNASSIGNED) {
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
        if (mDisplayOrder == PREFERENCE_UNASSIGNED) {
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

    public boolean isDefaultAccountUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_default_account_user_changeable);
    }

    public String getDefaultAccount() {
        if (!isDefaultAccountUserChangeable()) {
            return mDefaultAccount;
        }
        if (TextUtils.isEmpty(mDefaultAccount)) {
            final String accountString = mPreferences
                    .getString(mDefaultAccountKey, mDefaultAccount);
            if (!TextUtils.isEmpty(accountString)) {
                final AccountWithDataSet accountWithDataSet = AccountWithDataSet.unstringify(
                        accountString);
                mDefaultAccount = accountWithDataSet.name;
            }
        }
        return mDefaultAccount;
    }

    public void setDefaultAccount(AccountWithDataSet accountWithDataSet) {
        mDefaultAccount = accountWithDataSet == null ? null : accountWithDataSet.name;
        final Editor editor = mPreferences.edit();
        if (TextUtils.isEmpty(mDefaultAccount)) {
            editor.remove(mDefaultAccountKey);
        } else {
            editor.putString(mDefaultAccountKey, accountWithDataSet.stringify());
        }
        editor.putBoolean(mDefaultAccountSavedKey, true);
        editor.commit();
    }

    public String getContactMetadataSyncAccount() {
        for (Account account : getFocusGoogleAccounts()) {
            if (ContentResolver.getIsSyncable(account, CONTACT_METADATA_AUTHORITY) == 1
                    && ContentResolver.getSyncAutomatically(account, CONTACT_METADATA_AUTHORITY)) {
                return account.name;
            }
        }
        return DO_NOT_SYNC_CONTACT_METADATA_MSG;
    }

    public void setContactMetadataSyncAccount(AccountWithDataSet accountWithDataSet) {
        final String mContactMetadataSyncAccount =
                accountWithDataSet == null ? null : accountWithDataSet.name;
        toggleContactMetadata(mContactMetadataSyncAccount);
    }

    private void toggleContactMetadata(String syncAccount) {
        mContext.getContentResolver().delete(getMetadataSyncUri(), null, null);
        mContext.getContentResolver().delete(getMetadataSyncStateUri(), null, null);
        requestMetadataSyncForAccount(syncAccount);
    }

    /**
     * Turn on contact metadata sync for this {@param accountName} and turn off automatic sync
     * for other accounts. If accountName is null, then turn off automatic sync for all accounts.
     */
    private void requestMetadataSyncForAccount(String accountName) {
        for (Account account : getFocusGoogleAccounts()) {
            if (!TextUtils.isEmpty(accountName) && accountName.equals(account.name)) {
                ContentResolver.setIsSyncable(account, CONTACT_METADATA_AUTHORITY, 1 /*syncable*/);
                ContentResolver.setSyncAutomatically(account, CONTACT_METADATA_AUTHORITY, true);

                // Request sync.
                final Bundle b = new Bundle();
                b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                ContentResolver.requestSync(account, CONTACT_METADATA_AUTHORITY, b);
            } else {
                // Turn off automatic sync for all other accounts.
                ContentResolver.setSyncAutomatically(account, CONTACT_METADATA_AUTHORITY, false);
            }
        }
    }

    /**
     * @return google accounts with "com.google" account type and null data set.
     */
    private List<Account> getFocusGoogleAccounts() {
        List<Account> focusGoogleAccounts = new ArrayList<Account>();
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(mContext);
        List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(true);
        for (AccountWithDataSet account : accounts) {
            if (GoogleAccountType.ACCOUNT_TYPE.equals(account.type) && account.dataSet == null) {
                focusGoogleAccounts.add(account.getAccountOrNull());
            }
        }
        return focusGoogleAccounts;
    }

    private static Uri getMetadataSyncUri() {
        final Uri metadataUri = Uri.parse("content://" + CONTACT_METADATA_AUTHORITY);
        return metadataUri.buildUpon()
                .appendPath("metadata_sync")
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    private static Uri getMetadataSyncStateUri() {
        final Uri metadataUri = Uri.parse("content://" + CONTACT_METADATA_AUTHORITY);
        return metadataUri.buildUpon()
                .appendPath("metadata_sync_state")
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public void registerChangeListener(ChangeListener listener) {
        if (mListener != null) unregisterChangeListener();

        mListener = listener;

        // Reset preferences to "unknown" because they may have changed while the
        // listener was unregistered.
        mDisplayOrder = PREFERENCE_UNASSIGNED;
        mSortOrder = PREFERENCE_UNASSIGNED;
        mDefaultAccount = null;

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
                refreshValue(key);
            }
        });
    }

    /**
     * Forces the value for the given key to be looked up from shared preferences and notifies
     * the registered {@link ChangeListener}
     *
     * @param key the {@link SharedPreferences} key to look up
     */
    public void refreshValue(String key) {
        if (DISPLAY_ORDER_KEY.equals(key)) {
            mDisplayOrder = PREFERENCE_UNASSIGNED;
            mDisplayOrder = getDisplayOrder();
        } else if (SORT_ORDER_KEY.equals(key)) {
            mSortOrder = PREFERENCE_UNASSIGNED;
            mSortOrder = getSortOrder();
        } else if (mDefaultAccountKey.equals(key)) {
            mDefaultAccount = null;
            mDefaultAccount = getDefaultAccount();
        }
        if (mListener != null) mListener.onChange();
    }

    public interface ChangeListener {
        void onChange();
    }

    /**
     * If there are currently no preferences (which means this is the first time we are run),
     * For sort order and display order, check to see if there are any preferences stored in
     * system settings (pre-L) which can be copied into our own SharedPreferences.
     * For default account setting, check to see if there are any preferences stored in the previous
     * SharedPreferences which can be copied into current SharedPreferences.
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

        if (!mPreferences.contains(mDefaultAccountKey)) {
            final SharedPreferences previousPrefs =
                    PreferenceManager.getDefaultSharedPreferences(mContext);
            final String defaultAccount = previousPrefs.getString(mDefaultAccountKey, null);
            if (!TextUtils.isEmpty(defaultAccount)) {
                final AccountWithDataSet accountWithDataSet = AccountWithDataSet.unstringify(
                        defaultAccount);
                setDefaultAccount(accountWithDataSet);
            }
        }
    }
}
