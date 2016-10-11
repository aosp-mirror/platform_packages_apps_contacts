/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.editor;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.util.UiClosables;

import java.util.List;

/**
 * Controls the display of an account selector or header.
 *
 * TODO: This was mostly copied from {@link RawContactEditorView}. The code in that class
 * should probably be modified to use this instead of leaving it duplicated.
 */
public class AccountHeaderPresenter {

    public interface Observer {
        void onChange(AccountHeaderPresenter sender);

        public static final Observer NONE = new Observer() {
            @Override
            public void onChange(AccountHeaderPresenter sender) {
            }
        };
    }

    private final Context mContext;
    private AccountDisplayInfoFactory mAccountDisplayInfoFactory;

    private AccountWithDataSet mCurrentAccount;

    // Account header
    private final View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;
    private ImageView mAccountHeaderIcon;
    private ImageView mAccountHeaderExpanderIcon;

    // This would be different if the account was readonly
    @StringRes
    private int mSelectorTitle = R.string.editor_account_selector_title;

    private Observer mObserver = Observer.NONE;

    public AccountHeaderPresenter(View container) {
        mContext = container.getContext();
        mAccountHeaderContainer = container;
        mAccountHeaderType = (TextView) container.findViewById(R.id.account_type);
        mAccountHeaderName = (TextView) container.findViewById(R.id.account_name);
        mAccountHeaderIcon = (ImageView) container.findViewById(R.id.account_type_icon);
        mAccountHeaderExpanderIcon = (ImageView) container.findViewById(R.id.account_expander_icon);

        mAccountDisplayInfoFactory = AccountDisplayInfoFactory.forWritableAccounts(mContext);
    }

    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    public void setCurrentAccount(@NonNull AccountWithDataSet account) {
        if (mCurrentAccount != null && mCurrentAccount.equals(account)) {
            return;
        }
        mCurrentAccount = account;
        if (mObserver != null) {
            mObserver.onChange(this);
        }
        updateDisplayedAccount();
    }

    public AccountWithDataSet getCurrentAccount() {
        return mCurrentAccount;
    }

    private void updateDisplayedAccount() {
        mAccountHeaderContainer.setVisibility(View.GONE);
        if (mCurrentAccount == null) return;

        final AccountDisplayInfo account =
                mAccountDisplayInfoFactory.getAccountDisplayInfo(mCurrentAccount);

        final String accountLabel = getAccountLabel(account);

        // Either the account header or selector should be shown, not both.
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(mContext).getAccounts(true);

        if (accounts.size() > 1) {
            addAccountSelector(accountLabel);
        } else {
            addAccountHeader(accountLabel);
        }
    }

    private void addAccountHeader(String accountLabel) {
        mAccountHeaderContainer.setVisibility(View.VISIBLE);

        // Set the account name
        mAccountHeaderName.setVisibility(View.VISIBLE);
        mAccountHeaderName.setText(accountLabel);

        // Set the account type
        final String selectorTitle = mContext.getResources().getString(mSelectorTitle);
        mAccountHeaderType.setText(selectorTitle);

        // Set the icon
        final AccountDisplayInfo displayInfo = mAccountDisplayInfoFactory
                .getAccountDisplayInfo(mCurrentAccount);
        mAccountHeaderIcon.setImageDrawable(displayInfo.getIcon());

        // Set the content description
        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(accountLabel,
                        selectorTitle));
    }

    private void addAccountSelector(CharSequence nameLabel) {
        final View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(mContext, null);
                final AccountsListAdapter adapter =
                        new AccountsListAdapter(mContext,
                                AccountsListAdapter.AccountListFilter.ACCOUNTS_CONTACT_WRITABLE,
                                mCurrentAccount);
                popup.setWidth(mAccountHeaderContainer.getWidth());
                popup.setAnchorView(mAccountHeaderContainer);
                popup.setAdapter(adapter);
                popup.setModal(true);
                popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        UiClosables.closeQuietly(popup);
                        final AccountWithDataSet newAccount = adapter.getItem(position);
                        setCurrentAccount(newAccount);
                    }
                });
                popup.show();
            }
        };
        setUpAccountSelector(nameLabel.toString(), onClickListener);
    }

    private void setUpAccountSelector(String nameLabel, View.OnClickListener listener) {
        addAccountHeader(nameLabel);
        // Add handlers for choosing another account to save to.
        mAccountHeaderExpanderIcon.setVisibility(View.VISIBLE);
        mAccountHeaderContainer.setOnClickListener(listener);
    }

    private String getAccountLabel(AccountDisplayInfo account) {
        // TODO: if used from editor this would need to be different if editing the user's profile.
        return account.getNameLabel().toString();
    }
}
