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

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.DisplayPhoto;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * Displays the primary photo.
 */
public class CompactPhotoEditorView extends RelativeLayout implements View.OnClickListener {

    private static final String TAG = CompactContactEditorFragment.TAG;

    private ContactPhotoManager mContactPhotoManager;
    private PhotoHandler mPhotoHandler;

    private final float mLandscapePhotoRatio;
    private final float mPortraitPhotoRatio;
    private final boolean mIsTwoPanel;

    private final int mActionBarHeight;
    private final int mStatusBarHeight;

    private ValuesDelta mValuesDelta;
    private boolean mReadOnly;
    private boolean mIsPhotoSet;
    private MaterialPalette mMaterialPalette;

    private QuickContactImageView mPhotoImageView;
    private View mPhotoIcon;
    private View mPhotoIconOverlay;
    private View mPhotoTouchInterceptOverlay;

    public CompactPhotoEditorView(Context context) {
        this(context, null);
    }

    public CompactPhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLandscapePhotoRatio = getTypedFloat(R.dimen.quickcontact_landscape_photo_ratio);
        mPortraitPhotoRatio = getTypedFloat(R.dimen.editor_portrait_photo_ratio);
        mIsTwoPanel = getResources().getBoolean(R.bool.quickcontact_two_panel);

        final TypedArray styledAttributes = getContext().getTheme().obtainStyledAttributes(
                new int[] { android.R.attr.actionBarSize });
        mActionBarHeight = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();

        final int resourceId = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        mStatusBarHeight = resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private float getTypedFloat(int resourceId) {
        final TypedValue typedValue = new TypedValue();
        getResources().getValue(resourceId, typedValue, /* resolveRefs =*/ true);
        return typedValue.getFloat();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContactPhotoManager = ContactPhotoManager.getInstance(getContext());

        mPhotoImageView = (QuickContactImageView) findViewById(R.id.photo);
        mPhotoIcon = findViewById(R.id.photo_icon);
        mPhotoIconOverlay = findViewById(R.id.photo_icon_overlay);
        mPhotoTouchInterceptOverlay = findViewById(R.id.photo_touch_intercept_overlay);
    }

    public void setValues(DataKind dataKind, ValuesDelta valuesDelta,
            RawContactDelta rawContactDelta, boolean readOnly, MaterialPalette materialPalette,
            ViewIdGenerator viewIdGenerator) {
        mValuesDelta = valuesDelta;
        mReadOnly = readOnly;
        mMaterialPalette = materialPalette;

        if (mReadOnly) {
            mPhotoIcon.setVisibility(View.GONE);
            mPhotoIconOverlay.setVisibility(View.GONE);
        } else {
            mPhotoTouchInterceptOverlay.setOnClickListener(this);
        }

        setId(viewIdGenerator.getId(rawContactDelta, dataKind, valuesDelta, /* viewIndex =*/ 0));

        setPhoto(valuesDelta);
    }

    /**
     * Sets the photo bitmap on this view from the given ValuesDelta. Note that the
     * RawContactDelta underlying this view is not modified in any way.  Using this method allows
     * you to show one photo (from a read-only contact, for example) and yet have a different
     * raw contact updated when a new photo is set (from the new raw contact created and attached
     * to the read-only contact). See go/editing-read-only-contacts
     */
    public void setPhoto(ValuesDelta valuesDelta) {
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
                    final Long fileId = valuesDelta.getAsLong(Photo.PHOTO_FILE_ID);
                    if (fileId != null) {
                        final Uri photoUri = DisplayPhoto.CONTENT_URI.buildUpon()
                                .appendPath(fileId.toString()).build();
                        setFullSizedPhoto(photoUri);
                    }
                }
            }
        }

        if (mIsPhotoSet) {
            // Add background color behind the white photo icon so that it's visible even
            // if the contact photo is white.
            mPhotoIconOverlay.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0x88000000}));
        } else {
            setDefaultPhotoTint();
        }

        // Adjust the photo dimensions following the same logic as MultiShrinkScroll.initialize
        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame =*/ false, new Runnable() {
            @Override
            public void run() {
                final int photoHeight, photoWidth;
                if (mIsTwoPanel) {
                    photoHeight = getContentViewHeight();
                    photoWidth = (int) (photoHeight * mLandscapePhotoRatio);
                } else {
                    // Make the photo slightly shorter that it is wide
                    photoWidth = getWidth();
                    photoHeight = (int) (photoWidth / mPortraitPhotoRatio);
                }
                final ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = photoHeight;
                layoutParams.width = photoWidth;
                setLayoutParams(layoutParams);
            }
        });
    }

    // We're calculating the height the hard way because using the height of the content view
    // (found using android.view.Window.ID_ANDROID_CONTENT) with the soft keyboard up when
    // going from portrait to landscape mode results in a very small height value.
    // See b/20526470
    private int getContentViewHeight() {
        final Activity activity = (Activity) getContext();
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels - mActionBarHeight - mStatusBarHeight;
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
