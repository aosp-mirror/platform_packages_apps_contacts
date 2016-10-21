/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.common.database;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.VisibleForTesting;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.contacts.common.Experiments;
import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contactsbind.experiments.Flags;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides data access methods for loading contacts from a SIM card and and migrating these
 * SIM contacts to a CP2 account.
 */
public class SimContactDao {
    private static final String TAG = "SimContactDao";

    @VisibleForTesting
    public static final Uri ICC_CONTENT_URI = Uri.parse("content://icc/adn");

    public static String _ID = BaseColumns._ID;
    public static String NAME = "name";
    public static String NUMBER = "number";
    public static String EMAILS = "emails";

    private final Context mContext;
    private final ContentResolver mResolver;
    private final TelephonyManager mTelephonyManager;

    public SimContactDao(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void warmupSimQueryIfNeeded() {
        // Not needed if we don't have an Assistant section
        if (!Flags.getInstance().getBoolean(Experiments.ASSISTANT) ||
                !shouldLoad()) return;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // We don't actually have to do any caching ourselves. Some other layer must do
                // caching of the data (OS or framework) because subsequent queries are very fast.
                final Cursor cursor = mResolver.query(ICC_CONTENT_URI, null, null, null, null);
                if (cursor != null) {
                    cursor.close();
                }
                return null;
            }
        }.execute();
    }

    public boolean shouldLoad() {
        final Set<String> simIds = hasTelephony() && hasPermissions() ? getSimCardIds() :
                Collections.<String>emptySet();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "shouldLoad: hasTelephony? " + hasTelephony() +
                    " hasPermissions? " + hasPermissions() +
                    " SIM absent? " + (getSimState() != TelephonyManager.SIM_STATE_ABSENT) +
                    " SIM ids=" + simIds +
                    " imported=" + SharedPreferenceUtil.getImportedSims(mContext));
        }
        return hasTelephony() && hasPermissions() &&
                getSimState() != TelephonyManager.SIM_STATE_ABSENT &&
                !Sets.difference(simIds, SharedPreferenceUtil.getImportedSims(mContext))
                        .isEmpty();
    }

    public Set<String> getSimCardIds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
            final List<SubscriptionInfo> subscriptions = subscriptionManager
                    .getActiveSubscriptionInfoList();
            if (subscriptions == null) {
                return Collections.emptySet();
            }
            final ArraySet<String> result = new ArraySet<>(
                    subscriptionManager.getActiveSubscriptionInfoCount());

            for (SubscriptionInfo info : subscriptions) {
                result.add(info.getIccId());
            }
            return result;
        }
        return Collections.singleton(getSimSerialNumber());
    }

    public int getSimState() {
        return mTelephonyManager.getSimState();
    }

    public String getSimSerialNumber() {
        return mTelephonyManager.getSimSerialNumber();
    }

    public ArrayList<SimContact> loadSimContacts(int subscriptionId) {
        return loadFrom(ICC_CONTENT_URI.buildUpon()
                .appendPath("subId")
                .appendPath(String.valueOf(subscriptionId))
                .build());
    }

    public ArrayList<SimContact> loadSimContacts() {
        return loadFrom(ICC_CONTENT_URI);
    }

    private ArrayList<SimContact> loadFrom(Uri uri) {
        final Cursor cursor = mResolver.query(uri, null, null, null, null);

        try {
            return loadFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    private ArrayList<SimContact> loadFromCursor(Cursor cursor) {
        final int colId = cursor.getColumnIndex(_ID);
        final int colName = cursor.getColumnIndex(NAME);
        final int colNumber = cursor.getColumnIndex(NUMBER);
        final int colEmails = cursor.getColumnIndex(EMAILS);

        final ArrayList<SimContact> result = new ArrayList<>();

        while (cursor.moveToNext()) {
            final long id = cursor.getLong(colId);
            final String name = cursor.getString(colName);
            final String number = cursor.getString(colNumber);
            final String emails = cursor.getString(colEmails);

            final SimContact contact = new SimContact(id, name, number, parseEmails(emails));
            result.add(contact);
        }
        return result;
    }

    public ContentProviderResult[] importContacts(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        final ArrayList<ContentProviderOperation> ops =
                createImportOperations(contacts, targetAccount);
        return mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    private ArrayList<ContentProviderOperation> createImportOperations(List<SimContact> contacts,
            AccountWithDataSet targetAccount) {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (SimContact contact : contacts) {
            contact.appendCreateContactOperations(ops, targetAccount);
        }
        return ops;
    }

    private String[] parseEmails(String emails) {
        return emails != null ? emails.split(",") : null;
    }

    public void persistImportSuccess() {
        // TODO: either need to have an assistant card per SIM card or show contacts from all
        // SIMs in the import view.
        final Set<String> simIds = getSimCardIds();
        for (String id : simIds) {
            SharedPreferenceUtil.addImportedSim(mContext, id);
        }
    }

    private boolean hasTelephony() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean hasPermissions() {
        return PermissionsUtil.hasContactsPermissions(mContext) &&
                PermissionsUtil.hasPhonePermissions(mContext);
    }
}
