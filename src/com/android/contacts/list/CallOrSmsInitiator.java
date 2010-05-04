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
package com.android.contacts.list;

import com.android.contacts.ContactsUtils;
import com.android.contacts.PhoneDisambigDialog;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;

/**
 * Initiates phone calls or SMS messages.
 */
public class CallOrSmsInitiator {

    private final Context mContext;
    private AsyncQueryHandler mQueryHandler;
    private int mCurrentToken;
    private boolean mSendSms;

    private static final String[] PHONE_NUMBER_PROJECTION = new String[] {
            Phone._ID,
            Phone.NUMBER,
            Phone.IS_SUPER_PRIMARY,
            RawContacts.ACCOUNT_TYPE,
            Phone.TYPE,
            Phone.LABEL
    };

    private static final String PHONE_NUMBER_SELECTION = Data.MIMETYPE + "='"
            + Phone.CONTENT_ITEM_TYPE + "' AND " + Phone.NUMBER + " NOT NULL";

    public CallOrSmsInitiator(Context context) {
        this.mContext = context;
        mQueryHandler = new AsyncQueryHandler(context.getContentResolver()) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                onPhoneNumberQueryComplete(token, cookie, cursor);
            }
        };
    }

    protected void onPhoneNumberQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            cursor.close();
            return;
        }

        if (token != mCurrentToken) { // Stale query, ignore
            cursor.close();
            return;
        }

        String phone = null;
        if (cursor.getCount() == 1) {
            // only one number, call it.
            cursor.moveToFirst();
            phone = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
        } else {
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                    // Found super primary, call it.
                    phone = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    break;
                }
            }
        }

        if (phone == null) {
            // Display dialog to choose a number to call.
            PhoneDisambigDialog phoneDialog = new PhoneDisambigDialog(mContext, cursor, mSendSms);
            phoneDialog.show();
        } else {
            if (mSendSms) {
                ContactsUtils.initiateSms(mContext, phone);
            } else {
                ContactsUtils.initiateCall(mContext, phone);
            }
        }
    }

    /**
     * Initiates a phone call with the specified contact. If necessary, displays
     * a disambiguation dialog to see which number to call.
     */
    public void initiateCall(Uri contactUri) {
        callOrSendSms(contactUri, false);
    }

    /**
     * Initiates a text message to the specified contact. If necessary, displays
     * a disambiguation dialog to see which number to call.
     */
    public void initiateSms(Uri contactUri) {
        callOrSendSms(contactUri, true);
    }

    private void callOrSendSms(Uri contactUri, boolean sendSms) {
        mCurrentToken++;
        mSendSms = sendSms;
        Uri dataUri = Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY);
        mQueryHandler.startQuery(mCurrentToken, dataUri, dataUri, PHONE_NUMBER_PROJECTION,
                PHONE_NUMBER_SELECTION, null, null);
    }
}
