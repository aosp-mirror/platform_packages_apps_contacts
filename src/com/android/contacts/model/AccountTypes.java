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

import com.android.contacts.model.BaseAccountType.DataKind;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;

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
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Singleton holder for all parsed {@link BaseAccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public class AccountTypes extends BroadcastReceiver implements OnAccountsUpdateListener {
    private static final String TAG = "AccountTypes";

    private Context mContext;
    private Context mApplicationContext;
    private AccountManager mAccountManager;

    private BaseAccountType mFallbackSource = null;

    private HashMap<String, BaseAccountType> mSources = Maps.newHashMap();
    private HashSet<String> mKnownPackages = Sets.newHashSet();

    private static SoftReference<AccountTypes> sInstance = null;

    /**
     * Requests the singleton instance of {@link AccountTypes} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static synchronized AccountTypes getInstance(Context context) {
        AccountTypes sources = sInstance == null ? null : sInstance.get();
        if (sources == null) {
            sources = new AccountTypes(context);
            sInstance = new SoftReference<AccountTypes>(sources);
        }
        return sources;
    }

    /**
     * Internal constructor that only performs initial parsing.
     */
    private AccountTypes(Context context) {
        mContext = context;
        mApplicationContext = context.getApplicationContext();
        mAccountManager = AccountManager.get(mApplicationContext);

        // Create fallback contacts source for on-phone contacts
        mFallbackSource = new FallbackAccountType();

        queryAccounts();

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

        mAccountManager.addOnAccountsUpdatedListener(this, null, false);
    }

    /** @hide exposed for unit tests */
    public AccountTypes(BaseAccountType... sources) {
        for (BaseAccountType source : sources) {
            addSource(source);
        }
    }

    protected void addSource(BaseAccountType source) {
        mSources.put(source.accountType, source);
        mKnownPackages.add(source.resPackageName);
    }

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
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
                        queryAccounts();
                    }
                }
            }
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            invalidateAllCache();
        }
    }

    protected void invalidateCache(String packageName) {
        for (BaseAccountType source : mSources.values()) {
            if (TextUtils.equals(packageName, source.resPackageName)) {
                // Invalidate any cache for the changed package
                source.invalidateCache();
            }
        }
    }

    protected void invalidateAllCache() {
        mFallbackSource.invalidateCache();
        for (BaseAccountType source : mSources.values()) {
            source.invalidateCache();
        }
    }

    /** {@inheritDoc} */
    public void onAccountsUpdated(Account[] accounts) {
        // Refresh to catch any changed accounts
        queryAccounts();
    }

    /**
     * Loads all {@link AuthenticatorDescription} known by the {@link AccountManager} on the system.
     */
    protected synchronized void queryAccounts() {
        mSources.clear();
        mKnownPackages.clear();

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

                BaseAccountType source;
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
     * Return list of all known, writable {@link BaseAccountType}. AccountTypes
     * returned may require inflation before they can be used.
     */
    public ArrayList<Account> getAccounts(boolean writableOnly) {
        final AccountManager am = mAccountManager;
        final Account[] accounts = am.getAccounts();
        final ArrayList<Account> matching = Lists.newArrayList();
        final IContentService cs = ContentResolver.getContentService();

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
            Log.d(TAG, String.format("found account (name: %s, type: %s, syncable: %b",
                    account.name, account.type, syncable));
            if (syncable) {
                // Ensure we have details loaded for each account
                final BaseAccountType accountType = getInflatedSource(account.type,
                        BaseAccountType.LEVEL_SUMMARY);
                final boolean hasContacts = accountType != null;
                final boolean matchesWritable =
                    (!writableOnly || (writableOnly && !accountType.readOnly));
                if (hasContacts && matchesWritable) {
                    matching.add(account);
                }
            }
        }
        return matching;
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link BaseAccountType#accountType} and {@link DataKind#mimeType}. If no
     * direct match found, we try searching {@link #mFallbackSource}.
     * When fourceRefresh is set to true, cache is refreshed and inflation of each
     * EditField will occur.
     */
    public DataKind getKindOrFallback(String accountType, String mimeType, Context context,
            int inflateLevel) {
        DataKind kind = null;

        // Try finding source and kind matching request
        final BaseAccountType source = mSources.get(accountType);
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
     * Return {@link BaseAccountType} for the given account type.
     */
    public BaseAccountType getInflatedSource(String accountType, int inflateLevel) {
        // Try finding specific source, otherwise use fallback
        BaseAccountType source = mSources.get(accountType);
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
