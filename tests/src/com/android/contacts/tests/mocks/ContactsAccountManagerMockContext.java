/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.tests.mocks;

import android.content.Context;

import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.model.AccountTypeManager;

/**
 * A ContactsMockContext with an additional mock AccountTypeManager.
 */
public class ContactsAccountManagerMockContext extends ContactsMockContext {

    private MockAccountTypeManager mMockAccountTypeManager;


    public ContactsAccountManagerMockContext(Context base) {
        super(base);
    }

    public void setMockAccountTypeManager(MockAccountTypeManager mockAccountTypeManager) {
        mMockAccountTypeManager = mockAccountTypeManager;
    }

    @Override
    public Object getSystemService(String name) {
        if (AccountTypeManager.ACCOUNT_TYPE_SERVICE.equals(name)) {
            return mMockAccountTypeManager;
        }
        return super.getSystemService(name);
    }
}
