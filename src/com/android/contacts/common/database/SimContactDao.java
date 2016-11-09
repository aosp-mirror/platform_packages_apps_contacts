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

import android.annotation.TargetApi;
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
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.contacts.R;
import com.android.contacts.common.Experiments;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.model.SimCard;
import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contactsbind.experiments.Flags;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides data access methods for loading contacts from a SIM card and and migrating these
 * SIM contacts to a CP2 account.
 */
public class SimContactDao {
    private static final String TAG = "SimContactDao";

    // Maximum number of SIM contacts to import in a single ContentResolver.applyBatch call.
    // This is necessary to avoid TransactionTooLargeException when there are a large number of
    // contacts. This has been tested on Nexus 6 NME70B and is probably be conservative enough
    // to work on any phone.
    private static final int IMPORT_MAX_BATCH_SIZE = 300;

    // How many SIM contacts to consider in a single query. This prevents hitting the SQLite
    // query parameter limit.
    static final int QUERY_MAX_BATCH_SIZE = 100;

    // Set to true for manual testing on an emulator or phone without a SIM card
    // DO NOT SUBMIT if set to true
    private static final boolean USE_FAKE_INSTANCE = false;

    @VisibleForTesting
    public static final Uri ICC_CONTENT_URI = Uri.parse("content://icc/adn");

    public static String _ID = BaseColumns._ID;
    public static String NAME = "name";
    public static String NUMBER = "number";
    public static String EMAILS = "emails";

    private final Context mContext;
    private final ContentResolver mResolver;
    private final TelephonyManager mTelephonyManager;

