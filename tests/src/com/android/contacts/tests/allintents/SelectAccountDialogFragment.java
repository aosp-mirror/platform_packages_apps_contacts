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

package com.android.contacts.tests.allintents;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Shows a dialog asking the user which account to chose.
 * The result is passed back to the owning Activity
 * Does not perform any action by itself.
 */
public class SelectAccountDialogFragment extends DialogFragment {
    public static final String TAG = "SelectAccountDialogFragment";

    private static final String EXTRA_TAG = "tag";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle parameters = getArguments();

        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(getActivity()).getAccounts(false);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = LayoutInflater.from(builder.getContext());

        final ArrayAdapter<AccountWithDataSet> accountAdapter =
                new ArrayAdapter<AccountWithDataSet>(builder.getContext(),
                        android.R.layout.simple_list_item_2, accounts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View resultView = convertView == null
                        ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                        : convertView;

                final TextView text1 = (TextView)resultView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView)resultView.findViewById(android.R.id.text2);

                final AccountWithDataSet account = getItem(position);

                text1.setText("Name: " + account.name);
                text2.setText("Type: " + account.type);

                return resultView;
            }
        };

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                ((Listener) getActivity()).onAccountChosen(accountAdapter.getItem(which),
                        parameters.getInt(EXTRA_TAG));
            }
        };

        builder.setTitle("Choose account to send to editor");
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        final AlertDialog result = builder.create();
        return result;
    }

    public static Bundle createBundle(int tag) {
        final Bundle result = new Bundle();
        result.putInt(EXTRA_TAG, tag);
        return result;
    }

    public interface Listener {
        void onAccountChosen(AccountWithDataSet account, int tag);
    }
}
