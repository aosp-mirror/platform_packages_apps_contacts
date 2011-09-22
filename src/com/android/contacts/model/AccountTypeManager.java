/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.model;

import com.android.contacts.util.Constants;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.internal.util.Objects;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import com.google.common.annotations.VisibleForTesting;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.OnAccountsUpdateListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.TimingLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Singleton holder for all parsed {@link AccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public abstract class AccountTypeManager {
    static final String TAG = "AccountTypeManager";

    public static final String ACCOUNT_TYPE_SERVICE = "contactAccountTypes";

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static AccountTypeManager getInstance(Context context) {
        context = context.getApplicationContext();
        AccountTypeManager service =
                (AccountTypeManager) context.getSystemService(ACCOUNT_TYPE_SERVICE);
        if (service == null) {
            service = createAccountTypeManager(context);
            Log.e(TAG, "No account type service in context: " + context);
        }
        return service;
    }

    public static synchronized AccountTypeManager createAccountTypeManager(Context context) {
        return new AccountTypeManagerImpl(context);
    }

    public abstract List<AccountWithDataSet> getAccounts(boolean writableOnly);

    public abstract AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet);

    public final AccountType getAccountType(String accountType, String dataSet) {
        return getAccountType(AccountTypeWithDataSet.get(accountType, dataSet));
    }

    public final AccountType getAccountTypeForAccount(AccountWithDataSet account) {
        return getAccountType(account.getAccountTypeWithDataSet());
    }

    /**
     * @return Unmodifiable map from {@link AccountTypeWithDataSet}s to {@link AccountType}s
     * which support the "invite" feature and have one or more account.
     */
    public abstract Map<AccountTypeWithDataSet, AccountType> getInvitableAccountTypes();

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    public DataKind getKindOrFallback(String accountType, String dataSet, String mimeType) {
        final AccountType type = getAccountType(accountType, dataSet);
        return type == null ? null : type.getKindForMimetype(mimeType);
    }
}

