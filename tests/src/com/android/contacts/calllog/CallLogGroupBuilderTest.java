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

package com.android.contacts.calllog;

import static com.google.android.collect.Lists.newArrayList;

import com.android.contacts.calllog.CallLogFragment.CallLogQuery;

import android.database.MatrixCursor;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;

import java.util.List;

/**
 * Unit tests for {@link CallLogGroupBuilder}
 */
public class CallLogGroupBuilderTest extends AndroidTestCase {
    /** A phone number for testing. */
    private static final String TEST_NUMBER1 = "14125551234";
    /** A phone number for testing. */
    private static final String TEST_NUMBER2 = "14125555555";

    /** The object under test. */
    private CallLogGroupBuilder mBuilder;
    /** Records the created groups. */
    private FakeGroupCreator mFakeGroupCreator;
    /** Cursor to store the values. */
    private MatrixCursor mCursor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFakeGroupCreator = new FakeGroupCreator();
        mBuilder = new CallLogGroupBuilder(mFakeGroupCreator);
        mCursor = new MatrixCursor(CallLogFragment.CallLogQuery.EXTENDED_PROJECTION);
    }

    @Override
    protected void tearDown() throws Exception {
        mCursor = null;
        mBuilder = null;
        mFakeGroupCreator = null;
        super.tearDown();
    }

    public void testAddGroups_NoCalls() {
        mBuilder.addGroups(mCursor);
        assertEquals(0, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_OneCall() {
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(0, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_TwoCallsNotMatching() {
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogEntry(TEST_NUMBER2, Calls.INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(0, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_ThreeCallsMatching() {
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 3, false, mFakeGroupCreator.groups.get(0));
    }

    public void testAddGroups_MatchingIncomingAndOutgoing() {
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogEntry(TEST_NUMBER1, Calls.OUTGOING_TYPE);
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 3, false, mFakeGroupCreator.groups.get(0));
    }

    public void testAddGroups_HeaderSplitsGroups() {
        addNewCallLogHeader();
        addNewCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addNewCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogHeader();
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        addOldCallLogEntry(TEST_NUMBER1, Calls.INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(2, mFakeGroupCreator.groups.size());
        assertGroupIs(1, 2, false, mFakeGroupCreator.groups.get(0));
        assertGroupIs(4, 2, false, mFakeGroupCreator.groups.get(1));
    }

    private void addOldCallLogEntry(String number, int type) {
        addCallLogEntry(number, type, CallLogQuery.SECTION_OLD_ITEM);
    }

    private void addNewCallLogEntry(String number, int type) {
        addCallLogEntry(number, type, CallLogQuery.SECTION_NEW_ITEM);
    }

    /** Adds a call log entry with the given number and type to the cursor. */
    private void addCallLogEntry(String number, int type, int section) {
        if (section != CallLogQuery.SECTION_NEW_ITEM
                && section != CallLogQuery.SECTION_OLD_ITEM) {
            throw new IllegalArgumentException("not an item section: " + section);
        }
        mCursor.moveToNext();
        mCursor.addRow(new Object[]{
                mCursor.getPosition(), number, 0L, 0L, type, "", "", section
        });
    }

    private void addOldCallLogHeader() {
        addCallLogHeader(CallLogQuery.SECTION_OLD_HEADER);
    }

    private void addNewCallLogHeader() {
        addCallLogHeader(CallLogQuery.SECTION_NEW_HEADER);
    }

    /** Adds a call log entry with a header to the cursor. */
    private void addCallLogHeader(int section) {
        if (section != CallLogQuery.SECTION_NEW_HEADER
                && section != CallLogQuery.SECTION_OLD_HEADER) {
            throw new IllegalArgumentException("not a header section: " + section);
        }
        mCursor.moveToNext();
        mCursor.addRow(new Object[]{ mCursor.getPosition(), "", 0L, 0L, 0, "", "", section });
    }

    /** Asserts that the group matches the given values. */
    private void assertGroupIs(int cursorPosition, int size, boolean expanded, GroupSpec group) {
        assertEquals(cursorPosition, group.cursorPosition);
        assertEquals(size, group.size);
        assertEquals(expanded, group.expanded);
    }

    private static class GroupSpec {
        public final int cursorPosition;
        public final int size;
        public final boolean expanded;

        public GroupSpec(int cursorPosition, int size, boolean expanded) {
            this.cursorPosition = cursorPosition;
            this.size = size;
            this.expanded = expanded;
        }
    }

    private static class FakeGroupCreator implements CallLogFragment.GroupCreator {
        public final List<GroupSpec> groups = newArrayList();
        @Override
        public void addGroup(int cursorPosition, int size, boolean expanded) {
            groups.add(new GroupSpec(cursorPosition, size, expanded));
        }
    }
}
