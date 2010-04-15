package com.android.contacts;

import com.android.contacts.activities.ContactDetailActivity;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.views.detail.ContactLoader;

import android.content.ContentUris;
import android.content.Intent;
import android.provider.ContactsContract.Contacts;
import android.test.ActivityUnitTestCase;

public class ContactDetailTest extends ActivityUnitTestCase<ContactDetailActivity> {
    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;

    public ContactDetailTest() {
        super(ContactDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ContactLoader.setSynchronous(true);
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        setActivityContext(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        ContactLoader.setSynchronous(false);
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
