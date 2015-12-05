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

package com.android.contacts.activities;

import com.android.contacts.R;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.detail.PhotoSelectionHandler;
import com.android.contacts.editor.CompactContactEditorFragment;
import com.android.contacts.editor.CompactPhotoSelectionFragment;
import com.android.contacts.editor.PhotoSourceDialogFragment;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorActivity extends ContactEditorBaseActivity implements
        PhotoSourceDialogFragment.Listener, CompactPhotoSelectionFragment.Listener {

    private static final String TAG_COMPACT_EDITOR = "compact_editor";
    private static final String TAG_PHOTO_SELECTION = "photo_selector";

    private static final String STATE_PHOTO_MODE = "photo_mode";
    private static final String STATE_IS_PHOTO_SELECTION = "is_photo_selection";
    private static final String STATE_ACTION_BAR_TITLE = "action_bar_title";
    private static final String STATE_PHOTO_URI = "photo_uri";

    /**
     * Displays a PopupWindow with photo edit options.
     */
    private final class CompactPhotoSelectionHandler extends PhotoSelectionHandler {

        /**
         * Receiver of photo edit option callbacks.
         */
        private final class CompactPhotoActionListener extends PhotoActionListener {

            @Override
            public void onRemovePictureChosen() {
                getEditorFragment().removePhoto();
                if (mIsPhotoSelection) {
                    showEditorFragment();
                }
            }

            @Override
            public void onPhotoSelected(Uri uri) throws FileNotFoundException {
                mPhotoUri = uri;
                getEditorFragment().updatePhoto(uri);
                if (mIsPhotoSelection) {
                    showEditorFragment();
                }

                // Re-create the photo handler the next time we need it so that additional photo
                // selections create a new temp file (and don't hit the one that was just added
                // to the cache).
                mPhotoSelectionHandler = null;
            }

            @Override
            public Uri getCurrentPhotoUri() {
                return mPhotoUri;
            }

            @Override
            public void onPhotoSelectionDismissed() {
                if (mIsPhotoSelection) {
                    showEditorFragment();
                }
            }
        }

        private final CompactPhotoActionListener mPhotoActionListener;
        private boolean mIsPhotoSelection;

        public CompactPhotoSelectionHandler(int photoMode, boolean isPhotoSelection) {
            // We pass a null changeAnchorView since we are overriding onClick so that we
            // can show the photo options in a dialog instead of a ListPopupWindow (which would
            // be anchored at changeAnchorView).

            // TODO: empty raw contact delta list
            super(CompactContactEditorActivity.this, /* changeAnchorView =*/ null, photoMode,
                    /* isDirectoryContact =*/ false, new RawContactDeltaList());
            mPhotoActionListener = new CompactPhotoActionListener();
            mIsPhotoSelection = isPhotoSelection;
        }

        @Override
        public PhotoActionListener getListener() {
            return mPhotoActionListener;
        }

        @Override
        protected void startPhotoActivity(Intent intent, int requestCode, Uri photoUri) {
            mPhotoUri = photoUri;
            startActivityForResult(intent, requestCode);
        }
    }

    private CompactPhotoSelectionFragment mPhotoSelectionFragment;
    private CompactPhotoSelectionHandler mPhotoSelectionHandler;
    private Uri mPhotoUri;
    private int mPhotoMode;
    private boolean mIsPhotoSelection;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        setContentView(R.layout.compact_contact_editor_activity);

        if (savedState == null) {
            // Create the editor and photo selection fragments
            mFragment = new CompactContactEditorFragment();
            mPhotoSelectionFragment = new CompactPhotoSelectionFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, getEditorFragment(), TAG_COMPACT_EDITOR)
                    .add(R.id.fragment_container, mPhotoSelectionFragment, TAG_PHOTO_SELECTION)
                    .hide(mPhotoSelectionFragment)
                    .commit();
        } else {
            // Restore state
            mPhotoMode = savedState.getInt(STATE_PHOTO_MODE);
            mIsPhotoSelection = savedState.getBoolean(STATE_IS_PHOTO_SELECTION);
            mActionBarTitleResId = savedState.getInt(STATE_ACTION_BAR_TITLE);
            mPhotoUri = Uri.parse(savedState.getString(STATE_PHOTO_URI));

            // Show/hide the editor and photo selection fragments (w/o animations)
            mFragment = (CompactContactEditorFragment) getFragmentManager()
                    .findFragmentByTag(TAG_COMPACT_EDITOR);
            mPhotoSelectionFragment = (CompactPhotoSelectionFragment) getFragmentManager()
                    .findFragmentByTag(TAG_PHOTO_SELECTION);
            final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            if (mIsPhotoSelection) {
                fragmentTransaction.hide(getEditorFragment()).show(mPhotoSelectionFragment);
                getActionBar().setTitle(getResources().getString(R.string.photo_picker_title));
            } else {
                fragmentTransaction.show(getEditorFragment()).hide(mPhotoSelectionFragment);
                getActionBar().setTitle(getResources().getString(mActionBarTitleResId));
            }
            fragmentTransaction.commit();
        }

        // Set listeners
        mFragment.setListener(mFragmentListener);
        mPhotoSelectionFragment.setListener(this);

        // Load editor data (even if it's hidden)
        final String action = getIntent().getAction();
        final Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PHOTO_MODE, mPhotoMode);
        outState.putBoolean(STATE_IS_PHOTO_SELECTION, mIsPhotoSelection);
        outState.putInt(STATE_ACTION_BAR_TITLE, mActionBarTitleResId);
        outState.putString(STATE_PHOTO_URI,
                mPhotoUri != null ? mPhotoUri.toString() : Uri.EMPTY.toString());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPhotoSelectionHandler == null) {
            mPhotoSelectionHandler = (CompactPhotoSelectionHandler) getPhotoSelectionHandler();
        }
        if (mPhotoSelectionHandler.handlePhotoActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (mIsPhotoSelection) {
            mIsPhotoSelection = false;
            showEditorFragment();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Displays photos from all raw contacts, clicking one set it as the super primary photo.
     */
    public void selectPhoto(ArrayList<CompactPhotoSelectionFragment.Photo> photos, int photoMode) {
        mPhotoMode = photoMode;
        mIsPhotoSelection = true;
        mPhotoSelectionFragment.setPhotos(photos, photoMode);
        showPhotoSelectionFragment();
    }

    /**
     * Opens a dialog showing options for the user to change their photo (take, choose, or remove
     * photo).
     */
    public void changePhoto(int photoMode) {
        mPhotoMode = photoMode;
        mIsPhotoSelection = false;
        PhotoSourceDialogFragment.show(this, mPhotoMode);
    }

    private void showPhotoSelectionFragment() {
        getFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
                .hide(getEditorFragment())
                .show(mPhotoSelectionFragment)
                .commit();
        getActionBar().setTitle(getResources().getString(R.string.photo_picker_title));
    }

    private void showEditorFragment() {
        getFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
                .hide(mPhotoSelectionFragment)
                .show((CompactContactEditorFragment) mFragment)
                .commit();
        getActionBar().setTitle(getResources().getString(mActionBarTitleResId));
        mIsPhotoSelection = false;
    }

    @Override
    public void onRemovePictureChosen() {
        getPhotoSelectionHandler().getListener().onRemovePictureChosen();
    }

    @Override
    public void onTakePhotoChosen() {
        getPhotoSelectionHandler().getListener().onTakePhotoChosen();
    }

    @Override
    public void onPickFromGalleryChosen() {
        getPhotoSelectionHandler().getListener().onPickFromGalleryChosen();
    }

    @Override
    public void onPhotoSelected(CompactPhotoSelectionFragment.Photo photo) {
        getEditorFragment().setPrimaryPhoto(photo);
        showEditorFragment();
    }

    private PhotoSelectionHandler getPhotoSelectionHandler() {
        if (mPhotoSelectionHandler == null) {
            mPhotoSelectionHandler = new CompactPhotoSelectionHandler(
                    mPhotoMode, mIsPhotoSelection);
        }
        return mPhotoSelectionHandler;
    }

    private CompactContactEditorFragment getEditorFragment() {
        return (CompactContactEditorFragment) mFragment;
    }
}
