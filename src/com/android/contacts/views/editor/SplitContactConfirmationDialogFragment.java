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
 * limitations under the License
 */

package com.android.contacts.views.editor;

import com.android.contacts.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Shows a dialog asking the user whether to split the contact. The result is passed back
 * to the Fragment with the Id that is passed in the constructor (or the Activity if -1 is passed).
 * The target must implement {@link SplitContactConfirmationDialogFragment.Listener}
 * Does not split the contact itself.
 */
public class SplitContactConfirmationDialogFragment extends TargetedDialogFragment {
    public static final String TAG = "SplitContactConfirmationDialog";

    public SplitContactConfirmationDialogFragment() {
    }

    public SplitContactConfirmationDialogFragment(int targetFragmentId) {
        super(targetFragmentId);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.splitConfirmation_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.splitConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                final Listener targetListener = (Listener) getTarget();
                targetListener.onSplitContactConfirmed();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }

    public interface Listener {
        void onSplitContactConfirmed();
    }
}
