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
package com.android.contacts.interactions;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.AccountWithDataSet;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

/**
 * A dialog for creating a new group.
 */
public class GroupCreationDialogFragment extends GroupNameDialogFragment {
    private static final String ARG_ACCOUNT_TYPE = "accountType";
    private static final String ARG_ACCOUNT_NAME = "accountName";
    private static final String ARG_DATA_SET = "dataSet";

    public static void show(
            FragmentManager fragmentManager, String accountType, String accountName,
            String dataSet) {
        GroupCreationDialogFragment dialog = new GroupCreationDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_TYPE, accountType);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_DATA_SET, dataSet);
        dialog.setArguments(args);
        dialog.show(fragmentManager, "createGroup");
    }

    @Override
    protected void initializeGroupLabelEditText(EditText editText) {
    }

    @Override
    protected int getTitleResourceId() {
        return R.string.create_group_dialog_title;
    }

    @Override
    protected void onCompleted(String groupLabel) {
        Bundle arguments = getArguments();
        String accountType = arguments.getString(ARG_ACCOUNT_TYPE);
        String accountName = arguments.getString(ARG_ACCOUNT_NAME);
        String dataSet = arguments.getString(ARG_DATA_SET);

        Activity activity = getActivity();
        activity.startService(ContactSaveService.createNewGroupIntent(activity,
                new AccountWithDataSet(accountName, accountType, dataSet), groupLabel,
                null /* no new members to add */,
                activity.getClass(), Intent.ACTION_EDIT));
    }
}
