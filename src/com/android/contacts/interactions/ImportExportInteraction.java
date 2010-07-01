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

import com.android.contacts.R;
import com.android.contacts.model.Sources;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.vcard.ExportVCardActivity;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * An interaction invoked to import/export contacts.
 */
public class ImportExportInteraction {

    private static final String TAG = "ImportExportInteraction";

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    private final Context mContext;

    public ImportExportInteraction(Context context) {
        this.mContext = context;
    }

    /**
     * Creates a {@link Dialog} that allows the user to choose between a bulk import
     * and bulk export task across all contacts.
     */
    public void startInteraction() {
        showDialog(R.id.dialog_import_export_options, null);
    }

    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_import_export_options: {
                return createOptionsDialog();
            }
            case R.string.import_from_sim:
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(mContext, id);
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
        }

        return null;
    }

    private Dialog createOptionsDialog() {
        // Wrap our context to inflate list items using the correct theme
        final Context dialogContext =
                new ContextThemeWrapper(mContext, android.R.style.Theme_Light);
        final Resources res = dialogContext.getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(mContext,
                android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                            parent, false);
                }

                final int resId = this.getItem(position);
                ((TextView)convertView).setText(resId);
                return convertView;
            }
        };

        if (TelephonyManager.getDefault().hasIccCard()) {
            adapter.add(R.string.import_from_sim);
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            adapter.add(R.string.export_to_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            adapter.add(R.string.share_visible_contacts);
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_sim:
                    case R.string.import_from_sdcard: {
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        Intent exportIntent = new Intent(mContext, ExportVCardActivity.class);
                        mContext.startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: " +
                                mContext.getResources().getResourceEntryName(resId));
                    }
                }
            }
        };

        return new AlertDialog.Builder(mContext)
                .setTitle(R.string.dialog_import_export)
                .setNegativeButton(android.R.string.cancel, null)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void doShareVisibleContacts() {

        // TODO move the query into a loader
        final Cursor cursor = mContext.getContentResolver().query(Contacts.CONTENT_URI,
                LOOKUP_PROJECTION, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder uriListBuilder = new StringBuilder();
            int index = 0;
            while (cursor.moveToNext()) {
                if (index != 0)
                    uriListBuilder.append(':');
                uriListBuilder.append(cursor.getString(0));
                index++;
            }
            Uri uri = Uri.withAppendedPath(
                    Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(uriListBuilder.toString()));

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Contacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            mContext.startActivity(intent);
        } finally {
            cursor.close();
        }
    }

    private void handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final Sources sources = Sources.getInstance(mContext);
        final List<Account> accountList = sources.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            showDialog(resId, null);
            return;
        }

        AccountSelectionUtil.doImport(mContext, resId, (size == 1 ? accountList.get(0) : null));
    }

    /* Visible for testing */
    void showDialog(int dialogId, Bundle bundle) {
        ((Activity)mContext).showDialog(dialogId, bundle);
    }
}
