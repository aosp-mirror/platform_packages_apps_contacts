package com.android.contacts.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.editor.PickRawContactDialogFragment;
import com.android.contacts.editor.PickRawContactLoader;
import com.android.contacts.editor.PickRawContactLoader.RawContactsMetadata;
import com.android.contactsbind.FeedbackHelper;

/**
 * Transparent springboard activity that hosts a dialog to select a raw contact to edit.
 * This activity has noHistory set to true, and all intents coming out from it have
 * {@code FLAG_ACTIVITY_FORWARD_RESULT} set.
 */
public class ContactEditorSpringBoardActivity extends AppCompatContactsActivity implements
        PickRawContactDialogFragment.PickRawContactListener {
    private static final String TAG = "EditorSpringBoard";
    private static final String TAG_RAW_CONTACTS_DIALOG = "rawContactsDialog";
    private static final int LOADER_RAW_CONTACTS = 1;

    public static final String EXTRA_SHOW_READ_ONLY = "showReadOnly";

    private Uri mUri;
    private RawContactsMetadata mResult;
    private MaterialPalette mMaterialPalette;
    private boolean mHasWritableAccount;
    private int mWritableAccountPosition;

    /**
     * The contact data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<RawContactsMetadata> mRawContactLoaderListener =
            new LoaderManager.LoaderCallbacks<RawContactsMetadata>() {

                @Override
                public Loader<RawContactsMetadata> onCreateLoader(int id, Bundle args) {
                    return new PickRawContactLoader(ContactEditorSpringBoardActivity.this, mUri);
                }

                @Override
                public void onLoadFinished(Loader<RawContactsMetadata> loader,
                        RawContactsMetadata result) {
                    if (result == null) {
                        toastErrorAndFinish();
                        return;
                    }
                    mResult = result;
                    maybeTrimReadOnly();
                    setHasWritableAccount();
                    if (mResult.rawContacts.size() > 1 && mHasWritableAccount) {
                        showDialog();
                    } else {
                        loadEditor();
                    }
                }

                @Override
                public void onLoaderReset(Loader<RawContactsMetadata> loader) {
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
            startEditorAndForwardExtras(getIntentForRawContact(rawContactId));
        } else if (android.provider.Contacts.AUTHORITY.equals(authority)) {
            // Fail if given a legacy URI.
            FeedbackHelper.sendFeedback(this, TAG,
                    "Legacy Uri was passed to editor.", new IllegalArgumentException());
            toastErrorAndFinish();
        } else {
            getLoaderManager().initLoader(LOADER_RAW_CONTACTS, null, mRawContactLoaderListener);
        }
    }

    @Override
    public void onPickRawContact(long rawContactId) {
        startEditorAndForwardExtras(getIntentForRawContact(rawContactId));
    }

    /**
     * If not configured to show read only raw contact, trim them from the result.
     */
    private void maybeTrimReadOnly() {
        final boolean showReadOnly = getIntent().getBooleanExtra(EXTRA_SHOW_READ_ONLY, false);
        mResult.showReadOnly = showReadOnly;

        if (showReadOnly) {
            return;
        }
        mResult.trimReadOnly(AccountTypeManager.getInstance(this));
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
            return;
        }
        final FragmentTransaction ft = fm.beginTransaction();
        if (oldFragment != null) {
            ft.remove(oldFragment);
        }
        final PickRawContactDialogFragment newFragment = PickRawContactDialogFragment.getInstance(
                 mResult);
        ft.add(newFragment, TAG_RAW_CONTACTS_DIALOG);
        // commitAllowingStateLoss is safe in this activity because the fragment entirely depends
        // on the result of the loader. Even if we lose the fragment because the activity was
        // in the background, when it comes back onLoadFinished will be called again which will
        // have all the state the picker needs. This situation should be very rare, since the load
        // should be quick.
        ft.commitAllowingStateLoss();
    }

    /**
     * Starts the editor for the only writable raw contact in the cursor if one exists. Otherwise,
     * the editor is started normally and handles creation of a new writable raw contact.
     */
    private void loadEditor() {
        final Intent intent;
        if (mHasWritableAccount) {
            intent = getIntentForRawContact(mResult.rawContacts.get(mWritableAccountPosition).id);
        } else {
            // If the contact has only read-only raw contacts, we'll want to let the editor create
            // the writable raw contact for it.
            intent = EditorIntents.createEditContactIntent(this, mUri, mMaterialPalette, -1);
            intent.setClass(this, ContactEditorActivity.class);
        }
        startEditorAndForwardExtras(intent);
    }

    /**
     * Determines if this contact has a writable account.
     */
    private void setHasWritableAccount() {
        mWritableAccountPosition = mResult.getIndexOfFirstWritableAccount(
                AccountTypeManager.getInstance(this));
        mHasWritableAccount = mWritableAccountPosition != -1;
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

    /**
     * Starts the given intent within the app, attaching any extras to it that were passed to us.
     */
    private void startEditorAndForwardExtras(Intent intent) {
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        ImplicitIntentsUtil.startActivityInApp(this, intent);
    }

    private void toastErrorAndFinish() {
        Toast.makeText(ContactEditorSpringBoardActivity.this,
                R.string.editor_failed_to_load, Toast.LENGTH_SHORT).show();
        setResult(RESULT_CANCELED, null);
        finish();
    }
}
