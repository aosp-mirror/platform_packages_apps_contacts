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

import android.app.ActionBar;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.detail.PhotoSelectionHandler;
import com.android.contacts.editor.CompactContactEditorFragment;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.editor.PhotoSourceDialogFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.DialogManager;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorActivity extends ContactsActivity implements
        PhotoSourceDialogFragment.Listener,
        DialogManager.DialogShowingViewActivity {
    private static final String TAG = "ContactEditorActivity";

    public static final String ACTION_JOIN_COMPLETED = "joinCompleted";
    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";

    public static final int RESULT_CODE_SPLIT = 2;
    // 3 used for ContactDeletionInteraction.RESULT_CODE_DELETED
    public static final int RESULT_CODE_EDITED = 4;

    /**
     * The contact will be saved to the device local account when this is set for an insert. This
     * is necessary because {@link android.accounts.Account} cannot be created with null values
     * for the name and type and an Account is needed for
     * {@link android.provider.ContactsContract.Intents.Insert#EXTRA_ACCOUNT}
     */
    public static final String EXTRA_SAVE_TO_DEVICE_FLAG =
            "com.android.contacts.SAVE_TO_DEVICE_FLAG";

    private static final String TAG_COMPACT_EDITOR = "compact_editor";

    private static final String STATE_PHOTO_MODE = "photo_mode";
    private static final String STATE_ACTION_BAR_TITLE = "action_bar_title";
    private static final String STATE_PHOTO_URI = "photo_uri";

    /**
     * Boolean intent key that specifies that this activity should finish itself
     * (instead of launching a new view intent) after the editor changes have been
     * saved.
     */
    public static final String INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED =
            "finishActivityOnSaveCompleted";

    /**
     * Contract for contact editors Fragments that are managed by this Activity.
     */
    public interface ContactEditor {

        /**
         * Modes that specify what the AsyncTask has to perform after saving
         */
        interface SaveMode {
            /**
             * Close the editor after saving
             */
            int CLOSE = 0;

            /**
             * Reload the data so that the user can continue editing
             */
            int RELOAD = 1;

            /**
             * Split the contact after saving
             */
            int SPLIT = 2;

            /**
             * Join another contact after saving
             */
            int JOIN = 3;

            /**
             * Navigate to the compact editor view after saving.
             */
            int COMPACT = 4;
        }

        /**
         * The status of the contact editor.
         */
        interface Status {
            /**
             * The loader is fetching data
             */
            int LOADING = 0;

            /**
             * Not currently busy. We are waiting for the user to enter data
             */
            int EDITING = 1;

            /**
             * The data is currently being saved. This is used to prevent more
             * auto-saves (they shouldn't overlap)
             */
            int SAVING = 2;

            /**
             * Prevents any more saves. This is used if in the following cases:
             * - After Save/Close
             * - After Revert
             * - After the user has accepted an edit suggestion
             * - After the user chooses to expand the compact editor
             */
            int CLOSING = 3;

            /**
             * Prevents saving while running a child activity.
             */
            int SUB_ACTIVITY = 4;
        }

        /**
         * Sets the hosting Activity that will receive callbacks from the contact editor.
         */
        void setListener(CompactContactEditorFragment.Listener listener);

        /**
         * Initialize the contact editor.
         */
        void load(String action, Uri lookupUri, Bundle intentExtras);

        /**
         * Applies extras from the hosting Activity to the first writable raw contact.
         */
        void setIntentExtras(Bundle extras);

        /**
         * Saves or creates the contact based on the mode, and if successful
         * finishes the activity.
         */
        boolean save(int saveMode);

        /**
         * If there are no unsaved changes, just close the editor, otherwise the user is prompted
         * before discarding unsaved changes.
         */
        boolean revert();

        /**
         * Invoked after the contact is saved.
         */
        void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
                Uri contactLookupUri, Long joinContactId);

        /**
         * Invoked after the contact is joined.
         */
        void onJoinCompleted(Uri uri);
    }

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
            }

            @Override
            public void onPhotoSelected(Uri uri) throws FileNotFoundException {
                mPhotoUri = uri;
                getEditorFragment().updatePhoto(uri);

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
            }
        }

        private final CompactPhotoActionListener mPhotoActionListener;

        public CompactPhotoSelectionHandler(int photoMode) {
            // We pass a null changeAnchorView since we are overriding onClick so that we
            // can show the photo options in a dialog instead of a ListPopupWindow (which would
            // be anchored at changeAnchorView).

            // TODO: empty raw contact delta list
            super(CompactContactEditorActivity.this, /* changeAnchorView =*/ null, photoMode,
                    /* isDirectoryContact =*/ false, new RawContactDeltaList());
            mPhotoActionListener = new CompactPhotoActionListener();
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

    private int mActionBarTitleResId;
    private ContactEditor mFragment;
    private boolean mFinishActivityOnSaveCompleted;
    private DialogManager mDialogManager = new DialogManager(this);

    private CompactPhotoSelectionHandler mPhotoSelectionHandler;
    private Uri mPhotoUri;
    private int mPhotoMode;

    private final CompactContactEditorFragment.Listener  mFragmentListener =
            new CompactContactEditorFragment.Listener() {

                @Override
                public void onDeleteRequested(Uri contactUri) {
                    ContactDeletionInteraction.start(
                            CompactContactEditorActivity.this, contactUri, true);
                }

                @Override
                public void onReverted() {
                    finish();
                }

                @Override
                public void onSaveFinished(Intent resultIntent) {
                    if (mFinishActivityOnSaveCompleted) {
                        setResult(resultIntent == null ? RESULT_CANCELED : RESULT_OK, resultIntent);
                    } else if (resultIntent != null) {
                        // If it's a smart profile Intent it must be started "for result"
                        if (QuickContact.ACTION_QUICK_CONTACT.equals(resultIntent.getAction())) {
                            ImplicitIntentsUtil.startActivityInApp(
                                    CompactContactEditorActivity.this, resultIntent);
                        } else {
                            startActivityForResult(resultIntent, /* requestCode */ 0);
                        }

                        ImplicitIntentsUtil.startActivityInApp(
                                CompactContactEditorActivity.this, resultIntent);
                    }
                    finish();
                }

                @Override
                public void onContactSplit(Uri newLookupUri) {
                    setResult(RESULT_CODE_SPLIT, /* data */ null);
                    finish();
                }

                @Override
                public void onContactNotFound() {
                    finish();
                }

                @Override
                public void onEditOtherContactRequested(
                        Uri contactLookupUri, ArrayList<ContentValues> values) {
                    final Intent intent = EditorIntents.createEditOtherContactIntent(
                            CompactContactEditorActivity.this, contactLookupUri, values);
                    ImplicitIntentsUtil.startActivityInApp(
                            CompactContactEditorActivity.this, intent);
                    finish();
                }

                @Override
                public void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                        Bundle intentExtras) {
                    final AccountTypeManager accountTypes =
                            AccountTypeManager.getInstance(CompactContactEditorActivity.this);
                    final AccountType accountType = accountTypes.getAccountType(
                            account.type, account.dataSet);

                    Intent intent = new Intent();
                    intent.setClassName(accountType.syncAdapterPackageName,
                            accountType.getCreateContactActivityClassName());
                    intent.setAction(Intent.ACTION_INSERT);
                    intent.setType(Contacts.CONTENT_ITEM_TYPE);
                    if (intentExtras != null) {
                        intent.putExtras(intentExtras);
                    }
                    intent.putExtra(RawContacts.ACCOUNT_NAME, account.name);
                    intent.putExtra(RawContacts.ACCOUNT_TYPE, account.type);
                    intent.putExtra(RawContacts.DATA_SET, account.dataSet);
                    intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onCustomEditContactActivityRequested(AccountWithDataSet account,
                        Uri rawContactUri, Bundle intentExtras, boolean redirect) {
                    final AccountTypeManager accountTypes =
                            AccountTypeManager.getInstance(CompactContactEditorActivity.this);
                    final AccountType accountType = accountTypes.getAccountType(
                            account.type, account.dataSet);

                    Intent intent = new Intent();
                    intent.setClassName(accountType.syncAdapterPackageName,
                            accountType.getEditContactActivityClassName());
                    intent.setAction(Intent.ACTION_EDIT);
                    intent.setData(rawContactUri);
                    if (intentExtras != null) {
                        intent.putExtras(intentExtras);
                    }

                    if (redirect) {
                        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                        startActivity(intent);
                        finish();
                    } else {
                        startActivity(intent);
                    }
                }
            };

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // Determine whether or not this activity should be finished after the user is done
        // editing the contact or if this activity should launch another activity to view the
        // contact's details.
        mFinishActivityOnSaveCompleted = intent.getBooleanExtra(
                INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, false);

        // The only situation where action could be ACTION_JOIN_COMPLETED is if the
        // user joined the contact with another and closed the activity before
        // the save operation was completed.  The activity should remain closed then.
        if (ACTION_JOIN_COMPLETED.equals(action)) {
            finish();
            return;
        }

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            finish();
            return;
        }

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            if (Intent.ACTION_EDIT.equals(action)) {
                mActionBarTitleResId = R.string.contact_editor_title_existing_contact;
            } else {
                mActionBarTitleResId = R.string.contact_editor_title_new_contact;
            }
            actionBar.setTitle(getResources().getString(mActionBarTitleResId));
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_dk);
        }

        setContentView(R.layout.compact_contact_editor_activity);

        if (savedState == null) {
            // Create the editor and photo selection fragments
            mFragment = new CompactContactEditorFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, getEditorFragment(), TAG_COMPACT_EDITOR)
                    .commit();
        } else {
            // Restore state
            mPhotoMode = savedState.getInt(STATE_PHOTO_MODE);
            mActionBarTitleResId = savedState.getInt(STATE_ACTION_BAR_TITLE);
            mPhotoUri = Uri.parse(savedState.getString(STATE_PHOTO_URI));

            // Show/hide the editor and photo selection fragments (w/o animations)
            mFragment = (CompactContactEditorFragment) getFragmentManager()
                    .findFragmentByTag(TAG_COMPACT_EDITOR);
            final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.show(getEditorFragment()).commit();
            getActionBar().setTitle(getResources().getString(mActionBarTitleResId));
        }

        // Set listeners
        mFragment.setListener(mFragmentListener);

        // Load editor data (even if it's hidden)
        final Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());
    }

    @Override
    protected void onPause() {
        super.onPause();
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        final View currentFocus = getCurrentFocus();
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mFragment == null) {
            return;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            mFragment.setIntentExtras(intent.getExtras());
        } else if (ACTION_SAVE_COMPLETED.equals(action)) {
            mFragment.onSaveCompleted(true,
                    intent.getIntExtra(CompactContactEditorFragment.SAVE_MODE_EXTRA_KEY,
                            ContactEditor.SaveMode.CLOSE),
                    intent.getBooleanExtra(ContactSaveService.EXTRA_SAVE_SUCCEEDED, false),
                    intent.getData(),
                    intent.getLongExtra(CompactContactEditorFragment.JOIN_CONTACT_ID_EXTRA_KEY, -1));
        } else if (ACTION_JOIN_COMPLETED.equals(action)) {
            mFragment.onJoinCompleted(intent.getData());
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PHOTO_MODE, mPhotoMode);
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
        if (mFragment != null) {
            mFragment.revert();
        }
    }

    /**
     * Opens a dialog showing options for the user to change their photo (take, choose, or remove
     * photo).
     */
    public void changePhoto(int photoMode) {
        mPhotoMode = photoMode;
        PhotoSourceDialogFragment.show(this, mPhotoMode);
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

    private PhotoSelectionHandler getPhotoSelectionHandler() {
        if (mPhotoSelectionHandler == null) {
            mPhotoSelectionHandler = new CompactPhotoSelectionHandler(mPhotoMode);
        }
        return mPhotoSelectionHandler;
    }

    private CompactContactEditorFragment getEditorFragment() {
        return (CompactContactEditorFragment) mFragment;
    }
}
