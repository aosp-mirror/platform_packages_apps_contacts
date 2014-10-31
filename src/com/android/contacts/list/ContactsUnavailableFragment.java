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
package com.android.contacts.list;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.provider.ContactsContract.ProviderStatus;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.contacts.R;

/**
 * Fragment shown when contacts are unavailable. It contains provider status
 * messaging as well as instructions for the user.
 */
public class ContactsUnavailableFragment extends Fragment implements OnClickListener {

    private View mView;
    private TextView mMessageView;
    private TextView mSecondaryMessageView;
    private Button mCreateContactButton;
    private Button mAddAccountButton;
    private Button mImportContactsButton;
    private Button mUninstallAppsButton;
    private Button mRetryUpgradeButton;
    private ProgressBar mProgress;
    private int mNoContactsMsgResId = -1;
    private int mNSecNoContactsMsgResId = -1;

    private OnContactsUnavailableActionListener mListener;

    private ProviderStatusWatcher.Status mProviderStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.contacts_unavailable_fragment, null);
        mMessageView = (TextView) mView.findViewById(R.id.message);
        mSecondaryMessageView = (TextView) mView.findViewById(R.id.secondary_message);
        mCreateContactButton = (Button) mView.findViewById(R.id.create_contact_button);
        mCreateContactButton.setOnClickListener(this);
        mAddAccountButton = (Button) mView.findViewById(R.id.add_account_button);
        mAddAccountButton.setOnClickListener(this);
        mImportContactsButton = (Button) mView.findViewById(R.id.import_contacts_button);
        mImportContactsButton.setOnClickListener(this);
        mUninstallAppsButton = (Button) mView.findViewById(R.id.import_failure_uninstall_button);
        mUninstallAppsButton.setOnClickListener(this);
        mRetryUpgradeButton = (Button) mView.findViewById(R.id.import_failure_retry_button);
        mRetryUpgradeButton.setOnClickListener(this);
        mProgress = (ProgressBar) mView.findViewById(R.id.progress);

        if (mProviderStatus != null) {
            updateStatus(mProviderStatus);
        }

        return mView;
    }

    public void setOnContactsUnavailableActionListener(
            OnContactsUnavailableActionListener listener) {
        mListener = listener;
    }

    public void updateStatus(ProviderStatusWatcher.Status providerStatus) {
        mProviderStatus = providerStatus;
        if (mView == null) {
            // The view hasn't been inflated yet.
            return;
        }
        switch (providerStatus.status) {
            case ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS:
                setMessageText(mNoContactsMsgResId, mNSecNoContactsMsgResId);
                mCreateContactButton.setVisibility(View.VISIBLE);
                mAddAccountButton.setVisibility(View.VISIBLE);
                mImportContactsButton.setVisibility(View.VISIBLE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.GONE);
                break;

            case ProviderStatus.STATUS_CHANGING_LOCALE:
                mMessageView.setText(R.string.locale_change_in_progress);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddAccountButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.VISIBLE);
                break;

            case ProviderStatus.STATUS_UPGRADING:
                mMessageView.setText(R.string.upgrade_in_progress);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddAccountButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.VISIBLE);
                break;

            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                String message = getResources().getString(R.string.upgrade_out_of_memory,
                        new Object[] { providerStatus.data});
                mMessageView.setText(message);
                mMessageView.setGravity(Gravity.START);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddAccountButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.VISIBLE);
                mRetryUpgradeButton.setVisibility(View.VISIBLE);
                mProgress.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        if (mListener == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.create_contact_button:
                mListener.onCreateNewContactAction();
                break;
            case R.id.add_account_button:
                mListener.onAddAccountAction();
                break;
            case R.id.import_contacts_button:
                mListener.onImportContactsFromFileAction();
                break;
            case R.id.import_failure_uninstall_button:
                mListener.onFreeInternalStorageAction();
                break;
            case R.id.import_failure_retry_button:
                final Context context = getActivity();
                if (context != null) { // Just in case.
                    ProviderStatusWatcher.retryUpgrade(context);
                }
                break;
        }
    }
    /**
     * Set the message to be shown if no data is available for the selected tab
     *
     * @param resId - String resource ID of the message , -1 means view will not be visible
     */
    public void setMessageText(int resId, int secResId) {
        mNoContactsMsgResId = resId;
        mNSecNoContactsMsgResId = secResId;
        if ((mMessageView != null) && (mProviderStatus != null) &&
                (mProviderStatus.status == ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS)) {
            if (resId != -1) {
                mMessageView.setText(mNoContactsMsgResId);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                if (secResId != -1) {
                    mSecondaryMessageView.setText(mNSecNoContactsMsgResId);
                    mSecondaryMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                    mSecondaryMessageView.setVisibility(View.VISIBLE);
                } else {
                    mSecondaryMessageView.setVisibility(View.INVISIBLE);
                }
            } else {
                mSecondaryMessageView.setVisibility(View.GONE);
                mMessageView.setVisibility(View.GONE);
            }
        }
    }
}
