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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SimpleCursorAdapter;

/**
 * Class used for displaying a dialog with a list of phone numbers of which
 * one will be chosen to make a call or initiate an sms message.
 */
public class PhoneDisambigDialog implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener, CompoundButton.OnCheckedChangeListener{

    private boolean mMakePrimary = false;
    private Context mContext;
    private AlertDialog mDialog;
    private boolean mSendSms;
    private Cursor mPhonesCursor;

    public PhoneDisambigDialog(Context context, Cursor phonesCursor) {
        this(context, phonesCursor, false /*make call*/);
    }

    public PhoneDisambigDialog(Context context, Cursor phonesCursor, boolean sendSms) {
        mContext = context;
        mSendSms = sendSms;
        mPhonesCursor = phonesCursor;

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View setPrimaryView = inflater.
                inflate(R.layout.set_primary_checkbox, null);
        ((CheckBox) setPrimaryView.findViewById(R.id.setPrimary)).
                setOnCheckedChangeListener(this);

        // Need to show disambig dialogue.
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext).
            setCursor(mPhonesCursor, this, Phone.NUMBER).
                    setTitle(sendSms ? R.string.sms_disambig_title : R.string.call_disambig_title).
                    setView(setPrimaryView);

        mDialog = dialogBuilder.create();
    }

    /**
     * Show the dialog.
     */
    public void show() {
        mDialog.show();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mPhonesCursor.moveToPosition(which)) {
            long id = mPhonesCursor.getLong(mPhonesCursor.getColumnIndex(Data._ID));
            String phone = mPhonesCursor.getString(mPhonesCursor.getColumnIndex(Phone.NUMBER));
            if (mMakePrimary) {
                ContentValues values = new ContentValues(1);
                values.put(Data.IS_SUPER_PRIMARY, 1);
                mContext.getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, id),
                        values, null, null);
            }

            if (mSendSms) {
                ContactsUtils.initiateSms(mContext, phone);
            } else {
                ContactsUtils.initiateCall(mContext, phone);
            }
        } else {
            dialog.dismiss();
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mMakePrimary = isChecked;
    }

    public void onDismiss(DialogInterface dialog) {
        mPhonesCursor.close();
    }
}
