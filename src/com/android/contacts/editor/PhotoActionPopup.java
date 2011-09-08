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

import com.android.contacts.R;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;

import java.util.ArrayList;

/**
 * Shows a popup asking the user what to do for a photo. The result is pased back to the Listener
 */
public class PhotoActionPopup {
    public static final String TAG = "PhotoActionPopup";

    public static final int MODE_NO_PHOTO = 0;
    public static final int MODE_READ_ONLY_ALLOW_PRIMARY = 1;
    public static final int MODE_PHOTO_DISALLOW_PRIMARY = 2;
    public static final int MODE_PHOTO_ALLOW_PRIMARY = 3;

    public static ListPopupWindow createPopupMenu(Context context, View anchorView,
            final Listener listener, int mode) {
        // Build choices, depending on the current mode. We assume this Dialog is never called
        // if there are NO choices (e.g. a read-only picture is already super-primary)
        final ArrayList<ChoiceListItem> choices = new ArrayList<ChoiceListItem>(4);
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
            choices.add(new ChoiceListItem(ChoiceListItem.ID_TAKE_PHOTO,
                    context.getString(resId)));
        }
        // Select from Gallery (or "Select new from Gallery")
        if (mode == MODE_NO_PHOTO || mode == MODE_PHOTO_ALLOW_PRIMARY
                || mode == MODE_PHOTO_DISALLOW_PRIMARY) {
            final int resId = mode == MODE_NO_PHOTO ? R.string.pick_photo :R.string.pick_new_photo;
            choices.add(new ChoiceListItem(ChoiceListItem.ID_PICK_PHOTO,
                    context.getString(resId)));
        }
        final ListAdapter adapter = new ArrayAdapter<ChoiceListItem>(context,
                R.layout.select_dialog_item, choices);

        final ListPopupWindow listPopupWindow = new ListPopupWindow(context);
        final OnItemClickListener clickListener = new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ChoiceListItem choice = choices.get(position);
                listPopupWindow.dismiss();

                switch (choice.getId()) {
                    case ChoiceListItem.ID_USE_AS_PRIMARY:
                        listener.onUseAsPrimaryChosen();
                        break;
                    case ChoiceListItem.ID_REMOVE:
                        listener.onRemovePictureChosen();
                        break;
                    case ChoiceListItem.ID_TAKE_PHOTO:
                        listener.onTakePhotoChosen();
                        break;
                    case ChoiceListItem.ID_PICK_PHOTO:
                        listener.onPickFromGalleryChosen();
                        break;
                }
            }
        };

        listPopupWindow.setAnchorView(anchorView);
        listPopupWindow.setAdapter(adapter);
        listPopupWindow.setOnItemClickListener(clickListener);
        listPopupWindow.setWidth(context.getResources().getDimensionPixelSize(
                R.dimen.photo_action_popup_width));
        listPopupWindow.setModal(true);
        listPopupWindow.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        return listPopupWindow;
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
        void onUseAsPrimaryChosen();
        void onRemovePictureChosen();
        void onTakePhotoChosen();
        void onPickFromGalleryChosen();
    }
}
