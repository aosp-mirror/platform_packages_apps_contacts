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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

/**
 * Shows a dialog asking the user what to do with an existing photo.
 * The result is passed back to the Fragment that is configured by
 * {@link Fragment#setTargetFragment(Fragment, int)}, which
 * has to implement {@link ModifyPhotoDialogFragment.Listener}.
 * Does not perform any action by itself.
 */
public class ModifyPhotoDialogFragment extends DialogFragment {
    public static final String TAG = "PhotoDialogFragment";
    private static final String BUNDLE_IS_READ_ONLY = "IS_READ_ONLY";
    private static final String BUNDLE_RAW_CONTACT_ID = "RAW_CONTACT_ID";

    private boolean mIsReadOnly;
    private long mRawContactId;

    public ModifyPhotoDialogFragment() {
    }

    public ModifyPhotoDialogFragment(boolean isReadOnly, long rawContactId) {
        mIsReadOnly = isReadOnly;
        mRawContactId = rawContactId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIsReadOnly = savedInstanceState.getBoolean(BUNDLE_IS_READ_ONLY);
            mRawContactId = savedInstanceState.getLong(BUNDLE_RAW_CONTACT_ID);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_IS_READ_ONLY, mIsReadOnly);
        outState.putLong(BUNDLE_RAW_CONTACT_ID, mRawContactId);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using correct theme
        final Activity context = getActivity();

        final String[] choices;
        if (mIsReadOnly) {
            choices = new String[1];
            choices[0] = context.getString(R.string.use_photo_as_primary);
        } else {
            choices = new String[3];
            choices[0] = context.getString(R.string.use_photo_as_primary);
            choices[1] = context.getString(R.string.removePicture);
            choices[2] = context.getString(R.string.changePicture);
        }
        final ListAdapter adapter = new ArrayAdapter<String>(context,
                android.R.layout.select_dialog_item, choices);

        final DialogInterface.OnClickListener clickListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    final Listener target = (Listener) getTargetFragment();
                    switch (which) {
                        case 0:
                            target.onUseAsPrimaryChosen(mRawContactId);
                            break;
                        case 1:
                            target.onRemovePictureChose(mRawContactId);
                            break;
                        case 2:
                            target.onChangePictureChosen(mRawContactId);
                            break;
                    }
                }
            };

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.attachToContact);
        builder.setSingleChoiceItems(adapter, -1, clickListener);
        return builder.create();
    }

    public interface Listener {
        void onUseAsPrimaryChosen(long rawContactId);
        void onRemovePictureChose(long rawContactId);
        void onChangePictureChosen(long rawContactId);
    }
}
