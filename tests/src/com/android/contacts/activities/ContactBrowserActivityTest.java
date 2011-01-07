/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.activities;

import com.android.contacts.R;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;

import android.content.Intent;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.ProviderStatus;
import android.test.ActivityUnitTestCase;

/**
 * Tests for {@link ContactBrowserActivity}.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
public class ContactBrowserActivityTest
        extends ActivityUnitTestCase<ContactBrowserActivity>
{
    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;

    public ContactBrowserActivityTest() {
        super(ContactBrowserActivity.class);
    }

    @Override
    public void setUp() {
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        setActivityContext(mContext);
    }

    public void testSingleAccountNoGroups() {

        // TODO: actually simulate a single account

        expectProviderStatusQueryAndReturnNormal();
        expectGroupsQueryAndReturnEmpty();

        Intent intent = new Intent(Intent.ACTION_DEFAULT);

        ContactBrowserActivity activity = startActivity(intent, null, null);

        getInstrumentation().callActivityOnResume(activity);
        getInstrumentation().callActivityOnStart(activity);

        mContext.waitForLoaders(activity, R.id.contact_list_filter_loader);

        getInstrumentation().waitForIdleSync();

        mContext.verify();
    }

    private void expectProviderStatusQueryAndReturnNormal() {
        mContactsProvider
                .expectQuery(ProviderStatus.CONTENT_URI)
                .withProjection(ProviderStatus.STATUS, ProviderStatus.DATA1)
                .returnRow(ProviderStatus.STATUS_NORMAL, null);
    }

    private void expectGroupsQueryAndReturnEmpty() {
        mContactsProvider
                .expectQuery(Groups.CONTENT_URI)
                .withAnyProjection()
                .withAnySelection()
                .returnEmptyCursor();
    }
}
