/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.contacts.list.CallOrSmsInitiator;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;

/**
 * An interstitial activity used when the user selects a QSB search suggestion using
 * a call button.
 */
public class CallContactActivity extends Activity implements OnDismissListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri data = getIntent().getData();
        if (data == null) {
            finish();
        }

        if (Contacts.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(data))) {
            CallOrSmsInitiator initiator = new CallOrSmsInitiator(this);
            initiator.setOnDismissListener(this);
            initiator.initiateCall(data);
        } else {
            startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, data));
            finish();
        }
    }

    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
