/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.contacts.common.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecomm.PhoneAccount;
import android.telecomm.TelecommManager;

import com.android.contacts.common.PhoneAccountManager;
import com.android.contacts.common.R;

import java.util.List;

/**
 * Dialog that allows the user to switch between default SIM cards
 */
public class SelectAccountDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = "selectAccount";
    private static final int NO_SELECTION = -1;

    private PhoneAccountManager mAccountManager;
    private int mSelection;
    private List<PhoneAccount> mAccounts;

    /* Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager,
            PhoneAccountManager accountManager) {
        if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
            SelectAccountDialogFragment fragment = new SelectAccountDialogFragment();
            fragment.mAccountManager = accountManager;
            fragment.show(fragmentManager, FRAGMENT_TAG);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mAccounts = mAccountManager.getAccounts();
        mSelection = NO_SELECTION;

        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSelection = which;
            }
        };
        final DialogInterface.OnClickListener okListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                if (mSelection != NO_SELECTION) {
                    // No need to update the current account if it has not been changed
                    mAccountManager.setCurrentAccount(mAccounts.get(mSelection));
                }
            }
        };

        CharSequence[] names = getAccountNames();
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_select_sim)
                .setSingleChoiceItems(names, getSelectedAccountIndex(names), selectionListener)
                .setPositiveButton(android.R.string.ok, okListener)
                // TODO(santoscordon): Abort the call from 'cancel' event.
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /**
     * Helper function to extract the index of the currently selected account.
     * Used in the dialog as the initially selected radio button.
     *
     * @param activeSubs String list of the labels referring to each of possible
     *         active accounts to choose from
     * @return the index of the selected account in the array of accounts
     */
    private int getSelectedAccountIndex(CharSequence[] activeSubs) {
        PhoneAccount initialAccount = mAccountManager.getCurrentAccount();
        if (initialAccount != null) {
            String label = TelecommManager.from(getActivity()).
                     getPhoneAccountMetadata(initialAccount).getLabel();

            if (label != null) {
                for (int i = 0; i < activeSubs.length; i++) {
                    if (label.equals(activeSubs[i])) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Extracts the label names from each of the accounts and returns as a list of strings
     *
     * @return a list of strings to display in the dialog
     */
    private CharSequence[] getAccountNames() {
        Context context = getActivity();
        CharSequence [] labels = new CharSequence[mAccounts.size()];
        for (int i = 0; i < mAccounts.size(); i++) {
            String label = TelecommManager.from(getActivity()).
                            getPhoneAccountMetadata(mAccounts.get(i)).getLabel();
            labels[i] = label == null ? "" : label;
        }
        return labels;
    }
}
