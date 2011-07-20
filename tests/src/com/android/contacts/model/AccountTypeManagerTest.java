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

package com.android.contacts.model;

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import android.accounts.Account;
import android.content.Context;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Test case for {@link AccountTypeManager}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.AccountTypeManagerTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
public class AccountTypeManagerTest extends AndroidTestCase {
    public void testFindInvitableAccountTypes() {
        final Context c = getContext();

        // Define account types.
        final AccountType typeA = new MockAccountType("typeA", null);
        final AccountType typeB = new MockAccountType("typeB", null);
        final AccountType typeC = new MockAccountType("typeC", "c");
        final AccountType typeD = new MockAccountType("typeD", "d");

        // Define users
        final Account accountA1 = new Account("a1", typeA.accountType);
        final Account accountC1 = new Account("c1", typeC.accountType);
        final Account accountC2 = new Account("c2", typeC.accountType);
        final Account accountD1 = new Account("d1", typeD.accountType);

        // empty - empty
        Map<String, AccountType> types = AccountTypeManagerImpl.findInvitableAccountTypes(c,
                buildAccounts(), buildAccountTypes());
        assertEquals(0, types.size());
        try {
            types.clear();
            fail("Returned Map should be unmodifiable.");
        } catch (UnsupportedOperationException ok) {
        }

        // No invite support, no accounts
        verifyAccountTypes(
                buildAccounts(),
                buildAccountTypes(typeA)
                /* empty */
                );

        // No invite support, with accounts
        verifyAccountTypes(
                buildAccounts(accountA1),
                buildAccountTypes(typeA)
                /* empty */
                );

        // With invite support, no accounts
        verifyAccountTypes(
                buildAccounts(),
                buildAccountTypes(typeC)
                /* empty */
                );

        // With invite support, 1 account
        verifyAccountTypes(
                buildAccounts(accountC1),
                buildAccountTypes(typeC),
                typeC
                );

        // With invite support, 2 account
        verifyAccountTypes(
                buildAccounts(accountC1, accountC2),
                buildAccountTypes(typeC),
                typeC
                );

        // Combinations...
        verifyAccountTypes(
                buildAccounts(accountA1),
                buildAccountTypes(typeA, typeC)
                /* empty */
                );

        verifyAccountTypes(
                buildAccounts(accountC1, accountA1),
                buildAccountTypes(typeA, typeC),
                typeC
                );

        verifyAccountTypes(
                buildAccounts(accountC1, accountA1),
                buildAccountTypes(typeD, typeA, typeC),
                typeC
                );

        verifyAccountTypes(
                buildAccounts(accountC1, accountA1, accountD1),
                buildAccountTypes(typeD, typeA, typeC),
                typeC, typeD
                );
    }

    /**
     * Array of {@link AccountType} -> {@link Map}
     */
    private static Map<String, AccountType> buildAccountTypes(AccountType... types) {
        final HashMap<String, AccountType> result = Maps.newHashMap();
        for (AccountType type : types) {
            result.put(type.accountType, type);
        }
        return result;
    }

    /**
     * Array of {@link Account} -> {@link Collection}
     */
    private static Collection<Account> buildAccounts(Account... accounts) {
        final ArrayList<Account> result = Lists.newArrayList();
        for (Account account : accounts) {
            result.add(account);
        }
        return result;
    }

    /**
     * Executes {@link AccountTypeManagerImpl#findInvitableAccountTypes} and verifies the
     * result.
     */
    private void verifyAccountTypes(Collection<Account> accounts,
            Map<String, AccountType> types, AccountType... expectedTypes) {
        Map<String, AccountType> result = AccountTypeManagerImpl.findInvitableAccountTypes(
                getContext(), accounts, types);
        for (AccountType type : expectedTypes) {
            if (!result.containsKey(type.accountType)) {
                fail("Result doesn't contain type=" + type.accountType);
            }
        }
    }

    private static class MockAccountType extends AccountType {
        private final String mInviteContactActivityClassName;

        public MockAccountType(String type, String inviteContactActivityClassName) {
            accountType = type;
            mInviteContactActivityClassName = inviteContactActivityClassName;
        }

        @Override
        public String getInviteContactActivityClassName() {
            return mInviteContactActivityClassName;
        }

        @Override
        public int getHeaderColor(Context context) {
            return 0;
        }

        @Override
        public int getSideBarColor(Context context) {
            return 0;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }
    }
}
