/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common.util;

import junit.framework.TestCase;

import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.Time;

/**
 * Unit tests for {@link com.android.contacts.common.util.DateUtils}.
 */
@SmallTest
public class DateUtilTests extends TestCase {

    /**
     * Test date differences which are in the same day.
     */
    public void testDayDiffNone() {
        Time time = new Time();
        long date1 = System.currentTimeMillis();
        long date2 = System.currentTimeMillis() + android.text.format.DateUtils.HOUR_IN_MILLIS;
        assertEquals(0, DateUtils.getDayDifference(time, date1, date2));
        assertEquals(0, DateUtils.getDayDifference(time, date2, date1));
    }

    /**
     * Test date differences which are a day apart.
     */
    public void testDayDiffOne() {
        Time time = new Time();
        long date1 = System.currentTimeMillis();
        long date2 = date1 + android.text.format.DateUtils.DAY_IN_MILLIS;
        assertEquals(1, DateUtils.getDayDifference(time, date1, date2));
        assertEquals(1, DateUtils.getDayDifference(time, date2, date1));
    }

    /**
     * Test date differences which are two days apart.
     */
    public void testDayDiffTwo() {
        Time time = new Time();
        long date1 = System.currentTimeMillis();
        long date2 = date1 + 2*android.text.format.DateUtils.DAY_IN_MILLIS;
        assertEquals(2, DateUtils.getDayDifference(time, date1, date2));
        assertEquals(2, DateUtils.getDayDifference(time, date2, date1));
    }
}
