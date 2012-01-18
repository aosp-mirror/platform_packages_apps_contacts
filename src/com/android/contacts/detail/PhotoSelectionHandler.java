/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.detail;

import com.android.contacts.R;
import com.android.contacts.editor.PhotoActionPopup;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityDeltaList;
import com.android.contacts.model.EntityModifier;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.RawContacts;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handles displaying a photo selection popup for a given photo view and dealing with the results
 * that come back.
 */
public class PhotoSelectionHandler implements OnClickListener {

    private static final String TAG = PhotoSelectionHandler.class.getSimpleName();

    private static final File PHOTO_DIR = new File(
            Environment.getExternalStorageDirectory() + "/DCIM/Camera");

    private static final String PHOTO_DATE_FORMAT = "'IMG'_yyyyMMdd_HHmmss";

    private static final int REQUEST_CODE_CAMERA_WITH_DATA = 1001;
    private static final int REQUEST_CODE_PHOTO_PICKED_WITH_DATA = 1002;

    private final Context mContext;
    private final View mPhotoView;
    private final int mPhotoMode;
    private final int mPhotoPickSize;
    private final EntityDeltaList mState;
    private final boolean mIsDirectoryContact;
    private ListPopupWindow mPopup;
    private AccountType mWritableAccount;
    private PhotoActionListener mListener;

    public PhotoSelectionHandler(Context context, View photoView, int photoMode,
            boolean isDirectoryContact, EntityDeltaList state) {
        mContext = context;
        mPhotoView = photoView;
        mPhotoMode = photoMode;
        mIsDirectoryContact = isDirectoryContact;
        mState = state;
        mPhotoPickSize = getPhotoPickSize();
    }

