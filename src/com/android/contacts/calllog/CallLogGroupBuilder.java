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

import com.android.common.widget.GroupingListAdapter;
import com.google.common.annotations.VisibleForTesting;

import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;

/**
 * Groups together calls in the call log.
 * <p>
 * This class is meant to be used in conjunction with {@link GroupingListAdapter}.
 */
public class CallLogGroupBuilder {
    public interface GroupCreator {
        public void addGroup(int cursorPosition, int size, boolean expanded);
    }

    /** The object on which the groups are created. */
    private final GroupCreator mGroupCreator;

    public CallLogGroupBuilder(GroupCreator groupCreator) {
        mGroupCreator = groupCreator;
    }

    /**
     * Finds all groups of adjacent entries in the call log which should be grouped together and
     * calls {@link GroupCreator#addGroup(int, int, boolean)} on {@link #mGroupCreator} for each of
     * them.
     * <p>
     * For entries that are not grouped with others, we do not need to create a group of size one.
     * <p>
     * It assumes that the cursor will not change during its execution.
     *
     * @see GroupingListAdapter#addGroups(Cursor)
     */
    public void addGroups(Cursor cursor) {
        final int count = cursor.getCount();
        if (count == 0) {
            return;
        }

        int currentGroupSize = 1;
        cursor.moveToFirst();
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQuery.NUMBER);
        // This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            final String currentNumber = cursor.getString(CallLogQuery.NUMBER);
            final int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
            final boolean sameNumber = equalNumbers(firstNumber, currentNumber);
            final boolean shouldGroup;

            if (CallLogQuery.isSectionHeader(cursor)) {
                // Cannot group headers.
                shouldGroup = false;
            } else if (!sameNumber) {
                // Should only group with calls from the same number.
                shouldGroup = false;
            } else if (firstCallType == Calls.VOICEMAIL_TYPE
                    || firstCallType == Calls.MISSED_TYPE) {
                // Voicemail and missed calls should only be grouped with subsequent missed calls.
                shouldGroup = callType == Calls.MISSED_TYPE;
            } else {
                // Incoming and outgoing calls group together.
                shouldGroup = callType == Calls.INCOMING_TYPE || callType == Calls.OUTGOING_TYPE;
            }

            if (shouldGroup) {
                // Increment the size of the group to include the current call, but do not create
                // the group until we find a call that does not match.
                currentGroupSize++;
            } else {
                // Create a group for the previous set of calls, excluding the current one, but do
                // not create a group for a single call.
                if (currentGroupSize > 1) {
                    addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize);
                }
                // Start a new group; it will include at least the current call.
                currentGroupSize = 1;
                // The current entry is now the first in the group.
                firstNumber = currentNumber;
                firstCallType = callType;
            }
        }
        // If the last set of calls at the end of the call log was itself a group, create it now.
        if (currentGroupSize > 1) {
            addGroup(count - currentGroupSize, currentGroupSize);
        }
    }

    /**
     * Creates a group of items in the cursor.
     * <p>
     * The group is always unexpanded.
     *
     * @see CallLogAdapter#addGroup(int, int, boolean)
     */
    private void addGroup(int cursorPosition, int size) {
        mGroupCreator.addGroup(cursorPosition, size, false);
    }

    @VisibleForTesting
    boolean equalNumbers(String number1, String number2) {
        if (PhoneNumberUtils.isUriNumber(number1) || PhoneNumberUtils.isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    @VisibleForTesting
    boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) return number1 == number2;

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }
}
