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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;

import android.os.Bundle;

public class GroupDetailActivity extends ContactsActivity {

    private static final String TAG = "GroupDetailActivity";

    public static final String KEY_ACCOUNT_TYPE = "accountType";
    public static final String KEY_ACCOUNT_NAME = "accountName";
    public static final String KEY_GROUP_ID = "groupId";
    public static final String KEY_GROUP_SOURCE_ID = "groupSourceId";
    public static final String KEY_GROUP_READ_ONLY = "groupReadOnly";
    public static final String KEY_GROUP_TITLE = "title";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // TODO: Create Intent Resolver to handle the different ways users can get to this list.
        // TODO: Handle search or key down

        setContentView(R.layout.group_detail_activity);
    }
}
