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
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Singleton holder for all parsed {@link ContactsSource} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public class Sources {
    private static final String TAG = "Sources";

    public static final String ACCOUNT_TYPE_FALLBACK = HardCodedSources.ACCOUNT_TYPE_GOOGLE;

    private Context mContext;

    private HashMap<String, ContactsSource> mSources = new HashMap<String, ContactsSource>();

    private static SoftReference<Sources> sInstance = null;

    /**
     * Requests the singleton instance of {@link Sources} with data bound from
     * the available authenticators. This method blocks until its interaction
     * with {@link AccountManager} is finished, so don't call from a UI thread.
     */
    public static synchronized Sources getInstance(Context context) {
        Sources sources = sInstance == null ? null : sInstance.get();
        if (sources == null) {
            sources = new Sources(context);
            sInstance = new SoftReference<Sources>(sources);
        }
        return sources;
    }

    /**
     * Internal constructor that only performs initial parsing. Obtain a
     * {@link android.provider.ContactsContract.RawContacts#ACCOUNT_TYPE}.
     */
    private Sources(Context context) {
        mContext = context;
        loadAccounts();
    }

    /**
     * Blocking call to load all {@link AuthenticatorDescription} known by the
     * {@link AccountManager} on the system.
     */
    protected void loadAccounts() {
        mSources.clear();

        final AccountManager am = AccountManager.get(mContext);
        final IContentService cs = ContentResolver.getContentService();

        try {
            final SyncAdapterType[] syncs = cs.getSyncAdapterTypes();
            final AuthenticatorDescription[] auths = am.getAuthenticatorTypes();

            for (SyncAdapterType sync : syncs) {
                if (ContactsContract.AUTHORITY.equals(sync.authority)) {
                    // Skip sync adapters that don't provide contact data.
                    continue;
                }

                // Look for the formatting details provided by each sync
                // adapter, using the authenticator to find general resources.
                final String accountType = sync.accountType;
                final AuthenticatorDescription auth = findAuthenticator(auths, accountType);

                final ContactsSource source = new ContactsSource();
                source.accountType = auth.type;
                // TODO: use syncadapter package instead, since it provides resources
                source.resPackageName = auth.packageName;
                source.titleRes = auth.labelId;
                source.iconRes = auth.iconId;

                mSources.put(accountType, source);
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
     * Return list of all known, writable {@link ContactsSource}. Sources
     * returned may require inflation before they can be used.
     */
    public ArrayList<Account> getWritableAccounts() {
        final AccountManager am = AccountManager.get(mContext);
        final Account[] accounts = am.getAccounts();
        final ArrayList<Account> writable = new ArrayList<Account>();

        for (Account account : accounts) {
            // Ensure we have details loaded for each account
            final ContactsSource source = getInflatedSource(account.type,
                    ContactsSource.LEVEL_SUMMARY);
            if (!source.readOnly) {
                writable.add(account);
            }
        }
        return writable;
    }

    protected ContactsSource getSourceForType(String accountType) {
        ContactsSource source = mSources.get(accountType);
        if (source == null) {
            Log.w(TAG, "Unknown account type '" + accountType + "', falling back to default");
            source = mSources.get(ACCOUNT_TYPE_FALLBACK);
        }
        return source;
    }

    /**
     * Return {@link ContactsSource} for the given account type.
     */
    public ContactsSource getInflatedSource(String accountType, int inflateLevel) {
        final ContactsSource source = getSourceForType(accountType);
        if (source.isInflated(inflateLevel)) {
            // Found inflated, so return directly
            return source;
        } else {
            // Not inflated, but requested that we force-inflate
            source.ensureInflated(mContext, inflateLevel);
            return source;
        }
    }
}
