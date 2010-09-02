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

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Shows a dialog asking the user which account to chose.
 * The result is passed back to the Fragment with the Id that is passed in the constructor
 * (or the Activity if -1 is passed).
 * The target must implement {@link SelectAccountDialogFragment.Listener}.
 * Does not perform any action by itself.
 */
public class SelectAccountDialogFragment extends TargetedDialogFragment {
    public static final String TAG = "PickPhotoDialogFragment";
    private static final String IS_NEW_CONTACT = "IS_NEW_CONTACT";

    private boolean mIsNewContact;

    public SelectAccountDialogFragment() {
    }

    public SelectAccountDialogFragment(int targetFragmentId, boolean isNewContact) {
        super(targetFragmentId);
        mIsNewContact = isNewContact;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIsNewContact = savedInstanceState.getBoolean(IS_NEW_CONTACT);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_NEW_CONTACT, mIsNewContact);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(getActivity(),
                android.R.style.Theme_Light);
        final LayoutInflater dialogInflater =
                (LayoutInflater)dialogContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Sources sources = Sources.getInstance(getActivity());
        final ArrayList<Account> accounts = Sources.getInstance(getActivity()).getAccounts(true);

        final ArrayAdapter<Account> accountAdapter = new ArrayAdapter<Account>(getActivity(),
                android.R.layout.simple_list_item_2, accounts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_2,
                            parent, false);
                }

                // TODO: show icon along with title
                final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                final Account account = this.getItem(position);
                final ContactsSource source = sources.getInflatedSource(account.type,
                        ContactsSource.LEVEL_SUMMARY);

                text1.setText(account.name);
                text2.setText(source.getDisplayLabel(getContext()));

                return convertView;
            }
        };

        final Listener targetListener = (Listener) getTarget();
        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                targetListener.onAccountChosen(accountAdapter.getItem(which), mIsNewContact);
            }
        };

        final DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                targetListener.onAccountSelectorCancelled();
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_new_contact_account);
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        builder.setOnCancelListener(cancelListener);
        return builder.create();
    }

    public interface Listener {
        void onAccountChosen(Account account, boolean isNewContact);
        void onAccountSelectorCancelled();
    }
}
