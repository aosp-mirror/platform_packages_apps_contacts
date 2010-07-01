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

import com.android.contacts.interactions.PhoneNumberInteraction;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;

/**
 * An interstitial activity used when the user selects a QSB search suggestion using
 * a call button.
 */
public class CallContactActivity extends Activity implements OnDismissListener {

    private PhoneNumberInteraction mPhoneNumberInteraction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPhoneNumberInteraction = new PhoneNumberInteraction(this, false, this);

        Uri contactUri = getIntent().getData();
        if (contactUri == null) {
            finish();
        }

        // If we are being invoked with a saved state, rely on Activity to restore it
        if (savedInstanceState != null) {
            return;
        }

        if (Contacts.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(contactUri))) {
            mPhoneNumberInteraction.startInteraction(contactUri);
        } else {
            startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, contactUri));
            finish();
        }
    }

    public void onDismiss(DialogInterface dialog) {
        if (!isChangingConfigurations()) {
            finish();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        return mPhoneNumberInteraction.onCreateDialog(id, args);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        mPhoneNumberInteraction.onPrepareDialog(id, dialog, args);
    }
}
