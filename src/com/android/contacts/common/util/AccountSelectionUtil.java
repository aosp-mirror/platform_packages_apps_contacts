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

package com.android.contacts.common.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.ImportVCardActivity;

import java.util.List;

/**
 * Utility class for selecting an Account for importing contact(s)
 */
public class AccountSelectionUtil {
    // TODO: maybe useful for EditContactActivity.java...
    private static final String LOG_TAG = "AccountSelectionUtil";

    public static boolean mVCardShare = false;

    public static Uri mPath;

    public static class AccountSelectedListener
            implements DialogInterface.OnClickListener {

        final private Context mContext;
        final private int mResId;
        final private int mSubscriptionId;

        final protected List<AccountWithDataSet> mAccountList;

        public AccountSelectedListener(Context context, List<AccountWithDataSet> accountList,
                int resId, int subscriptionId) {
            if (accountList == null || accountList.size() == 0) {
                Log.e(LOG_TAG, "The size of Account list is 0.");
            }
            mContext = context;
            mAccountList = accountList;
            mResId = resId;
            mSubscriptionId = subscriptionId;
        }

        public AccountSelectedListener(Context context, List<AccountWithDataSet> accountList,
                int resId) {
            // Subscription id is only needed for importing from SIM card. We can safely ignore
            // its value for SD card importing.
            this(context, accountList, resId, /* subscriptionId = */ -1);
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            doImport(mContext, mResId, mAccountList.get(which), mSubscriptionId);
        }
    }

    public static Dialog getSelectAccountDialog(Context context, int resId) {
        return getSelectAccountDialog(context, resId, null, null);
    }

    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener) {
        return getSelectAccountDialog(context, resId, onClickListener, null);
    }

    /**
     * When OnClickListener or OnCancelListener is null, uses a default listener.
     * The default OnCancelListener just closes itself with {@link Dialog#dismiss()}.
     */
    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        final List<AccountWithDataSet> writableAccountList = accountTypes.getAccounts(true);

        Log.i(LOG_TAG, "The number of available accounts: " + writableAccountList.size());

        // Assume accountList.size() > 1

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(
                context, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ArrayAdapter<AccountWithDataSet> accountAdapter =
            new ArrayAdapter<AccountWithDataSet>(
                    context, R.layout.account_selector_list_item_condensed, writableAccountList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(
                            R.layout.account_selector_list_item_condensed,
                            parent, false);
                }

                final TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);
                final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);

                final AccountWithDataSet account = this.getItem(position);
                final AccountType accountType = accountTypes.getAccountType(
                        account.type, account.dataSet);
                final Context context = getContext();

                text1.setText(accountType.getDisplayLabel(context));
                text2.setText(account.name);
                icon.setImageDrawable(accountType.getDisplayIcon(getContext()));

                return convertView;
            }
        };

        if (onClickListener == null) {
            AccountSelectedListener accountSelectedListener =
                new AccountSelectedListener(context, writableAccountList, resId);
            onClickListener = accountSelectedListener;
        }
        if (onCancelListener == null) {
            onCancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            };
        }
        return new AlertDialog.Builder(context)
            .setTitle(R.string.dialog_new_contact_account)
            .setSingleChoiceItems(accountAdapter, 0, onClickListener)
            .setOnCancelListener(onCancelListener)
            .create();
    }

    public static void doImport(Context context, int resId, AccountWithDataSet account,
            int subscriptionId) {
        if (resId == R.string.import_from_sim) {
            doImportFromSim(context, account, subscriptionId);
        } else if (resId == R.string.import_from_vcf_file) {
            doImportFromVcfFile(context, account);
        }
    }

    public static void doImportFromSim(Context context, AccountWithDataSet account,
            int subscriptionId) {
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }
        importIntent.putExtra("subscription_id", (Integer) subscriptionId);
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        context.startActivity(importIntent);
    }

    public static void doImportFromVcfFile(Context context, AccountWithDataSet account) {
        Intent importIntent = new Intent(context, ImportVCardActivity.class);
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }

        if (mVCardShare) {
            importIntent.setAction(Intent.ACTION_VIEW);
            importIntent.setData(mPath);
        }
        mVCardShare = false;
        mPath = null;
        context.startActivity(importIntent);
    }
}
