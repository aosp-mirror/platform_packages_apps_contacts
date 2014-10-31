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

package com.android.contacts.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.contacts.R;

/**
 * Shows a dialog asking the user whether to split the contact. The result is passed back
 * to the Fragment that is configured by {@link Fragment#setTargetFragment(Fragment, int)}, which
 * has to implement {@link SplitContactConfirmationDialogFragment.Listener}.
 * Does not split the contact itself.
 */
public class SplitContactConfirmationDialogFragment extends DialogFragment {
    public static final String TAG = "SplitContactConfirmationDialog";

    public SplitContactConfirmationDialogFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.splitConfirmation_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(R.string.splitConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final Listener targetListener = (Listener) getTargetFragment();
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
