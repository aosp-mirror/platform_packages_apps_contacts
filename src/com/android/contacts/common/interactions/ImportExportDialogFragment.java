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

package com.android.contacts.common.interactions;

import android.app.Activity;
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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.R;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountSelectionUtil;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.vcard.ExportVCardActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;
import com.android.contacts.common.vcard.ShareVCardActivity;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;

import java.util.List;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment
        implements SelectAccountDialogFragment.Listener {
    public static final String TAG = "ImportExportDialogFragment";

    public static final int EXPORT_MODE_FAVORITES = 0;
    public static final int EXPORT_MODE_ALL_CONTACTS = 1;
    public static final int EXPORT_MODE_DEFAULT = -1;

    private static final String KEY_RES_ID = "resourceId";
    private static final String KEY_SUBSCRIPTION_ID = "subscriptionId";
    private static final String ARG_CONTACTS_ARE_AVAILABLE = "CONTACTS_ARE_AVAILABLE";

    private static int mExportMode = EXPORT_MODE_DEFAULT;

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    private SubscriptionManager mSubscriptionManager;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager, boolean contactsAreAvailable,
                            Class callingActivity, int exportMode) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONTACTS_ARE_AVAILABLE, contactsAreAvailable);
        args.putString(VCardCommonArguments.ARG_CALLING_ACTIVITY, callingActivity.getName());
        fragment.setArguments(args);
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
        mExportMode = exportMode;
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        AnalyticsUtil.sendScreenView(this);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final boolean contactsAreAvailable = getArguments().getBoolean(ARG_CONTACTS_ARE_AVAILABLE);
        final String callingActivity = getArguments().getString(
                VCardCommonArguments.ARG_CALLING_ACTIVITY);

        // Adapter that shows a list of string resources
        final ArrayAdapter<AdapterEntry> adapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                result.setText(getItem(position).mLabel);
                return result;
            }
        };

        final TelephonyManager manager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        if (res.getBoolean(R.bool.config_allow_import_from_vcf_file)) {
            adapter.add(new AdapterEntry(getString(R.string.import_from_vcf_file),
                    R.string.import_from_vcf_file));
        }

        if (CompatUtils.isMSIMCompatible()) {
            mSubscriptionManager = SubscriptionManager.from(getActivity());
            if (manager != null && res.getBoolean(R.bool.config_allow_sim_import)) {
                List<SubscriptionInfo> subInfoRecords = null;
                try {
                    subInfoRecords =  mSubscriptionManager.getActiveSubscriptionInfoList();
                } catch (SecurityException e) {
                    Log.w(TAG, "SecurityException thrown, lack permission for"
                            + " getActiveSubscriptionInfoList", e);
                }
                if (subInfoRecords != null) {
                    if (subInfoRecords.size() == 1) {
                        adapter.add(new AdapterEntry(getString(R.string.import_from_sim),
                                R.string.import_from_sim, subInfoRecords.get(0).getSubscriptionId()));
                    } else {
                        for (SubscriptionInfo record : subInfoRecords) {
                            adapter.add(new AdapterEntry(getSubDescription(record),
                                    R.string.import_from_sim, record.getSubscriptionId()));
                        }
                    }
                }
            }
        } else {
            if (manager != null && manager.hasIccCard()
                    && res.getBoolean(R.bool.config_allow_sim_import)) {
                adapter.add(new AdapterEntry(getString(R.string.import_from_sim),
                        R.string.import_from_sim, -1));
            }
        }

        if (res.getBoolean(R.bool.config_allow_export)) {
            if (contactsAreAvailable) {
                adapter.add(new AdapterEntry(getString(R.string.export_to_vcf_file),
                        R.string.export_to_vcf_file));
            }
        }
        if (res.getBoolean(R.bool.config_allow_share_contacts) && contactsAreAvailable) {
            if (mExportMode == EXPORT_MODE_FAVORITES) {
                // share favorite and frequently contacted contacts from Favorites tab
                adapter.add(new AdapterEntry(getString(R.string.share_favorite_contacts),
                        R.string.share_contacts));
            } else {
                // share "all" contacts (in groups selected in "Customize") from All tab for now
                // TODO: change the string to share_visible_contacts if implemented
                adapter.add(new AdapterEntry(getString(R.string.share_contacts),
                        R.string.share_contacts));
            }
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = adapter.getItem(which).mChoiceResourceId;
                if (resId == R.string.import_from_sim || resId == R.string.import_from_vcf_file) {
                        dismissDialog = handleImportRequest(resId,
                                adapter.getItem(which).mSubscriptionId);
                } else if (resId == R.string.export_to_vcf_file) {
                    dismissDialog = true;
                    final Intent exportIntent = new Intent(
                            getActivity(), ExportVCardActivity.class);
                    exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
                            callingActivity);
                    getActivity().startActivity(exportIntent);
                } else if (resId == R.string.share_contacts) {
                    dismissDialog = true;
                    if (mExportMode == EXPORT_MODE_FAVORITES) {
                        doShareFavoriteContacts();
                    } else { // EXPORT_MODE_ALL_CONTACTS
                        final Intent exportIntent = new Intent(
                                getActivity(), ShareVCardActivity.class);
                        exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
                                callingActivity);
                        getActivity().startActivity(exportIntent);
                    }
                } else {
                    dismissDialog = true;
                    Log.e(TAG, "Unexpected resource: "
                            + getActivity().getResources().getResourceEntryName(resId));
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(contactsAreAvailable
                        ? R.string.dialog_import_export
                        : R.string.dialog_import)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void doShareFavoriteContacts() {
        try{
            final Cursor cursor = getActivity().getContentResolver().query(
                    Contacts.CONTENT_STREQUENT_URI, LOOKUP_PROJECTION, null, null,
                    Contacts.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (cursor != null) {
                try {
                    if (!cursor.moveToFirst()) {
                        Toast.makeText(getActivity(), R.string.no_contact_to_share,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Build multi-vcard Uri for sharing
                    final StringBuilder uriListBuilder = new StringBuilder();
                    int index = 0;
                    do {
                        if (index != 0)
                            uriListBuilder.append(':');
                        uriListBuilder.append(cursor.getString(0));
                        index++;
                    } while (cursor.moveToNext());
                    final Uri uri = Uri.withAppendedPath(
                            Contacts.CONTENT_MULTI_VCARD_URI,
                            Uri.encode(uriListBuilder.toString()));

                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(Contacts.CONTENT_VCARD_TYPE);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    ImplicitIntentsUtil.startActivityOutsideApp(getActivity(), intent);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sharing contacts failed", e);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), R.string.share_contacts_failure,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Handle "import from SIM" and "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId, int subscriptionId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            args.putInt(KEY_SUBSCRIPTION_ID, subscriptionId);
            SelectAccountDialogFragment.show(
                    getFragmentManager(), this,
                    R.string.dialog_new_contact_account,
                    AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, args);

            // In this case, because this DialogFragment is used as a target fragment to
            // SelectAccountDialogFragment, we can't close it yet.  We close the dialog when
            // we get a callback from it.
            return false;
        }

        AccountSelectionUtil.doImport(getActivity(), resId,
                (size == 1 ? accountList.get(0) : null),
                (CompatUtils.isMSIMCompatible() ? subscriptionId : -1));
        return true; // Close the dialog.
    }

    /**
     * Called when an account is selected on {@link SelectAccountDialogFragment}.
     */
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(getActivity(), extraArgs.getInt(KEY_RES_ID),
                account, extraArgs.getInt(KEY_SUBSCRIPTION_ID));

        // At this point the dialog is still showing (which is why we can use getActivity() above)
        // So close it.
        dismiss();
    }

    @Override
    public void onAccountSelectorCancelled() {
        // See onAccountChosen() -- at this point the dialog is still showing.  Close it.
        dismiss();
    }

    private CharSequence getSubDescription(SubscriptionInfo record) {
        CharSequence name = record.getDisplayName();
        if (TextUtils.isEmpty(record.getNumber())) {
            // Don't include the phone number in the description, since we don't know the number.
            return getString(R.string.import_from_sim_summary_no_number, name);
        }
        return TextUtils.expandTemplate(
                getString(R.string.import_from_sim_summary),
                name,
                PhoneNumberUtilsCompat.createTtsSpannable(record.getNumber()));
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final int mSubscriptionId;

        public AdapterEntry(CharSequence label, int resId, int subId) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSubscriptionId = subId;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* subId = */ -1);
        }
    }
}
