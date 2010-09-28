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
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

/**
 * Shows a dialog asking the user whether to take a photo or pick a photo from the gallery.
 * The result is passed back to the Fragment that is configured by
 * {@link Fragment#setTargetFragment(Fragment, int)}, which
 * has to implement {@link PickPhotoDialogFragment.Listener}.
 * Does not perform any action by itself.
 */
public class PickPhotoDialogFragment extends DialogFragment {
    public static final String TAG = "PickPhotoDialogFragment";

    public PickPhotoDialogFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] choices = new String[2];
        choices[0] = getActivity().getString(R.string.take_photo);
        choices[1] = getActivity().getString(R.string.pick_photo);
        final ListAdapter adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.select_dialog_item, choices);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.attachToContact);
        builder.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                final Listener targetListener = (Listener) getTargetFragment();
                switch(which) {
                    case 0:
                        targetListener.onTakePhotoChosen();
                        break;
                    case 1:
                        targetListener.onPickFromGalleryChosen();
                        break;
                }
            }
        });
        return builder.create();
    }

    public interface Listener {
        void onTakePhotoChosen();
        void onPickFromGalleryChosen();
    }
}
