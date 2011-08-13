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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * A common superclass for creating and renaming groups.
 */
public abstract class GroupNameDialogFragment extends DialogFragment
        implements TextWatcher, OnShowListener {
    private EditText mEdit;

    protected abstract int getTitleResourceId();
    protected abstract void initializeGroupLabelEditText(EditText editText);
    protected abstract void onCompleted(String groupLabel);

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.group_name_dialog, null);
        mEdit = (EditText) view.findViewById(R.id.group_label);
        initializeGroupLabelEditText(mEdit);

        mEdit.addTextChangedListener(this);

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(getTitleResourceId())
                .setView(view)
                .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            onCompleted(mEdit.getText().toString().trim());
                        }
                    }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(this);
        return dialog;
    }

    public void onShow(DialogInterface dialog) {
        updateOkButtonState((AlertDialog) dialog);
    }

    @Override
    public void afterTextChanged(Editable s) {
        AlertDialog dialog = (AlertDialog) getDialog();
        // Make sure the dialog has not already been dismissed or destroyed.
        if (dialog != null) {
            updateOkButtonState(dialog);
        }
    }

    private void updateOkButtonState(AlertDialog dialog) {
        Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        okButton.setEnabled(!TextUtils.isEmpty(mEdit.getText().toString().trim()));
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
}
