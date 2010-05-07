/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.edit;

import com.android.contacts.JoinContactActivity;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Editor;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.GoogleSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;
import com.android.contacts.ui.widget.BaseContactEditorView;
import com.android.contacts.ui.widget.PhotoEditorView;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.WeakAsyncTask;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.patterns.Loader;
import android.app.patterns.LoaderManagingFragment;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.ContentProviderOperation.Builder;
import android.content.DialogInterface.OnDismissListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class ContactEditFragment
        extends LoaderManagingFragment<ContactEditLoader.Result> {
    private static final String TAG = "ContactEditFragment";

    private static final int LOADER_DATA = 1;

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    /** The launch code when a contact to join with is returned */
    private static final int REQUEST_JOIN_CONTACT = 3022;

    /** The launch code when taking a picture */
    private static final int CAMERA_WITH_DATA = 3023;

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_RAW_CONTACT_ID_REQUESTING_PHOTO = "photorequester";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";
    private static final String KEY_CURRENT_PHOTO_FILE = "currentphotofile";
    private static final String KEY_QUERY_SELECTION = "queryselection";
    private static final String KEY_QUERY_SELECTION_ARGS = "queryselectionargs";
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";

    private static final String BUNDLE_SELECT_ACCOUNT_LIST = "account_list";

    public static final int SAVE_MODE_DEFAULT = 0;
    public static final int SAVE_MODE_SPLIT = 1;
    public static final int SAVE_MODE_JOIN = 2;

    private long mRawContactIdRequestingPhoto = -1;

    private static final int ICON_SIZE = 96;

    private static final File PHOTO_DIR = new File(
            Environment.getExternalStorageDirectory() + "/DCIM/Camera");

    private final Context mContext;
    private final View mView;
    private final LayoutInflater mInflater;
    private final EntityDeltaComparator mComparator = new EntityDeltaComparator();
    private final String mAction;
    private final Uri mUri;
    private final String mMimeType;
    private final Bundle mIntentExtras;
    private final Callbacks mCallbacks;

    private File mCurrentPhotoFile;

    private String mQuerySelection;
    private String[] mQuerySelectionArgs;

    private long mContactIdForJoin;

    private LinearLayout mContent;
    private EntitySet mState;

    private ViewIdGenerator mViewIdGenerator;

    // TODO: We might not need to pass Context and View manually here as the framework should
    // take care of this...?
    public ContactEditFragment(Context context, View view, String action, Uri uri,
            String mimeType, Bundle intentExtras, Callbacks callbacks) {
        super();

        if (callbacks == null) throw new IllegalArgumentException("callbacks must be given");

        mContext = context;
        mView = view;
        mAction = action;
        mUri = uri;
        mMimeType = mimeType;
        mIntentExtras = intentExtras;
        mCallbacks = callbacks;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // Build editor and listen for photo requests
        mContent = (LinearLayout) view.findViewById(R.id.editors);

        view.findViewById(R.id.btn_done).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doSaveAction(SAVE_MODE_DEFAULT);
            }
        });
        view.findViewById(R.id.btn_discard).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mCallbacks.closeAfterRevert();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedState) {
        // TODO: Currently savedState is always null (framework issue). Test once this is fixed
        super.onCreate(savedState);

        // Handle initial actions only when existing state missing
        final boolean hasIncomingState =
                savedState != null && savedState.containsKey(KEY_EDIT_STATE);

        if (!hasIncomingState) {
            if (Intent.ACTION_EDIT.equals(mAction)) {
                // Read initial state from database
                mCallbacks.setTitleTo(R.string.editContact_title_edit);
                startLoading(LOADER_DATA, null);
            } else if (Intent.ACTION_INSERT.equals(mAction)) {
                mCallbacks.setTitleTo(R.string.editContact_title_insert);

                // Load Accounts async so that we can present them
                AsyncTask<Void, Void, ArrayList<Account>> loadAccountsTask =
                        new AsyncTask<Void, Void, ArrayList<Account>>() {
                            @Override
                            protected ArrayList<Account> doInBackground(Void... params) {
                                return Sources.getInstance(mContext).getAccounts(true);
                            }
                            @Override
                            protected void onPostExecute(ArrayList<Account> result) {
                                selectAccountAndCreateContact(result, true);
                            }
                };
                loadAccountsTask.execute();
            } else throw new IllegalArgumentException("Unknown Action String " + mAction +
                    ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT);
        }

        if (savedState == null) {
            // If savedState is non-null, onRestoreInstanceState() will restore the generator.
            mViewIdGenerator = new ViewIdGenerator();
        }
    }

    @Override
    protected Loader<ContactEditLoader.Result> onCreateLoader(int id, Bundle args) {
        return new ContactEditLoader(mContext, mUri, mMimeType, mIntentExtras);
    }

    @Override
    protected void onInitializeLoaders() {
    }

    @Override
    protected void onLoadFinished(Loader<ContactEditLoader.Result> loader,
            ContactEditLoader.Result data) {
        if (data == ContactEditLoader.Result.NOT_FOUND) {
            // Item has been deleted
            Log.i(TAG, "No contact found. Closing fragment");
            mCallbacks.closeBecauseContactNotFound();
            return;
        }
        setData(data);
    }

    public void setData(ContactEditLoader.Result data) {
        mState = data.getEntitySet();
        bindEditors();
    }

    public void selectAccountAndCreateContact(ArrayList<Account> accounts, boolean isNewContact) {
        // No Accounts available.  Create a phone-local contact.
        if (accounts.isEmpty()) {
            createContact(null, isNewContact);
            return;  // Don't show a dialog.
        }

        // In the common case of a single account being writable, auto-select
        // it without showing a dialog.
        if (accounts.size() == 1) {
            createContact(accounts.get(0), isNewContact);
            return;  // Don't show a dialog.
        }

        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(BUNDLE_SELECT_ACCOUNT_LIST, accounts);
        getActivity().showDialog(R.id.edit_dialog_select_account, bundle);
    }

    /**
     * @param account may be null to signal a device-local contact should
     *     be created.
     * @param prefillFromIntent If this is set, the intent extras will be used to prefill the fields
     */
    private void createContact(Account account, boolean prefillFromIntent) {
        final Sources sources = Sources.getInstance(mContext);
        final ContentValues values = new ContentValues();
        if (account != null) {
            values.put(RawContacts.ACCOUNT_NAME, account.name);
            values.put(RawContacts.ACCOUNT_TYPE, account.type);
        } else {
            values.putNull(RawContacts.ACCOUNT_NAME);
            values.putNull(RawContacts.ACCOUNT_TYPE);
        }

        // Parse any values from incoming intent
        EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
        final ContactsSource source = sources.getInflatedSource(
                account != null ? account.type : null,
                ContactsSource.LEVEL_CONSTRAINTS);
        EntityModifier.parseExtras(mContext, source, insert,
                prefillFromIntent ? mIntentExtras : null);

        // Ensure we have some default fields
        EntityModifier.ensureKindExists(insert, source, Phone.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, source, Email.CONTENT_ITEM_TYPE);

        if (mState == null) {
            // Create state if none exists yet
            mState = EntitySet.fromSingle(insert);
        } else {
            // Add contact onto end of existing state
            mState.add(insert);
        }

        bindEditors();
    }

    private void bindEditors() {
        // Sort the editors
        Collections.sort(mState, mComparator);

        // Remove any existing editors and rebuild any visible
        mContent.removeAllViews();

        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final Sources sources = Sources.getInstance(mContext);
        int size = mState.size();
        for (int i = 0; i < size; i++) {
            // TODO ensure proper ordering of entities in the list
            final EntityDelta entity = mState.get(i);
            final ValuesDelta values = entity.getValues();
            if (!values.isVisible()) continue;

            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_CONSTRAINTS);
            final long rawContactId = values.getAsLong(RawContacts._ID);

            final BaseContactEditorView editor;
            if (!source.readOnly) {
                editor = (BaseContactEditorView) inflater.inflate(R.layout.item_contact_editor,
                        mContent, false);
            } else {
                editor = (BaseContactEditorView) inflater.inflate(
                        R.layout.item_read_only_contact_editor, mContent, false);
            }
            final PhotoEditorView photoEditor = editor.getPhotoEditor();
            photoEditor.setEditorListener(new PhotoListener(rawContactId, source.readOnly,
                    photoEditor));

            mContent.addView(editor);
            editor.setState(entity, source, mViewIdGenerator);
        }

        // Show editor now that we've loaded state
        mContent.setVisibility(View.VISIBLE);
    }

    public void onClick(View v) {
        // TODO forward to controller?
    }

    /**
     * Pick a specific photo to be added under the currently selected tab.
     */
    /* package */ boolean doPickPhotoAction(long rawContactId) {
        if (!hasValidState()) return false;

        mRawContactIdRequestingPhoto = rawContactId;

        getActivity().showDialog(R.id.edit_dialog_pick_photo);
        return false;
    }

    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.edit_dialog_confirm_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case R.id.edit_dialog_confirm_readonly_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case R.id.edit_dialog_confirm_multiple_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case R.id.edit_dialog_confirm_readonly_hide:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case R.id.edit_dialog_pick_photo:
                return createPickPhotoDialog();
            case R.id.edit_dialog_split:
                return createSplitDialog();
            case R.id.edit_dialog_select_account:
                return createSelectAccountDialog(bundle);
            default:
                return null;
        }
    }

    private Dialog createSelectAccountDialog(Bundle bundle) {
        final ArrayList<Account> accounts = bundle.getParcelableArrayList(
                BUNDLE_SELECT_ACCOUNT_LIST);
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(mContext, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater =
            (LayoutInflater)dialogContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Sources sources = Sources.getInstance(mContext);

        final ArrayAdapter<Account> accountAdapter = new ArrayAdapter<Account>(mContext,
                android.R.layout.simple_list_item_2, accounts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_2,
                            parent, false);
                }

                // TODO: show icon along with title
                final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                final Account account = this.getItem(position);
                final ContactsSource source = sources.getInflatedSource(account.type,
                        ContactsSource.LEVEL_SUMMARY);

                text1.setText(account.name);
                text2.setText(source.getDisplayLabel(mContext));

                return convertView;
            }
        };

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                // Create new contact based on selected source
                final Account account = accountAdapter.getItem(which);
                createContact(account, false);
            }
        };

        final DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                // If nothing remains, close activity
                if (!hasValidState()) {
                    // TODO: pass this back to Activity
                    // finish();
                }
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.dialog_new_contact_account);
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        builder.setOnCancelListener(cancelListener);
        final Dialog result = builder.create();
        result.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                // TODO: Check if we even need this...seems useless
                //removeDialog(DIALOG_SELECT_ACCOUNT);
            }
        });
        return result;
    }

    private Dialog createSplitDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.splitConfirmation_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.splitConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Split the contacts
                mState.splitRawContacts();
                doSaveAction(SAVE_MODE_SPLIT);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }

    /**
     * Creates a dialog offering two options: take a photo or pick a photo from the gallery.
     */
    private Dialog createPickPhotoDialog() {
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(mContext,
                android.R.style.Theme_Light);

        String[] choices = new String[2];
        choices[0] = mContext.getString(R.string.take_photo);
        choices[1] = mContext.getString(R.string.pick_photo);
        final ListAdapter adapter = new ArrayAdapter<String>(dialogContext,
                android.R.layout.simple_list_item_1, choices);

        final AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
        builder.setTitle(R.string.attachToContact);
        builder.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                switch(which) {
                    case 0:
                        doTakePhoto();
                        break;
                    case 1:
                        doPickPhotoFromGallery();
                        break;
                }
            }
        });
        return builder.create();
    }


    /**
     * Launches Gallery to pick a photo.
     */
    protected void doPickPhotoFromGallery() {
        try {
            // Launch picker to choose photo for selected contact
            final Intent intent = getPhotoPickIntent();
            // TODO: Do this again (Controller?)
            //startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for picking a photo from Gallery, cropping it and returning the bitmap.
     */
    public static Intent getPhotoPickIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", ICON_SIZE);
        intent.putExtra("outputY", ICON_SIZE);
        intent.putExtra("return-data", true);
        return intent;
    }

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    private boolean hasValidState() {
        return mState != null && mState.size() > 0;
    }

    /**
     * Create a file name for the icon photo using current time.
     */
    private String getPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
        return dateFormat.format(date) + ".jpg";
    }

    /**
     * Launches Camera to take a picture and store it in a file.
     */
    protected void doTakePhoto() {
        try {
            // Launch camera to take photo for selected contact
            PHOTO_DIR.mkdirs();
            mCurrentPhotoFile = new File(PHOTO_DIR, getPhotoFileName());
            final Intent intent = getTakePickIntent(mCurrentPhotoFile);

            // TODO: Start camera
            //startActivityForResult(intent, CAMERA_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for capturing a photo and storing it in a temporary file.
     */
    public static Intent getTakePickIntent(File f) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        return intent;
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    private boolean doSaveAction(int saveMode) {
        if (!hasValidState()) {
            return false;
        }

        // TODO: Status still needed?
        //mStatus = STATUS_SAVING;
        final PersistTask task = new PersistTask(this, saveMode);
        task.execute(mState);

        return true;
    }

    private void onSaveCompleted(boolean success, int saveMode, Uri contactLookupUri) {
        switch (saveMode) {
            case SAVE_MODE_DEFAULT:
                final Intent resultIntent;
                final int resultCode;
                if (success && contactLookupUri != null) {
                    final String requestAuthority = mUri == null ? null : mUri.getAuthority();

                    final String legacyAuthority = "contacts";

                    resultIntent = new Intent();
                    if (legacyAuthority.equals(requestAuthority)) {
                        // Build legacy Uri when requested by caller
                        final long contactId = ContentUris.parseId(Contacts.lookupContact(
                                mContext.getContentResolver(), contactLookupUri));
                        final Uri legacyContentUri = Uri.parse("content://contacts/people");
                        final Uri legacyUri = ContentUris.withAppendedId(
                                legacyContentUri, contactId);
                        resultIntent.setData(legacyUri);
                    } else {
                        // Otherwise pass back a lookup-style Uri
                        resultIntent.setData(contactLookupUri);
                    }

                    resultCode = Activity.RESULT_OK;
                } else {
                    resultCode = Activity.RESULT_CANCELED;
                    resultIntent = null;
                }
                mCallbacks.closeAfterSaving(resultCode, resultIntent);
                break;
// TODO: Other save modes

//            case SAVE_MODE_SPLIT:
//                if (success) {
//                    Intent intent = new Intent();
//                    intent.setData(contactLookupUri);
//                    setResult(RESULT_CLOSE_VIEW_ACTIVITY, intent);
//                }
//                finish();
//                break;
//
//            case SAVE_MODE_JOIN:
//                mStatus = STATUS_EDITING;
//                if (success) {
//                    showJoinAggregateActivity(contactLookupUri);
//                }
//                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
    public void showJoinAggregateActivity(Uri contactLookupUri) {
        if (contactLookupUri == null) {
            return;
        }

        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
        Intent intent = new Intent(JoinContactActivity.JOIN_CONTACT);
        intent.putExtra(JoinContactActivity.EXTRA_TARGET_CONTACT_ID, mContactIdForJoin);
        getActivity().startActivityForResult(intent, REQUEST_JOIN_CONTACT);
    }

    private interface JoinContactQuery {
        String[] PROJECTION = {
                RawContacts._ID,
                RawContacts.CONTACT_ID,
                RawContacts.NAME_VERIFIED,
        };

        String SELECTION = RawContacts.CONTACT_ID + "=? OR " + RawContacts.CONTACT_ID + "=?";

        int _ID = 0;
        int CONTACT_ID = 1;
        int NAME_VERIFIED = 2;
    }

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
    private void joinAggregate(final long contactId) {
        final ContentResolver resolver = mContext.getContentResolver();

        // Load raw contact IDs for all raw contacts involved - currently edited and selected
        // in the join UIs
        Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                JoinContactQuery.SELECTION,
                new String[]{String.valueOf(contactId), String.valueOf(mContactIdForJoin)}, null);

        long rawContactIds[];
        long verifiedNameRawContactId = -1;
        try {
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToNext();
                long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
                if (c.getLong(JoinContactQuery.CONTACT_ID) == mContactIdForJoin) {
                    if (verifiedNameRawContactId == -1
                            || c.getInt(JoinContactQuery.NAME_VERIFIED) != 0) {
                        verifiedNameRawContactId = rawContactId;
                    }
                }
            }
        } finally {
            c.close();
        }

        // For each pair of raw contacts, insert an aggregation exception
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
            }
        }

        // Mark the original contact as "name verified" to make sure that the contact
        // display name does not change as a result of the join
        Builder builder = ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, verifiedNameRawContactId));
        builder.withValue(RawContacts.NAME_VERIFIED, 1);
        operations.add(builder.build());

        // Apply all aggregation exceptions as one batch
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);

            // We can use any of the constituent raw contacts to refresh the UI - why not the first
            final Intent intent = new Intent();
            intent.setData(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactIds[0]));

            // Reload the new state from database
            // TODO: Reload necessary or do we have a listener?
            //new QueryEntitiesTask(this).execute(intent);

            Toast.makeText(mContext, R.string.contactsJoinedMessage, Toast.LENGTH_LONG).show();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_TOGETHER} ContentProviderOperation.
     */
    private void buildJoinContactDiff(ArrayList<ContentProviderOperation> operations,
            long rawContactId1, long rawContactId2) {
        Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        operations.add(builder.build());
    }

    public static interface Callbacks {
        /**
         * Contact was not found, so somehow close this fragment.
         */
        void closeBecauseContactNotFound();

        /**
         * User has tapped Revert, close the fragment now.
         */
        void closeAfterRevert();

        /**
         * Set the Title (e.g. of the Activity)
         */
        void setTitleTo(int resourceId);

        /**
         * Contact was
         * @param resultCode
         * @param resultIntent
         */
        void closeAfterSaving(int resultCode, Intent resultIntent);
    }

    private class EntityDeltaComparator implements Comparator<EntityDelta> {
        /**
         * Compare EntityDeltas for sorting the stack of editors.
         */
        public int compare(EntityDelta one, EntityDelta two) {
            // Check direct equality
            if (one.equals(two)) {
                return 0;
            }

            final Sources sources = Sources.getInstance(mContext);
            String accountType = one.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource oneSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            accountType = two.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource twoSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);

            // Check read-only
            if (oneSource.readOnly && !twoSource.readOnly) {
                return 1;
            } else if (twoSource.readOnly && !oneSource.readOnly) {
                return -1;
            }

            // Check account type
            boolean skipAccountTypeCheck = false;
            boolean oneIsGoogle = oneSource instanceof GoogleSource;
            boolean twoIsGoogle = twoSource instanceof GoogleSource;
            if (oneIsGoogle && !twoIsGoogle) {
                return -1;
            } else if (twoIsGoogle && !oneIsGoogle) {
                return 1;
            } else if (oneIsGoogle && twoIsGoogle){
                skipAccountTypeCheck = true;
            }

            int value;
            if (!skipAccountTypeCheck) {
                value = oneSource.accountType.compareTo(twoSource.accountType);
                if (value != 0) {
                    return value;
                }
            }

            // Check account name
            ValuesDelta oneValues = one.getValues();
            String oneAccount = oneValues.getAsString(RawContacts.ACCOUNT_NAME);
            if (oneAccount == null) oneAccount = "";
            ValuesDelta twoValues = two.getValues();
            String twoAccount = twoValues.getAsString(RawContacts.ACCOUNT_NAME);
            if (twoAccount == null) twoAccount = "";
            value = oneAccount.compareTo(twoAccount);
            if (value != 0) {
                return value;
            }

            // Both are in the same account, fall back to contact ID
            Long oneId = oneValues.getAsLong(RawContacts._ID);
            Long twoId = twoValues.getAsLong(RawContacts._ID);
            if (oneId == null) {
                return -1;
            } else if (twoId == null) {
                return 1;
            }

            return (int)(oneId - twoId);
        }
    }

    /**
     * Class that listens to requests coming from photo editors
     */
    private class PhotoListener implements EditorListener, DialogInterface.OnClickListener {
        private long mRawContactId;
        private boolean mReadOnly;
        private PhotoEditorView mEditor;

        public PhotoListener(long rawContactId, boolean readOnly, PhotoEditorView editor) {
            mRawContactId = rawContactId;
            mReadOnly = readOnly;
            mEditor = editor;
        }

        public void onDeleted(Editor editor) {
            // Do nothing
        }

        public void onRequest(int request) {
            // TODO: Still needed?
//            if (!hasValidState()) return;

            if (request == EditorListener.REQUEST_PICK_PHOTO) {
                if (mEditor.hasSetPhoto()) {
                    // There is an existing photo, offer to remove, replace, or promoto to primary
                    createPhotoDialog().show();
                } else if (!mReadOnly) {
                    // No photo set and not read-only, try to set the photo
                    doPickPhotoAction(mRawContactId);
                }
            }
        }

        /**
         * Prepare dialog for picking a new {@link EditType} or entering a
         * custom label. This dialog is limited to the valid types as determined
         * by {@link EntityModifier}.
         */
        public Dialog createPhotoDialog() {
            // Wrap our context to inflate list items using correct theme
            final Context dialogContext = new ContextThemeWrapper(mContext,
                    android.R.style.Theme_Light);

            String[] choices;
            if (mReadOnly) {
                choices = new String[1];
                choices[0] = mContext.getString(R.string.use_photo_as_primary);
            } else {
                choices = new String[3];
                choices[0] = mContext.getString(R.string.use_photo_as_primary);
                choices[1] = mContext.getString(R.string.removePicture);
                choices[2] = mContext.getString(R.string.changePicture);
            }
            final ListAdapter adapter = new ArrayAdapter<String>(dialogContext,
                    android.R.layout.simple_list_item_1, choices);

            final AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
            builder.setTitle(R.string.attachToContact);
            builder.setSingleChoiceItems(adapter, -1, this);
            return builder.create();
        }

        /**
         * Called when something in the dialog is clicked
         */
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();

            switch (which) {
                case 0:
                    // Set the photo as super primary
                    mEditor.setSuperPrimary(true);

                    // And set all other photos as not super primary
                    int count = mContent.getChildCount();
                    for (int i = 0; i < count; i++) {
                        View childView = mContent.getChildAt(i);
                        if (childView instanceof BaseContactEditorView) {
                            BaseContactEditorView editor = (BaseContactEditorView) childView;
                            PhotoEditorView photoEditor = editor.getPhotoEditor();
                            if (!photoEditor.equals(mEditor)) {
                                photoEditor.setSuperPrimary(false);
                            }
                        }
                    }
                    break;

                case 1:
                    // Remove the photo
                    mEditor.setPhotoBitmap(null);
                    break;

                case 2:
                    // Pick a new photo for the contact
                    doPickPhotoAction(mRawContactId);
                    break;
            }
        }
    }


    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // TODO: Delete
