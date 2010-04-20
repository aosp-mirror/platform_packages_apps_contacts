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

package com.android.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Displays a message when there is nothing to display in a contact list.
 */
public class ContactListEmptyView extends ScrollView {

    private static final String TAG = "ContactListEmptyView";

    public ContactListEmptyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void hide() {
        TextView empty = (TextView) findViewById(R.id.emptyText);
        empty.setVisibility(GONE);
    }

    public void show(boolean searchMode, boolean displayOnlyPhones,
            boolean isFavoritesMode, boolean isQueryMode, boolean isShortcutAction,
            boolean isMultipleSelectionEnabled, boolean showSelectedOnly) {
        if (searchMode) {
            return;
        }

        TextView empty = (TextView) findViewById(R.id.emptyText);
        Context context = getContext();
        if (displayOnlyPhones) {
            empty.setText(context.getText(R.string.noContactsWithPhoneNumbers));
        } else if (isFavoritesMode) {
            empty.setText(context.getText(R.string.noFavoritesHelpText));
        } else if (isQueryMode) {
            empty.setText(context.getText(R.string.noMatchingContacts));
        } if (isMultipleSelectionEnabled) {
            if (showSelectedOnly) {
                empty.setText(context.getText(R.string.no_contacts_selected));
            } else {
                empty.setText(context.getText(R.string.noContactsWithPhoneNumbers));
            }
        } else {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            boolean hasSim = telephonyManager.hasIccCard();
            if (isSyncActive()) {
                if (isShortcutAction) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(
                            context.getText(R.string.noContactsHelpTextWithSyncForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(context.getText(R.string.noContactsHelpTextWithSync));
                } else {
                    empty.setText(context.getText(R.string.noContactsNoSimHelpTextWithSync));
                }
            } else {
                if (isShortcutAction) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(context.getText(R.string.noContactsHelpTextForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(context.getText(R.string.noContactsHelpText));
                } else {
                    empty.setText(context.getText(R.string.noContactsNoSimHelpText));
                }
            }
        }
        empty.setVisibility(VISIBLE);
    }

    private boolean isSyncActive() {
        Account[] accounts = AccountManager.get(getContext()).getAccounts();
        if (accounts != null && accounts.length > 0) {
            IContentService contentService = ContentResolver.getContentService();
            for (Account account : accounts) {
                try {
                    if (contentService.isSyncActive(account, ContactsContract.AUTHORITY)) {
                        return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not get the sync status");
                }
            }
        }
        return false;
    }
}
