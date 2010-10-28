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

import java.util.ArrayList;

/**
 * Shows a dialog asking the user what to do for a photo. The dialog has to be
 * configured by {@link #setArguments(int, long)}.
 * The result is passed back to the Fragment that is configured by
 * {@link Fragment#setTargetFragment(Fragment, int)}, which
 * has to implement {@link PhotoDialogFragment.Listener}.
 * Does not perform any action by itself.
 */
public class PhotoDialogFragment extends DialogFragment {
    public static final String TAG = "PhotoDialogFragment";
    private static final String BUNDLE_MODE = "MODE";
    private static final String BUNDLE_RAW_CONTACT_ID = "RAW_CONTACT_ID";

    public static final int MODE_NO_PHOTO = 0;
    public static final int MODE_READ_ONLY_ALLOW_PRIMARY = 1;
    public static final int MODE_PHOTO_DISALLOW_PRIMARY = 2;
    public static final int MODE_PHOTO_ALLOW_PRIMARY = 3;

    public PhotoDialogFragment() {
    }

    public void setArguments(int mode, long rawContactId) {
        final Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_MODE, mode);
        bundle.putLong(BUNDLE_RAW_CONTACT_ID, rawContactId);
        setArguments(bundle);
    }

    private int getMode() {
        return getArguments().getInt(BUNDLE_MODE);
    }

    private long getRawContactId() {
        return getArguments().getLong(BUNDLE_RAW_CONTACT_ID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using correct theme
        final Activity context = getActivity();

        int mode = getMode();
        // Build choices, depending on the current mode. We assume this Dialog is never called
        // if there are NO choices (e.g. a read-only picture is already super-primary)
        final ArrayList<ChoiceListItem> choices =
                new ArrayList<PhotoDialogFragment.ChoiceListItem>(4);
        // Use as Primary
        if (mode == MODE_PHOTO_ALLOW_PRIMARY || mode == MODE_READ_ONLY_ALLOW_PRIMARY) {
            choices.add(new ChoiceListItem(ChoiceListItem.ID_USE_AS_PRIMARY,
                    context.getString(R.string.use_photo_as_primary)));
        }
        // Remove
        if (mode == MODE_PHOTO_DISALLOW_PRIMARY || mode == MODE_PHOTO_ALLOW_PRIMARY) {
            choices.add(new ChoiceListItem(ChoiceListItem.ID_REMOVE,
                    context.getString(R.string.removePhoto)));
        }
        // Take photo (if there is already a photo, it says "Take new photo")
        if (mode == MODE_NO_PHOTO || mode == MODE_PHOTO_ALLOW_PRIMARY
                || mode == MODE_PHOTO_DISALLOW_PRIMARY) {
            final int resId = mode == MODE_NO_PHOTO ? R.string.take_photo :R.string.take_new_photo;
            choices.add(new ChoiceListItem(ChoiceListItem.ID_TAKE_PHOTO, context.getString(resId)));
        }
        // Select from Gallery (or "Select new from Gallery")
        if (mode == MODE_NO_PHOTO || mode == MODE_PHOTO_ALLOW_PRIMARY
                || mode == MODE_PHOTO_DISALLOW_PRIMARY) {
            final int resId = mode == MODE_NO_PHOTO ? R.string.pick_photo :R.string.pick_new_photo;
            choices.add(new ChoiceListItem(ChoiceListItem.ID_PICK_PHOTO, context.getString(resId)));
        }
        final ListAdapter adapter = new ArrayAdapter<ChoiceListItem>(context,
                android.R.layout.select_dialog_item, choices);

        final DialogInterface.OnClickListener clickListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    final ChoiceListItem choice = choices.get(which);

                    final Listener target = (Listener) getTargetFragment();
                    switch (choice.getId()) {
                        case ChoiceListItem.ID_USE_AS_PRIMARY:
                            target.onUseAsPrimaryChosen(getRawContactId());
                            break;
                        case ChoiceListItem.ID_REMOVE:
                            target.onRemovePictureChose(getRawContactId());
                            break;
                        case ChoiceListItem.ID_TAKE_PHOTO:
                            target.onTakePhotoChosen(getRawContactId());
                            break;
                        case ChoiceListItem.ID_PICK_PHOTO:
                            target.onPickFromGalleryChosen(getRawContactId());
                            break;
                    }
                }
            };

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.contact_photo_dialog_title);
        builder.setSingleChoiceItems(adapter, -1, clickListener);
        return builder.create();
    }

    private static final class ChoiceListItem {
        private final int mId;
        private final String mCaption;

        public static final int ID_USE_AS_PRIMARY = 0;
        public static final int ID_TAKE_PHOTO = 1;
        public static final int ID_PICK_PHOTO = 2;
        public static final int ID_REMOVE = 3;

        public ChoiceListItem(int id, String caption) {
            mId = id;
            mCaption = caption;
        }

        @Override
        public String toString() {
            return mCaption;
        }

        public int getId() {
            return mId;
        }
    }

    public interface Listener {
        void onUseAsPrimaryChosen(long rawContactId);
        void onRemovePictureChose(long rawContactId);
        void onTakePhotoChosen(long rawContactId);
        void onPickFromGalleryChosen(long rawContactId);
    }
}