    public void destroy() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
    }

    public PhotoActionListener getListener() {
        return mListener;
    }

    public void setListener(PhotoActionListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            if (getWritableEntityIndex() != -1) {
                mPopup = PhotoActionPopup.createPopupMenu(
                        mContext, mPhotoView, mListener, mPhotoMode);
                mPopup.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        mListener.onPhotoSelectionDismissed();
                    }
                });
                mPopup.show();
            }
        }
    }

    /**
     * Attempts to handle the given activity result.  Returns whether this handler was able to
     * process the result successfully.
     * @param requestCode The request code.
     * @param resultCode The result code.
     * @param data The intent that was returned.
     * @return Whether the handler was able to process the result.
     */
    public boolean handlePhotoActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_PHOTO_PICKED_WITH_DATA: {
                    Bitmap bitmap = BitmapFactory.decodeFile(
                            mListener.getCurrentPhotoFile().getAbsolutePath());
                    mListener.onPhotoSelected(bitmap);
                    return true;
                }
                case REQUEST_CODE_CAMERA_WITH_DATA: {
                    doCropPhoto(mListener.getCurrentPhotoFile());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return the index of the first entity in the contact data that belongs to a contact-writable
     * account, or -1 if no such entity exists.
     */
    private int getWritableEntityIndex() {
        // Directory entries are non-writable.
        if (mIsDirectoryContact) {
            return -1;
        }

        // Find the first writable entity.
        int entityIndex = 0;
        for (EntityDelta delta : mState) {
            ContentValues entityValues = delta.getValues().getCompleteValues();
            String type = entityValues.getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet = entityValues.getAsString(RawContacts.DATA_SET);
            AccountType accountType = AccountTypeManager.getInstance(mContext).getAccountType(
                    type, dataSet);
            if (accountType.areContactsWritable()) {
                mWritableAccount = accountType;
                return entityIndex;
            }
            entityIndex++;
        }
        return -1;
    }

    /**
     * Return the raw-contact id of the first entity in the contact data that belongs to a
     * contact-writable account, or -1 if no such entity exists.
     */
    protected long getWritableEntityId() {
        int index = getWritableEntityIndex();
        if (index == -1) return -1;
        return mState.get(index).getValues().getId();
    }

    /**
     * Utility method to retrieve the entity delta for attaching the given bitmap to the contact.
     * This will attach the photo to the first contact-writable account that provided data to the
     * contact.  It is the caller's responsibility to apply the delta.
     * @param bitmap The photo to use.
     * @return An entity delta list that can be applied to associate the bitmap with the contact,
     *     or null if the photo could not be parsed or none of the accounts associated with the
     *     contact are writable.
     */
    public EntityDeltaList getDeltaForAttachingPhotoToContact() {
        // Find the first writable entity.
        int writableEntityIndex = getWritableEntityIndex();
        if (writableEntityIndex != -1) {
            // Note - guaranteed to have contact data if we have a writable entity index.
            EntityDelta delta = mState.get(writableEntityIndex);
            ValuesDelta child = EntityModifier.ensureKindExists(
                    delta, mWritableAccount, Photo.CONTENT_ITEM_TYPE);
            child.setFromTemplate(false);
            child.put(Photo.IS_SUPER_PRIMARY, 1);

            return mState;
        }
        return null;
    }

    /**
     * Sends a newly acquired photo to Gallery for cropping
     */
    private void doCropPhoto(File f) {
        try {
            // Add the image to the media store
            MediaScannerConnection.scanFile(
                    mContext,
                    new String[] { f.getAbsolutePath() },
                    new String[] { null },
                    null);

            // Launch gallery to crop the photo
            final Intent intent = getCropImageIntent(f);
            mListener.startPickFromGalleryActivity(intent, REQUEST_CODE_PHOTO_PICKED_WITH_DATA, f);
        } catch (Exception e) {
            Log.e(TAG, "Cannot crop image", e);
            Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    private String getPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat(PHOTO_DATE_FORMAT);
        return dateFormat.format(date) + ".jpg";
    }

    private File getPhotoFile() {
        PHOTO_DIR.mkdirs();
        return new File(PHOTO_DIR, getPhotoFileName());
    }

    private int getPhotoPickSize() {
        // Note that this URI is safe to call on the UI thread.
        Cursor c = mContext.getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                new String[]{DisplayPhoto.DISPLAY_MAX_DIM}, null, null, null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    /**
     * Constructs an intent for picking a photo from Gallery, cropping it and returning the bitmap.
     */
    private Intent getPhotoPickIntent(File photoFile) {
        Uri photoUri = Uri.fromFile(photoFile);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", mPhotoPickSize);
        intent.putExtra("outputY", mPhotoPickSize);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        return intent;
    }

    /**
     * Constructs an intent for image cropping.
     */
    private Intent getCropImageIntent(File photoFile) {
        Uri photoUri = Uri.fromFile(photoFile);
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(photoUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", mPhotoPickSize);
        intent.putExtra("outputY", mPhotoPickSize);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        return intent;
    }

    /**
     * Constructs an intent for capturing a photo and storing it in a temporary file.
     */
    public static Intent getTakePhotoIntent(File f) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        return intent;
    }

    public abstract class PhotoActionListener implements PhotoActionPopup.Listener {
        @Override
        public void onUseAsPrimaryChosen() {
            // No default implementation.
        }

        @Override
        public void onRemovePictureChosen() {
            // No default implementation.
        }

        @Override
        public void onTakePhotoChosen() {
            try {
                // Launch camera to take photo for selected contact
                File f = getPhotoFile();
                final Intent intent = getTakePhotoIntent(f);
                startTakePhotoActivity(intent, REQUEST_CODE_CAMERA_WITH_DATA, f);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText,
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onPickFromGalleryChosen() {
            try {
                // Launch picker to choose photo for selected contact
                File f = getPhotoFile();
                final Intent intent = getPhotoPickIntent(f);
                startPickFromGalleryActivity(intent, REQUEST_CODE_PHOTO_PICKED_WITH_DATA, f);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText,
                        Toast.LENGTH_LONG).show();
            }
        }

        /**
         * Should initiate an activity to take a photo using the camera.
         * @param intent The image capture intent.
         * @param requestCode The request code to use, suitable for handling by
         *     {@link PhotoSelectionHandler#handlePhotoActivityResult(int, int, Intent)}.
         * @param photoFile The file path that will be used to store the photo.  This is generally
         *     what should be returned by
         *     {@link PhotoSelectionHandler.PhotoActionListener#getCurrentPhotoFile()}.
         */
        public abstract void startTakePhotoActivity(Intent intent, int requestCode, File photoFile);

        /**
         * Should initiate an activity pick a photo from the gallery.
         * @param intent The image capture intent.
         * @param requestCode The request code to use, suitable for handling by
         * @param photoFile The temporary file that the cropped image is written to before being
         *     stored by the content-provider.
         *     {@link PhotoSelectionHandler#handlePhotoActivityResult(int, int, Intent)}.
         */
        public abstract void startPickFromGalleryActivity(Intent intent, int requestCode,
                File photoFile);

        /**
         * Called when the user has completed selection of a photo.
         * @param bitmap The selected and cropped photo.
         */
        public abstract void onPhotoSelected(Bitmap bitmap);

        /**
         * Gets the current photo file that is being interacted with.  It is the activity or
         * fragment's responsibility to maintain this in saved state, since this handler instance
         * will not survive rotation.
         */
        public abstract File getCurrentPhotoFile();

        /**
         * Called when the photo selection dialog is dismissed.
         */
        public abstract void onPhotoSelectionDismissed();
    }
}
