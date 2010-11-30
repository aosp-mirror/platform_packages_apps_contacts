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

import com.android.contacts.model.AccountType.DataKind;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

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
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Singleton holder for all parsed {@link AccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public class AccountTypes extends BroadcastReceiver
        implements OnAccountsUpdateListener, SyncStatusObserver {
    private static final String TAG = "AccountTypes";

    private Context mContext;
    private Context mApplicationContext;
    private AccountManager mAccountManager;

    private AccountType mFallbackSource = null;

    private ArrayList<Account> mAccounts = Lists.newArrayList();
    private ArrayList<Account> mWritableAccounts = Lists.newArrayList();
    private HashMap<String, AccountType> mSources = Maps.newHashMap();
    private HashSet<String> mKnownPackages = Sets.newHashSet();

    private static final int MESSAGE_LOAD_DATA = 0;
    private static final int MESSAGE_PROCESS_BROADCAST_INTENT = 1;
    private static final int MESSAGE_SYNC_STATUS_CHANGED = 2;

    private HandlerThread mListenerThread;
    private Handler mListenerHandler;

    /* A latch that ensures that asynchronous initialization completes before data is used */
    private CountDownLatch mInitializationLatch;

    private static AccountTypes sInstance = null;

    private static final Comparator<Account> ACCOUNT_COMPARATOR = new Comparator<Account>() {

        @Override
        public int compare(Account account1, Account account2) {
            int diff = account1.name.compareTo(account2.name);
            if (diff != 0) {
                return diff;
            }
            return account1.type.compareTo(account2.type);
        }
    };

    /**
     * Requests the singleton instance of {@link AccountTypes} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static synchronized AccountTypes getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountTypes(context);
        }
        return sInstance;
    }

    /**
     * Internal constructor that only performs initial parsing.
     */
    private AccountTypes(Context context) {
        mInitializationLatch = new CountDownLatch(1);

        mContext = context;
        mApplicationContext = context.getApplicationContext();
        mAccountManager = AccountManager.get(mApplicationContext);

        // Create fallback contacts source for on-phone contacts
        mFallbackSource = new FallbackAccountType();

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
                    case MESSAGE_SYNC_STATUS_CHANGED:
                        loadAccountsInBackground();
                        break;
                }
            }
        };

        // Request updates when packages or accounts change
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mApplicationContext.registerReceiver(this, filter);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mApplicationContext.registerReceiver(this, sdFilter);

        // Request updates when locale is changed so that the order of each field will
        // be able to be changed on the locale change.
        filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mApplicationContext.registerReceiver(this, filter);

        mAccountManager.addOnAccountsUpdatedListener(this, mListenerHandler, false);

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);

        mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
    }

    /** @hide exposed for unit tests */
    public AccountTypes(AccountType... sources) {
        for (AccountType source : sources) {
            addSource(source);
        }
    }

    protected void addSource(AccountType source) {
        mSources.put(source.accountType, source);
        mKnownPackages.add(source.resPackageName);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Message msg = mListenerHandler.obtainMessage(MESSAGE_PROCESS_BROADCAST_INTENT, intent);
        mListenerHandler.sendMessage(msg);
    }

    @Override
    public void onStatusChanged(int which) {
        mListenerHandler.sendEmptyMessage(MESSAGE_SYNC_STATUS_CHANGED);
    }

    public void processBroadcastIntent(Intent intent) {
        final String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_CHANGED.equals(action) ||
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action) ||
                Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
            String[] pkgList = null;
            // Handle applications on sdcard.
            if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action) ||
                    Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            } else {
                final String packageName = intent.getData().getSchemeSpecificPart();
                pkgList = new String[] { packageName };
            }
            if (pkgList != null) {
                for (String packageName : pkgList) {
                    final boolean knownPackage = mKnownPackages.contains(packageName);
                    if (knownPackage) {
                        // Invalidate cache of existing source
                        invalidateCache(packageName);
                    } else {
                        // Unknown source, so reload from scratch
                        loadAccountsInBackground();
                    }
                }
            }
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            invalidateAllCache();
        }
    }

    protected void invalidateCache(String packageName) {
        for (AccountType source : mSources.values()) {
            if (TextUtils.equals(packageName, source.resPackageName)) {
                // Invalidate any cache for the changed package
                source.invalidateCache();
            }
        }
    }

    protected void invalidateAllCache() {
        mFallbackSource.invalidateCache();
        for (AccountType source : mSources.values()) {
            source.invalidateCache();
        }
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
     * Loads account list and corresponding account types. Always called on a
     * background thread.
     */
    protected void loadAccountsInBackground() {
        mSources.clear();
        mKnownPackages.clear();
        mAccounts.clear();
        mWritableAccounts.clear();

        final AccountManager am = mAccountManager;
        final IContentService cs = ContentResolver.getContentService();

        try {
            final SyncAdapterType[] syncs = cs.getSyncAdapterTypes();
            final AuthenticatorDescription[] auths = am.getAuthenticatorTypes();

            for (SyncAdapterType sync : syncs) {
                if (!ContactsContract.AUTHORITY.equals(sync.authority)) {
                    // Skip sync adapters that don't provide contact data.
                    continue;
                }

                // Look for the formatting details provided by each sync
                // adapter, using the authenticator to find general resources.
                final String accountType = sync.accountType;
                final AuthenticatorDescription auth = findAuthenticator(auths, accountType);

                AccountType source;
                if (GoogleAccountType.ACCOUNT_TYPE.equals(accountType)) {
                    source = new GoogleAccountType(auth.packageName);
                } else if (ExchangeAccountType.ACCOUNT_TYPE.equals(accountType)) {
                    source = new ExchangeAccountType(auth.packageName);
                } else {
                    // TODO: use syncadapter package instead, since it provides resources
                    Log.d(TAG, "Creating external source for type=" + accountType
                            + ", packageName=" + auth.packageName);
                    source = new ExternalAccountType(auth.packageName);
                    source.readOnly = !sync.supportsUploading();
                }

                source.accountType = auth.type;
                source.titleRes = auth.labelId;
                source.iconRes = auth.iconId;

                addSource(source);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Problem loading accounts: " + e.toString());
        }

        Account[] accounts = mAccountManager.getAccounts();
        for (Account account : accounts) {
            boolean syncable = false;
            try {
                int isSyncable = cs.getIsSyncable(account, ContactsContract.AUTHORITY);
                if (isSyncable > 0) {
                    syncable = true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot obtain sync flag for account: " + account, e);
            }

            if (syncable) {
                // Ensure we have details loaded for each account
                final AccountType accountType = getAccountType(
                        account.type, AccountType.LEVEL_SUMMARY);
                if (accountType != null) {
                    mAccounts.add(account);
                    if (!accountType.readOnly) {
                        mWritableAccounts.add(account);
                    }
                }
            }
        }

        Collections.sort(mAccounts, ACCOUNT_COMPARATOR);
        Collections.sort(mWritableAccounts, ACCOUNT_COMPARATOR);

        // The UI will need a phone number formatter.  We can preload meta data for the
        // current locale to prevent a delay later on.
        PhoneNumberUtil.getInstance().getAsYouTypeFormatter(Locale.getDefault().getCountry());

        if (mInitializationLatch != null) {
            mInitializationLatch.countDown();
            mInitializationLatch = null;
        }
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
        throw new IllegalStateException("Couldn't find authenticator for specific account type");
    }

    /**
     * Return list of all known, writable {@link Account}'s.
     */
    public ArrayList<Account> getAccounts(boolean writableOnly) {
        ensureAccountsLoaded();
        return writableOnly ? mWritableAccounts : mAccounts;
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType} and {@link DataKind#mimeType}. If no
     * direct match found, we try searching {@link #mFallbackSource}.
     * When fourceRefresh is set to true, cache is refreshed and inflation of each
     * EditField will occur.
     */
    public DataKind getKindOrFallback(String accountType, String mimeType, Context context,
            int inflateLevel) {
        ensureAccountsLoaded();
        DataKind kind = null;

        // Try finding source and kind matching request
        final AccountType source = mSources.get(accountType);
        if (source != null) {
            source.ensureInflated(context, inflateLevel);
            kind = source.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            // Nothing found, so try fallback as last resort
            mFallbackSource.ensureInflated(context, inflateLevel);
            kind = mFallbackSource.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            Log.w(TAG, "Unknown type=" + accountType + ", mime=" + mimeType);
        }

        return kind;
    }

    /**
     * Return {@link AccountType} for the given account type.
     */
    public AccountType getInflatedSource(String accountType, int inflateLevel) {
        ensureAccountsLoaded();
        return getAccountType(accountType, inflateLevel);
    }

    AccountType getAccountType(String accountType, int inflateLevel) {
        // Try finding specific source, otherwise use fallback
        AccountType source = mSources.get(accountType);
        if (source == null) source = mFallbackSource;

        if (source.isInflated(inflateLevel)) {
            // Already inflated, so return directly
            return source;
        } else {
            // Not inflated, but requested that we force-inflate
            source.ensureInflated(mContext, inflateLevel);
            return source;
        }
    }
}
