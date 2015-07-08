/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.DisplayPhoto;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager.DefaultImageProvider;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.util.ContactPhotoUtils;

/**
 * Simple editor for {@link Photo}.
 */
public class PhotoEditorView extends LinearLayout implements Editor {

    private ImageView mPhotoImageView;
    private Button mChangeButton;
    private RadioButton mPrimaryCheckBox;

    private ValuesDelta mEntry;
    private EditorListener mListener;
    private ContactPhotoManager mContactPhotoManager;

    private boolean mHasSetPhoto = false;

    public PhotoEditorView(Context context) {
        super(context);
    }

    public PhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    @Override
    public void editNewlyAddedField() {
        // Never called, since the user never adds a new photo-editor;
        // you can only change the picture in an existing editor.
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContactPhotoManager = ContactPhotoManager.getInstance(getContext());
        mPhotoImageView = (ImageView) findViewById(R.id.photo);
        mPrimaryCheckBox = (RadioButton) findViewById(R.id.primary_checkbox);
        mChangeButton = (Button) findViewById(R.id.change_button);
        mPrimaryCheckBox = (RadioButton) findViewById(R.id.primary_checkbox);
        if (mChangeButton != null) {
            mChangeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onRequest(EditorListener.REQUEST_PICK_PHOTO);
                    }
                }
            });
        }
        // Turn off own state management. We do this ourselves on rotation.
        mPrimaryCheckBox.setSaveEnabled(false);
        mPrimaryCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.REQUEST_PICK_PRIMARY_PHOTO);
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onFieldChanged(String column, String value) {
        throw new UnsupportedOperationException("Photos don't support direct field changes");
    }

    /** {@inheritDoc} */
    @Override
    public void setValues(DataKind kind, ValuesDelta values, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        mEntry = values;

        setId(vig.getId(state, kind, values, 0));

        mPrimaryCheckBox.setChecked(values != null && values.isSuperPrimary());

        if (values != null) {
            // Try decoding photo if actual entry
            final byte[] photoBytes = values.getAsByteArray(Photo.PHOTO);
            if (photoBytes != null) {
                final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                        photoBytes.length);

                mPhotoImageView.setImageBitmap(photo);
                mHasSetPhoto = true;
                mEntry.setFromTemplate(false);

                if (values.getAfter() == null || values.getAfter().get(Photo.PHOTO) == null) {
                    // If the user hasn't updated the PHOTO value, then PHOTO_FILE_ID may contain
                    // a reference to a larger version of PHOTO that we can bind to the UI.
                    // Otherwise, we need to wait for a call to #setFullSizedPhoto() to update
                    // our full sized image.
                    final Integer photoFileId = values.getAsInteger(Photo.PHOTO_FILE_ID);
                    if (photoFileId != null) {
                        final Uri photoUri = DisplayPhoto.CONTENT_URI.buildUpon()
                                .appendPath(photoFileId.toString()).build();
                        setFullSizedPhoto(photoUri);
                    }
                }

            } else {
                resetDefault();
            }
        } else {
            resetDefault();
        }
    }

    /**
     * Whether to display a "Primary photo" RadioButton. This is only needed if there are multiple
     * candidate photos.
     */
    public void setShowPrimary(boolean showPrimaryCheckBox) {
        mPrimaryCheckBox.setVisibility(showPrimaryCheckBox ? View.VISIBLE : View.GONE);
    }

    /**
     * Return true if a valid {@link Photo} has been set.
     */
    public boolean hasSetPhoto() {
        return mHasSetPhoto;
    }

    /**
     * Assign the given {@link Bitmap} as the new value for the sake of building
     * {@link ValuesDelta}. We may as well bind a thumbnail to the UI while we are at it.
     */
    public void setPhotoEntry(Bitmap photo) {
        if (photo == null) {
            // Clear any existing photo and return
            mEntry.put(Photo.PHOTO, (byte[])null);
            resetDefault();
            return;
        }

        final int size = ContactsUtils.getThumbnailSize(getContext());
        final Bitmap scaled = Bitmap.createScaledBitmap(photo, size, size, false);

        mPhotoImageView.setImageBitmap(scaled);
        mHasSetPhoto = true;
        mEntry.setFromTemplate(false);

        // When the user chooses a new photo mark it as super primary
        mEntry.setSuperPrimary(true);

        // Even though high-res photos cannot be saved by passing them via
        // an EntityDeltaList (since they cause the Bundle size limit to be
        // exceeded), we still pass a low-res thumbnail. This simplifies
        // code all over the place, because we don't have to test whether
        // there is a change in EITHER the delta-list OR a changed photo...
        // this way, there is always a change in the delta-list.
        final byte[] compressed = ContactPhotoUtils.compressBitmap(scaled);
        if (compressed != null) {
            mEntry.setPhoto(compressed);
        }
    }

    /**
     * Bind the {@param photoUri}'s photo to editor's UI. This doesn't affect {@link ValuesDelta}.
     */
    public void setFullSizedPhoto(Uri photoUri) {
        if (photoUri != null) {
            final DefaultImageProvider fallbackToPreviousImage = new DefaultImageProvider() {
                @Override
                public void applyDefaultImage(ImageView view, int extent, boolean darkTheme,
                        DefaultImageRequest defaultImageRequest) {
                    // Before we finish setting the full sized image, don't change the current
                    // image that is set in any way.
                }
            };
            mContactPhotoManager.loadPhoto(mPhotoImageView, photoUri,
                    mPhotoImageView.getWidth(), /* darkTheme = */ false, /* isCircular = */ false,
                    /* defaultImageRequest = */ null, fallbackToPreviousImage);
        }
    }

    /**
     * Set the super primary bit on the photo.
     */
    public void setSuperPrimary(boolean superPrimary) {
        mEntry.put(Photo.IS_SUPER_PRIMARY, superPrimary ? 1 : 0);
    }

    protected void resetDefault() {
        // Invalid photo, show default "add photo" place-holder
        mPhotoImageView.setImageDrawable(
                ContactPhotoManager.getDefaultAvatarDrawableForContact(getResources(), false, null));
        mHasSetPhoto = false;
        mEntry.setFromTemplate(true);
    }

    /** {@inheritDoc} */
    @Override
    public void setEditorListener(EditorListener listener) {
        mListener = listener;
    }

    @Override
    public void setDeletable(boolean deletable) {
        // Photo is not deletable
    }

    @Override
    public boolean isEmpty() {
        return !mHasSetPhoto;
    }

    @Override
    public void markDeleted() {
        // Photo is not deletable
    }

    @Override
    public void deleteEditor() {
        // Photo is not deletable
    }

    @Override
    public void clearAllFields() {
        resetDefault();
    }

    /**
     * The change drop down menu should be anchored to this view.
     */
    public View getChangeAnchorView() {
        return mChangeButton;
    }
}
