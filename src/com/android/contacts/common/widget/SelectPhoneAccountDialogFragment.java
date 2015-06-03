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

package com.android.contacts.common.widget;

import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.contacts.common.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog that allows the user to select a phone accounts for a given action. Optionally provides
 * the choice to set the phone account as default.
 */
public class SelectPhoneAccountDialogFragment extends DialogFragment {
    private static final String ARG_TITLE_RES_ID = "title_res_id";
    private static final String ARG_CAN_SET_DEFAULT = "can_set_default";
    private static final String ARG_ACCOUNT_HANDLES = "account_handles";
    private static final String ARG_IS_DEFAULT_CHECKED = "is_default_checked";

    private int mTitleResId;
    private boolean mCanSetDefault;
    private List<PhoneAccountHandle> mAccountHandles;
    private boolean mIsSelected;
    private boolean mIsDefaultChecked;
    private TelecomManager mTelecomManager;
    private SelectPhoneAccountListener mListener;

    /**
     * Create new fragment instance with default title and no option to set as default.
     *
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     */
    public static SelectPhoneAccountDialogFragment newInstance(
            List<PhoneAccountHandle> accountHandles, SelectPhoneAccountListener listener) {
        return newInstance(R.string.select_account_dialog_title, false,
                accountHandles, listener);
    }

    /**
     * Create new fragment instance.
     * This method also allows specifying a custom title and "set default" checkbox.
     *
     * @param titleResId The resource ID for the string to use in the title of the dialog.
     * @param canSetDefault {@code true} if the dialog should include an option to set the selection
     * as the default. False otherwise.
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     */
    public static SelectPhoneAccountDialogFragment newInstance(int titleResId,
            boolean canSetDefault, List<PhoneAccountHandle> accountHandles,
            SelectPhoneAccountListener listener) {
        ArrayList<PhoneAccountHandle> accountHandlesCopy = new ArrayList<PhoneAccountHandle>();
        if (accountHandles != null) {
            accountHandlesCopy.addAll(accountHandles);
        }
        SelectPhoneAccountDialogFragment fragment = new SelectPhoneAccountDialogFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE_RES_ID, titleResId);
        args.putBoolean(ARG_CAN_SET_DEFAULT, canSetDefault);
        args.putParcelableArrayList(ARG_ACCOUNT_HANDLES, accountHandlesCopy);
        fragment.setArguments(args);
        fragment.setListener(listener);
        return fragment;
    }

    public SelectPhoneAccountDialogFragment() {
    }

    public void setListener(SelectPhoneAccountListener listener) {
        mListener = listener;
    }

    public interface SelectPhoneAccountListener {
        void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle, boolean setDefault);
        void onDialogDismissed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_IS_DEFAULT_CHECKED, mIsDefaultChecked);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
        mCanSetDefault = getArguments().getBoolean(ARG_CAN_SET_DEFAULT);
        mAccountHandles = getArguments().getParcelableArrayList(ARG_ACCOUNT_HANDLES);
        if (savedInstanceState != null) {
            mIsDefaultChecked = savedInstanceState.getBoolean(ARG_IS_DEFAULT_CHECKED);
        }
        mIsSelected = false;
        mTelecomManager =
                (TelecomManager) getActivity().getSystemService(Context.TELECOM_SERVICE);

        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mIsSelected = true;
                PhoneAccountHandle selectedAccountHandle = mAccountHandles.get(which);
                mListener.onPhoneAccountSelected(selectedAccountHandle, mIsDefaultChecked);
            }
        };

        final CompoundButton.OnCheckedChangeListener checkListener =
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton check, boolean isChecked) {
                mIsDefaultChecked = isChecked;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        ListAdapter selectAccountListAdapter = new SelectAccountListAdapter(
                builder.getContext(),
                R.layout.select_account_list_item,
                mAccountHandles);

        AlertDialog dialog = builder.setTitle(mTitleResId)
                .setAdapter(selectAccountListAdapter, selectionListener)
                .create();

        if (mCanSetDefault) {
            // Generate custom checkbox view
            LinearLayout checkboxLayout = (LinearLayout) getActivity()
                    .getLayoutInflater()
                    .inflate(R.layout.default_account_checkbox, null);

            CheckBox cb =
                    (CheckBox) checkboxLayout.findViewById(R.id.default_account_checkbox_view);
            cb.setOnCheckedChangeListener(checkListener);
            cb.setChecked(mIsDefaultChecked);

            dialog.getListView().addFooterView(checkboxLayout);
        }

        return dialog;
    }

    private class SelectAccountListAdapter extends ArrayAdapter<PhoneAccountHandle> {
        private int mResId;

        public SelectAccountListAdapter(
                Context context, int resource, List<PhoneAccountHandle> accountHandles) {
            super(context, resource, accountHandles);
            mResId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.labelTextView = (TextView) rowView.findViewById(R.id.label);
                holder.numberTextView = (TextView) rowView.findViewById(R.id.number);
                holder.imageView = (ImageView) rowView.findViewById(R.id.icon);
                rowView.setTag(holder);
            }
            else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            PhoneAccountHandle accountHandle = getItem(position);
            PhoneAccount account = mTelecomManager.getPhoneAccount(accountHandle);
            if (account == null) {
                return rowView;
            }
            holder.labelTextView.setText(account.getLabel());
            if (account.getAddress() == null ||
                    TextUtils.isEmpty(account.getAddress().getSchemeSpecificPart())) {
                holder.numberTextView.setVisibility(View.GONE);
            } else {
                holder.numberTextView.setVisibility(View.VISIBLE);
                holder.numberTextView.setText(
                        PhoneNumberUtils.createTtsSpannable(
                                account.getAddress().getSchemeSpecificPart()));
            }
            holder.imageView.setImageDrawable(account.getIcon() != null
                    ? account.getIcon().loadDrawable(getContext()) : null);
            return rowView;
        }

        private class ViewHolder {
            TextView labelTextView;
            TextView numberTextView;
            ImageView imageView;
        }
    }

    @Override
    public void onStop() {
        if (!mIsSelected) {
            mListener.onDialogDismissed();
        }
        super.onStop();
    }
}
