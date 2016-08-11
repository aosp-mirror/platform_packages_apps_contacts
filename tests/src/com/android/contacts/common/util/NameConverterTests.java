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

package src.com.android.contacts.common.util;

import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import com.android.contacts.common.util.NameConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link NameConverter}.
 */
@SmallTest
public class NameConverterTests extends AndroidTestCase {

    public void testStructuredNameToDisplayName() {
        Map<String, String> structuredName = new HashMap<String, String>();
        structuredName.put(StructuredName.PREFIX, "Mr.");
        structuredName.put(StructuredName.GIVEN_NAME, "John");
        structuredName.put(StructuredName.MIDDLE_NAME, "Quincy");
        structuredName.put(StructuredName.FAMILY_NAME, "Adams");
        structuredName.put(StructuredName.SUFFIX, "Esquire");

        assertEquals("Mr. John Quincy Adams, Esquire",
                NameConverter.structuredNameToDisplayName(mContext, structuredName));

        structuredName.remove(StructuredName.SUFFIX);
        assertEquals("Mr. John Quincy Adams",
                NameConverter.structuredNameToDisplayName(mContext, structuredName));

        structuredName.remove(StructuredName.MIDDLE_NAME);
        assertEquals("Mr. John Adams",
                NameConverter.structuredNameToDisplayName(mContext, structuredName));
    }

    public void testDisplayNameToStructuredName() {
        assertStructuredName("Mr. John Quincy Adams, Esquire",
                "Mr.", "John", "Quincy", "Adams", "Esquire");
        assertStructuredName("John Doe", null, "John", null, "Doe", null);
        assertStructuredName("Ms. Jane Eyre", "Ms.", "Jane", null, "Eyre", null);
        assertStructuredName("Dr Leo Spaceman, PhD", "Dr", "Leo", null, "Spaceman", "PhD");
    }

    /**
     * Helper method to check whether a given display name parses out to the other parameters.
     * @param displayName Display name to break into a structured name.
     * @param prefix Expected prefix (null if not expected).
     * @param givenName Expected given name (null if not expected).
     * @param middleName Expected middle name (null if not expected).
     * @param familyName Expected family name (null if not expected).
     * @param suffix Expected suffix (null if not expected).
     */
    private void assertStructuredName(String displayName, String prefix,
            String givenName, String middleName, String familyName, String suffix) {
        Map<String, String> structuredName = NameConverter.displayNameToStructuredName(mContext,
                displayName);
        checkNameComponent(StructuredName.PREFIX, prefix, structuredName);
        checkNameComponent(StructuredName.GIVEN_NAME, givenName, structuredName);
        checkNameComponent(StructuredName.MIDDLE_NAME, middleName, structuredName);
        checkNameComponent(StructuredName.FAMILY_NAME, familyName, structuredName);
        checkNameComponent(StructuredName.SUFFIX, suffix, structuredName);
        assertEquals(0, structuredName.size());
    }

    /**
     * Checks that the given field and value are present in the structured name map (or not present
     * if the given value is null).  If the value is present and matches, the key is removed from
     * the map - once all components of the name are checked, the map should be empty.
     * @param field Field to check.
     * @param value Expected value for the field (null if it is not expected to be populated).
     * @param structuredName The map of structured field names to values.
     */
    private void checkNameComponent(String field, String value,
            Map<String, String> structuredName) {
        if (TextUtils.isEmpty(value)) {
            assertNull(structuredName.get(field));
        } else {
            assertEquals(value, structuredName.get(field));
        }
        structuredName.remove(field);
    }
}
