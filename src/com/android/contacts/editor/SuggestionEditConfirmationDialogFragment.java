/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;

public class SuggestionEditConfirmationDialogFragment extends DialogFragment {

    private static final String ARG_CONTACT_URI = "contactUri";

    public static void show(ContactEditorBaseFragment fragment, Uri contactUri) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_CONTACT_URI, contactUri);

        final SuggestionEditConfirmationDialogFragment dialog = new
                SuggestionEditConfirmationDialogFragment();
        dialog.setArguments(args);
        dialog.setTargetFragment(fragment, 0);
        dialog.show(fragment.getFragmentManager(), "edit");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.aggregation_suggestion_edit_dialog_message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                final ContactEditorBaseFragment targetFragment =
                                        (ContactEditorBaseFragment) getTargetFragment();
                                final Uri contactUri =
                                        getArguments().getParcelable(ARG_CONTACT_URI);
                                targetFragment.doEditSuggestedContact(contactUri);
                            }
                        }
                )
                .setNegativeButton(android.R.string.no, null)
                .create();
    }
}
