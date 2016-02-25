/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.common.list;

import android.provider.ContactsContract.Contacts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;

import static com.android.contacts.common.list.DefaultContactListAdapter.getDisplayNameSelection;
import static com.android.contacts.common.list.DefaultContactListAdapter.getDisplayNameSelectionArgs;

/**
 * Unit tests for {@link com.android.contacts.common.list.DefaultContactListAdapter}.
 */
@SmallTest
public class DefaultContactListAdapterTest extends AndroidTestCase {

    public void testDisplayNameSelection() {
        final String dn = Contacts.DISPLAY_NAME;
        assertNull(getDisplayNameSelection(null, dn));
        assertNull(getDisplayNameSelection("", dn));
        assertNull(getDisplayNameSelection(" ", dn));
        assertNull(getDisplayNameSelection("\t", dn));
        assertNull(getDisplayNameSelection("\t ", dn));

        final String pn = Contacts.PHONETIC_NAME;
        String expected = "(" + dn + " LIKE ?1 OR " + pn + " LIKE ?1)";
        assertEquals(expected, getDisplayNameSelection("foo", dn));

        expected = "(" + dn + " LIKE ?1 OR " + pn + " LIKE ?1) OR " +
                "(" + dn + " LIKE ?2 OR " + pn + " LIKE ?2)";
        assertEquals(expected, getDisplayNameSelection("foo bar", dn));
        assertEquals(expected, getDisplayNameSelection(" foo bar ", dn));
        assertEquals(expected, getDisplayNameSelection("foo\t bar", dn));
        assertEquals(expected, getDisplayNameSelection(" \tfoo\t bar\t ", dn));
    }

    public void testDisplayNameSelectionArgs() {
        assertNull(getDisplayNameSelectionArgs(null));
        assertNull(getDisplayNameSelectionArgs(""));
        assertNull(getDisplayNameSelectionArgs(" "));
        assertNull(getDisplayNameSelectionArgs("\t"));
        assertNull(getDisplayNameSelectionArgs("\t "));

        String[] expected = new String[]{"foo%"};
        assertArrayEquals(expected, getDisplayNameSelectionArgs("foo"));

        expected = new String[]{"foo%","bar%"};
        assertArrayEquals(expected, getDisplayNameSelectionArgs("foo bar"));
        assertArrayEquals(expected, getDisplayNameSelectionArgs(" foo bar "));
        assertArrayEquals(expected, getDisplayNameSelectionArgs("foo\t bar"));
        assertArrayEquals(expected, getDisplayNameSelectionArgs("\t foo\t bar\t "));
    }

    private void assertArrayEquals(String[] expected, String[] actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) fail("expected:" + expected + " but was:" + actual);
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }
}