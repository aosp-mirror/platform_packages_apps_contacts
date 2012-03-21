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
package com.android.contacts.activities;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.detail.PhotoSelectionHandler;
import com.android.contacts.editor.PhotoActionPopup;
import com.android.contacts.model.EntityDeltaList;
import com.android.contacts.util.SchedulingUtils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;

import java.io.File;

/**
 * Popup activity for choosing a contact photo within the Contacts app.
 */
public class PhotoSelectionActivity extends Activity {

    /** Number of ms for the animation to expand the photo. */
    private static final int PHOTO_EXPAND_DURATION = 100;

    /** Number of ms for the animation to contract the photo on activity exit. */
    private static final int PHOTO_CONTRACT_DURATION = 50;

    /** Number of ms for the animation to hide the backdrop on finish. */
    private static final int BACKDROP_FADEOUT_DURATION = 100;

    private static final String KEY_CURRENT_PHOTO_FILE = "currentphotofile";

    private static final String KEY_SUB_ACTIVITY_IN_PROGRESS = "subinprogress";

    /** Intent extra to get the photo URI. */
    public static final String PHOTO_URI = "photo_uri";

    /** Intent extra to get the entity delta list. */
    public static final String ENTITY_DELTA_LIST = "entity_delta_list";

    /** Intent extra to indicate whether the contact is the user's profile. */
    public static final String IS_PROFILE = "is_profile";

    /** Intent extra to indicate whether the contact is from a directory (non-editable). */
    public static final String IS_DIRECTORY_CONTACT = "is_directory_contact";

    /**
     * Intent extra to indicate whether the photo should be animated to show the full contents of
     * the photo (on a larger portion of the screen) when clicked.  If unspecified or false, the
     * photo will not move from its original location.
     */
    public static final String EXPAND_PHOTO = "expand_photo";

    /** Source bounds of the image that was clicked on. */
    private Rect mSourceBounds;

    /**
     * The photo URI. May be null, in which case the default avatar will be used.
     */
    private Uri mPhotoUri;

    /** Entity delta list of the contact. */
    private EntityDeltaList mState;

    /** Whether the contact is the user's profile. */
    private boolean mIsProfile;

    /** Whether the contact is from a directory. */
    private boolean mIsDirectoryContact;

    /** Whether to animate the photo to an expanded view covering more of the screen. */
    private boolean mExpandPhoto;

    /** The semi-transparent backdrop. */
    private View mBackdrop;

    /** The photo view. */
    private ImageView mPhotoView;

    /** The photo handler attached to this activity, if any. */
    private PhotoHandler mPhotoHandler;

    /** Animator to expand the photo out to full size. */
    private ObjectAnimator mPhotoAnimator;

    /** Listener for the animation. */
    private AnimatorListenerAdapter mAnimationListener;

    /** Whether a change in layout of the photo has occurred that has no animation yet. */
    private boolean mAnimationPending;

    /** Prior position of the image (for animating). */
    Rect mOriginalPos = new Rect();

    /** Layout params for the photo view before we started animating. */
    private LayoutParams mPhotoStartParams;

    /** Layout params for the photo view after we finished animating. */
    private LayoutParams mPhotoEndParams;

    /** Whether a sub-activity is currently in progress. */
    private boolean mSubActivityInProgress;

    /**
     * A photo result received by the activity, persisted across activity lifecycle.
     */
    private PendingPhotoResult mPendingPhotoResult;

