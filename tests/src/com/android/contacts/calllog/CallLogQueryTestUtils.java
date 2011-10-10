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

import static junit.framework.Assert.assertEquals;

import android.provider.CallLog.Calls;

import junit.framework.Assert;

/**
 * Helper class to create test values for {@link CallLogQuery}.
 */
public class CallLogQueryTestUtils {
    public static Object[] createTestValues() {
        Object[] values = new Object[]{
                0L, "", 0L, 0L, Calls.INCOMING_TYPE, "", "", "", null, 0, null, null, null, null,
                0L, null, 0,
        };
        assertEquals(CallLogQuery._PROJECTION.length, values.length);
        return values;
    }

    public static Object[] createTestExtendedValues() {
        Object[] values = new Object[]{
                0L, "", 0L, 0L, Calls.INCOMING_TYPE, "", "", "", null, 0, null, null, null, null,
                0L, null, 1, CallLogQuery.SECTION_OLD_ITEM
        };
        Assert.assertEquals(CallLogQuery.EXTENDED_PROJECTION.length, values.length);
        return values;
    }
}
