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

package com.android.contacts.ui;

import com.android.contacts.R;
import com.android.contacts.model.AugmentedEntity;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.ui.widget.ContactEditorView;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        View.OnFocusChangeListener {

    // TODO: with new augmentedentity approach, turn insert and update cases into same action

    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    // Dialog IDs
    final static int DELETE_CONFIRMATION_DIALOG = 2;

    // Menu item IDs
    public static final int MENU_ITEM_DONE = 1;
    public static final int MENU_ITEM_REVERT = 2;
    public static final int MENU_ITEM_PHOTO = 6;


    private LayoutInflater mInflater;
    private ViewGroup mContentView;

//    private MenuItem mPhotoMenuItem;
//    private boolean mPhotoPresent = false;

    /** Flag marking this contact as changed, meaning we should write changes back. */
//    private boolean mContactChanged = false;

    private Uri mUri;
    private ArrayList<AugmentedEntity> mEntities = new ArrayList<AugmentedEntity>();

    private ContentResolver mResolver;
    private ContactEditorView mEditor;

    private ViewGroup mTabContent;

    // we edit an aggregate, which has several entities
    // we query and build AugmentedEntities, which is what ContactEditorView expects



    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = this;

        mInflater = getLayoutInflater();
        mResolver = getContentResolver();

        mContentView = (ViewGroup)mInflater.inflate(R.layout.act_edit, null);
        mTabContent = (ViewGroup)mContentView.findViewById(android.R.id.tabcontent);

        setContentView(mContentView);

        // Setup floating buttons
        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);



        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        mUri = intent.getData();
        
        // TODO: read all contacts part of this aggregate and hook into tabs

        // Resolve the intent
        if (Intent.ACTION_EDIT.equals(action) && mUri != null) {

            try {
                final long aggId = ContentUris.parseId(mUri);
                final EntityIterator iterator = mResolver.queryEntities(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContactsContract.RawContacts.CONTACT_ID + "=" + aggId, null, null);
                while (iterator.hasNext()) {
                    final Entity before = iterator.next();
                    final AugmentedEntity entity = AugmentedEntity.fromBefore(before);

                    mEntities.add(entity);

                    Log.d(TAG, "Loaded entity...");
                }
                iterator.close();
            } catch (RemoteException e) {
                Log.d(TAG, "Problem reading aggregate", e);
            }

//            if (icicle == null) {
//                // Build the entries & views
//                buildEntriesForEdit(extras);
//                buildViews();
//            }
            setTitle(R.string.editContact_title_edit);
        } else if (Intent.ACTION_INSERT.equals(action)) {
//            if (icicle == null) {
//                // Build the entries & views
//                buildEntriesForInsert(extras);
//                buildViews();
//            }
//            setTitle(R.string.editContact_title_insert);
//            mState = STATE_INSERT;
        }

//        if (mState == STATE_UNKNOWN) {
//            Log.e(TAG, "Cannot resolve intent: " + intent);
//            finish();
//            return;
//        }

        mEditor = new ContactEditorView(context);

        mTabContent.removeAllViews();
        mTabContent.addView(mEditor.getView());

        final ContactsSource source = Sources.getInstance().getKindsForAccountType(
                Sources.ACCOUNT_TYPE_GOOGLE);
        mEditor.setState(mEntities.get(0), source);


    }


    // TODO: build entity from incoming intent
    // TODO: build entity from "new" action


