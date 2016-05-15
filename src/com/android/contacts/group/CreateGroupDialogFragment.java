/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, softwareateCre
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.contacts.group;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import com.android.contacts.R;

/**
 * Prompts the user for the name of the new group.
 */
public final class CreateGroupDialogFragment extends DialogFragment {

    private static final String TAG_CREATE_GROUP_DIALOG = "createGroup";

    /** Callbacks for hosts of the {@link CreateGroupDialogFragment}. */
    public interface Listener {
        void onCreateGroup(String groupName);
        void onCreateGroupCancelled();
    }

    private EditText mGroupNameEditText;

    public static <F extends Fragment & Listener> void show(
            FragmentManager fragmentManager, F targetFragment) {
        final CreateGroupDialogFragment dialog = new CreateGroupDialogFragment();
        dialog.setTargetFragment(targetFragment, /* requestCode */ 0);
        dialog.show(fragmentManager, TAG_CREATE_GROUP_DIALOG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Build a dialog with two buttons and a view of a single EditText input field
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.create_group_dialog_title)
                .setView(R.layout.create_group_dialog)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onCreateGroupCancelled();
                        dismiss();
                    }
                })
                .setPositiveButton(R.string.create_group_dialog_button, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onCreateGroup();
                    }
                });

        // Disable the create button when the name is empty
        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                mGroupNameEditText = (EditText) alertDialog.findViewById(android.R.id.text1);

                final Button createButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                createButton.setEnabled(!TextUtils.isEmpty(getGroupName()));
                mGroupNameEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        createButton.setEnabled(!TextUtils.isEmpty(s));
                    }
                });
            }
        });
        return alertDialog;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        onCreateGroupCancelled();
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        setTargetFragment(null, /* requestCode */ -1);
        super.onSaveInstanceState(b);
    }

    private void onCreateGroupCancelled() {
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            ((Listener) targetFragment).onCreateGroupCancelled();
        }
    }

    private void onCreateGroup() {
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            ((Listener) targetFragment).onCreateGroup(getGroupName());
        }
    }

    private String getGroupName() {
        return mGroupNameEditText == null || mGroupNameEditText.getText() == null
                ? null : mGroupNameEditText.getText().toString();
    }
}
