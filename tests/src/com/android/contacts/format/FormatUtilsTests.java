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

package com.android.contacts.format;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test cases for format utility methods.
 */
@SmallTest
public class FormatUtilsTests extends AndroidTestCase {

    public void testOverlapPoint() throws Exception {
        assertEquals(2, FormatUtils.overlapPoint("abcde", "cdefg"));
        assertEquals(-1, FormatUtils.overlapPoint("John Doe", "John Doe"));
        assertEquals(5, FormatUtils.overlapPoint("John Doe", "Doe, John"));
        assertEquals(-1, FormatUtils.overlapPoint("Mr. John Doe", "Mr. Doe, John"));
        assertEquals(13, FormatUtils.overlapPoint("John Herbert Doe", "Doe, John Herbert"));
    }
}
