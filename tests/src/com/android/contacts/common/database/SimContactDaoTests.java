///*
// * Copyright (C) 2016 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.android.contacts.common.database;
//
//import android.content.ContentProviderOperation;
//import android.content.ContentResolver;
//import android.content.Context;
//import android.database.Cursor;
//import android.database.CursorWrapper;
//import android.database.DatabaseUtils;
//import android.provider.ContactsContract;
//import android.support.annotation.RequiresApi;
//import android.support.test.InstrumentationRegistry;
//import android.support.test.filters.LargeTest;
//import android.support.test.filters.SdkSuppress;
//import android.support.test.filters.Suppress;
//import android.support.test.runner.AndroidJUnit4;
//
//import com.android.contacts.common.model.SimContact;
//import com.android.contacts.common.model.account.AccountWithDataSet;
//import com.android.contacts.tests.AccountsTestHelper;
//import com.android.contacts.tests.SimContactsTestHelper;
//
//import org.hamcrest.BaseMatcher;
//import org.hamcrest.Description;
//import org.hamcrest.Matcher;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.experimental.runners.Enclosed;
//import org.junit.runner.RunWith;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//
//import static android.os.Build.VERSION_CODES;
//import static org.hamcrest.Matchers.allOf;
//import static org.junit.Assert.assertThat;
//
//@RunWith(Enclosed.class)
//public class SimContactDaoTests {
//
//    // Lollipop MR1 required for AccountManager.removeAccountExplicitly
//    @RequiresApi(api = VERSION_CODES.LOLLIPOP_MR1)
//    @SdkSuppress(minSdkVersion = VERSION_CODES.LOLLIPOP_MR1)
//    @LargeTest
//    @RunWith(AndroidJUnit4.class)
//    public static class ImportIntegrationTest {
//        private AccountWithDataSet mAccount;
//        private AccountsTestHelper mAccountsHelper;
//        private ContentResolver mResolver;
//
//        @Before
//        public void setUp() throws Exception {
//            mAccountsHelper = new AccountsTestHelper();
//            mAccount = mAccountsHelper.addTestAccount();
//            mResolver = getContext().getContentResolver();
//        }
//
//        @After
//        public void tearDown() throws Exception {
//            mAccountsHelper.cleanup();
//        }
//
//        @Test
//        public void importFromSim() throws Exception {
//            final SimContactDao sut = new SimContactDao(getContext());
//
//            sut.importContacts(Arrays.asList(
//                    new SimContact(1, "Test One", "15095550101", null),
//                    new SimContact(2, "Test Two", "15095550102", null),
//                    new SimContact(3, "Test Three", "15095550103", new String[] {
//                            "user@example.com", "user2@example.com"
//                    })
//            ), mAccount);
//
//            Cursor cursor = queryContactWithName("Test One");
//            assertThat(cursor, hasCount(2));
//            assertThat(cursor, hasName("Test One"));
//            assertThat(cursor, hasPhone("15095550101"));
//            cursor.close();
//
//            cursor = queryContactWithName("Test Two");
//            assertThat(cursor, hasCount(2));
//            assertThat(cursor, hasName("Test Two"));
//            assertThat(cursor, hasPhone("15095550102"));
//            cursor.close();
//
//            cursor = queryContactWithName("Test Three");
//            assertThat(cursor, hasCount(4));
//            assertThat(cursor, hasName("Test Three"));
//            assertThat(cursor, hasPhone("15095550103"));
//            assertThat(cursor, allOf(hasEmail("user@example.com"), hasEmail("user2@example.com")));
//            cursor.close();
//        }
//
//        @Test
//        public void importContactWhichOnlyHasName() throws Exception {
//            final SimContactDao sut = new SimContactDao(getContext());
//
//            sut.importContacts(Arrays.asList(
//                    new SimContact(1, "Test importJustName", null, null)
//            ), mAccount);
//
//            Cursor cursor = queryAllDataInAccount();
//
//            assertThat(cursor, hasCount(1));
//            assertThat(cursor, hasName("Test importJustName"));
//            cursor.close();
//        }
//
//        @Test
//        public void importContactWhichOnlyHasPhone() throws Exception {
//            final SimContactDao sut = new SimContactDao(getContext());
//
//            sut.importContacts(Arrays.asList(
//                    new SimContact(1, null, "15095550111", null)
//            ), mAccount);
//
//            Cursor cursor = queryAllDataInAccount();
//
//            assertThat(cursor, hasCount(1));
//            assertThat(cursor, hasPhone("15095550111"));
//            cursor.close();
//        }
//
//        @Test
//        public void ignoresEmptyContacts() throws Exception {
//            final SimContactDao sut = new SimContactDao(getContext());
//
//            // This probably isn't possible but we'll test it to demonstrate expected behavior and
//            // just in case it does occur
//            sut.importContacts(Arrays.asList(
//                    new SimContact(1, null, null, null),
//                    new SimContact(2, null, null, null),
//                    new SimContact(3, null, null, null),
//                    new SimContact(4, "Not null", null, null)
//            ), mAccount);
//
//            final Cursor contactsCursor = queryAllRawContactsInAccount();
//            assertThat(contactsCursor, hasCount(1));
//            contactsCursor.close();
//
//            final Cursor dataCursor = queryAllDataInAccount();
//            assertThat(dataCursor, hasCount(1));
//
//            dataCursor.close();
//        }
//
//        private Cursor queryAllRawContactsInAccount() {
//            return new StringableCursor(mResolver.query(ContactsContract.RawContacts.CONTENT_URI, null,
//                    ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
//                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
//                    new String[] {
//                            mAccount.name,
//                            mAccount.type
//                    }, null));
//        }
//
//        private Cursor queryAllDataInAccount() {
//            return new StringableCursor(mResolver.query(ContactsContract.Data.CONTENT_URI, null,
//                    ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
//                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
//                    new String[] {
//                            mAccount.name,
//                            mAccount.type
//                    }, null));
//        }
//
//        private Cursor queryContactWithName(String name) {
//            return new StringableCursor(mResolver.query(ContactsContract.Data.CONTENT_URI, null,
//                    ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
//                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=? AND " +
//                            ContactsContract.Data.DISPLAY_NAME + "=?",
//                    new String[] {
//                            mAccount.name,
//                            mAccount.type,
//                            name
//                    }, null));
//        }
//    }
//
//    @LargeTest
//    // suppressed because failed assumptions are reported as test failures by the build server
//    @Suppress
//    @RunWith(AndroidJUnit4.class)
//    public static class ReadIntegrationTest {
//        private SimContactsTestHelper mSimTestHelper;
//        private ArrayList<ContentProviderOperation> mSimSnapshot;
//
//        @Before
//        public void setUp() throws Exception {
//            mSimTestHelper = new SimContactsTestHelper();
//
//            mSimTestHelper.assumeSimWritable();
//            if (!mSimTestHelper.isSimWritable()) return;
//
//            mSimSnapshot = mSimTestHelper.captureRestoreSnapshot();
//            mSimTestHelper.deleteAllSimContacts();
//        }
//
//        @After
//        public void tearDown() throws Exception {
//            mSimTestHelper.restore(mSimSnapshot);
//        }
//
//        @Test
//        public void readFromSim() {
//            mSimTestHelper.addSimContact("Test Simone", "15095550101");
//            mSimTestHelper.addSimContact("Test Simtwo", "15095550102");
//            mSimTestHelper.addSimContact("Test Simthree", "15095550103");
//
//            final SimContactDao sut = new SimContactDao(getContext());
//            final ArrayList<SimContact> contacts = sut.loadSimContacts();
//
//            assertThat(contacts.get(0), isSimContactWithNameAndPhone("Test Simone", "15095550101"));
//            assertThat(contacts.get(1), isSimContactWithNameAndPhone("Test Simtwo", "15095550102"));
//            assertThat(contacts.get(2), isSimContactWithNameAndPhone("Test Simthree", "15095550103"));
//        }
//    }
//
//    private static Matcher<SimContact> isSimContactWithNameAndPhone(final String name,
//            final String phone) {
//        return new BaseMatcher<SimContact>() {
//            @Override
//            public boolean matches(Object o) {
//                if (!(o instanceof SimContact))  return false;
//
//                SimContact other = (SimContact) o;
//
//                return name.equals(other.getName())
//                        && phone.equals(other.getPhone());
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("SimContact with name=" + name + " and phone=" +
//                        phone);
//            }
//        };
//    }
//
//    private static Matcher<Cursor> hasCount(final int count) {
//        return new BaseMatcher<Cursor>() {
//            @Override
//            public boolean matches(Object o) {
//                if (!(o instanceof Cursor)) return false;
//                return ((Cursor)o).getCount() == count;
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("Cursor with " + count + " rows");
//            }
//        };
//    }
//
//    private static Matcher<Cursor> hasMimeType(String type) {
//        return hasValueForColumn(ContactsContract.Data.MIMETYPE, type);
//    }
//
//    private static Matcher<Cursor> hasValueForColumn(final String column, final String value) {
//        return new BaseMatcher<Cursor>() {
//
//            @Override
//            public boolean matches(Object o) {
//                if (!(o instanceof Cursor)) return false;
//                final Cursor cursor = (Cursor)o;
//
//                final int index = cursor.getColumnIndexOrThrow(column);
//                return value.equals(cursor.getString(index));
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("Cursor with " + column + "=" + value);
//            }
//        };
//    }
//
//    private static Matcher<Cursor> hasRowMatching(final Matcher<Cursor> rowMatcher) {
//        return new BaseMatcher<Cursor>() {
//            @Override
//            public boolean matches(Object o) {
//                if (!(o instanceof Cursor)) return false;
//                final Cursor cursor = (Cursor)o;
//
//                cursor.moveToPosition(-1);
//                while (cursor.moveToNext()) {
//                    if (rowMatcher.matches(cursor)) return true;
//                }
//
//                return false;
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("Cursor with row matching ");
//                rowMatcher.describeTo(description);
//            }
//        };
//    }
//
//    private static Matcher<Cursor> hasName(final String name) {
//        return hasRowMatching(allOf(
//                hasMimeType(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
//                hasValueForColumn(
//                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)));
//    }
//
//    private static Matcher<Cursor> hasPhone(final String phone) {
//        return hasRowMatching(allOf(
//                hasMimeType(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
//                hasValueForColumn(
//                        ContactsContract.CommonDataKinds.Phone.NUMBER, phone)));
//    }
//
//    private static Matcher<Cursor> hasEmail(final String email) {
//        return hasRowMatching(allOf(
//                hasMimeType(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
//                hasValueForColumn(
//                        ContactsContract.CommonDataKinds.Email.ADDRESS, email)));
//    }
//
//    static class StringableCursor extends CursorWrapper {
//        public StringableCursor(Cursor cursor) {
//            super(cursor);
//        }
//
//        @Override
//        public String toString() {
//            final Cursor wrapped = getWrappedCursor();
//
//            if (wrapped.getCount() == 0) {
//                return "Empty Cursor";
//            }
//
//            return DatabaseUtils.dumpCursorToString(wrapped);
//        }
//    }
//
//    static Context getContext() {
//        return InstrumentationRegistry.getTargetContext();
//    }
//}