//            Sources sources = Sources.getInstance(mContext);
//            // Mark all raw contacts for deletion
//            for (EntityDelta delta : mState) {
//                delta.markDeleted();
//            }
//            // Save the deletes
//            doSaveAction(SAVE_MODE_DEFAULT);
//            finish();
        }
    }

    // TODO: There has to be a nicer way than this WeakAsyncTask...? Maybe call a service?
    /**
     * Background task for persisting edited contact data, using the changes
     * defined by a set of {@link EntityDelta}. This task starts
     * {@link EmptyService} to make sure the background thread can finish
     * persisting in cases where the system wants to reclaim our process.
     */
    public static class PersistTask extends
            WeakAsyncTask<EntitySet, Void, Integer, ContactEditFragment> {
        private static final int PERSIST_TRIES = 3;

        private static final int RESULT_UNCHANGED = 0;
        private static final int RESULT_SUCCESS = 1;
        private static final int RESULT_FAILURE = 2;

        private WeakReference<ProgressDialog> mProgress;
        private final Context mContext;

        private int mSaveMode;
        private Uri mContactLookupUri = null;

        public PersistTask(ContactEditFragment target, int saveMode) {
            super(target);
            mSaveMode = saveMode;
            mContext = target.mContext;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(ContactEditFragment target) {
            mProgress = new WeakReference<ProgressDialog>(ProgressDialog.show(mContext, null,
                    mContext.getText(R.string.savingContact)));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            mContext.startService(new Intent(mContext, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Integer doInBackground(ContactEditFragment target, EntitySet... params) {
            final ContentResolver resolver = mContext.getContentResolver();

            EntitySet state = params[0];

            // Trim any empty fields, and RawContacts, before persisting
            final Sources sources = Sources.getInstance(mContext);
            EntityModifier.trimEmpty(state, sources);

            // Attempt to persist changes
            int tries = 0;
            Integer result = RESULT_FAILURE;
            while (tries++ < PERSIST_TRIES) {
                try {
                    // Build operations and try applying
                    final ArrayList<ContentProviderOperation> diff = state.buildDiff();
                    ContentProviderResult[] results = null;
                    if (!diff.isEmpty()) {
                         results = resolver.applyBatch(ContactsContract.AUTHORITY, diff);
                    }

                    final long rawContactId = getRawContactId(state, diff, results);
                    if (rawContactId != -1) {
                        final Uri rawContactUri = ContentUris.withAppendedId(
                                RawContacts.CONTENT_URI, rawContactId);

                        // convert the raw contact URI to a contact URI
                        mContactLookupUri = RawContacts.getContactLookupUri(resolver,
                                rawContactUri);
                    }
                    result = (diff.size() > 0) ? RESULT_SUCCESS : RESULT_UNCHANGED;
                    break;

                } catch (RemoteException e) {
                    // Something went wrong, bail without success
                    Log.e(TAG, "Problem persisting user edits", e);
                    break;

                } catch (OperationApplicationException e) {
                    // Version consistency failed, re-parent change and try again
                    Log.w(TAG, "Version consistency failed, re-parenting: " + e.toString());
                    final EntitySet newState = EntitySet.fromQuery(resolver,
                            target.mQuerySelection, target.mQuerySelectionArgs, null);
                    state = EntitySet.mergeAfter(newState, state);
                }
            }

            return result;
        }

        private long getRawContactId(EntitySet state,
                final ArrayList<ContentProviderOperation> diff,
                final ContentProviderResult[] results) {
            long rawContactId = state.findRawContactId();
            if (rawContactId != -1) {
                return rawContactId;
            }

            // we gotta do some searching for the id
            final int diffSize = diff.size();
            for (int i = 0; i < diffSize; i++) {
                ContentProviderOperation operation = diff.get(i);
                if (operation.getType() == ContentProviderOperation.TYPE_INSERT
                        && operation.getUri().getEncodedPath().contains(
                                RawContacts.CONTENT_URI.getEncodedPath())) {
                    return ContentUris.parseId(results[i].uri);
                }
            }
            return -1;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(ContactEditFragment target, Integer result) {
            final ProgressDialog progress = mProgress.get();

            if (result == RESULT_SUCCESS && mSaveMode != SAVE_MODE_JOIN) {
                Toast.makeText(mContext, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            } else if (result == RESULT_FAILURE) {
                Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }

            // TODO: How do we get rid of the dialog
//            dismissDialog(progress);

            // Stop the service that was protecting us
            mContext.stopService(new Intent(mContext, EmptyService.class));

            target.onSaveCompleted(result != RESULT_FAILURE, mSaveMode, mContactLookupUri);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }

        outState.putLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO, mRawContactIdRequestingPhoto);
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);
        if (mCurrentPhotoFile != null) {
            outState.putString(KEY_CURRENT_PHOTO_FILE, mCurrentPhotoFile.toString());
        }
        outState.putString(KEY_QUERY_SELECTION, mQuerySelection);
        outState.putStringArray(KEY_QUERY_SELECTION_ARGS, mQuerySelectionArgs);
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Read modifications from instance
        mState = savedInstanceState.<EntitySet> getParcelable(KEY_EDIT_STATE);
        mRawContactIdRequestingPhoto = savedInstanceState.getLong(
                KEY_RAW_CONTACT_ID_REQUESTING_PHOTO);
        mViewIdGenerator = savedInstanceState.getParcelable(KEY_VIEW_ID_GENERATOR);
        String fileName = savedInstanceState.getString(KEY_CURRENT_PHOTO_FILE);
        if (fileName != null) {
            mCurrentPhotoFile = new File(fileName);
        }
        mQuerySelection = savedInstanceState.getString(KEY_QUERY_SELECTION);
        mQuerySelectionArgs = savedInstanceState.getStringArray(KEY_QUERY_SELECTION_ARGS);
        mContactIdForJoin = savedInstanceState.getLong(KEY_CONTACT_ID_FOR_JOIN);

        bindEditors();

        super.onRestoreInstanceState(savedInstanceState);
    }
}
