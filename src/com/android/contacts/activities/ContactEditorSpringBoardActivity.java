package com.android.contacts.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.editor.PickRawContactDialogFragment;
import com.android.contacts.editor.PickRawContactLoader;

/**
 * Transparent springboard activity that hosts a dialog to select a raw contact to edit.
 * This activity has noHistory set to true, and all intents coming out from it have
 * {@code FLAG_ACTIVITY_FORWARD_RESULT} set.
 */
public class ContactEditorSpringBoardActivity extends AppCompatContactsActivity  {
    private static final String TAG = "EditorSpringBoard";
    private static final String TAG_RAW_CONTACTS_DIALOG = "rawContactsDialog";
    private static final int LOADER_RAW_CONTACTS = 1;

    private Uri mUri;
    private Cursor mCursor;
    private MaterialPalette mMaterialPalette;

    /**
     * The contact data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<Cursor> mRawContactLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                    return new PickRawContactLoader(ContactEditorSpringBoardActivity.this, mUri);
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
                    if (cursor == null) {
                        Toast.makeText(ContactEditorSpringBoardActivity.this,
                                R.string.editor_failed_to_load, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    mCursor = cursor;
                    if (mCursor.getCount() == 1) {
                        loadEditor();
                    } else {
                        showDialog();
                    }
                }

                @Override
                public void onLoaderReset(Loader<Cursor> loader) {
                    mCursor = null;
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (!Intent.ACTION_EDIT.equals(action)) {
            finish();
            return;
        }
        // Just for shorter variable names.
        final String primary = ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR;
        final String secondary =
                ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR;
        if (intent.hasExtra(primary) && intent.hasExtra(secondary)) {
            mMaterialPalette = new MaterialPalette(intent.getIntExtra(primary, -1),
                    intent.getIntExtra(secondary, -1));
        }

        mUri = intent.getData();
        final String authority = mUri.getAuthority();
        final String type = getContentResolver().getType(mUri);
        // Go straight to editor if we're passed a raw contact Uri.
        if (ContactsContract.AUTHORITY.equals(authority) &&
                RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
            final long rawContactId = ContentUris.parseId(mUri);
            final Intent editorIntent = getIntentForRawContact(rawContactId);
            ImplicitIntentsUtil.startActivityInApp(this, editorIntent);
        } else {
            getLoaderManager().initLoader(LOADER_RAW_CONTACTS, null, mRawContactLoaderListener);
        }
    }

    /**
     * Start the dialog to pick the raw contact to edit.
     */
    private void showDialog() {
        final FragmentManager fm = getFragmentManager();
        final PickRawContactDialogFragment oldFragment = (PickRawContactDialogFragment)
                fm.findFragmentByTag(TAG_RAW_CONTACTS_DIALOG);
        if (oldFragment != null && oldFragment.getDialog() != null
                && oldFragment.getDialog().isShowing()) {
            // Just update the cursor without reshowing the dialog.
            oldFragment.setCursor(mCursor);
            return;
        }
        final FragmentTransaction ft = fm.beginTransaction();
        if (oldFragment != null) {
            ft.remove(oldFragment);
        }
        final PickRawContactDialogFragment newFragment =
                PickRawContactDialogFragment.getInstance(mUri, mCursor, mMaterialPalette);
        ft.add(newFragment, TAG_RAW_CONTACTS_DIALOG);
        // commitAllowingStateLoss is safe in this activity because the fragment entirely depends
        // on the result of the loader. Even if we lose the fragment because the activity was
        // in the background, when it comes back onLoadFinished will be called again which will
        // have all the state the picker needs. This situation should be very rare, since the load
        // should be quick.
        ft.commitAllowingStateLoss();
    }

    /**
     * Starts the editor for the first (only) raw contact in the cursor.
     */
    private void loadEditor() {
        final Intent intent;
        if (isSingleWritableAccount()) {
            mCursor.moveToFirst();
            final long rawContactId = mCursor.getLong(PickRawContactLoader.RAW_CONTACT_ID);
            intent = getIntentForRawContact(rawContactId);

        } else {
            // If it's a single read-only raw contact, we'll want to let the editor create
            // the writable raw contact for it.
            intent = EditorIntents.createEditContactIntent(this, mUri, mMaterialPalette, -1);
            intent.setClass(this, ContactEditorActivity.class);
        }
        // Destroy the loader to prevent multiple onLoadFinished calls in case CP2 is updating in
        // the background.
        getLoaderManager().destroyLoader(LOADER_RAW_CONTACTS);
        ImplicitIntentsUtil.startActivityInApp(this, intent);
    }

    /**
     * @return true if there is only one raw contact in the contact and it is from a writable
     * account.
     */
    private boolean isSingleWritableAccount() {
        if (mCursor.getCount() != 1) {
            return false;
        }
        mCursor.moveToFirst();
        final String accountType = mCursor.getString(PickRawContactLoader.ACCOUNT_TYPE);
        final String dataSet = mCursor.getString(PickRawContactLoader.DATA_SET);
        final AccountType account = AccountTypeManager.getInstance(this)
                .getAccountType(accountType, dataSet);
        return account.areContactsWritable();
    }

    /**
     * Returns an intent to load the editor for the given raw contact. Sets
     * {@code FLAG_ACTIVITY_FORWARD_RESULT} in case the activity that started us expects a result.
     * @param rawContactId Raw contact to edit
     */
    private Intent getIntentForRawContact(long rawContactId) {
        final Intent intent = EditorIntents.createEditContactIntentForRawContact(
                this, mUri, rawContactId, mMaterialPalette);
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        return intent;
    }
}
