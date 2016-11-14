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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;

/**
 * Shows a dialog asking the user which account to chose.
 *
 * The result is passed to {@code targetFragment} passed to {@link #show}.
 */
public final class SelectAccountDialogFragment extends DialogFragment {
    public static final String TAG = "SelectAccountDialogFragment";

    private static final String KEY_TITLE_RES_ID = "title_res_id";
    private static final String KEY_LIST_FILTER = "list_filter";
    private static final String KEY_EXTRA_ARGS = "extra_args";

    /**
     * Show the dialog.
     *
     * @param fragmentManager {@link FragmentManager}.
     * @param titleResourceId resource ID to use as the title.
     * @param accountListFilter account filter.
     * @param extraArgs Extra arguments, which will later be passed to
     *     {@link Listener#onAccountChosen}.  {@code null} will be converted to
     *     {@link Bundle#EMPTY}.
     */
    public static void show(FragmentManager fragmentManager, int titleResourceId,
            AccountListFilter accountListFilter, Bundle extraArgs) {
        show(fragmentManager, titleResourceId, accountListFilter, extraArgs, /* tag */ null);
    }

    public static void show(FragmentManager fragmentManager, int titleResourceId,
            AccountListFilter accountListFilter, Bundle extraArgs, String tag) {
        final Bundle args = new Bundle();
        args.putInt(KEY_TITLE_RES_ID, titleResourceId);
        args.putSerializable(KEY_LIST_FILTER, accountListFilter);
        args.putBundle(KEY_EXTRA_ARGS, (extraArgs == null) ? Bundle.EMPTY : extraArgs);

        final SelectAccountDialogFragment instance = new SelectAccountDialogFragment();
        instance.setArguments(args);
        instance.show(fragmentManager, tag);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Bundle args = getArguments();

        final AccountListFilter filter = (AccountListFilter) args.getSerializable(KEY_LIST_FILTER);
        final AccountsListAdapter accountAdapter = new AccountsListAdapter(builder.getContext(),
                filter);
        accountAdapter.setCustomLayout(R.layout.account_selector_list_item_condensed);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                onAccountSelected(accountAdapter.getItem(which));
            }
        };

        final TextView title = (TextView) View.inflate(getActivity(), R.layout.dialog_title, null);
        title.setText(args.getInt(KEY_TITLE_RES_ID));
        builder.setCustomTitle(title);
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        final AlertDialog result = builder.create();
        return result;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        final Listener listener = getListener();
        if (listener != null) {
            listener.onAccountSelectorCancelled();
        }
    }

    /**
     * Calls {@link Listener#onAccountChosen}.
     */
    private void onAccountSelected(AccountWithDataSet account) {
        final Listener listener = getListener();
        if (listener != null) {
            listener.onAccountChosen(account, getArguments().getBundle(KEY_EXTRA_ARGS));
        }
    }

    private Listener getListener() {
        Listener listener = null;
        final Activity activity = getActivity();
        if (activity != null && activity instanceof Listener) {
            listener = (Listener) activity;
        }
        return listener;
    }

    public interface Listener {
        void onAccountChosen(AccountWithDataSet account, Bundle extraArgs);
        void onAccountSelectorCancelled();
    }
}
