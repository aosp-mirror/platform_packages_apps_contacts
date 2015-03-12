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
 * limitations under the License.
 */

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageProvider;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.editor.CompactContactEditorFragment.PhotoHandler;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.widget.QuickContactImageView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.DisplayPhoto;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Displays the primary photo.
 */
public class CompactPhotoEditorView extends LinearLayout implements View.OnClickListener {

    private static final String TAG = CompactContactEditorFragment.TAG;

    private ContactPhotoManager mContactPhotoManager;
    private PhotoHandler mPhotoHandler;

    private final float mLandscapePhotoRatio;
    private final boolean mIsTwoPanel;

    private ValuesDelta mValuesDelta;
    private boolean mReadOnly;
    private boolean mIsPhotoSet;
    private MaterialPalette mMaterialPalette;

    private QuickContactImageView mPhotoImageView;

    public CompactPhotoEditorView(Context context) {
        this(context, null);
    }

    public CompactPhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final TypedValue landscapePhotoRatio = new TypedValue();
        getResources().getValue(R.dimen.quickcontact_landscape_photo_ratio, landscapePhotoRatio,
                /* resolveRefs =*/ true);
        mLandscapePhotoRatio = landscapePhotoRatio.getFloat();
        mIsTwoPanel = getResources().getBoolean(R.bool.quickcontact_two_panel);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContactPhotoManager = ContactPhotoManager.getInstance(getContext());

        mPhotoImageView = (QuickContactImageView) findViewById(R.id.photo);
        mPhotoImageView.setOnClickListener(this);
    }

    public void setValues(DataKind dataKind, ValuesDelta valuesDelta,
            RawContactDelta rawContactDelta, boolean readOnly, MaterialPalette materialPalette,
            ViewIdGenerator viewIdGenerator) {
        mValuesDelta = valuesDelta;
        mReadOnly = readOnly;
        mMaterialPalette = materialPalette;

        setId(viewIdGenerator.getId(rawContactDelta, dataKind, valuesDelta, /* viewIndex =*/ 0));

        if (valuesDelta == null) {
            setDefaultPhoto();
        } else {
            final byte[] bytes = valuesDelta.getAsByteArray(Photo.PHOTO);
            if (bytes == null) {
                setDefaultPhoto();
            } else {
                final Bitmap bitmap = BitmapFactory.decodeByteArray(
                        bytes, /* offset =*/ 0, bytes.length);
                mPhotoImageView.setImageBitmap(bitmap);
                mIsPhotoSet = true;
                mValuesDelta.setFromTemplate(false);

                // Check if we can update to the full size photo immediately
                if (valuesDelta.getAfter() == null
                        || valuesDelta.getAfter().get(Photo.PHOTO) == null) {
                    // If the user hasn't updated the PHOTO value, then PHOTO_FILE_ID may contain
                    // a reference to a larger version of PHOTO that we can bind to the UI.
                    // Otherwise, we need to wait for a call to #setFullSizedPhoto() to update
                    // our full sized image.
                    final Integer fileId = valuesDelta.getAsInteger(Photo.PHOTO_FILE_ID);
                    if (fileId != null) {
                        final Uri photoUri = DisplayPhoto.CONTENT_URI.buildUpon()
                                .appendPath(fileId.toString()).build();
                        setFullSizedPhoto(photoUri);
                    }
                }
            }
        }

        if (!mIsPhotoSet) {
            setDefaultPhotoTint();
        }

        // Adjust the photo dimensions following the same logic as in
        // MultiShrinkScroll.initialize
        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame =*/ false, new Runnable() {
            @Override
            public void run() {

                final int photoHeight, photoWidth;
                if (mIsTwoPanel) {
                    // Make the photo slightly more narrow than it is tall
                    photoHeight = getHeight();
                    photoWidth = (int) (photoHeight * mLandscapePhotoRatio);
                } else {
                    // Make the photo a square
                    photoHeight = getWidth();
                    photoWidth = photoHeight;
                }
                final ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = photoHeight;
                layoutParams.width = photoWidth;
                setLayoutParams(layoutParams);
            }
        });
    }

    /**
     * Set the {@link PhotoHandler} to forward clicks (i.e. requests to edit the photo) to.
     */
    public void setPhotoHandler(PhotoHandler photoHandler) {
        mPhotoHandler = photoHandler;
    }

    /**
     * Whether a writable {@link Photo} has been set.
     */
    public boolean isWritablePhotoSet() {
        return mIsPhotoSet && !mReadOnly;
    }

    /**
     * Set the given {@link Bitmap} as the photo in the underlying {@link ValuesDelta}
     * and bind a thumbnail to the UI.
     */
    public void setPhoto(Bitmap bitmap) {
        if (mReadOnly) {
            Log.w(TAG, "Attempted to set read only photo. Aborting");
            return;
        }
        if (bitmap == null) {
            mValuesDelta.put(ContactsContract.CommonDataKinds.Photo.PHOTO, (byte[]) null);
            setDefaultPhoto();
            return;
        }

        final int thumbnailSize = ContactsUtils.getThumbnailSize(getContext());
        final Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                bitmap, thumbnailSize, thumbnailSize, /* filter =*/ false);

        mPhotoImageView.setImageBitmap(scaledBitmap);
        mIsPhotoSet = true;
        mValuesDelta.setFromTemplate(false);

        // When the user chooses a new photo mark it as super primary
        mValuesDelta.setSuperPrimary(true);

        // Even though high-res photos cannot be saved by passing them via
        // an EntityDeltaList (since they cause the Bundle size limit to be
        // exceeded), we still pass a low-res thumbnail. This simplifies
        // code all over the place, because we don't have to test whether
        // there is a change in EITHER the delta-list OR a changed photo...
        // this way, there is always a change in the delta-list.
        final byte[] compressed = ContactPhotoUtils.compressBitmap(scaledBitmap);
        if (compressed != null) {
            mValuesDelta.setPhoto(compressed);
        }
    }

    /**
     * Show the default "add photo" place holder.
     */
    private void setDefaultPhoto() {
        mPhotoImageView.setImageDrawable(ContactPhotoManager.getDefaultAvatarDrawableForContact(
                getResources(), /* hires =*/ false, /* defaultImageRequest =*/ null));
        setDefaultPhotoTint();
        mIsPhotoSet = false;
        mValuesDelta.setFromTemplate(true);
    }

    private void setDefaultPhotoTint() {
        final int color = mMaterialPalette == null
                ? MaterialColorMapUtils.getDefaultPrimaryAndSecondaryColors(
                getResources()).mPrimaryColor
                : mMaterialPalette.mPrimaryColor;
        mPhotoImageView.setTint(color);
    }

    /**
     * Bind the photo at the given Uri to the UI but do not set the photo on the underlying
     * {@link ValuesDelta}.
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
                    mPhotoImageView.getWidth(), /* darkTheme =*/ false, /* isCircular =*/ false,
                    /* defaultImageRequest =*/ null, fallbackToPreviousImage);
        }
    }

    @Override
    public void onClick(View view) {
        if (mPhotoHandler != null) {
            mPhotoHandler.onClick(view);
        }
    }
}
