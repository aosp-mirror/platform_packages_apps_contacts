/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.editor;

import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.test.NeededForTesting;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for the "account changed" notification in the new contact creation flow.
 *
 * TODO Remove all the "@VisibleForTesting"s once they're actually used in the app.
 *      (Until then we need them to avoid "no such method" in tests)
 */
public class ContactEditorUtils {
    private static final String TAG = "ContactEditorUtils";

    private static final String KEY_DEFAULT_ACCOUNT = "ContactEditorUtils_default_account";
    private static final String KEY_KNOWN_ACCOUNTS = "ContactEditorUtils_known_accounts";
    // Key to tell the first time launch.
    private static final String KEY_ANYTHING_SAVED = "ContactEditorUtils_anything_saved";

    private static final List<AccountWithDataSet> EMPTY_ACCOUNTS = ImmutableList.of();

    private static ContactEditorUtils sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final AccountTypeManager mAccountTypes;

    private ContactEditorUtils(Context context) {
        this(context, AccountTypeManager.getInstance(context));
    }

    @VisibleForTesting
    ContactEditorUtils(Context context, AccountTypeManager accountTypes) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mAccountTypes = accountTypes;
    }

    public static synchronized ContactEditorUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ContactEditorUtils(context);
        }
        return sInstance;
    }

    void cleanupForTest() {
        mPrefs.edit().remove(KEY_DEFAULT_ACCOUNT).remove(KEY_KNOWN_ACCOUNTS)
                .remove(KEY_ANYTHING_SAVED).apply();
    }

    private List<AccountWithDataSet> getWritableAccounts() {
        return mAccountTypes.getAccounts(true);
    }

    /**
     * @return true if it's the first launch and {@link #saveDefaultAndAllAccounts} has never
     *     been called.
     */
    private boolean isFirstLaunch() {
        return !mPrefs.getBoolean(KEY_ANYTHING_SAVED, false);
    }

    /**
     * Saves all writable accounts and the default account, which can later be obtained
     * with {@link #getDefaultAccount}.
     *
     * This should be called when saving a newly created contact.
     *
     * @param defaultAccount the account used to save a newly created contact.  Or pass {@code null}
     *     If the user selected "local only".
     */
    @NeededForTesting
    public void saveDefaultAndAllAccounts(AccountWithDataSet defaultAccount) {
        mPrefs.edit()
                .putBoolean(KEY_ANYTHING_SAVED, true)
                .putString(
                        KEY_KNOWN_ACCOUNTS,AccountWithDataSet.stringifyList(getWritableAccounts()))
                .putString(KEY_DEFAULT_ACCOUNT,
                        (defaultAccount == null) ? "" : defaultAccount.stringify())
                .apply();
    }

    /**
     * @return the default account saved with {@link #saveDefaultAndAllAccounts}.
     *
     * Note the {@code null} return value can mean either {@link #saveDefaultAndAllAccounts} has
     * never been called, or {@code null} was passed to {@link #saveDefaultAndAllAccounts} --
     * i.e. the user selected "local only".
     *
     * Also note that the returned account may have been removed already.
     */
    @NeededForTesting
    public AccountWithDataSet getDefaultAccount() {
        final String saved = mPrefs.getString(KEY_DEFAULT_ACCOUNT, null);
        if (TextUtils.isEmpty(saved)) {
            return null;
        }
        return AccountWithDataSet.unstringify(saved);
    }

    /**
     * @return true if an account still exists.  {@code null} is considered "local only" here,
     *    so it's valid too.
     */
    @VisibleForTesting
    boolean isValidAccount(AccountWithDataSet account) {
        if (account == null) {
            return true; // It's "local only" account, which is valid.
        }
        return getWritableAccounts().contains(account);
    }

    /**
     * @return saved known accounts, or an empty list if none has been saved yet.
     */
    @VisibleForTesting
    List<AccountWithDataSet> getSavedAccounts() {
        final String saved = mPrefs.getString(KEY_KNOWN_ACCOUNTS, null);
        if (TextUtils.isEmpty(saved)) {
            return EMPTY_ACCOUNTS;
        }
        return AccountWithDataSet.unstringifyList(saved);
    }

    /**
     * @return true if the contact editor should show the "accounts changed" notification, that is:
     * - If it's the first launch.
     * - Or, if an account has been added.
     * - Or, if the default account has been removed.
     *
     * Note if this method returns {@code false}, the caller can safely assume that
     * {@link #getDefaultAccount} will return a valid account.  (Either an account which still
     * exists, or {@code null} which should be interpreted as "local only".)
     */
    @NeededForTesting
    public boolean shouldShowAccountChangedNotification() {
        if (isFirstLaunch()) {
            return true;
        }

        // Account added?
        final List<AccountWithDataSet> savedAccounts = getSavedAccounts();
        for (AccountWithDataSet account : getWritableAccounts()) {
            if (!savedAccounts.contains(account)) {
                return true; // New account found.
            }
        }

        // Does default account still exist?
        if (!isValidAccount(getDefaultAccount())) {
            return true;
        }

        // All good.
        return false;
    }

    @VisibleForTesting
    String[] getWritableAccountTypeStrings() {
        final Set<String> types = Sets.newHashSet();
        for (AccountType type : mAccountTypes.getAccountTypes(true)) {
            types.add(type.accountType);
        }
        return types.toArray(new String[types.size()]);
    }

    /**
     * Create an {@link Intent} to start "add new account" setup wizard.  Selectable account
     * types will be limited to ones that supports editing contacts.
     *
     * Use {@link Activity#startActivityForResult} or
     * {@link android.app.Fragment#startActivityForResult} to start the wizard, and
     * {@link Activity#onActivityResult} or {@link android.app.Fragment#onActivityResult} to
     * get the result.
     */
    @NeededForTesting
    public Intent createAddWritableAccountIntent() {
        return AccountManager.newChooseAccountIntent(
                null, // selectedAccount
                new ArrayList<Account>(), // allowableAccounts
                getWritableAccountTypeStrings(), // allowableAccountTypes
                false, // alwaysPromptForAccount
                null, // descriptionOverrideText
                null, // addAccountAuthTokenType
                null, // addAccountRequiredFeatures
                null // addAccountOptions
                );
    }

    /**
     * Parses a result from {@link #createAddWritableAccountIntent} and returns the created
     * {@link Account}, or null if the user has canceled the wizard.  Pass the {@code resultCode}
     * and {@code data} parameters passed to {@link Activity#onActivityResult} or
     * {@link android.app.Fragment#onActivityResult}.
     *
     * Note although the return type is {@link AccountWithDataSet}, return values from this method
     * will never have {@link AccountWithDataSet#dataSet} set, as there's no way to create an
     * extension package account from setup wizard.
     */
    @NeededForTesting
    public AccountWithDataSet getCreatedAccount(int resultCode, Intent resultData) {
        // Javadoc doesn't say anything about resultCode but that the data intent will be non null
        // on success.
        if (resultData == null) return null;

        final String accountType = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
        final String accountName = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

        // Just in case
        if (TextUtils.isEmpty(accountType) || TextUtils.isEmpty(accountName)) return null;

        return new AccountWithDataSet(accountName, accountType, null);
    }
}

