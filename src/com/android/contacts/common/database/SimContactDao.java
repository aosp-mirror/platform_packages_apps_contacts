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
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.contacts.common.Experiments;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.model.SimCard;
import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contactsbind.experiments.Flags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides data access methods for loading contacts from a SIM card and and migrating these
 * SIM contacts to a CP2 account.
 */
public class SimContactDao {
    private static final String TAG = "SimContactDao";

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

    public SimContactDao(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void warmupSimQueryIfNeeded() {
        // Not needed if we don't have an Assistant section
        if (!Flags.getInstance().getBoolean(Experiments.ASSISTANT) ||
                !canReadSimContacts()) return;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                for (SimCard card : getSimCards()) {
                    // We don't actually have to do any caching ourselves. Some other layer must do
                    // caching of the data (OS or framework) because subsequent queries are very
                    // fast.
                    card.loadContacts(SimContactDao.this);
                }
                return null;
            }
        }.execute();
    }

    public boolean canReadSimContacts() {
        return hasTelephony() && hasPermissions() &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    public List<SimCard> getSimCards() {
        if (!canReadSimContacts()) {
            return Collections.emptyList();
        }
        final List<SimCard> sims = CompatUtils.isMSIMCompatible() ?
                getSimCardsFromSubscriptions() :
                Collections.singletonList(SimCard.create(mTelephonyManager));
        return SharedPreferenceUtil.restoreSimStates(mContext, sims);
    }

    @NonNull
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

    public ArrayList<SimContact> loadSimContacts(int subscriptionId) {
        return loadFrom(ICC_CONTENT_URI.buildUpon()
                .appendPath("subId")
                .appendPath(String.valueOf(subscriptionId))
                .build());
    }

    public ArrayList<SimContact> loadSimContacts() {
        return loadFrom(ICC_CONTENT_URI);
    }

    public Context getContext() {
        return mContext;
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
        final List<SimCard> sims = getSimCards();
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
                    .addSimCard(new SimCard("fake-sim-id1", 1, "Fake Carrier", "Card 1",
                            "15095550101", "us").withContacts(
                            new SimContact(1, "Sim One", "15095550111", null),
                            new SimContact(2, "Sim Two", "15095550112", null),
                            new SimContact(3, "Sim Three", "15095550113", null),
                            new SimContact(4, "Sim Four", "15095550114", null)
                    ))
                    .addSimCard(new SimCard("fake-sim-id2", 1, "Carrier Two", "Card 2",
                            "15095550102", "us").withContacts(
                            new SimContact(1, "John Sim", "15095550121", null),
                            new SimContact(2, "Bob Sim", "15095550122", null),
                            new SimContact(3, "Mary Sim", "15095550123", null),
                            new SimContact(4, "Alice Sim", "15095550124", null)
                    ));
        }

        return new SimContactDao(context);
    }

    // TODO remove this class and the USE_FAKE_INSTANCE flag once this code is not under
    // active development or anytime after 3/1/2017
    private static class DebugImpl extends SimContactDao {

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
}
