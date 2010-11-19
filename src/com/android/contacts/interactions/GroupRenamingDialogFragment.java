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

import com.android.contacts.R;
import com.android.contacts.views.ContactSaveService;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

/**
 * A dialog for renaming a group.
 */
public class GroupRenamingDialogFragment extends DialogFragment {

    private static final String ARG_GROUP_ID = "groupId";
    private static final String ARG_LABEL = "label";

    private EditText mEdit;

    public static void show(FragmentManager fragmentManager, long groupId, String label) {
        GroupRenamingDialogFragment dialog = new GroupRenamingDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_LABEL, label);
        dialog.setArguments(args);
        dialog.show(fragmentManager, "renameGroup");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.group_name_dialog, null);
        mEdit = (EditText) view.findViewById(R.id.group_label);
        String label = getArguments().getString(ARG_LABEL);
        mEdit.setText(label);
        if (label != null) {
            mEdit.setSelection(label.length());
        }

        return new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.rename_group_dialog_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            saveGroupLabel();
                        }
                    }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    protected void saveGroupLabel() {
        String newLabel = mEdit.getText().toString();

        Bundle arguments = getArguments();
        long groupId = arguments.getLong(ARG_GROUP_ID);

        getActivity().startService(ContactSaveService.createGroupRenameIntent(
                getActivity(), groupId, newLabel));
    }
}