    private SimContactDao(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void warmupSimQueryIfNeeded() {
        if (!canReadSimContacts()) return;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                getSimCardsWithContacts();
                return null;
            }
        }.execute();
    }

    public Context getContext() {
        return mContext;
    }

    public boolean canReadSimContacts() {
        // Require SIM_STATE_READY because the TelephonyManager methods related to SIM require
        // this state
        return hasTelephony() && hasPermissions() &&
                mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    public List<SimCard> getSimCards() {
        if (!canReadSimContacts()) {
            return Collections.emptyList();
        }
        final List<SimCard> sims = CompatUtils.isMSIMCompatible() ?
                getSimCardsFromSubscriptions() :
                Collections.singletonList(SimCard.create(mTelephonyManager,
                        mContext.getString(R.string.single_sim_display_label)));
        return SharedPreferenceUtil.restoreSimStates(mContext, sims);
    }

    public List<SimCard> getSimCardsWithContacts() {
        final List<SimCard> result = new ArrayList<>();
        for (SimCard sim : getSimCards()) {
            result.add(sim.withContacts(loadContactsForSim(sim)));
        }
        return result;
    }

    public ArrayList<SimContact> loadContactsForSim(SimCard sim) {
        if (sim.hasValidSubscriptionId()) {
            return loadSimContacts(sim.getSubscriptionId());
        }
        return loadSimContacts();
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

    public ContentProviderResult[] importContacts(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        if (contacts.size() < IMPORT_MAX_BATCH_SIZE) {
            return importBatch(contacts, targetAccount);
        }
        final List<ContentProviderResult> results = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i += IMPORT_MAX_BATCH_SIZE) {
            results.addAll(Arrays.asList(importBatch(
                    contacts.subList(i, Math.min(contacts.size(), i + IMPORT_MAX_BATCH_SIZE)),
                    targetAccount)));
        }
        return results.toArray(new ContentProviderResult[results.size()]);
    }

    public void persistSimState(SimCard sim) {
        SharedPreferenceUtil.persistSimStates(mContext, Collections.singletonList(sim));
    }

    public void persistSimStates(List<SimCard> simCards) {
        SharedPreferenceUtil.persistSimStates(mContext, simCards);
    }

    public SimCard getFirstSimCard() {
        return getSimBySubscriptionId(SimCard.NO_SUBSCRIPTION_ID);
    }

    public SimCard getSimBySubscriptionId(int subscriptionId) {
        final List<SimCard> sims = SharedPreferenceUtil.restoreSimStates(mContext, getSimCards());
        if (subscriptionId == SimCard.NO_SUBSCRIPTION_ID && !sims.isEmpty()) {
            return sims.get(0);
        }
        for (SimCard sim : getSimCards()) {
            if (sim.getSubscriptionId() == subscriptionId) {
                return sim;
            }
        }
        return null;
    }

    public Map<AccountWithDataSet, Set<SimContact>> findAccountsOfExistingSimContacts(
            List<SimContact> contacts) {
        final Map<AccountWithDataSet, Set<SimContact>> result = new ArrayMap<>();
        for (int i = 0; i < contacts.size(); i += QUERY_MAX_BATCH_SIZE) {
            findAccountsOfExistingSimContacts(
                    contacts.subList(i, Math.min(contacts.size(), i + QUERY_MAX_BATCH_SIZE)),
                    result);
        }
        return result;
    }

    private void findAccountsOfExistingSimContacts(List<SimContact> contacts,
            Map<AccountWithDataSet, Set<SimContact>> result) {
        final Map<Long, List<SimContact>> rawContactToSimContact = new HashMap<>();
        Collections.sort(contacts, SimContact.compareByPhoneThenName());

        final Cursor dataCursor = queryRawContactsForSimContacts(contacts);

        try {
            while (dataCursor.moveToNext()) {
                final String number = DataQuery.getPhoneNumber(dataCursor);
                final String name = DataQuery.getDisplayName(dataCursor);

                final int index = SimContact.findByPhoneAndName(contacts, number, name);
                if (index < 0) {
                    continue;
                }
                final SimContact contact = contacts.get(index);
                final long id = DataQuery.getRawContactId(dataCursor);
                if (!rawContactToSimContact.containsKey(id)) {
                    rawContactToSimContact.put(id, new ArrayList<SimContact>());
                }
                rawContactToSimContact.get(id).add(contact);
            }
        } finally {
            dataCursor.close();
        }

        final Cursor accountsCursor = queryAccountsOfRawContacts(rawContactToSimContact.keySet());
        try {
            while (accountsCursor.moveToNext()) {
                final AccountWithDataSet account = AccountQuery.getAccount(accountsCursor);
                final long id = AccountQuery.getId(accountsCursor);
                if (!result.containsKey(account)) {
                    result.put(account, new ArraySet<SimContact>());
                }
                for (SimContact contact : rawContactToSimContact.get(id)) {
                    result.get(account).add(contact);
                }
            }
        } finally {
            accountsCursor.close();
        }
    }


    private ContentProviderResult[] importBatch(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        final ArrayList<ContentProviderOperation> ops =
                createImportOperations(contacts, targetAccount);
        return mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private List<SimCard> getSimCardsFromSubscriptions() {
        final SubscriptionManager subscriptionManager = (SubscriptionManager)
                mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        final List<SubscriptionInfo> subscriptions = subscriptionManager
                .getActiveSubscriptionInfoList();
        final ArrayList<SimCard> result = new ArrayList<>();
        for (SubscriptionInfo subscriptionInfo : subscriptions) {
            result.add(SimCard.create(subscriptionInfo));
        }
        return result;
    }

    private List<SimContact> getContactsForSim(SimCard sim) {
        final List<SimContact> contacts = sim.getContacts();
        return contacts != null ? contacts : loadContactsForSim(sim);
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

    private Cursor queryRawContactsForSimContacts(List<SimContact> contacts) {
        final StringBuilder selectionBuilder = new StringBuilder();

        int phoneCount = 0;
        for (SimContact contact : contacts) {
            if (contact.hasPhone()) {
                phoneCount++;
            }
        }
        List<String> selectionArgs = new ArrayList<>(phoneCount + 1);

        selectionBuilder.append(ContactsContract.Data.MIMETYPE).append("=? AND ");
        selectionArgs.add(Phone.CONTENT_ITEM_TYPE);

        selectionBuilder.append(Phone.NUMBER).append(" IN (")
                .append(Joiner.on(',').join(Collections.nCopies(phoneCount, '?')))
                .append(")");
        for (SimContact contact : contacts) {
            if (contact.hasPhone()) {
                selectionArgs.add(contact.getPhone());
            }
        }

        return mResolver.query(ContactsContract.Data.CONTENT_URI.buildUpon()
                        .appendQueryParameter(ContactsContract.Data.VISIBLE_CONTACTS_ONLY, "true")
                        .build(),
                DataQuery.PROJECTION,
                selectionBuilder.toString(),
                selectionArgs.toArray(new String[selectionArgs.size()]),
                null);
    }

    private Cursor queryAccountsOfRawContacts(Set<Long> ids) {
        final StringBuilder selectionBuilder = new StringBuilder();

        final String[] args = new String[ids.size()];

        selectionBuilder.append(RawContacts._ID).append(" IN (")
                .append(Joiner.on(',').join(Collections.nCopies(args.length, '?')))
                .append(")");
        int i = 0;
        for (long id : ids) {
            args[i++] = String.valueOf(id);
        }
        return mResolver.query(RawContacts.CONTENT_URI,
                AccountQuery.PROJECTION,
                selectionBuilder.toString(),
                args,
                null);
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

    private boolean hasTelephony() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean hasPermissions() {
        return PermissionsUtil.hasContactsPermissions(mContext) &&
                PermissionsUtil.hasPhonePermissions(mContext);
    }

    public static SimContactDao create(Context context) {
        if (USE_FAKE_INSTANCE) {
            return new DebugImpl(context)
                    .addSimCard(new SimCard("fake-sim-id1", 1, "Fake Carrier",
                            "Card 1", "15095550101", "us").withContacts(
                            new SimContact(1, "Sim One", "15095550111", null),
                            new SimContact(2, "Sim Two", "15095550112", null),
                            new SimContact(3, "Sim Three", "15095550113", null),
                            new SimContact(4, "Sim Four", "15095550114", null),
                            new SimContact(5, "411 & more", "411", null)
                    ))
                    .addSimCard(new SimCard("fake-sim-id2", 2, "Carrier Two",
                            "Card 2", "15095550102", "us").withContacts(
                            new SimContact(1, "John Sim", "15095550121", null),
                            new SimContact(2, "Bob Sim", "15095550122", null),
                            new SimContact(3, "Mary Sim", "15095550123", null),
                            new SimContact(4, "Alice Sim", "15095550124", null),
                            new SimContact(5, "Sim Duplicate", "15095550121", null)
                    ));
        }
        return new SimContactDao(context);
    }

    // TODO remove this class and the USE_FAKE_INSTANCE flag once this code is not under
    // active development or anytime after 3/1/2017
    public static class DebugImpl extends SimContactDao {

        private List<SimCard> mSimCards = new ArrayList<>();
        private SparseArray<SimCard> mCardsBySubscription = new SparseArray<>();

        public DebugImpl(Context context) {
            super(context);
        }

        public DebugImpl addSimCard(SimCard sim) {
            mSimCards.add(sim);
            mCardsBySubscription.put(sim.getSubscriptionId(), sim);
            return this;
        }

        @Override
        public void warmupSimQueryIfNeeded() {
        }

        @Override
        public List<SimCard> getSimCards() {
            return SharedPreferenceUtil.restoreSimStates(getContext(), mSimCards);
        }

        @Override
        public ArrayList<SimContact> loadSimContacts() {
            return new ArrayList<>(mSimCards.get(0).getContacts());
        }

        @Override
        public ArrayList<SimContact> loadSimContacts(int subscriptionId) {
            return new ArrayList<>(mCardsBySubscription.get(subscriptionId).getContacts());
        }

        @Override
        public boolean canReadSimContacts() {
            return true;
        }
    }

    // Query used for detecting existing contacts that may match a SimContact.
    private static final class DataQuery {

        public static final String[] PROJECTION = new String[] {
                ContactsContract.Data.RAW_CONTACT_ID, Phone.NUMBER, Phone.DISPLAY_NAME
        };

        public static final int RAW_CONTACT_ID = 0;
        public static final int PHONE_NUMBER = 1;
        public static final int DISPLAY_NAME = 2;

        public static long getRawContactId(Cursor cursor) {
            return cursor.getLong(RAW_CONTACT_ID);
        }

        public static String getPhoneNumber(Cursor cursor) {
            return cursor.getString(PHONE_NUMBER);
        }

        public static String getDisplayName(Cursor cursor) {
            return cursor.getString(DISPLAY_NAME);
        }
    }

    private static final class AccountQuery {
        public static final String[] PROJECTION = new String[] {
                RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
                RawContacts.DATA_SET
        };

        public static long getId(Cursor cursor) {
            return cursor.getLong(0);
        }

        public static AccountWithDataSet getAccount(Cursor cursor) {
            return new AccountWithDataSet(cursor.getString(1), cursor.getString(2),
                    cursor.getString(3));
        }
    }
}
