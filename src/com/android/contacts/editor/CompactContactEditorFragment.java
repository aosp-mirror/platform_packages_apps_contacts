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

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.CompactContactEditorActivity;
import com.android.contacts.activities.ContactEditorBaseActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.detail.PhotoSelectionHandler;
import com.android.contacts.util.ContactPhotoUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileNotFoundException;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorFragment extends ContactEditorBaseFragment implements
        CompactRawContactsEditorView.Listener {

    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String KEY_PHOTO_RAW_CONTACT_ID = "photo_raw_contact_id";
    private static final String KEY_UPDATED_PHOTOS = "updated_photos";
    private static final String KEY_MATERIAL_PALETTE = "material_palette";

    /**
     * Displays a PopupWindow with photo edit options.
     */
    final class PhotoHandler extends PhotoSelectionHandler {

        /**
         * Receiver of photo edit option callbacks.
         */
        private final class PhotoListener extends PhotoActionListener {
            @Override
            public void onRemovePictureChosen() {
                getContent().setPhoto(/* bitmap =*/ null);
                mUpdatedPhotos.remove(String.valueOf(mPhotoRawContactId));
            }

            @Override
            public void onPhotoSelected(Uri uri) throws FileNotFoundException {
                final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(getActivity(), uri);
                if (bitmap == null || bitmap.getHeight() <= 0 || bitmap.getWidth() <= 0) {
                    Log.w(TAG, "Invalid photo selected");
                }
                getContent().setPhoto(bitmap);
                mUpdatedPhotos.putParcelable(String.valueOf(mPhotoRawContactId), uri);
            }

            @Override
            public Uri getCurrentPhotoUri() {
                return mPhotoUri;
            }

            @Override
            public void onPhotoSelectionDismissed() {
            }
        }

        private PhotoListener mPhotoListener;

        public PhotoHandler(Context context, View changeAnchorView, int photoMode,
                boolean isDirectoryContact, RawContactDeltaList state) {
            super(context, changeAnchorView, photoMode, isDirectoryContact, state);
            mPhotoListener = new PhotoListener();
        }

        @Override
        public PhotoActionListener getListener() {
            return mPhotoListener;
        }

        @Override
        protected void startPhotoActivity(Intent intent, int requestCode, Uri photoUri) {
            mPhotoUri = photoUri;
            mStatus = Status.SUB_ACTIVITY;

            CompactContactEditorFragment.this.startActivityForResult(intent, requestCode);
        }
    }

    private PhotoHandler mPhotoHandler;
    private Uri mPhotoUri;
    private long mPhotoRawContactId;
    private Bundle mUpdatedPhotos = new Bundle();
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mPhotoUri = savedState.getParcelable(KEY_PHOTO_URI);
            mPhotoRawContactId = savedState.getLong(KEY_PHOTO_RAW_CONTACT_ID);
            mUpdatedPhotos = savedState.getParcelable(KEY_UPDATED_PHOTOS);
            mMaterialPalette = savedState.getParcelable(KEY_MATERIAL_PALETTE);
        } else {
            mMaterialPalette = getActivity().getIntent().getParcelableExtra(
                    ContactEditorBaseActivity.INTENT_KEY_MATERIAL_PALETTE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);

        final View view = inflater.inflate(
                R.layout.compact_contact_editor_fragment, container, false);
        mContent = (LinearLayout) view.findViewById(R.id.editors);
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_PHOTO_URI, mPhotoUri);
        outState.putLong(KEY_PHOTO_RAW_CONTACT_ID, mPhotoRawContactId);
        outState.putParcelable(KEY_UPDATED_PHOTOS, mUpdatedPhotos);
        if (mMaterialPalette != null) {
            outState.putParcelable(KEY_MATERIAL_PALETTE, mMaterialPalette);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mStatus == Status.SUB_ACTIVITY) {
            mStatus = Status.EDITING;
        }
        if (mPhotoHandler != null
                && mPhotoHandler.handlePhotoActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void bindEditors() {
        if (!isReadyToBindEditors()) {
            return;
        }

        final CompactRawContactsEditorView editorView = getContent();
        editorView.setListener(this);
        editorView.setState(mState, mMaterialPalette, mViewIdGenerator);
        editorView.setEnabled(isEnabled());
        editorView.setVisibility(View.VISIBLE);

        mPhotoHandler = createPhotoHandler();
        mPhotoRawContactId = editorView.getPhotoRawContactId();
        editorView.setPhotoHandler(mPhotoHandler);

        invalidateOptionsMenu();
    }

    private boolean isReadyToBindEditors() {
        if (mState.isEmpty()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "No data to bind editors");
            }
            return false;
        }
        if (mIsEdit && !mExistingContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Existing contact data is not ready to bind editors.");
            }
            return false;
        }
        if (mHasNewContact && !mNewContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "New contact data is not ready to bind editors.");
            }
            return false;
        }
        return true;
    }

    private PhotoHandler createPhotoHandler() {
        // To determine the options that are available to the user to update their photo
        // (i.e. the photo mode), check if any of the writable raw contacts has a photo set
        Integer photoMode = null;
        boolean hasWritableAccountType = false;
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        for (RawContactDelta rawContactDelta : mState) {
            if (!rawContactDelta.isVisible()) {
                continue;
            }
            final AccountType accountType = rawContactDelta.getAccountType(accountTypes);
            if (accountType.areContactsWritable()) {
                hasWritableAccountType = true;
                if (getContent().isWritablePhotoSet()) {
                    photoMode = PhotoActionPopup.Modes.WRITE_ABLE_PHOTO;
                    break;
                }
            }
        }
        // If the mode was not set, base it on whether we saw a writable contact or not
        if (photoMode == null) {
            photoMode = hasWritableAccountType
                    ? PhotoActionPopup.Modes.NO_PHOTO : PhotoActionPopup.Modes.READ_ONLY_PHOTO;
        }

        return new PhotoHandler(getActivity(), getContent().getPhotoPopupAnchorView(),
                photoMode, /* isDirectoryContact =*/ false, mState);
    }

    @Override
    protected void setGroupMetaData() {
        // The compact editor does not support groups.
    }

    @Override
    protected boolean doSaveAction(int saveMode) {
        // Save contact
        final Intent intent = ContactSaveService.createSaveContactIntent(mContext, mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                ((Activity) mContext).getClass(),
                CompactContactEditorActivity.ACTION_SAVE_COMPLETED,
                mUpdatedPhotos);
        mContext.startService(intent);

        return true;
    }

    @Override
    protected void joinAggregate(final long contactId) {
        final Intent intent = ContactSaveService.createJoinContactsIntent(
                mContext, mContactIdForJoin, contactId, mContactWritableForJoin,
                CompactContactEditorActivity.class,
                CompactContactEditorActivity.ACTION_JOIN_COMPLETED);
        mContext.startService(intent);
    }

    @Override
    public void onExpandEditor() {
        Toast.makeText(mContext, "Not yet implemented", Toast.LENGTH_SHORT).show();
    }

    private CompactRawContactsEditorView getContent() {
        return (CompactRawContactsEditorView) mContent;
    }
}