//    private void addFromExtras(Bundle extras, Uri phonesUri, Uri methodsUri) {
//        EditEntry entry;
//
//        // Read the name from the bundle
//        CharSequence name = extras.getCharSequence(Insert.NAME);
//        if (name != null && TextUtils.isGraphic(name)) {
//            mNameView.setText(name);
//        }
//
//        // Read the phonetic name from the bundle
//        CharSequence phoneticName = extras.getCharSequence(Insert.PHONETIC_NAME);
//        if (!TextUtils.isEmpty(phoneticName)) {
//            mPhoneticNameView.setText(phoneticName);
//        }
//
//        // StructuredPostal entries from extras
//        CharSequence postal = extras.getCharSequence(Insert.POSTAL);
//        int postalType = extras.getInt(Insert.POSTAL_TYPE, INVALID_TYPE);
//        if (!TextUtils.isEmpty(postal) && postalType == INVALID_TYPE) {
//            postalType = DEFAULT_POSTAL_TYPE;
//        }
//
//        if (postalType != INVALID_TYPE) {
//            entry = EditEntry.newPostalEntry(this, null, postalType, postal.toString(),
//                    methodsUri, 0);
//            entry.isPrimary = extras.getBoolean(Insert.POSTAL_ISPRIMARY);
//            mPostalEntries.add(entry);
//        }
//
//        // Email entries from extras
//        addEmailFromExtras(extras, methodsUri, Insert.EMAIL, Insert.EMAIL_TYPE,
//                Insert.EMAIL_ISPRIMARY);
//        addEmailFromExtras(extras, methodsUri, Insert.SECONDARY_EMAIL, Insert.SECONDARY_EMAIL_TYPE,
//                null);
//        addEmailFromExtras(extras, methodsUri, Insert.TERTIARY_EMAIL, Insert.TERTIARY_EMAIL_TYPE,
//                null);
//
//        // Phone entries from extras
//        addPhoneFromExtras(extras, phonesUri, Insert.PHONE, Insert.PHONE_TYPE,
//                Insert.PHONE_ISPRIMARY);
//        addPhoneFromExtras(extras, phonesUri, Insert.SECONDARY_PHONE, Insert.SECONDARY_PHONE_TYPE,
//                null);
//        addPhoneFromExtras(extras, phonesUri, Insert.TERTIARY_PHONE, Insert.TERTIARY_PHONE_TYPE,
//                null);
//
//        // IM entries from extras
//        CharSequence imHandle = extras.getCharSequence(Insert.IM_HANDLE);
//        CharSequence imProtocol = extras.getCharSequence(Insert.IM_PROTOCOL);
//
//        if (imHandle != null && imProtocol != null) {
//            Object protocolObj = ContactMethods.decodeImProtocol(imProtocol.toString());
//            if (protocolObj instanceof Number) {
//                int protocol = ((Number)F protocolObj).intValue();
//                entry = EditEntry.newImEntry(this,
//                        getLabelsForKind(this, Contacts.KIND_IM)[protocol], protocol,
//                        imHandle.toString(), methodsUri, 0);
//            } else {
//                entry = EditEntry.newImEntry(this, protocolObj.toString(), -1, imHandle.toString(),
//                        methodsUri, 0);
//            }
//            entry.isPrimary = extras.getBoolean(Insert.IM_ISPRIMARY);
//            mImEntries.add(entry);
//        }
//    }
//
//    private void addEmailFromExtras(Bundle extras, Uri methodsUri, String emailField,
//            String typeField, String primaryField) {
//        CharSequence email = extras.getCharSequence(emailField);
//
//        // Correctly handle String in typeField as TYPE_CUSTOM
//        int emailType = INVALID_TYPE;
//        String customLabel = null;
//        if(extras.get(typeField) instanceof String) {
//            emailType = ContactsContract.TYPE_CUSTOM;
//            customLabel = extras.getString(typeField);
//        } else {
//            emailType = extras.getInt(typeField, INVALID_TYPE);
//        }
//
//        if (!TextUtils.isEmpty(email) && emailType == INVALID_TYPE) {
//            emailType = DEFAULT_EMAIL_TYPE;
//            mPrimaryEmailAdded = true;
//        }
//
//        if (emailType != INVALID_TYPE) {
//            EditEntry entry = EditEntry.newEmailEntry(this, customLabel, emailType, email.toString(),
//                    methodsUri, 0);
//            entry.isPrimary = (primaryField == null) ? false : extras.getBoolean(primaryField);
//            mEmailEntries.add(entry);
//
//            // Keep track of which primary types have been added
//            if (entry.isPrimary) {
//                mPrimaryEmailAdded = true;
//            }
//        }
//    }
//
//    private void addPhoneFromExtras(Bundle extras, Uri phonesUri, String phoneField,
//            String typeField, String primaryField) {
//        CharSequence phoneNumber = extras.getCharSequence(phoneField);
//
//        // Correctly handle String in typeField as TYPE_CUSTOM
//        int phoneType = INVALID_TYPE;
//        String customLabel = null;
//        if(extras.get(typeField) instanceof String) {
//            phoneType = Phone.TYPE_CUSTOM;
//            customLabel = extras.getString(typeField);
//        } else {
//            phoneType = extras.getInt(typeField, INVALID_TYPE);
//        }
//
//        if (!TextUtils.isEmpty(phoneNumber) && phoneType == INVALID_TYPE) {
//            phoneType = DEFAULT_PHONE_TYPE;
//        }
//
//        if (phoneType != INVALID_TYPE) {
//            EditEntry entry = EditEntry.newPhoneEntry(this, customLabel, phoneType,
//                    phoneNumber.toString(), phonesUri, 0);
//            entry.isPrimary = (primaryField == null) ? false : extras.getBoolean(primaryField);
//            mPhoneEntries.add(entry);
//
//            // Keep track of which primary types have been added
//            if (phoneType == Phone.TYPE_MOBILE) {
//                mMobilePhoneAdded = true;
//            }
//        }
//    }
//    */



    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.saveButton:
                doSaveAction();
                break;

            case R.id.discardButton:
                doRevertAction();
                break;

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                doSaveAction();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }



    @Override
    protected void onSaveInstanceState(Bundle outState) {

        // TODO: store down uri, selected contactid, and pile of augmentedstates

        // store down the focused tab and child data  _id (what about storing as index?)

        // tell sections to store their state, which also picks correct field and cursor location



//
//        // To store current focus between config changes, follow focus down the
//        // view tree, keeping track of any parents with EditEntry tags
//        View focusedChild = mContentView.getFocusedChild();
//        EditEntry focusedEntry = null;
//        while (focusedChild != null) {
//            Object tag = focusedChild.getTag();
//            if (tag instanceof EditEntry) {
//                focusedEntry = (EditEntry) tag;
//            }
//
//            // Keep going deeper until child isn't a group
//            if (focusedChild instanceof ViewGroup) {
//                View deeperFocus = ((ViewGroup) focusedChild).getFocusedChild();
//                if (deeperFocus != null) {
//                    focusedChild = deeperFocus;
//                } else {
//                    break;
//                }
//            } else {
//                break;
//            }
//        }
//
//        if (focusedChild != null) {
//            int requestFocusId = focusedChild.getId();
//            int requestCursor = 0;
//            if (focusedChild instanceof EditText) {
//                requestCursor = ((EditText) focusedChild).getSelectionStart();
//            }
//
//            // Store focus values in EditEntry if found, otherwise store as
//            // generic values
//            if (focusedEntry != null) {
//                focusedEntry.requestFocusId = requestFocusId;
//                focusedEntry.requestCursor = requestCursor;
//            } else {
//                outState.putInt("requestFocusId", requestFocusId);
//                outState.putInt("requestCursor", requestCursor);
//            }
//        }
//
//        outState.putParcelableArrayList("phoneEntries", mPhoneEntries);
//        outState.putParcelableArrayList("emailEntries", mEmailEntries);
//        outState.putParcelableArrayList("imEntries", mImEntries);
//        outState.putParcelableArrayList("postalEntries", mPostalEntries);
//        outState.putParcelableArrayList("orgEntries", mOrgEntries);
//        outState.putParcelableArrayList("noteEntries", mNoteEntries);
//        outState.putParcelableArrayList("otherEntries", mOtherEntries);
//        outState.putInt("state", mState);
//        outState.putBoolean("insert", mInsert);
//        outState.putParcelable("uri", mUri);
//        outState.putString("name", mNameView.getText().toString());
//        outState.putParcelable("photo", mPhoto);
//        outState.putBoolean("photoChanged", mPhotoChanged);
//        outState.putString("phoneticName", mPhoneticNameView.getText().toString());
//        outState.putBoolean("contactChanged", mContactChanged);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
//        mPhoneEntries = inState.getParcelableArrayList("phoneEntries");
//        mEmailEntries = inState.getParcelableArrayList("emailEntries");
//        mImEntries = inState.getParcelableArrayList("imEntries");
//        mPostalEntries = inState.getParcelableArrayList("postalEntries");
//        mOrgEntries = inState.getParcelableArrayList("orgEntries");
//        mNoteEntries = inState.getParcelableArrayList("noteEntries");
//        mOtherEntries = inState.getParcelableArrayList("otherEntries");
//        setupSections();
//
//        mState = inState.getInt("state");
//        mInsert = inState.getBoolean("insert");
//        mUri = inState.getParcelable("uri");
//        mNameView.setText(inState.getString("name"));
//        mPhoto = inState.getParcelable("photo");
//        if (mPhoto != null) {
//            mPhotoImageView.setImageBitmap(mPhoto);
//            setPhotoPresent(true);
//        } else {
//            mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);
//            setPhotoPresent(false);
//        }
//        mPhotoChanged = inState.getBoolean("photoChanged");
//        mPhoneticNameView.setText(inState.getString("phoneticName"));
//        mContactChanged = inState.getBoolean("contactChanged");
//
//        // Now that everything is restored, build the view
//        buildViews();
//
//        // Try restoring any generally requested focus
//        int requestFocusId = inState.getInt("requestFocusId", View.NO_ID);
//        View focusedChild = mContentView.findViewById(requestFocusId);
//        if (focusedChild != null) {
//            focusedChild.requestFocus();
//            if (focusedChild instanceof EditText) {
//                int requestCursor = inState.getInt("requestCursor", 0);
//                ((EditText) focusedChild).setSelection(requestCursor);
//            }
//        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
//                final Bundle extras = data.getExtras();
//                if (extras != null) {
//                    Bitmap photo = extras.getParcelable("data");
//                    mPhoto = photo;
//                    mPhotoChanged = true;
//                    mPhotoImageView.setImageBitmap(photo);
//                    setPhotoPresent(true);
//                }
//                break;
            }
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
//        menu.add(0, MENU_ITEM_SAVE, 0, R.string.menu_done)
//                .setIcon(android.R.drawable.ic_menu_save)
//                .setAlphabeticShortcut('\n');
//        menu.add(0, MENU_ITEM_DONT_SAVE, 0, R.string.menu_doNotSave)
//                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
//                .setAlphabeticShortcut('q');
//        if (!mInsert) {
//            menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
//                    .setIcon(android.R.drawable.ic_menu_delete);
//        }
//
//        mPhotoMenuItem = menu.add(0, MENU_ITEM_PHOTO, 0, null);
//        // Updates the state of the menu item
//        setPhotoPresent(mPhotoPresent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case MENU_ITEM_SAVE:
//                doSaveAction();
//                return true;
//
//            case MENU_ITEM_DONT_SAVE:
//                doRevertAction();
//                return true;
//
//            case MENU_ITEM_DELETE:
//                // Get confirmation
//                showDialog(DELETE_CONFIRMATION_DIALOG);
//                return true;
//
//            case MENU_ITEM_PHOTO:
//                if (!mPhotoPresent) {
//                    doPickPhotoAction();
//                } else {
//                    doRemovePhotoAction();
//                }
//                return true;
        }

        return false;
    }



    private void doRevertAction() {
        finish();
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DELETE_CONFIRMATION_DIALOG:
//                return new AlertDialog.Builder(EditContactActivity.this)
//                        .setTitle(R.string.deleteConfirmation_title)
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .setMessage(R.string.deleteConfirmation)
//                        .setNegativeButton(android.R.string.cancel, null)
//                        .setPositiveButton(android.R.string.ok, mDeleteContactDialogListener)
//                        .setCancelable(false)
//                        .create();
        }
        return super.onCreateDialog(id);
    }


    /**
     * Saves or creates the contact based on the mode, and if sucessful finishes the activity.
     */
    private void doSaveAction() {
        // Save or create the contact if needed
//        switch (mState) {
//            case STATE_EDIT:
//                save();
//                break;
//
//            /*
//            case STATE_INSERT:
//                create();
//                break;
//            */
//
//            default:
//                Log.e(TAG, "Unknown state in doSaveOrCreate: " + mState);
//                break;
//        }
        finish();
    }


    /**
     * Save the various fields to the existing contact.
     */
    private void save() {
//        ContentValues values = new ContentValues();
//        String data;
//        int numValues = 0;
//
//        // Handle the name and send to voicemail specially
//        final String name = mNameView.getText().toString();
//        if (name != null && TextUtils.isGraphic(name)) {
//            numValues++;
//        }
//
//        values.put(StructuredName.DISPLAY_NAME, name);
//        /*
//        values.put(People.PHONETIC_NAME, mPhoneticNameView.getText().toString());
//        */
//        mResolver.update(mStructuredNameUri, values, null, null);
//
//        // This will go down in for loop somewhere
//        if (mPhotoChanged) {
//            // Only write the photo if it's changed, since we don't initially load mPhoto
//            values.clear();
//            if (mPhoto != null) {
//                ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
//                values.put(Photo.PHOTO, stream.toByteArray());
//                mResolver.update(mPhotoDataUri, values, null, null);
//            } else {
//                values.putNull(Photo.PHOTO);
//                mResolver.update(mPhotoDataUri, values, null, null);
//            }
//        }
//
//        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
//        for (int i = 0; i < entryCount; i++) {
//            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
//            data = entry.getData();
//            boolean empty = data == null || !TextUtils.isGraphic(data);
//            /*
//            if (kind == EditEntry.KIND_GROUP) {
//                if (entry.id != 0) {
//                    for (int g = 0; g < mGroups.length; g++) {
//                        long groupId = getGroupId(mResolver, mGroups[g].toString());
//                        if (mInTheGroup[g]) {
//                            Contacts.People.addToGroup(mResolver, entry.id, groupId);
//                            numValues++;
//                        } else {
//                            deleteGroupMembership(entry.id, groupId);
//                        }
//                    }
//                }
//            }
//            */
//            if (!empty) {
//                values.clear();
//                entry.toValues(values);
//                if (entry.id != 0) {
//                    mResolver.update(entry.uri, values, null, null);
//                } else {
//                    /* mResolver.insert(entry.uri, values); */
//                }
//            } else if (entry.id != 0) {
//                mResolver.delete(entry.uri, null, null);
//            }
//        }
//
//        /*
//        if (numValues == 0) {
//            // The contact is completely empty, delete it
//            mResolver.delete(mUri, null, null);
//            mUri = null;
//            setResult(RESULT_CANCELED);
//        } else {
//            // Add the entry to the my contacts group if it isn't there already
//            People.addToMyContactsGroup(mResolver, ContentUris.parseId(mUri));
//            setResult(RESULT_OK, new Intent().setData(mUri));
//
//            // Only notify user if we actually changed contact
//            if (mContactChanged || mPhotoChanged) {
//                Toast.makeText(this, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
//            }
//        }
//        */
    }

    /**
     * Takes the entered data and saves it to a new contact.
     */
    /*
    private void create() {
        ContentValues values = new ContentValues();
        String data;
        int numValues = 0;

        // Create the contact itself
        final String name = mNameView.getText().toString();
        if (name != null && TextUtils.isGraphic(name)) {
            numValues++;
        }
        values.put(People.NAME, name);
        values.put(People.PHONETIC_NAME, mPhoneticNameView.getText().toString());

        // Add the contact to the My Contacts group
        Uri contactUri = People.createPersonInMyContactsGroup(mResolver, values);

        // Add the contact to the group that is being displayed in the contact list
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int displayType = prefs.getInt(ContactsListActivity.PREF_DISPLAY_TYPE,
                ContactsListActivity.DISPLAY_TYPE_UNKNOWN);
        if (displayType == ContactsListActivity.DISPLAY_TYPE_USER_GROUP) {
            String displayGroup = prefs.getString(ContactsListActivity.PREF_DISPLAY_INFO,
                    null);
            if (!TextUtils.isEmpty(displayGroup)) {
                People.addToGroup(mResolver, ContentUris.parseId(contactUri), displayGroup);
            }
        } else {
            // Check to see if we're not syncing everything and if so if My Contacts is synced.
            // If it isn't then the created contact can end up not in any groups that are
            // currently synced and end up getting removed from the phone, which is really bad.
            boolean syncingEverything = !"0".equals(Contacts.Settings.getSetting(mResolver, null,
                    Contacts.Settings.SYNC_EVERYTHING));
            if (!syncingEverything) {
                boolean syncingMyContacts = false;
                Cursor c = mResolver.query(Groups.CONTENT_URI, new String[] { Groups.SHOULD_SYNC },
                        Groups.SYSTEM_ID + "=?", new String[] { Groups.GROUP_MY_CONTACTS }, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            syncingMyContacts = !"0".equals(c.getString(0));
                        }
                    } finally {
                        c.close();
                    }
                }

                if (!syncingMyContacts) {
                    // Not syncing My Contacts, so find a group that is being synced and stick
                    // the contact in there. We sort the list so at least all contacts
                    // will appear in the same group.
                    c = mResolver.query(Groups.CONTENT_URI, new String[] { Groups._ID },
                            Groups.SHOULD_SYNC + "!=0", null, Groups.DEFAULT_SORT_ORDER);
                    if (c != null) {
                        try {
                            if (c.moveToFirst()) {
                                People.addToGroup(mResolver, ContentUris.parseId(contactUri),
                                        c.getLong(0));
                            }
                        } finally {
                            c.close();
                        }
                    }
                }
            }
        }

        // Handle the photo
        if (mPhoto != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
            Contacts.People.setPhotoData(getContentResolver(), contactUri, stream.toByteArray());
        }

        // Create the contact methods
        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
        for (int i = 0; i < entryCount; i++) {
            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
            if (entry.kind == EditEntry.KIND_GROUP) {
                long contactId = ContentUris.parseId(contactUri);
                for (int g = 0; g < mGroups.length; g++) {
                    if (mInTheGroup[g]) {
                        long groupId = getGroupId(mResolver, mGroups[g].toString());
                        People.addToGroup(mResolver, contactId, groupId);
                        numValues++;
                    }
                }
            } else if (entry.kind != EditEntry.KIND_CONTACT) {
                values.clear();
                if (entry.toValues(values)) {
                    // Only create the entry if there is data
                    entry.uri = mResolver.insert(
                            Uri.withAppendedPath(contactUri, entry.contentDirectory), values);
                    entry.id = ContentUris.parseId(entry.uri);
                }
            } else {
                // Update the contact with any straggling data, like notes
                data = entry.getData();
                values.clear();
                if (data != null && TextUtils.isGraphic(data)) {
                    values.put(entry.column, data);
                    mResolver.update(contactUri, values, null, null);
                }
            }
        }

        if (numValues == 0) {
            mResolver.delete(contactUri, null, null);
            setResult(RESULT_CANCELED);
        } else {
            mUri = contactUri;
            Intent resultIntent = new Intent()
                    .setData(mUri)
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, R.string.contactCreatedToast, Toast.LENGTH_SHORT).show();
        }
    }
    */



