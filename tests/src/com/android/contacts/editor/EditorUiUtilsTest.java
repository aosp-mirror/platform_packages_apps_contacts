/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.GoogleAccountType;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

/**
 * Tests {@link EditorUiUtils}.
 */
@SmallTest
public class EditorUiUtilsTest extends AndroidTestCase {

    private static final String ACCOUNT_NAME = "somebody@lunkedin.com";
    private static final String DISPLAY_LABEL = "LunkedIn";

    private static final String GOOGLE_ACCOUNT_NAME = "somebody@gmail.com";
    private static final String GOOGLE_DISPLAY_LABEL = "Google";

    private static final String RINGTONE = "content://media/external/audio/media/31";

    private static final class MockAccountType extends AccountType {

        private final String mDisplayLabel;

        private MockAccountType(String displayLabel) {
            mDisplayLabel = displayLabel;
        }

        @Override
        public boolean areContactsWritable() {
            return false;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }

        @Override
        public CharSequence getDisplayLabel(Context context) {
            return mDisplayLabel;
        }
    }

    public void testGetProfileAccountInfo_AccountName() {
        final Pair pair = EditorUiUtils.getLocalAccountInfo(getContext(),
                ACCOUNT_NAME, new MockAccountType(DISPLAY_LABEL));

        assertNotNull(pair);
        assertEquals(ACCOUNT_NAME, pair.first);
        assertEquals(getContext().getString(R.string.external_profile_title, DISPLAY_LABEL),
                pair.second); // My LunkedIn profile
    }

    public void testGetProfileAccountInfo_NoAccountName() {
        final Pair pair = EditorUiUtils.getLocalAccountInfo(getContext(),
                /* accountName =*/ null, new MockAccountType(DISPLAY_LABEL));

        assertNotNull(pair);
        assertNull(pair.first);
        assertEquals(getContext().getString(R.string.local_profile_title),
                pair.second); // "My local profile
    }

    public void testGetAccountInfo_AccountName_DisplayLabel() {
        final Pair pair = EditorUiUtils.getAccountInfo(getContext(),
                ACCOUNT_NAME, new MockAccountType(DISPLAY_LABEL));

        assertNotNull(pair);
        assertEquals(getContext().getString(R.string.from_account_format, ACCOUNT_NAME),
                pair.first); // somebody@lunkedin.com
        assertEquals(getContext().getString(R.string.account_type_format, DISPLAY_LABEL),
                pair.second); // LunkedIn Contact
    }

    public void testGetAccountInfo_AccountName_DisplayLabel_GoogleAccountType() {
        final AccountType accountType = new MockAccountType(GOOGLE_DISPLAY_LABEL);
        accountType.accountType = GoogleAccountType.ACCOUNT_TYPE;
        final Pair pair = EditorUiUtils.getAccountInfo(getContext(),
                GOOGLE_ACCOUNT_NAME, accountType);

        assertNotNull(pair);
        assertEquals(getContext().getString(R.string.from_account_format, GOOGLE_ACCOUNT_NAME),
                pair.first); // somebody@gmail.com
        assertEquals(
                getContext().getString(R.string.google_account_type_format, GOOGLE_DISPLAY_LABEL),
                pair.second); // Google Account
    }

    public void testGetAccountInfo_AccountName_NoDisplayLabel() {
        final Pair pair = EditorUiUtils.getAccountInfo(getContext(),
                ACCOUNT_NAME, new MockAccountType(/* displayLabel =*/ null));

        assertNotNull(pair);
        assertEquals(getContext().getString(R.string.from_account_format, ACCOUNT_NAME),
                pair.first); // somebody@lunkedin.com
        assertEquals(
                getContext().getString(R.string.account_type_format,
                        getContext().getString(R.string.account_phone)),
                pair.second); // "Phone-only, unsynced contact"
    }

    public void testGetAccountInfo_NoAccountName_DisplayLabel() {
        final Pair pair = EditorUiUtils.getAccountInfo(getContext(),
                /* accountName =*/ null, new MockAccountType(DISPLAY_LABEL));

        assertNotNull(pair);
        assertNull(pair.first);
        assertEquals(getContext().getString(R.string.account_type_format, DISPLAY_LABEL),
                pair.second); // LunkedIn contact
    }

    public void testGetAccountInfo_NoAccountName_NoDisplayLabel() {
        final Pair pair = EditorUiUtils.getAccountInfo(getContext(),
                /* accountName =*/ null, new MockAccountType(/* displayLabel =*/ null));

        assertNotNull(pair);
        assertNull(pair.first);
        assertEquals(
                getContext().getString(R.string.account_type_format,
                        getContext().getString(R.string.account_phone)),
                pair.second); // "Phone-only, unsynced contact"
    }

    public void testGetRingtongStrFromUri_lessThanOrEqualsToM() {
        final int currentVersion = Build.VERSION_CODES.M;
        assertNull(EditorUiUtils.getRingtoneStringFromUri(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneStringFromUri(Settings.System.DEFAULT_RINGTONE_URI,
                currentVersion));
        assertEquals(RINGTONE, EditorUiUtils.getRingtoneStringFromUri(Uri.parse(RINGTONE),
                        currentVersion));
    }

    public void testGetRingtongStrFromUri_nOrGreater() {
        final int currentVersion = Build.VERSION_CODES.M + 1;
        assertEquals("", EditorUiUtils.getRingtoneStringFromUri(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneStringFromUri(Settings.System.DEFAULT_RINGTONE_URI,
                currentVersion));
        assertEquals(RINGTONE, EditorUiUtils.getRingtoneStringFromUri(Uri.parse(RINGTONE),
                        currentVersion));
    }

    public void testGetRingtongUriFromStr_lessThanOrEqualsToM() {
        final int currentVersion = Build.VERSION_CODES.M;
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), EditorUiUtils
                        .getRingtoneUriFromString(null, currentVersion));
        assertEquals(Uri.parse(""), EditorUiUtils.getRingtoneUriFromString("", currentVersion));
        assertEquals(Uri.parse(RINGTONE), EditorUiUtils.getRingtoneUriFromString(RINGTONE,
                currentVersion));
    }

    public void testGetRingtongUriFromStr_nOrGreater() {
        final int currentVersion = Build.VERSION_CODES.M + 1;
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), EditorUiUtils
                        .getRingtoneUriFromString(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneUriFromString("", currentVersion));
        assertEquals(Uri.parse(RINGTONE), EditorUiUtils.getRingtoneUriFromString(RINGTONE,
                currentVersion));
    }

}
