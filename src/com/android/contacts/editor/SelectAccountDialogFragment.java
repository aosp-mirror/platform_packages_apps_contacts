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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.AccountsListAdapter.AccountListFilter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Shows a dialog asking the user which account to chose.
 * The result is passed back to the Fragment that is configured by
 * {@link Fragment#setTargetFragment(Fragment, int)}, which has to implement
 * {@link SelectAccountDialogFragment.Listener}.
 * Does not perform any action by itself.
 */
public class SelectAccountDialogFragment extends DialogFragment {
    public static final String TAG = "SelectAccountDialogFragment";

    // TODO: This dialog is used in the context of group editing by default, but should be generic
    // to work for contact editing as well. Save/restore the resource ID and account list filter
    // that are passed in as parameters on device rotation. Bug: 5369853
    private int mTitleResourceId = R.string.dialog_new_group_account;
    private AccountListFilter mAccountListFilter = AccountListFilter.ACCOUNTS_GROUP_WRITABLE;

    public SelectAccountDialogFragment() {
    }

    public SelectAccountDialogFragment(int titleResourceId, AccountListFilter accountListFilter) {
        mTitleResourceId = titleResourceId;
        mAccountListFilter = accountListFilter;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final AccountsListAdapter accountAdapter = new AccountsListAdapter(builder.getContext(),
                mAccountListFilter);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                onAccountSelected(accountAdapter.getItem(which));
            }
        };

        builder.setTitle(mTitleResourceId);
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        final AlertDialog result = builder.create();
        return result;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            final Listener target = (Listener) targetFragment;
            target.onAccountSelectorCancelled();
        }
    }

    /**
     * Calls {@link Listener#onAccountChosen(int, AccountWithDataSet)} if the target fragment is
     * castable to {@link Listener}. Subclasses can also overide to directly perform an operation.
     */
    protected void onAccountSelected(AccountWithDataSet account) {
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            final Listener target = (Listener) targetFragment;
            target.onAccountChosen(getTargetRequestCode(), account);
        }
    }

    public interface Listener {
        void onAccountChosen(int requestCode, AccountWithDataSet account);
        void onAccountSelectorCancelled();
    }
}
