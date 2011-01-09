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
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Singleton holder for all parsed {@link AccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public class AccountTypeManager extends BroadcastReceiver
        implements OnAccountsUpdateListener, SyncStatusObserver {
    private static final String TAG = "ContactAccountTypes";

    private Context mContext;
    private AccountManager mAccountManager;

    private AccountType mFallbackAccountType = new FallbackAccountType();

    private ArrayList<Account> mAccounts = Lists.newArrayList();
    private ArrayList<Account> mWritableAccounts = Lists.newArrayList();
    private HashMap<String, AccountType> mAccountTypes = Maps.newHashMap();

    private static final int MESSAGE_LOAD_DATA = 0;
    private static final int MESSAGE_PROCESS_BROADCAST_INTENT = 1;

    private HandlerThread mListenerThread;
    private Handler mListenerHandler;

    /* A latch that ensures that asynchronous initialization completes before data is used */
    private volatile CountDownLatch mInitializationLatch = new CountDownLatch(1);

    private static AccountTypeManager sInstance = null;

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
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static synchronized AccountTypeManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountTypeManager(context.getApplicationContext());
        }
        return sInstance;
    }

    public static void injectAccountTypes(AccountTypeManager injectedAccountTypes) {
        sInstance = injectedAccountTypes;
    }

    /**
     * Internal constructor that only performs initial parsing.
     */
    private AccountTypeManager(Context context) {
        mContext = context;
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
        mContext.registerReceiver(this, filter);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(this, sdFilter);

        // Request updates when locale is changed so that the order of each field will
        // be able to be changed on the locale change.
        filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(this, filter);

        mAccountManager.addOnAccountsUpdatedListener(this, mListenerHandler, false);

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);

        mListenerHandler.sendEmptyMessage(MESSAGE_LOAD_DATA);
    }

    /** @hide exposed for unit tests */
    public AccountTypeManager(AccountType... accountTypes) {
        for (AccountType accountType : accountTypes) {
            mAccountTypes.put(accountType.accountType, accountType);
        }
        mInitializationLatch = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Message msg = mListenerHandler.obtainMessage(MESSAGE_PROCESS_BROADCAST_INTENT, intent);
        mListenerHandler.sendMessage(msg);
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
     * Loads account list and corresponding account types. Always called on a
     * background thread.
     */
    protected void loadAccountsInBackground() {
        long startTime = SystemClock.currentThreadTimeMillis();

        HashMap<String, AccountType> accountTypes = Maps.newHashMap();
        ArrayList<Account> allAccounts = Lists.newArrayList();
        ArrayList<Account> writableAccounts = Lists.newArrayList();

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
                    accountType = new ExternalAccountType(mContext, auth.packageName);
                    accountType.readOnly = !sync.supportsUploading();
                }

                accountType.accountType = auth.type;
                accountType.titleRes = auth.labelId;
                accountType.iconRes = auth.iconId;

                accountTypes.put(accountType.accountType, accountType);
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
                final AccountType accountType = accountTypes.get(account.type);
                if (accountType != null) {
                    allAccounts.add(account);
                    if (!accountType.readOnly) {
                        writableAccounts.add(account);
                    }
                }
            }
        }

        Collections.sort(allAccounts, ACCOUNT_COMPARATOR);
        Collections.sort(writableAccounts, ACCOUNT_COMPARATOR);

        // The UI will need a phone number formatter.  We can preload meta data for the
        // current locale to prevent a delay later on.
        PhoneNumberUtil.getInstance().getAsYouTypeFormatter(Locale.getDefault().getCountry());

        long endTime = SystemClock.currentThreadTimeMillis();

        synchronized (this) {
            mAccountTypes = accountTypes;
            mAccounts = allAccounts;
            mWritableAccounts = writableAccounts;
        }

        Log.i(TAG, "Loaded meta-data for " + mAccountTypes.size() + " account types, "
                + mAccounts.size() + " accounts in " + (endTime - startTime) + "ms");

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
        return null;
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
     * direct match found, we try searching {@link #mFallbackAccountType}.
     * When fourceRefresh is set to true, cache is refreshed and inflation of each
     * EditField will occur.
     */
    public DataKind getKindOrFallback(String accountType, String mimeType, Context context) {
        ensureAccountsLoaded();
        DataKind kind = null;

        // Try finding account type and kind matching request
        final AccountType type = mAccountTypes.get(accountType);
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
     * Return {@link AccountType} for the given account type.
     */
    public AccountType getAccountType(String accountType) {
        ensureAccountsLoaded();
        synchronized (this) {
            AccountType type = mAccountTypes.get(accountType);
            return type != null ? type : mFallbackAccountType;
        }
    }
}
