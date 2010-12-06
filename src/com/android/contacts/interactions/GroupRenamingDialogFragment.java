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

import android.app.FragmentManager;
import android.os.Bundle;
import android.widget.EditText;

/**
 * A dialog for renaming a group.
 */
public class GroupRenamingDialogFragment extends GroupNameDialogFragment {

    private static final String ARG_GROUP_ID = "groupId";
    private static final String ARG_LABEL = "label";

    public static void show(FragmentManager fragmentManager, long groupId, String label) {
        GroupRenamingDialogFragment dialog = new GroupRenamingDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_LABEL, label);
        dialog.setArguments(args);
        dialog.show(fragmentManager, "renameGroup");
    }

    @Override
    protected void initializeGroupLabelEditText(EditText editText) {
        String label = getArguments().getString(ARG_LABEL);
        editText.setText(label);
        if (label != null) {
            editText.setSelection(label.length());
        }
    }

    @Override
    protected int getTitleResourceId() {
        return R.string.rename_group_dialog_title;
    }

    @Override
    protected void onCompleted(String groupLabel) {
        Bundle arguments = getArguments();
        long groupId = arguments.getLong(ARG_GROUP_ID);

        getActivity().startService(ContactSaveService.createGroupRenameIntent(
                getActivity(), groupId, groupLabel));
    }
}
