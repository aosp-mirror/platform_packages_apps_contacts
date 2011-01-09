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

import com.android.contacts.ContactsApplication;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.FallbackAccountType;
import com.android.contacts.test.InjectedServices;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockAccountTypeManager;
import com.android.contacts.tests.mocks.MockContentProvider;

import android.accounts.Account;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.Settings;
import android.test.ActivityInstrumentationTestCase2;

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
        extends ActivityInstrumentationTestCase2<ContactBrowserActivity>
{
    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;

    public ContactBrowserActivityTest() {
        super(ContactBrowserActivity.class);
    }

    @Override
    public void setUp() {
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        mSettingsProvider = mContext.getSettingsProvider();
        InjectedServices services = new InjectedServices();
        services.setContentResolver(mContext.getContentResolver());

        FallbackAccountType accountType = new FallbackAccountType();
        accountType.accountType = "testAccountType";

        Account account = new Account("testAccount", "testAccountType");

        services.setSystemService(AccountTypeManager.ACCOUNT_TYPE_SERVICE,
                new MockAccountTypeManager(
                        new AccountType[] { accountType }, new Account[] { account }));
        ContactsApplication.injectServices(services);
    }

    public void testSingleAccountNoGroups() {
        expectSettingsQueriesAndReturnDefault();
        expectProviderStatusQueryAndReturnNormal();
        expectGroupsQueryAndReturnEmpty();
        expectContactListAndReturnEmpty();

        setActivityIntent(new Intent(Intent.ACTION_DEFAULT));

        ContactBrowserActivity activity = getActivity();

        getInstrumentation().waitForIdleSync();

        mContext.waitForLoaders(activity.getLoaderManager(), R.id.contact_list_filter_loader);

        getInstrumentation().waitForIdleSync();

        mContext.verify();
    }

    private void expectSettingsQueriesAndReturnDefault() {
        mSettingsProvider
                .expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.DISPLAY_ORDER)
                .returnRow(ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY)
                .anyNumberOfTimes();
        mSettingsProvider
                .expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.SORT_ORDER)
                .returnRow(ContactsContract.Preferences.SORT_ORDER_PRIMARY)
                .anyNumberOfTimes();
    }

    private void expectProviderStatusQueryAndReturnNormal() {
        mContactsProvider
                .expectQuery(ProviderStatus.CONTENT_URI)
                .withProjection(ProviderStatus.STATUS, ProviderStatus.DATA1)
                .returnRow(ProviderStatus.STATUS_NORMAL, null)
                .anyNumberOfTimes();
    }

    private void expectGroupsQueryAndReturnEmpty() {
        mContactsProvider
                .expectQuery(Groups.CONTENT_URI)
                .withAnyProjection()
                .withAnySelection()
                .returnEmptyCursor()
                .anyNumberOfTimes();
    }

    private void expectContactListAndReturnEmpty() {
        Uri uri = Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true")
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();

        mContactsProvider
                .expectQuery(uri)
                .withAnyProjection()
                .withAnySelection()
                .withAnySortOrder()
                .returnEmptyCursor();
    }
}
