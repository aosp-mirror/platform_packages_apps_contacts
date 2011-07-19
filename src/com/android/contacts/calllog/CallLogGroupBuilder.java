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

import com.android.contacts.calllog.CallLogFragment.CallLogQuery;

import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;

/**
 * Groups together calls in the call log.
 */
public class CallLogGroupBuilder {
    /** Reusable char array buffer. */
    private CharArrayBuffer mBuffer1 = new CharArrayBuffer(128);
    /** Reusable char array buffer. */
    private CharArrayBuffer mBuffer2 = new CharArrayBuffer(128);

    private final CallLogFragment.CallLogAdapter mAdapter;

    public CallLogGroupBuilder(CallLogFragment.CallLogAdapter adapter) {
        mAdapter = adapter;
    }

    public void addGroups(Cursor cursor) {
        int count = cursor.getCount();
        if (count == 0) {
            return;
        }

        int groupItemCount = 1;

        CharArrayBuffer currentValue = mBuffer1;
        CharArrayBuffer value = mBuffer2;
        cursor.moveToFirst();
        cursor.copyStringToBuffer(CallLogQuery.NUMBER, currentValue);
        int currentCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
        for (int i = 1; i < count; i++) {
            cursor.moveToNext();
            cursor.copyStringToBuffer(CallLogQuery.NUMBER, value);
            boolean sameNumber = equalPhoneNumbers(value, currentValue);

            // Group adjacent calls with the same number. Make an exception
            // for the latest item if it was a missed call.  We don't want
            // a missed call to be hidden inside a group.
            if (sameNumber && currentCallType != Calls.MISSED_TYPE
                    && !CallLogFragment.CallLogQuery.isSectionHeader(cursor)) {
                groupItemCount++;
            } else {
                if (groupItemCount > 1) {
                    addGroup(i - groupItemCount, groupItemCount, false);
                }

                groupItemCount = 1;

                // Swap buffers
                CharArrayBuffer temp = currentValue;
                currentValue = value;
                value = temp;

                // If we have just examined a row following a missed call, make
                // sure that it is grouped with subsequent calls from the same number
                // even if it was also missed.
                if (sameNumber && currentCallType == Calls.MISSED_TYPE) {
                    currentCallType = 0;       // "not a missed call"
                } else {
                    currentCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
                }
            }
        }
        if (groupItemCount > 1) {
            addGroup(count - groupItemCount, groupItemCount, false);
        }
    }

    /** @see CallLogFragment.CallLogAdapter#addGroup(int, int, boolean) */
    private void addGroup(int cursorPosition, int size, boolean expanded) {
        mAdapter.addGroup(cursorPosition, size, expanded);
    }

    private boolean equalPhoneNumbers(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {
        // TODO add PhoneNumberUtils.compare(CharSequence, CharSequence) to avoid
        // string allocation
        return PhoneNumberUtils.compare(new String(buffer1.data, 0, buffer1.sizeCopied),
                new String(buffer2.data, 0, buffer2.sizeCopied));
    }
}
