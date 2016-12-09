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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.OnAccountsUpdateListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncAdapterType;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TimingLogger;

import com.android.contacts.Experiments;
import com.android.contacts.R;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountTypeWithDataSet;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.ExchangeAccountType;
import com.android.contacts.model.account.ExternalAccountType;
import com.android.contacts.model.account.FallbackAccountType;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contacts.model.account.SamsungAccountType;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DeviceLocalAccountTypeFactory;
import com.android.contactsbind.ObjectFactory;
import com.android.contactsbind.experiments.Flags;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nullable;

import static com.android.contacts.util.DeviceLocalAccountTypeFactory.Util.isLocalAccountType;

/**
 * Singleton holder for all parsed {@link AccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public abstract class AccountTypeManager {
    static final String TAG = "AccountTypeManager";

    private static final Object mInitializationLock = new Object();
    private static AccountTypeManager mAccountTypeManager;

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static AccountTypeManager getInstance(Context context) {
        if (!hasRequiredPermissions(context)) {
            // Hopefully any component that depends on the values returned by this class
            // will be restarted if the permissions change.
            return EMPTY;
        }
        synchronized (mInitializationLock) {
            if (mAccountTypeManager == null) {
                context = context.getApplicationContext();
                mAccountTypeManager = new AccountTypeManagerImpl(context,
                        ObjectFactory.getDeviceLocalAccountTypeFactory(context));
            }
        }
        return mAccountTypeManager;
    }

    /**
     * Set the instance of account type manager.  This is only for and should only be used by unit
     * tests.  While having this method is not ideal, it's simpler than the alternative of
     * holding this as a service in the ContactsApplication context class.
     *
     * @param mockManager The mock AccountTypeManager.
     */
    public static void setInstanceForTest(AccountTypeManager mockManager) {
        synchronized (mInitializationLock) {
            mAccountTypeManager = mockManager;
        }
    }

    private static final AccountTypeManager EMPTY = new AccountTypeManager() {
        @Override
        public List<AccountWithDataSet> getAccounts(boolean contactWritableOnly) {
            return Collections.emptyList();
        }

        @Override
        public List<AccountWithDataSet> getAccounts(Predicate<AccountWithDataSet> filter) {
            return Collections.emptyList();
        }

        @Override
        public List<AccountWithDataSet> getGroupWritableAccounts() {
            return Collections.emptyList();
        }

        @Override
        public Account getDefaultGoogleAccount() {
            return null;
        }

        @Override
        public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
            return null;
        }
    };

    /**
     * Returns the list of all accounts (if contactWritableOnly is false) or just the list of
     * contact writable accounts (if contactWritableOnly is true).
     */
    // TODO: Consider splitting this into getContactWritableAccounts() and getAllAccounts()
    public abstract List<AccountWithDataSet> getAccounts(boolean contactWritableOnly);

    public abstract List<AccountWithDataSet> getAccounts(Predicate<AccountWithDataSet> filter);

    /**
     * Returns the list of accounts that are group writable.
     */
    public abstract List<AccountWithDataSet> getGroupWritableAccounts();

    /**
     * Returns the default google account.
     */
    public abstract Account getDefaultGoogleAccount();

    static Account getDefaultGoogleAccount(AccountManager accountManager,
            SharedPreferences prefs, String defaultAccountKey) {
        // Get all the google accounts on the device
        final Account[] accounts = accountManager.getAccountsByType(
                GoogleAccountType.ACCOUNT_TYPE);
        if (accounts == null || accounts.length == 0) {
            return null;
        }

        // Get the default account from preferences
        final String defaultAccount = prefs.getString(defaultAccountKey, null);
        final AccountWithDataSet accountWithDataSet = defaultAccount == null ? null :
                AccountWithDataSet.unstringify(defaultAccount);

        // Look for an account matching the one from preferences
        if (accountWithDataSet != null) {
            for (int i = 0; i < accounts.length; i++) {
                if (TextUtils.equals(accountWithDataSet.name, accounts[i].name)
                        && TextUtils.equals(accountWithDataSet.type, accounts[i].type)) {
                    return accounts[i];
                }
            }
        }

        // Just return the first one
        return accounts[0];
    }

    public abstract AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet);

    public final AccountType getAccountType(String accountType, String dataSet) {
        return getAccountType(AccountTypeWithDataSet.get(accountType, dataSet));
    }

    public final AccountType getAccountTypeForAccount(AccountWithDataSet account) {
        if (account != null) {
            return getAccountType(account.getAccountTypeWithDataSet());
        }
        return getAccountType(null, null);
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    public DataKind getKindOrFallback(AccountType type, String mimeType) {
        return type == null ? null : type.getKindForMimetype(mimeType);
    }

    /**
     * @param contactWritableOnly if true, it only returns ones that support writing contacts.
     * @return true when this instance contains the given account.
     */
    public boolean contains(AccountWithDataSet account, boolean contactWritableOnly) {
        for (AccountWithDataSet account_2 : getAccounts(contactWritableOnly)) {
            if (account.equals(account_2)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasGoogleAccount() {
        return getDefaultGoogleAccount() != null;
    }

    /**
     * Sorts the accounts in-place such that defaultAccount is first in the list and the rest
     * of the accounts are ordered in manner that is useful for display purposes
     */
    public static void sortAccounts(AccountWithDataSet defaultAccount,
            List<AccountWithDataSet> accounts) {
        Collections.sort(accounts, new AccountComparator(defaultAccount));
    }

    private static boolean hasRequiredPermissions(Context context) {
        final boolean canGetAccounts = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
        final boolean canReadContacts = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        return canGetAccounts && canReadContacts;
    }

    public static Predicate<AccountWithDataSet> nonNullAccountFilter() {
        return new Predicate<AccountWithDataSet>() {
            @Override
            public boolean apply(@Nullable AccountWithDataSet account) {
                return account != null && account.name != null && account.type != null;
            }
        };
    }
}

class AccountComparator implements Comparator<AccountWithDataSet> {
    private AccountWithDataSet mDefaultAccount;

    public AccountComparator(AccountWithDataSet defaultAccount) {
        mDefaultAccount = defaultAccount;
    }

    @Override
    public int compare(AccountWithDataSet a, AccountWithDataSet b) {
        if (Objects.equal(a.name, b.name) && Objects.equal(a.type, b.type)
                && Objects.equal(a.dataSet, b.dataSet)) {
            return 0;
        } else if (b.name == null || b.type == null) {
            return -1;
        } else if (a.name == null || a.type == null) {
            return 1;
        } else if (isWritableGoogleAccount(a) && a.equals(mDefaultAccount)) {
            return -1;
        } else if (isWritableGoogleAccount(b) && b.equals(mDefaultAccount)) {
            return 1;
        } else if (isWritableGoogleAccount(a) && !isWritableGoogleAccount(b)) {
            return -1;
        } else if (isWritableGoogleAccount(b) && !isWritableGoogleAccount(a)) {
            return 1;
        } else {
            int diff = a.name.compareToIgnoreCase(b.name);
            if (diff != 0) {
                return diff;
            }
            diff = a.type.compareToIgnoreCase(b.type);
            if (diff != 0) {
                return diff;
            }

            // Accounts without data sets get sorted before those that have them.
            if (a.dataSet != null) {
                return b.dataSet == null ? 1 : a.dataSet.compareToIgnoreCase(b.dataSet);
            } else {
                return -1;
            }
        }
    }

    private static boolean isWritableGoogleAccount(AccountWithDataSet account) {
        return GoogleAccountType.ACCOUNT_TYPE.equals(account.type) && account.dataSet == null;
    }
}

class AccountTypeManagerImpl extends AccountTypeManager
        implements OnAccountsUpdateListener, SyncStatusObserver {

    private Context mContext;
    private AccountManager mAccountManager;
    private DeviceLocalAccountTypeFactory mDeviceLocalAccountTypeFactory;

    private AccountType mFallbackAccountType;

    private List<AccountWithDataSet> mAccounts = Lists.newArrayList();
    private List<AccountWithDataSet> mContactWritableAccounts = Lists.newArrayList();
    private List<AccountWithDataSet> mGroupWritableAccounts = Lists.newArrayList();
    private Map<AccountTypeWithDataSet, AccountType> mAccountTypesWithDataSets = Maps.newHashMap();

    private static final int MESSAGE_LOAD_DATA = 0;
    private static final int MESSAGE_PROCESS_BROADCAST_INTENT = 1;

    private HandlerThread mListenerThread;
    private Handler mListenerHandler;

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCheckFilterValidityRunnable = new Runnable () {
        @Override
        public void run() {
            ContactListFilterController.getInstance(mContext).checkFilterValidity(true);
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = mListenerHandler.obtainMessage(MESSAGE_PROCESS_BROADCAST_INTENT, intent);
            mListenerHandler.sendMessage(msg);
        }

    };

    /* A latch that ensures that asynchronous initialization completes before data is used */
    private volatile CountDownLatch mInitializationLatch = new CountDownLatch(1);

    /**
     * Internal constructor that only performs initial parsing.
     */
    public AccountTypeManagerImpl(Context context,
            DeviceLocalAccountTypeFactory deviceLocalAccountTypeFactory) {
        mContext = context;
        mFallbackAccountType = new FallbackAccountType(context);
        mDeviceLocalAccountTypeFactory = deviceLocalAccountTypeFactory;

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


        if (Flags.getInstance().getBoolean(Experiments.OEM_CP2_DEVICE_ACCOUNT_DETECTION_ENABLED)) {
            // Observe changes to RAW_CONTACTS so that we will update the list of "Device" accounts
            // if a new device contact is added.
            mContext.getContentResolver().registerContentObserver(
                    ContactsContract.RawContacts.CONTENT_URI, /* notifyDescendents */ true,
                    new ContentObserver(mListenerHandler) {
                        @Override
                        public boolean deliverSelfNotifications() {
                            return true;
                        }

                        @Override
                        public void onChange(boolean selfChange) {
                            mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
                        }

                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
                        }
                    });
        }

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
        final Map<AccountTypeWithDataSet, AccountType> accountTypesByTypeAndDataSet =
                Maps.newHashMap();

        // The same AccountTypes, but keyed off {@link RawContacts#ACCOUNT_TYPE}.  Since there can
        // be multiple account types (with different data sets) for the same type of account, each
        // type string may have multiple AccountType entries.
        final Map<String, List<AccountType>> accountTypesByType = Maps.newHashMap();

        final List<AccountWithDataSet> allAccounts = Lists.newArrayList();
        final List<AccountWithDataSet> contactWritableAccounts = Lists.newArrayList();
        final List<AccountWithDataSet> groupWritableAccounts = Lists.newArrayList();
        final Set<String> extensionPackages = Sets.newHashSet();

        final AccountManager am = mAccountManager;

        final SyncAdapterType[] syncs = ContentResolver.getSyncAdapterTypes();
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
            } else if (ExchangeAccountType.isExchangeType(type)) {
                accountType = new ExchangeAccountType(mContext, auth.packageName, type);
            } else if (SamsungAccountType.isSamsungAccountType(mContext, type,
                    auth.packageName)) {
                accountType = new SamsungAccountType(mContext, auth.packageName, type);
            } else if (!ExternalAccountType.hasContactsXml(mContext, auth.packageName)
                    && isLocalAccountType(mDeviceLocalAccountTypeFactory, type)) {
                // This will be loaded by the DeviceLocalAccountLocator so don't try to create an
                // ExternalAccountType for it.
                continue;
            } else {
                Log.d(TAG, "Registering external account type=" + type
                        + ", packageName=" + auth.packageName);
                accountType = new ExternalAccountType(mContext, auth.packageName, false);
            }
            if (!accountType.isInitialized()) {
                if (accountType.isEmbedded()) {
                    throw new IllegalStateException("Problem initializing embedded type "
                            + accountType.getClass().getCanonicalName());
                } else {
                    // Skip external account types that couldn't be initialized.
                    continue;
                }
            }

            accountType.initializeFieldsFromAuthenticator(auth);

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
        timings.addSplit("Loaded account types");

        boolean foundWritableGoogleAccount = false;
        // Map in accounts to associate the account names with each account type entry.
        Account[] accounts = mAccountManager.getAccounts();
        for (Account account : accounts) {
            boolean syncable =
                ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) > 0;

            if (syncable || GoogleAccountType.ACCOUNT_TYPE.equals(account.type)) {
                List<AccountType> accountTypes = accountTypesByType.get(account.type);
                if (accountTypes != null) {
                    // Add an account-with-data-set entry for each account type that is
                    // authenticated by this account.
                    for (AccountType accountType : accountTypes) {
                        AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                                account.name, account.type, accountType.dataSet);
                        allAccounts.add(accountWithDataSet);
                        if (accountType.areContactsWritable()) {
                            contactWritableAccounts.add(accountWithDataSet);
                            if (GoogleAccountType.ACCOUNT_TYPE.equals(account.type)
                                    && accountWithDataSet.dataSet == null) {
                                foundWritableGoogleAccount = true;
                            }

                            if (accountType.isGroupMembershipEditable()) {
                                groupWritableAccounts.add(accountWithDataSet);
                            }
                        }
                    }
                }
            }
        }

        final DeviceLocalAccountLocator deviceAccountLocator = DeviceLocalAccountLocator
                .create(mContext, allAccounts);
        final List<AccountWithDataSet> localAccounts = deviceAccountLocator
                .getDeviceLocalAccounts();
        allAccounts.addAll(localAccounts);

        for (AccountWithDataSet localAccount : localAccounts) {
            // Prefer a known type if it exists. This covers the case that a local account has an
            // authenticator with a valid contacts.xml
            AccountType localAccountType = accountTypesByTypeAndDataSet.get(
                    localAccount.getAccountTypeWithDataSet());
            if (localAccountType == null) {
                localAccountType = mDeviceLocalAccountTypeFactory.getAccountType(localAccount.type);
            }
            accountTypesByTypeAndDataSet.put(localAccount.getAccountTypeWithDataSet(),
                    localAccountType);

            // Skip the null account if there is a Google account available. This is done because
            // the Google account's sync adapter will automatically move accounts in the "null"
            // account.  Hence, it would be confusing to still show it as an available writable
            // account since contacts that were saved to it would magically change accounts when the
            // sync adapter runs.
            if (foundWritableGoogleAccount && localAccount.type == null) {
                continue;
            }
            if (localAccountType.areContactsWritable()) {
                contactWritableAccounts.add(localAccount);

                if (localAccountType.isGroupMembershipEditable()) {
                    groupWritableAccounts.add(localAccount);
                }
            }
        }

        final AccountComparator accountComparator = new AccountComparator(null);
        Collections.sort(allAccounts, accountComparator);
        Collections.sort(contactWritableAccounts, accountComparator);
        Collections.sort(groupWritableAccounts, accountComparator);

        timings.addSplit("Loaded accounts");

        synchronized (this) {
            mAccountTypesWithDataSets = accountTypesByTypeAndDataSet;
            mAccounts = allAccounts;
            mContactWritableAccounts = contactWritableAccounts;
            mGroupWritableAccounts = groupWritableAccounts;
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

        // Check filter validity since filter may become obsolete after account update. It must be
        // done from UI thread.
        mMainThreadHandler.post(mCheckFilterValidityRunnable);
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
     * Return list of all known or contact writable {@link AccountWithDataSet}'s.
     * {@param contactWritableOnly} whether to restrict to contact writable accounts only
     */
    @Override
    public List<AccountWithDataSet> getAccounts(boolean contactWritableOnly) {
        ensureAccountsLoaded();
        return Lists.newArrayList(contactWritableOnly ? mContactWritableAccounts : mAccounts);
    }

    @Override
    public List<AccountWithDataSet> getAccounts(Predicate<AccountWithDataSet> filter) {
        return new ArrayList<>(Collections2.filter(mAccounts, filter));
    }

    /**
     * Return the list of all known, group writable {@link AccountWithDataSet}'s.
     */
    public List<AccountWithDataSet> getGroupWritableAccounts() {
        ensureAccountsLoaded();
        return Lists.newArrayList(mGroupWritableAccounts);
    }

    /**
     * Returns the default google account specified in preferences, the first google account
     * if it is not specified in preferences or is no longer on the device, and null otherwise.
     */
    @Override
    public Account getDefaultGoogleAccount() {
        final AccountManager accountManager = AccountManager.get(mContext);
        final SharedPreferences sharedPreferences =
                mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        final String defaultAccountKey =
                mContext.getResources().getString(R.string.contact_editor_default_account_key);
        return getDefaultGoogleAccount(accountManager, sharedPreferences, defaultAccountKey);
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    @Override
    public DataKind getKindOrFallback(AccountType type, String mimeType) {
        ensureAccountsLoaded();
        DataKind kind = null;

        // Try finding account type and kind matching request
        if (type != null) {
            kind = type.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            // Nothing found, so try fallback as last resort
            kind = mFallbackAccountType.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unknown type=" + type + ", mime=" + mimeType);
            }
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
}