    /**
     * The photo file being interacted with, if any.  Saved/restored between activity instances.
     */
    private File mCurrentPhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photoselection_activity);
        if (savedInstanceState != null) {
            String fileName = savedInstanceState.getString(KEY_CURRENT_PHOTO_FILE);
            if (fileName != null) {
                mCurrentPhotoFile = new File(fileName);
            }
            mSubActivityInProgress = savedInstanceState.getBoolean(KEY_SUB_ACTIVITY_IN_PROGRESS);
        }

        // Pull data out of the intent.
        final Intent intent = getIntent();
        mPhotoUri = intent.getParcelableExtra(PHOTO_URI);
        mState = (EntityDeltaList) intent.getParcelableExtra(ENTITY_DELTA_LIST);
        mIsProfile = intent.getBooleanExtra(IS_PROFILE, false);
        mIsDirectoryContact = intent.getBooleanExtra(IS_DIRECTORY_CONTACT, false);
        mExpandPhoto = intent.getBooleanExtra(EXPAND_PHOTO, false);

        mBackdrop = findViewById(R.id.backdrop);
        mPhotoView = (ImageView) findViewById(R.id.photo);
        mSourceBounds = intent.getSourceBounds();

        // Fade in the background.
        animateInBackground();

        // Dismiss the dialog on clicking the backdrop.
        mBackdrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Wait until the layout pass to show the photo, so that the source bounds will match up.
        SchedulingUtils.doAfterLayout(mBackdrop, new Runnable() {
            @Override
            public void run() {
                displayPhoto();
            }
        });
    }

    @Override
    public void finish() {
        if (!mSubActivityInProgress) {
            closePhotoAndFinish();
        } else {
            activityFinish();
        }
    }

    /**
     * Builds a well-formed intent for invoking this activity.
     * @param context The context.
     * @param photoUri The URI of the current photo (may be null, in which case the default
     *     avatar image will be displayed).
     * @param photoBitmap The bitmap of the current photo (may be null, in which case the default
     *     avatar image will be displayed).
     * @param photoBytes The bytes for the current photo (may be null, in which case the default
     *     avatar image will be displayed).
     * @param photoBounds The pixel bounds of the current photo.
     * @param delta The entity delta list for the contact.
     * @param isProfile Whether the contact is the user's profile.
     * @param isDirectoryContact Whether the contact comes from a directory (non-editable).
     * @param expandPhotoOnClick Whether the photo should be expanded on click or not (generally,
     *     this should be true for phones, and false for tablets).
     * @return An intent that can be used to invoke the photo selection activity.
     */
    public static Intent buildIntent(Context context, Uri photoUri, Bitmap photoBitmap,
            byte[] photoBytes, Rect photoBounds, EntityDeltaList delta, boolean isProfile,
            boolean isDirectoryContact, boolean expandPhotoOnClick) {
        Intent intent = new Intent(context, PhotoSelectionActivity.class);
        if (photoUri != null && photoBitmap != null && photoBytes != null) {
            intent.putExtra(PHOTO_URI, photoUri);
        }
        intent.setSourceBounds(photoBounds);
        intent.putExtra(ENTITY_DELTA_LIST, (Parcelable) delta);
        intent.putExtra(IS_PROFILE, isProfile);
        intent.putExtra(IS_DIRECTORY_CONTACT, isDirectoryContact);
        intent.putExtra(EXPAND_PHOTO, expandPhotoOnClick);
        return intent;
    }

    private void activityFinish() {
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPhotoAnimator != null) {
            mPhotoAnimator.cancel();
            mPhotoAnimator = null;
        }
        if (mPhotoHandler != null) {
            mPhotoHandler.destroy();
            mPhotoHandler = null;
        }
    }

    private void displayPhoto() {
        // Load the photo.
        if (mPhotoUri != null) {
            // If we have a URI, the bitmap should be cached directly.
            ContactPhotoManager.getInstance(this).loadPhoto(mPhotoView, mPhotoUri, true, false);
        } else {
            // Fall back to avatar image.
            mPhotoView.setImageResource(ContactPhotoManager.getDefaultAvatarResId(true, false));
        }

        // Animate the photo view into its end location.
        final int[] pos = new int[2];
        mBackdrop.getLocationOnScreen(pos);
        LayoutParams layoutParams = new LayoutParams(mSourceBounds.width(),
                mSourceBounds.height());
        mOriginalPos.left = mSourceBounds.left - pos[0];
        mOriginalPos.top = mSourceBounds.top - pos[1];
        mOriginalPos.right = mOriginalPos.left + mSourceBounds.width();
        mOriginalPos.bottom = mOriginalPos.top + mSourceBounds.height();
        layoutParams.setMargins(mOriginalPos.left, mOriginalPos.top, mOriginalPos.right,
                mOriginalPos.bottom);
        mPhotoStartParams = layoutParams;
        mPhotoView.setLayoutParams(layoutParams);
        mPhotoView.requestLayout();

        mPhotoView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (mAnimationPending) {
                    mAnimationPending = false;
                    PropertyValuesHolder pvhLeft =
                            PropertyValuesHolder.ofInt("left", mOriginalPos.left, left);
                    PropertyValuesHolder pvhTop =
                            PropertyValuesHolder.ofInt("top", mOriginalPos.top, top);
                    PropertyValuesHolder pvhRight =
                            PropertyValuesHolder.ofInt("right", mOriginalPos.right, right);
                    PropertyValuesHolder pvhBottom =
                            PropertyValuesHolder.ofInt("bottom", mOriginalPos.bottom, bottom);
                    ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(mPhotoView,
                            pvhLeft, pvhTop, pvhRight, pvhBottom).setDuration(
                            PHOTO_EXPAND_DURATION);
                    if (mAnimationListener != null) {
                        anim.addListener(mAnimationListener);
                    }
                    anim.start();
                }
            }
        });
        attachPhotoHandler();
    }

    private LayoutParams getPhotoEndParams() {
        if (mPhotoEndParams == null) {
            mPhotoEndParams = new LayoutParams(mPhotoStartParams);
            if (mExpandPhoto) {
                Rect bounds = new Rect();
                mBackdrop.getDrawingRect(bounds);
                if (bounds.height() > bounds.width()) {
                    //Take up full width.
                    mPhotoEndParams.width = bounds.width();
                    mPhotoEndParams.height = bounds.width();
                } else {
                    // Take up full height, leaving space for the popup.
                    mPhotoEndParams.height = bounds.height() - 150;
                    mPhotoEndParams.width = bounds.height() - 150;
                }
                mPhotoEndParams.topMargin = 0;
                mPhotoEndParams.leftMargin = 0;
                mPhotoEndParams.bottomMargin = mPhotoEndParams.height;
                mPhotoEndParams.rightMargin = mPhotoEndParams.width;
            }
        }
        return mPhotoEndParams;
    }

    private void animatePhotoOpen() {
        mAnimationListener = new AnimatorListenerAdapter() {
            private void capturePhotoPos() {
                mPhotoView.requestLayout();
                mOriginalPos.left = mPhotoView.getLeft();
                mOriginalPos.top = mPhotoView.getTop();
                mOriginalPos.right = mPhotoView.getRight();
                mOriginalPos.bottom = mPhotoView.getBottom();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                capturePhotoPos();
                if (mPhotoHandler != null) {
                    mPhotoHandler.onClick(mPhotoView);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                capturePhotoPos();
            }
        };
        animatePhoto(getPhotoEndParams());
    }

    private void closePhotoAndFinish() {
        mAnimationListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // After the photo animates down, fade it away and finish.
                ObjectAnimator anim = ObjectAnimator.ofFloat(
                        mPhotoView, "alpha", 0f).setDuration(PHOTO_CONTRACT_DURATION);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        activityFinish();
                    }
                });
                anim.start();
            }
        };

        // TODO: This won't animate in the right way if the rotation has changed since the activity
        // was first started.
        animatePhoto(mPhotoStartParams);
        animateAwayBackground();
    }

    private void animatePhoto(MarginLayoutParams to) {
        // Cancel any existing animation.
        if (mPhotoAnimator != null) {
            mPhotoAnimator.cancel();
        }

        mPhotoView.setLayoutParams(to);
        mAnimationPending = true;
        mPhotoView.requestLayout();
    }

    private void animateInBackground() {
        ObjectAnimator.ofFloat(mBackdrop, "alpha", 0, 0.5f).setDuration(
                PHOTO_EXPAND_DURATION).start();
    }

    private void animateAwayBackground() {
        ObjectAnimator.ofFloat(mBackdrop, "alpha", 0f).setDuration(
                BACKDROP_FADEOUT_DURATION).start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCurrentPhotoFile != null) {
            outState.putString(KEY_CURRENT_PHOTO_FILE, mCurrentPhotoFile.toString());
        }
        outState.putBoolean(KEY_SUB_ACTIVITY_IN_PROGRESS, mSubActivityInProgress);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPhotoHandler != null) {
            mSubActivityInProgress = false;
            if (mPhotoHandler.handlePhotoActivityResult(requestCode, resultCode, data)) {
                // Clear out any pending photo result.
                mPendingPhotoResult = null;
            } else {
                // User returning to the photo selection activity.  Re-display options.
                mPhotoHandler.onClick(mPhotoView);
            }
        } else {
            // Create a pending photo result to be handled when the photo handler is created.
            mPendingPhotoResult = new PendingPhotoResult(requestCode, resultCode, data);
        }
    }

    private void attachPhotoHandler() {
        // Always provide the same two choices (take a photo with the camera, select a photo
        // from the gallery), but with slightly different wording.
        // Note: don't worry about this being a read-only contact; this code will not be invoked.
        int mode = (mPhotoUri == null) ? PhotoActionPopup.Modes.NO_PHOTO
                : PhotoActionPopup.Modes.PHOTO_DISALLOW_PRIMARY;
        // We don't want to provide a choice to remove the photo for two reasons:
        //   1) the UX designs don't call for it
        //   2) even if we wanted to, the implementation would be moderately hairy
        mode &= ~PhotoActionPopup.Flags.REMOVE_PHOTO;

        mPhotoHandler = new PhotoHandler(this, mPhotoView, mode, mState);
        if (mPendingPhotoResult != null) {
            mPhotoHandler.handlePhotoActivityResult(mPendingPhotoResult.mRequestCode,
                    mPendingPhotoResult.mResultCode, mPendingPhotoResult.mData);
            mPendingPhotoResult = null;
        } else {
            // Setting the photo in displayPhoto() resulted in a relayout
            // request... to avoid jank, wait until this layout has happened.
            SchedulingUtils.doAfterLayout(mBackdrop, new Runnable() {
                @Override
                public void run() {
                    animatePhotoOpen();
                }
            });
        }
    }

    private final class PhotoHandler extends PhotoSelectionHandler {
        private PhotoHandler(Context context, View photoView, int photoMode,
                EntityDeltaList state) {
            super(context, photoView, photoMode, mIsDirectoryContact, state);
            setListener(new PhotoListener(context, mIsProfile));
        }

        private final class PhotoListener extends PhotoActionListener {
            @SuppressWarnings("hiding")
            private final boolean mIsProfile;
            private final Context mContext;

            private PhotoListener(Context context, boolean isProfile) {
                mContext = context;
                mIsProfile = isProfile;
            }

            @Override
            public void startTakePhotoActivity(Intent intent, int requestCode, File photoFile) {
                mSubActivityInProgress = true;
                mCurrentPhotoFile = photoFile;
                startActivityForResult(intent, requestCode);
            }

            @Override
            public void startPickFromGalleryActivity(Intent intent, int requestCode,
                    File photoFile) {
                mSubActivityInProgress = true;
                mCurrentPhotoFile = photoFile;
                startActivityForResult(intent, requestCode);
            }

            @Override
            public void onPhotoSelected(Bitmap bitmap) {
                EntityDeltaList delta = getDeltaForAttachingPhotoToContact();
                long rawContactId = getWritableEntityId();
                String filePath = mCurrentPhotoFile.getAbsolutePath();
                Intent intent = ContactSaveService.createSaveContactIntent(mContext, delta,
                        "", 0, mIsProfile, PhotoSelectionActivity.class,
                        ContactEditorActivity.ACTION_SAVE_COMPLETED, rawContactId, filePath);
                startService(intent);
                finish();
            }

            @Override
            public File getCurrentPhotoFile() {
                return mCurrentPhotoFile;
            }

            @Override
            public void onPhotoSelectionDismissed() {
                if (!mSubActivityInProgress) {
                    finish();
                }
            }
        }
    }

    private static class PendingPhotoResult {
        private int mRequestCode;
        private int mResultCode;
        private Intent mData;
        private PendingPhotoResult(int requestCode, int resultCode, Intent data) {
            mRequestCode = requestCode;
            mResultCode = resultCode;
            mData = data;
        }
    }
}
