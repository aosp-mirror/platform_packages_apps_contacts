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
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.vcard.ExportVCardActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportExportDialogFragment";

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(),
                android.R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(android.R.layout.select_dialog_item, parent, false));

                final int resId = getItem(position);
                result.setText(resId);
                return result;
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
            @Override
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
                        Intent exportIntent = new Intent(getActivity(), ExportVCardActivity.class);
                        getActivity().startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.dialog_import_export)
                .setNegativeButton(android.R.string.cancel, null)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void doShareVisibleContacts() {
        // TODO move the query into a loader and do this in a background thread
        final Cursor cursor = getActivity().getContentResolver().query(Contacts.CONTENT_URI,
                LOOKUP_PROJECTION, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
        if (cursor != null) {
            try {
                if (!cursor.moveToFirst()) {
                    Toast.makeText(getActivity(), R.string.share_error, Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder uriListBuilder = new StringBuilder();
                int index = 0;
                do {
                    if (index != 0)
                        uriListBuilder.append(':');
                    uriListBuilder.append(cursor.getString(0));
                    index++;
                } while (cursor.moveToNext());
                Uri uri = Uri.withAppendedPath(
                        Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                getActivity().startActivity(intent);
            } finally {
                cursor.close();
            }
        }
    }

    private void handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            ImportExportAccountSelectorDialog.show(getFragmentManager(), resId);
            return;
        }

        AccountSelectionUtil.doImport(getActivity(), resId,
                (size == 1 ? accountList.get(0) : null));
    }

    /** Sub-Dialog for showing an account selector in case there are several accounts */
    public static class ImportExportAccountSelectorDialog extends SelectAccountDialogFragment {
        private static final String SELECTOR_TAG = "ImportExportAccountSelectorDialog";
        private static final String BUNDLE_RES_ID = "resourceId";

        public static void show(FragmentManager manager, int resId) {
            final ImportExportAccountSelectorDialog dialog =
                new ImportExportAccountSelectorDialog();
            final Bundle bundle = new Bundle();
            bundle.putInt(BUNDLE_RES_ID, resId);
            dialog.setArguments(bundle);
            dialog.show(manager, SELECTOR_TAG);
        }

        @Override
        protected void onAccountSelected(AccountWithDataSet account) {
            final int resourceId = getArguments().getInt(BUNDLE_RES_ID);
            AccountSelectionUtil.doImport(getActivity(), resourceId, account);
        }
    }
}
