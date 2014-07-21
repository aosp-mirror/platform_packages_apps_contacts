/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.contacts.common.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract.Contacts.Data;
import android.test.AndroidTestCase;

import com.android.contacts.common.Collapser;
import com.android.contacts.common.model.account.BaseAccountType;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;

import java.util.ArrayList;

/**
 * Test case for {@link DataItem}.
 */
public class DataItemTests extends AndroidTestCase {

    public void testDataItemPrimaryCollapsing() {
        ContentValues cv1 = new ContentValues();
        ContentValues cv2 = new ContentValues();

        cv1.put(Data._ID, 1);
        cv2.put(Data._ID, 2);

        cv1.put(Data.IS_SUPER_PRIMARY, 1);
        cv2.put(Data.IS_PRIMARY, 0);

        DataItem data1 = DataItem.createFrom(cv1);
        DataItem data2 = DataItem.createFrom(cv2);

        DataKind kind = new DataKind("test.mimetype", 0, 0, false);
        kind.actionBody = new BaseAccountType.SimpleInflater(0);
        data1.setDataKind(kind);
        data2.setDataKind(kind);

        ArrayList<DataItem> dataList = new ArrayList<>(2);
        dataList.add(data1);
        dataList.add(data2);

        Collapser.collapseList(dataList, getContext());

        assertEquals(1, dataList.size());
        assertEquals(true, dataList.get(0).isSuperPrimary());
        assertEquals(true, dataList.get(0).isPrimary());
    }
}
