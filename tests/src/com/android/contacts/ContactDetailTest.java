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

package com.android.contacts;

import android.test.ActivityUnitTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.activities.ContactDetailActivity;
import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider;

@SmallTest
public class ContactDetailTest extends ActivityUnitTestCase<ContactDetailActivity> {
    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;

    public ContactDetailTest() {
        super(ContactDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        setActivityContext(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

//    public void testFoo() {
//        // Use lookup-style Uris that also contain the Contact-ID
//        //long rawContactId1 = mCreator.createRawContact("JohnDoe", "John", "Doe");
//        //long contactId1 = mCreator.getContactIdByRawContactId(rawContactId1);
//        //Uri contactUri1 = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId1);
//        Intent intent = new Intent(Intent.ACTION_VIEW,
//                ContentUris.withAppendedId(Contacts.CONTENT_URI, 123));
//        startActivity(intent, null, null);
//        ContactDetailActivity activity = getActivity();
//        mContactsProvider.verify();
//    }
}
