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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactsActivity;
import com.android.contacts.DialtactsActivity;
import com.android.contacts.util.PhoneCapabilityTester;

import android.content.Intent;
import android.os.Bundle;

public class ContactsFrontDoor extends ContactsActivity {
    public static final String EXTRA_FRONT_DOOR = "front_door";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        Intent originalIntent = getIntent();
        Intent intent = new Intent();
        intent.setAction(originalIntent.getAction());
        intent.setDataAndType(originalIntent.getData(), originalIntent.getType());
        intent.setFlags(
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_FRONT_DOOR, true);

        if (PhoneCapabilityTester.isPhone(this)) {
            // Default to the normal dialtacts layout
            intent.setClass(this, DialtactsActivity.class);
        } else {
            // No tabs, just a contact list
            intent.setClass(this, ContactBrowserActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
