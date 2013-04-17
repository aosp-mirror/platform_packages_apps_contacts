/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.content.Intent;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.MoreContactUtils;

/**
 * Tests for {@link ContactsUtils}.
 */
@SmallTest
public class ContactsUtilsTests extends AndroidTestCase {

    public void testIsGraphicNull() throws Exception {
        assertFalse(ContactsUtils.isGraphic(null));
    }

    public void testIsGraphicEmpty() throws Exception {
        assertFalse(ContactsUtils.isGraphic(""));
    }

    public void testIsGraphicSpaces() throws Exception {
        assertFalse(ContactsUtils.isGraphic("  "));
    }

    public void testIsGraphicPunctuation() throws Exception {
        assertTrue(ContactsUtils.isGraphic("."));
    }

    public void testAreObjectsEqual() throws Exception {
        assertTrue("null:null", ContactsUtils.areObjectsEqual(null, null));
        assertTrue("1:1", ContactsUtils.areObjectsEqual(1, 1));

        assertFalse("null:1", ContactsUtils.areObjectsEqual(null, 1));
        assertFalse("1:null", ContactsUtils.areObjectsEqual(1, null));
        assertFalse("1:2", ContactsUtils.areObjectsEqual(1, 2));
    }

    public void testAreIntentActionEqual() throws Exception {
        assertTrue("1", ContactsUtils.areIntentActionEqual(null, null));
        assertTrue("1", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("a")));

        assertFalse("11", ContactsUtils.areIntentActionEqual(new Intent("a"), null));
        assertFalse("12", ContactsUtils.areIntentActionEqual(null, new Intent("a")));

        assertFalse("21", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent()));
        assertFalse("22", ContactsUtils.areIntentActionEqual(new Intent(), new Intent("b")));
        assertFalse("23", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("b")));
    }
}