class AccountTypeManagerImpl extends AccountTypeManager
        implements OnAccountsUpdateListener, SyncStatusObserver {

    private Context mContext;
    private AccountManager mAccountManager;

    private AccountType mFallbackAccountType;

    private List<AccountWithDataSet> mAccounts = Lists.newArrayList();
    private List<AccountWithDataSet> mWritableAccounts = Lists.newArrayList();
    private Map<AccountTypeWithDataSet, AccountType> mAccountTypesWithDataSets = Maps.newHashMap();
    private Map<AccountTypeWithDataSet, AccountType> mInvitableAccountTypes =
            Collections.unmodifiableMap(new HashMap<AccountTypeWithDataSet, AccountType>());

    private static final int MESSAGE_LOAD_DATA = 0;
    private static final int MESSAGE_PROCESS_BROADCAST_INTENT = 1;

    private HandlerThread mListenerThread;
    private Handler mListenerHandler;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = mListenerHandler.obtainMessage(MESSAGE_PROCESS_BROADCAST_INTENT, intent);
            mListenerHandler.sendMessage(msg);
        }

    };

    /* A latch that ensures that asynchronous initialization completes before data is used */
    private volatile CountDownLatch mInitializationLatch = new CountDownLatch(1);

    private static final Comparator<Account> ACCOUNT_COMPARATOR = new Comparator<Account>() {
        @Override
        public int compare(Account a, Account b) {
            String aDataSet = null;
            String bDataSet = null;
            if (a instanceof AccountWithDataSet) {
                aDataSet = ((AccountWithDataSet) a).dataSet;
            }
            if (b instanceof AccountWithDataSet) {
                bDataSet = ((AccountWithDataSet) b).dataSet;
            }

            if (Objects.equal(a.name, b.name) && Objects.equal(a.type, b.type)
                    && Objects.equal(aDataSet, bDataSet)) {
                return 0;
            } else if (b.name == null || b.type == null) {
                return -1;
            } else if (a.name == null || a.type == null) {
                return 1;
            } else {
                int diff = a.name.compareTo(b.name);
                if (diff != 0) {
                    return diff;
                }
                diff = a.type.compareTo(b.type);
                if (diff != 0) {
                    return diff;
                }

                // Accounts without data sets get sorted before those that have them.
                if (aDataSet != null) {
                    return bDataSet == null ? 1 : aDataSet.compareTo(bDataSet);
                } else {
                    return -1;
                }
            }
        }
    };

    /**
     * Internal constructor that only performs initial parsing.
     */
    public AccountTypeManagerImpl(Context context) {
        mContext = context;
        mFallbackAccountType = new FallbackAccountType(context);

        mAccountManager = AccountManager.get(mContext);

        mListenerThread = new HandlerThread("AccountChangeListener");
        mListenerThread.start();
        mListenerHandler = new Handler(mListenerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_LOAD_DATA:
                        loadAccountsInBackground();
                        break;
                    case MESSAGE_PROCESS_BROADCAST_INTENT:
                        processBroadcastIntent((Intent) msg.obj);
                        break;
                }
            }
        };

        // Request updates when packages or accounts change
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mBroadcastReceiver, filter);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(mBroadcastReceiver, sdFilter);

        // Request updates when locale is changed so that the order of each field will
        // be able to be changed on the locale change.
        filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        mAccountManager.addOnAccountsUpdatedListener(this, mListenerHandler, false);

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);

        mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
    }

    @Override
    public void onStatusChanged(int which) {
        mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
    }

    public void processBroadcastIntent(Intent intent) {
        mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
    }

    /* This notification will arrive on the background thread */
    public void onAccountsUpdated(Account[] accounts) {
        // Refresh to catch any changed accounts
        loadAccountsInBackground();
    }

    /**
     * Returns instantly if accounts and account types have already been loaded.
     * Otherwise waits for the background thread to complete the loading.
     */
    void ensureAccountsLoaded() {
        CountDownLatch latch = mInitializationLatch;
        if (latch == null) {
            return;
        }
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Loads account list and corresponding account types (potentially with data sets). Always
     * called on a background thread.
     */
    protected void loadAccountsInBackground() {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "AccountTypeManager.loadAccountsInBackground start");
        }
        TimingLogger timings = new TimingLogger(TAG, "loadAccountsInBackground");
        final long startTime = SystemClock.currentThreadTimeMillis();
        final long startTimeWall = SystemClock.elapsedRealtime();

        // Account types, keyed off the account type and data set concatenation.
        Map<AccountTypeWithDataSet, AccountType> accountTypesByTypeAndDataSet = Maps.newHashMap();

        // The same AccountTypes, but keyed off {@link RawContacts#ACCOUNT_TYPE}.  Since there can
        // be multiple account types (with different data sets) for the same type of account, each
        // type string may have multiple AccountType entries.
        Map<String, List<AccountType>> accountTypesByType = Maps.newHashMap();

        List<AccountWithDataSet> allAccounts = Lists.newArrayList();
        List<AccountWithDataSet> writableAccounts = Lists.newArrayList();
        Set<String> extensionPackages = Sets.newHashSet();

        final AccountManager am = mAccountManager;
        final IContentService cs = ContentResolver.getContentService();

        try {
            final SyncAdapterType[] syncs = cs.getSyncAdapterTypes();
            final AuthenticatorDescription[] auths = am.getAuthenticatorTypes();

            // First process sync adapters to find any that provide contact data.
            for (SyncAdapterType sync : syncs) {
                if (!ContactsContract.AUTHORITY.equals(sync.authority)) {
                    // Skip sync adapters that don't provide contact data.
                    continue;
                }

                // Look for the formatting details provided by each sync
                // adapter, using the authenticator to find general resources.
                final String type = sync.accountType;
                final AuthenticatorDescription auth = findAuthenticator(auths, type);
                if (auth == null) {
                    Log.w(TAG, "No authenticator found for type=" + type + ", ignoring it.");
                    continue;
                }

                AccountType accountType;
                if (GoogleAccountType.ACCOUNT_TYPE.equals(type)) {
                    accountType = new GoogleAccountType(mContext, auth.packageName);
                } else if (ExchangeAccountType.ACCOUNT_TYPE.equals(type)) {
                    accountType = new ExchangeAccountType(mContext, auth.packageName);
                } else {
                    // TODO: use syncadapter package instead, since it provides resources
                    Log.d(TAG, "Registering external account type=" + type
                            + ", packageName=" + auth.packageName);
                    accountType = new ExternalAccountType(mContext, auth.packageName, false);
                    if (!((ExternalAccountType) accountType).isInitialized()) {
                        // Skip external account types that couldn't be initialized.
                        continue;
                    }
                }

                accountType.accountType = auth.type;
                accountType.titleRes = auth.labelId;
                accountType.iconRes = auth.iconId;

                addAccountType(accountType, accountTypesByTypeAndDataSet, accountTypesByType);

                // Check to see if the account type knows of any other non-sync-adapter packages
                // that may provide other data sets of contact data.
                extensionPackages.addAll(accountType.getExtensionPackageNames());
            }

            // If any extension packages were specified, process them as well.
            if (!extensionPackages.isEmpty()) {
                Log.d(TAG, "Registering " + extensionPackages.size() + " extension packages");
                for (String extensionPackage : extensionPackages) {
                    ExternalAccountType accountType =
                            new ExternalAccountType(mContext, extensionPackage, true);
                    if (!accountType.isInitialized()) {
                        // Skip external account types that couldn't be initialized.
                        continue;
                    }
                    if (!accountType.hasContactsMetadata()) {
                        Log.w(TAG, "Skipping extension package " + extensionPackage + " because"
                                + " it doesn't have the CONTACTS_STRUCTURE metadata");
                        continue;
                    }
                    if (TextUtils.isEmpty(accountType.accountType)) {
                        Log.w(TAG, "Skipping extension package " + extensionPackage + " because"
                                + " the CONTACTS_STRUCTURE metadata doesn't have the accountType"
                                + " attribute");
                        continue;
                    }
                    Log.d(TAG, "Registering extension package account type="
                            + accountType.accountType + ", dataSet=" + accountType.dataSet
                            + ", packageName=" + extensionPackage);

                    addAccountType(accountType, accountTypesByTypeAndDataSet, accountTypesByType);
                }
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Problem loading accounts: " + e.toString());
        }
        timings.addSplit("Loaded account types");

        // Map in accounts to associate the account names with each account type entry.
        Account[] accounts = mAccountManager.getAccounts();
        for (Account account : accounts) {
            boolean syncable = false;
            try {
                syncable = cs.getIsSyncable(account, ContactsContract.AUTHORITY) > 0;
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot obtain sync flag for account: " + account, e);
            }

            if (syncable) {
                List<AccountType> accountTypes = accountTypesByType.get(account.type);
                if (accountTypes != null) {
                    // Add an account-with-data-set entry for each account type that is
                    // authenticated by this account.
                    for (AccountType accountType : accountTypes) {
                        AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                                account.name, account.type, accountType.dataSet);
                        allAccounts.add(accountWithDataSet);
                        if (accountType.areContactsWritable()) {
                            writableAccounts.add(accountWithDataSet);
                        }
                    }
                }
            }
        }

        Collections.sort(allAccounts, ACCOUNT_COMPARATOR);
        Collections.sort(writableAccounts, ACCOUNT_COMPARATOR);

        timings.addSplit("Loaded accounts");

        synchronized (this) {
            mAccountTypesWithDataSets = accountTypesByTypeAndDataSet;
            mAccounts = allAccounts;
            mWritableAccounts = writableAccounts;
            mInvitableAccountTypes = findInvitableAccountTypes(
                    mContext, allAccounts, accountTypesByTypeAndDataSet);
        }

        timings.dumpToLog();
        final long endTimeWall = SystemClock.elapsedRealtime();
        final long endTime = SystemClock.currentThreadTimeMillis();

        Log.i(TAG, "Loaded meta-data for " + mAccountTypesWithDataSets.size() + " account types, "
                + mAccounts.size() + " accounts in " + (endTimeWall - startTimeWall) + "ms(wall) "
                + (endTime - startTime) + "ms(cpu)");

        if (mInitializationLatch != null) {
            mInitializationLatch.countDown();
            mInitializationLatch = null;
        }
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "AccountTypeManager.loadAccountsInBackground finish");
        }
    }

    // Bookkeeping method for tracking the known account types in the given maps.
    private void addAccountType(AccountType accountType,
            Map<AccountTypeWithDataSet, AccountType> accountTypesByTypeAndDataSet,
            Map<String, List<AccountType>> accountTypesByType) {
        accountTypesByTypeAndDataSet.put(accountType.getAccountTypeAndDataSet(), accountType);
        List<AccountType> accountsForType = accountTypesByType.get(accountType.accountType);
        if (accountsForType == null) {
            accountsForType = Lists.newArrayList();
        }
        accountsForType.add(accountType);
        accountTypesByType.put(accountType.accountType, accountsForType);
    }

    /**
     * Find a specific {@link AuthenticatorDescription} in the provided list
     * that matches the given account type.
     */
    protected static AuthenticatorDescription findAuthenticator(AuthenticatorDescription[] auths,
            String accountType) {
        for (AuthenticatorDescription auth : auths) {
            if (accountType.equals(auth.type)) {
                return auth;
            }
        }
        return null;
    }

    /**
     * Return list of all known, writable {@link AccountWithDataSet}'s.
     */
    @Override
    public List<AccountWithDataSet> getAccounts(boolean writableOnly) {
        ensureAccountsLoaded();
        return writableOnly ? mWritableAccounts : mAccounts;
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    @Override
    public DataKind getKindOrFallback(String accountType, String dataSet, String mimeType) {
        ensureAccountsLoaded();
        DataKind kind = null;

        // Try finding account type and kind matching request
        final AccountType type = mAccountTypesWithDataSets.get(
                AccountTypeWithDataSet.get(accountType, dataSet));
        if (type != null) {
            kind = type.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            // Nothing found, so try fallback as last resort
            kind = mFallbackAccountType.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            Log.w(TAG, "Unknown type=" + accountType + ", mime=" + mimeType);
        }

        return kind;
    }

    /**
     * Return {@link AccountType} for the given account type and data set.
     */
    @Override
    public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
        ensureAccountsLoaded();
        synchronized (this) {
            AccountType type = mAccountTypesWithDataSets.get(accountTypeWithDataSet);
            return type != null ? type : mFallbackAccountType;
        }
    }

    @Override
    public Map<AccountTypeWithDataSet, AccountType> getInvitableAccountTypes() {
        return mInvitableAccountTypes;
    }

    /**
     * Return all {@link AccountType}s with at least one account which supports "invite", i.e.
     * its {@link AccountType#getInviteContactActivityClassName()} is not empty.
     */
    @VisibleForTesting
    static Map<AccountTypeWithDataSet, AccountType> findInvitableAccountTypes(Context context,
            Collection<AccountWithDataSet> accounts,
            Map<AccountTypeWithDataSet, AccountType> accountTypesByTypeAndDataSet) {
        HashMap<AccountTypeWithDataSet, AccountType> result = Maps.newHashMap();
        for (AccountWithDataSet account : accounts) {
            AccountTypeWithDataSet accountTypeWithDataSet = account.getAccountTypeWithDataSet();
            AccountType type = accountTypesByTypeAndDataSet.get(accountTypeWithDataSet);
            if (type == null) continue; // just in case
            if (result.containsKey(accountTypeWithDataSet)) continue;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Type " + accountTypeWithDataSet
                        + " inviteClass=" + type.getInviteContactActivityClassName());
            }
            if (!TextUtils.isEmpty(type.getInviteContactActivityClassName())) {
                result.put(accountTypeWithDataSet, type);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
