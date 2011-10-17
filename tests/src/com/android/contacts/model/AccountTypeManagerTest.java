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

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test case for {@link AccountTypeManager}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.AccountTypeManagerTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class AccountTypeManagerTest extends AndroidTestCase {
    public void testFindAllInvitableAccountTypes() {
        final Context c = getContext();

        // Define account types.
        final AccountType typeA = new MockAccountType("type1", null, null);
        final AccountType typeB = new MockAccountType("type1", "minus", null);
        final AccountType typeC = new MockAccountType("type2", null, "c");
        final AccountType typeD = new MockAccountType("type2", "minus", "d");

        // Define users
        final AccountWithDataSet accountA1 = createAccountWithDataSet("a1", typeA);
        final AccountWithDataSet accountC1 = createAccountWithDataSet("c1", typeC);
        final AccountWithDataSet accountC2 = createAccountWithDataSet("c2", typeC);
        final AccountWithDataSet accountD1 = createAccountWithDataSet("d1", typeD);

        // empty - empty
        Map<AccountTypeWithDataSet, AccountType> types =
                AccountTypeManagerImpl.findAllInvitableAccountTypes(c,
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
                buildAccountTypes(typeA, typeB)
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
                buildAccountTypes(typeD, typeA, typeC, typeB),
                typeC, typeD
                );
    }

    private static AccountWithDataSet createAccountWithDataSet(String name, AccountType type) {
        return new AccountWithDataSet(name, type.accountType, type.dataSet);
    }

    /**
     * Array of {@link AccountType} -> {@link Map}
     */
    private static Map<AccountTypeWithDataSet, AccountType> buildAccountTypes(AccountType... types) {
        final HashMap<AccountTypeWithDataSet, AccountType> result = Maps.newHashMap();
        for (AccountType type : types) {
            result.put(type.getAccountTypeAndDataSet(), type);
        }
        return result;
    }

    /**
     * Array of {@link AccountWithDataSet} -> {@link Collection}
     */
    private static Collection<AccountWithDataSet> buildAccounts(AccountWithDataSet... accounts) {
        final List<AccountWithDataSet> result = Lists.newArrayList();
        for (AccountWithDataSet account : accounts) {
            result.add(account);
        }
        return result;
    }

    /**
     * Executes {@link AccountTypeManagerImpl#findInvitableAccountTypes} and verifies the
     * result.
     */
    private void verifyAccountTypes(
            Collection<AccountWithDataSet> accounts,
            Map<AccountTypeWithDataSet, AccountType> types,
            AccountType... expectedInvitableTypes
            ) {
        Map<AccountTypeWithDataSet, AccountType> result =
                AccountTypeManagerImpl.findAllInvitableAccountTypes(getContext(), accounts, types);
        for (AccountType type : expectedInvitableTypes) {
            assertTrue("Result doesn't contain type=" + type.getAccountTypeAndDataSet(),
                    result.containsKey(type.getAccountTypeAndDataSet()));
        }
        final int numExcessTypes = result.size() - expectedInvitableTypes.length;
        assertEquals("Result contains " + numExcessTypes + " excess type(s)", 0, numExcessTypes);
    }

    private static class MockAccountType extends AccountType {
        private final String mInviteContactActivityClassName;

        public MockAccountType(String type, String dataSet, String inviteContactActivityClassName) {
            accountType = type;
            this.dataSet = dataSet;
            mInviteContactActivityClassName = inviteContactActivityClassName;
        }

        @Override
        public String getInviteContactActivityClassName() {
            return mInviteContactActivityClassName;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }

        @Override
        public boolean areContactsWritable() {
            return false;
        }
    }
}