//    /**
//     * Builds the views for a specific section.
//     *
//     * @param layout the container
//     * @param section the section to build the views for
//     */
//    private void buildViewsForSection(final LinearLayout layout, ArrayList<EditEntry> section,
//            int separatorResource, int sectionType) {
//
//        View divider = mInflater.inflate(R.layout.edit_divider, layout, false);
//        layout.addView(divider);
//
//        // Count up undeleted children
//        int activeChildren = 0;
//        for (int i = section.size() - 1; i >= 0; i--) {
//            EditEntry entry = section.get(i);
//            if (!entry.isDeleted) {
//                activeChildren++;
//            }
//        }
//
//        // Build the correct group header based on undeleted children
//        ViewGroup header;
//        if (activeChildren == 0) {
//            header = (ViewGroup) mInflater.inflate(R.layout.edit_separator_alone, layout, false);
//        } else {
//            header = (ViewGroup) mInflater.inflate(R.layout.edit_separator, layout, false);
//        }
//
//        // Because we're emulating a ListView, we need to handle focus changes
//        // with some additional logic.
//        header.setOnFocusChangeListener(this);
//
//        TextView text = (TextView) header.findViewById(R.id.text);
//        text.setText(getText(separatorResource));
//
//        // Force TextView to always default color if we have children.  This makes sure
//        // we don't change color when parent is pressed.
//        if (activeChildren > 0) {
//            ColorStateList stateList = text.getTextColors();
//            text.setTextColor(stateList.getDefaultColor());
//        }
//
//        View addView = header.findViewById(R.id.separator);
//        addView.setTag(Integer.valueOf(sectionType));
//        addView.setOnClickListener(this);
//
//        // Build views for the current section
//        for (EditEntry entry : section) {
//            entry.activity = this; // this could be null from when the state is restored
//            if (!entry.isDeleted) {
//                View view = buildViewForEntry(entry);
//                header.addView(view);
//            }
//        }
//
//        layout.addView(header);
//    }




    public void onFocusChange(View v, boolean hasFocus) {
        // Because we're emulating a ListView, we need to setSelected() for
        // views as they are focused.
        v.setSelected(hasFocus);
    }
}
