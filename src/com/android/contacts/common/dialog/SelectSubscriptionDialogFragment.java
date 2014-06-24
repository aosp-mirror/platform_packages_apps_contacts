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
import android.telecomm.Subscription;

import com.android.contacts.common.R;
import com.android.contacts.common.SubscriptionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog that allows the user to switch between default SIM cards
 */
public class SelectSubscriptionDialogFragment extends DialogFragment {
    private SubscriptionManager mSubscriptionManager;
    private int mSelection;
    private List<Subscription> mSubscriptions;
    private static final int NO_SELECTION = -1;

    /* Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager,
            SubscriptionManager subscriptionManager) {
        SelectSubscriptionDialogFragment fragment = new SelectSubscriptionDialogFragment();
        fragment.mSubscriptionManager = subscriptionManager;
        fragment.show(fragmentManager, "selectSubscription");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mSubscriptions = mSubscriptionManager.getSubscriptions();
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
                    // No need to update the current subscription if it has not been changed
                    mSubscriptionManager.setCurrentSubscription(mSubscriptions.get(mSelection));
                }
            }
        };

        CharSequence[] names = getSubscriptionNames();
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_select_sim)
                .setSingleChoiceItems(names, getSelectedSubscriptionIndex(names), selectionListener)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /**
     * Helper function to extract the index of the currently selected subscription.
     * Used in the dialog as the initially selected radio button.
     *
     * @param activeSubs String list of the labels referring to each of possible
     *         active subscriptions to choose from
     * @return the index of the selected subscription in the array of subscriptions
     */
    private int getSelectedSubscriptionIndex(CharSequence[] activeSubs) {
        Subscription initialSubscription = mSubscriptionManager.getCurrentSubscription();
        if (initialSubscription == null) {
            return -1;
        }
        else {
            return Arrays.asList(activeSubs).indexOf(initialSubscription.getLabel(getActivity()));
        }
    }

    /**
     * Extracts the label names from each of the subscriptions and returns as a list of strings
     *
     * @return a list of strings to display in the dialog
     */
    private CharSequence[] getSubscriptionNames() {
        Context context = getActivity();
        List<String> strings = new ArrayList<String>();
        for (int i = 0; i < mSubscriptions.size(); i++) {
            strings.add(mSubscriptions.get(i).getLabel(context));
        }
        return strings.toArray(new CharSequence[strings.size()]);
    }
}