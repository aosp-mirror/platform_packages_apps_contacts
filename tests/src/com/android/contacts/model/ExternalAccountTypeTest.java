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

import com.android.contacts.tests.R;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test case for {@link ExternalAccountType}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.ExternalAccountTypeTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class ExternalAccountTypeTest extends AndroidTestCase {

    public void testResolveExternalResId() {
        final Context c = getContext();
        // In this test we use the test package itself as an external package.
        final String packageName = getTestContext().getPackageName();

        // Resource name empty.
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, null, packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "", packageName, ""));

        // Name doesn't begin with '@'
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "x", packageName, ""));

        // Invalid resource name
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@", packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@a", packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@a/b", packageName, ""));

        // Valid resource name
        assertEquals(R.string.test_string, ExternalAccountType.resolveExternalResId(c,
                "@string/test_string", packageName, ""));
    }
}
