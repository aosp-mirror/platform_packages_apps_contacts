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
package com.android.contacts.interactions;


import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import com.android.contacts.Collapser;
import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.model.AccountType.StringInflater;
import com.android.contacts.model.AccountTypeManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Initiates phone calls or a text message.
 */
public class PhoneNumberInteraction
        implements OnLoadCompleteListener<Cursor>, OnClickListener {

    public static final String EXTRA_KEY_ITEMS = "items";

    /**
     * A model object for capturing a phone number for a given contact.
     */
    static class PhoneItem implements Parcelable, Collapsible<PhoneItem> {
        long id;
        String phoneNumber;
        String accountType;
        long type;
        String label;

        public static Parcelable.Creator<PhoneItem> CREATOR = new Creator<PhoneItem>() {

            public PhoneItem[] newArray(int size) {
                return new PhoneItem[size];
            }

            public PhoneItem createFromParcel(Parcel source) {
                PhoneItem item = new PhoneItem();
                item.id = source.readLong();
                item.phoneNumber = source.readString();
                item.accountType = source.readString();
                item.type = source.readLong();
                item.label = source.readString();
                return item;
            }
        };

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeString(phoneNumber);
            dest.writeString(accountType);
            dest.writeLong(type);
            dest.writeString(label);
        }

        public int describeContents() {
            return 0;
        }

        public boolean collapseWith(PhoneItem phoneItem) {
            if (!shouldCollapseWith(phoneItem)) {
                return false;
            }
            // Just keep the number and id we already have.
            return true;
        }

        public boolean shouldCollapseWith(PhoneItem phoneItem) {
            try {
                PhoneNumberUtil util = PhoneNumberUtil.getInstance();
                PhoneNumber phoneNumber1 = util.parse(phoneNumber, "ZZ" /* Unknown */);
                PhoneNumber phoneNumber2 = util.parse(phoneItem.phoneNumber, "ZZ" /* Unknown */);
                MatchType matchType = util.isNumberMatch(phoneNumber1, phoneNumber2);
                if (matchType == MatchType.SHORT_NSN_MATCH) {
                    return true;
                }
            } catch (NumberParseException e) {
                return TextUtils.equals(phoneNumber, phoneItem.phoneNumber);
            }
            return false;
        }

        @Override
        public String toString() {
            return phoneNumber;
        }
    }

    /**
     * A list adapter that populates the list of contact's phone numbers.
     */
    private class PhoneItemAdapter extends ArrayAdapter<PhoneItem> {
        private final AccountTypeManager mAccountTypes;

        public PhoneItemAdapter(Context context) {
            super(context, R.layout.phone_disambig_item, android.R.id.text2);
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            PhoneItem item = getItem(position);
            AccountType accountType = mAccountTypes.getAccountType(item.accountType);

            // Obtain a string representation of the phone type specific to the
            // account type associated with that phone number
            TextView typeView = (TextView)view.findViewById(android.R.id.text1);
            DataKind kind = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
            if (kind != null) {
                ContentValues values = new ContentValues();
                values.put(Phone.TYPE, item.type);
                values.put(Phone.LABEL, item.label);
                StringInflater header = mSendTextMessage ? kind.actionAltHeader : kind.actionHeader;
                typeView.setText(header.inflateUsing(getContext(), values));
            } else {
                typeView.setText(R.string.call_other);
            }
            return view;
        }
    }

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

    private final Context mContext;
    private final OnDismissListener mDismissListener;
    private final boolean mSendTextMessage;

    private CursorLoader mLoader;

    public PhoneNumberInteraction(Context context, boolean sendTextMessage,
            DialogInterface.OnDismissListener dismissListener) {
        mContext = context;
        mSendTextMessage = sendTextMessage;
        mDismissListener = dismissListener;
    }

    private void performAction(String phoneNumber) {
        Intent intent;
        if (mSendTextMessage) {
            intent = new Intent(
                    Intent.ACTION_SENDTO, Uri.fromParts("sms", phoneNumber, null));
        } else {
            intent = new Intent(
                    Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("tel", phoneNumber, null));

        }
        startActivity(intent);
    }

    /**
     * Initiates the interaction. This may result in a phone call or sms message started
     * or a disambiguation dialog to determine which phone number should be used.
     */
    public void startInteraction(Uri contactUri) {
        if (mLoader != null) {
            mLoader.reset();
        }

        mLoader = new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY),
                PHONE_NUMBER_PROJECTION,
                PHONE_NUMBER_SELECTION,
                null,
                null);
        mLoader.registerListener(0, this);
        mLoader.startLoading();
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) {
            onDismiss();
            return;
        }

        ArrayList<PhoneItem> phoneList = new ArrayList<PhoneItem>();
        String primaryPhone = null;
        try {
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                    // Found super primary, call it.
                    primaryPhone = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    break;
                }

                PhoneItem item = new PhoneItem();
                item.id = cursor.getLong(cursor.getColumnIndex(Data._ID));
                item.phoneNumber = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                item.accountType =
                        cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
                item.type = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));
                item.label = cursor.getString(cursor.getColumnIndex(Phone.LABEL));

                phoneList.add(item);
            }
        } finally {
            cursor.close();
        }

        if (primaryPhone != null) {
            performAction(primaryPhone);
            onDismiss();
            return;
        }

        Collapser.collapseList(phoneList);

        if (phoneList.size() == 0) {
            onDismiss();
        } else if (phoneList.size() == 1) {
            onDismiss();
            performAction(phoneList.get(0).phoneNumber);
        } else {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(EXTRA_KEY_ITEMS, phoneList);
            showDialog(getDialogId(), bundle);
        }
    }

    private void onDismiss() {
        if (mDismissListener != null) {
            mDismissListener.onDismiss(null);
        }
    }

    private int getDialogId() {
        return mSendTextMessage
                ? R.id.dialog_phone_number_message_disambiguation
                : R.id.dialog_phone_number_call_disambiguation;
    }

    public Dialog onCreateDialog(int id, Bundle bundle) {
        if (id != getDialogId()) {
            return null;
        }

        LayoutInflater inflater = LayoutInflater.from(mContext);
        View setPrimaryView = inflater.inflate(R.layout.set_primary_checkbox, null);
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setAdapter(new PhoneItemAdapter(mContext), this)
                .setView(setPrimaryView)
                .setTitle(mSendTextMessage
                        ? R.string.sms_disambig_title
                        : R.string.call_disambig_title)
                .create();
        dialog.setOnDismissListener(mDismissListener);
        return dialog;
    }

    public boolean onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        if (id != getDialogId()) {
            return false;
        }

        ArrayList<PhoneItem> phoneList = bundle.getParcelableArrayList(EXTRA_KEY_ITEMS);

        AlertDialog alertDialog = (AlertDialog)dialog;
        PhoneItemAdapter adapter = (PhoneItemAdapter)alertDialog.getListView().getAdapter();
        adapter.clear();
        adapter.addAll(phoneList);

        return true;
    }

    /**
     * Handles the user selection in the disambiguation dialog.
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        AlertDialog alertDialog = (AlertDialog)dialog;
        PhoneItemAdapter adapter = (PhoneItemAdapter)alertDialog.getListView().getAdapter();
        PhoneItem phoneItem = adapter.getItem(which);
        if (phoneItem != null) {
            long id = phoneItem.id;
            String phone = phoneItem.phoneNumber;

            CheckBox checkBox = (CheckBox)alertDialog.findViewById(R.id.setPrimary);
            if (checkBox.isChecked()) {
                makePrimary(id);
            }

            performAction(phone);
        }
    }

    /**
     * Makes the selected phone number primary.
     */
    void makePrimary(long id) {
        final Intent intent = ContactSaveService.createSetSuperPrimaryIntent(mContext, id);
        mContext.startService(intent);
    }

    /* Visible for testing */
    void showDialog(int dialogId, Bundle bundle) {
        Activity activity = (Activity)mContext;
        if (!activity.isFinishing()) {
            activity.showDialog(dialogId, bundle);
        }
    }

    /* Visible for testing */
    void startActivity(Intent intent) {
        mContext.startActivity(intent);
    }

    /* Visible for testing */
    CursorLoader getLoader() {
        return mLoader;
    }
}
