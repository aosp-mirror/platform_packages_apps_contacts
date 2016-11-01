package com.android.contacts.common.model;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Created by mhagerott on 10/6/16.
 */

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimContactTests {
    @Test
    public void parcelRoundtrip() {
        assertParcelsCorrectly(new SimContact(1, "name1", "phone1",
                new String[] { "email1a", "email1b" }));
        assertParcelsCorrectly(new SimContact(2, "name2", "phone2", null, 2));
        assertParcelsCorrectly(new SimContact(3, "name3", null,
                new String[] { "email3" }));
        assertParcelsCorrectly(new SimContact(4, null, "phone4",
                new String[] { "email4" }));
        assertParcelsCorrectly(new SimContact(5, null, null, null));
        assertParcelsCorrectly(new SimContact(6, "name6", "phone6",
                new String[0]));
    }

    private void assertParcelsCorrectly(SimContact contact) {
        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(contact, 0);
        parcel.setDataPosition(0);
        final SimContact unparceled = parcel.readParcelable(
                SimContact.class.getClassLoader());
        assertThat(unparceled, equalTo(contact));
        parcel.recycle();
    }
}
