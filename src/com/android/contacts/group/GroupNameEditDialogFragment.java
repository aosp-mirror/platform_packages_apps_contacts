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

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.android.contacts.R;

/**
 * Edits the name of a group.
 */
public final class GroupNameEditDialogFragment extends DialogFragment {

    private static final String KEY_IS_INSERT = "isInsert";
    private static final String KEY_GROUP_NAME = "groupName";

    private static final String ARG_IS_INSERT = "isInsert";
    private static final String ARG_GROUP_NAME = "groupName";

    /** Callbacks for hosts of the {@link GroupNameEditDialogFragment}. */
    public interface Listener {
        void onGroupNameEdit(String groupName, boolean isInsert);
        void onGroupNameEditCancelled();
    }

    private boolean mIsInsert;
    private String mGroupName;
    private EditText mGroupNameEditText;

    public static void showInsertDialog(FragmentManager fragmentManager, String tag) {
        showDialog(fragmentManager, tag, /* isInsert */ true, /* groupName */ null);
    }

    public static void showUpdateDialog(FragmentManager fragmentManager,
            String tag, String groupName) {
        showDialog(fragmentManager, tag, /* isInsert */ false, groupName);
    }

    private static void showDialog(FragmentManager fragmentManager,
            String tag, boolean isInsert, String groupName) {
        final Bundle args = new Bundle();
        args.putBoolean(ARG_IS_INSERT, isInsert);
        args.putString(ARG_GROUP_NAME, groupName);

        final GroupNameEditDialogFragment dialog = new GroupNameEditDialogFragment();
        dialog.setArguments(args);
        dialog.show(fragmentManager, tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            final Bundle args = getArguments();
            mIsInsert = args.getBoolean(KEY_IS_INSERT);
            mGroupName = args.getString(KEY_GROUP_NAME);
        } else {
            mIsInsert = savedInstanceState.getBoolean(ARG_IS_INSERT);
            mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Build a dialog with two buttons and a view of a single EditText input field
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(mIsInsert
                        ? R.string.group_name_dialog_insert_title
                        : R.string.group_name_dialog_update_title)
                .setView(R.layout.group_name_edit_dialog)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hideInputMethod();
                        getListener().onGroupNameEditCancelled();
                        dismiss();
                    }
                })
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getListener().onGroupNameEdit(getGroupName(), mIsInsert);
                    }
                });

        // Disable the create button when the name is empty
        final AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                mGroupNameEditText = (EditText) alertDialog.findViewById(android.R.id.text1);
                if (!TextUtils.isEmpty(mGroupName)) {
                    mGroupNameEditText.setText(mGroupName);
                    // Guard against already created group names that are longer than the max
                    final int maxLength = getResources().getInteger(
                            R.integer.group_name_max_length);
                    mGroupNameEditText.setSelection(
                            mGroupName.length() > maxLength ? maxLength : mGroupName.length());
                }
                showInputMethod(mGroupNameEditText);

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
        getListener().onGroupNameEditCancelled();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_INSERT, mIsInsert);
        outState.putString(KEY_GROUP_NAME, getGroupName());
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, /* flags */ 0);
        }
    }

    private void hideInputMethod() {
        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && mGroupNameEditText != null) {
            imm.hideSoftInputFromWindow(mGroupNameEditText.getWindowToken(), /* flags */ 0);
        }
    }

    private Listener getListener() {
        if (!(getActivity() instanceof Listener)) {
            throw new ClassCastException(getActivity() + " must implement " +
                    Listener.class.getName());
        }
        return (Listener) getActivity();
    }

    private String getGroupName() {
        return mGroupNameEditText == null || mGroupNameEditText.getText() == null
                ? null : mGroupNameEditText.getText().toString();
    }
}
