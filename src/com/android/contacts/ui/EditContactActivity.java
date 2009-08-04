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
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.Sources;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.widget.ContactEditorView;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        View.OnFocusChangeListener {

    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    // Dialog IDs
    final static int DELETE_CONFIRMATION_DIALOG = 2;

    // Menu item IDs
    public static final int MENU_ITEM_DONE = 1;
    public static final int MENU_ITEM_REVERT = 2;
    public static final int MENU_ITEM_PHOTO = 6;

    private View mTabContent;
    private ContactEditorView mEditor;

    private Uri mUri;
    private Sources mSources;

    private ArrayList<EntityDelta> mEntities = new ArrayList<EntityDelta>();



    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = this;
        final LayoutInflater inflater = this.getLayoutInflater();

        setContentView(R.layout.act_edit);

        mTabContent = this.findViewById(android.R.id.tabcontent);

        mEditor = new ContactEditorView(context);
        mEditor.swapWith(mTabContent);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        mUri = intent.getData();
        mSources = Sources.getInstance(this);

        if (Intent.ACTION_EDIT.equals(action) && icicle == null) {
            // Read initial state from database
            readEntities();
            rebuildTabs();
        }
    }

    private static final String KEY_DELTAS = "deltas";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Store entities with modifications
        outState.putParcelableArrayList(KEY_DELTAS, mEntities);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {

        // Read and apply modifications from instance
        mEntities = savedInstanceState.<EntityDelta> getParcelableArrayList(KEY_DELTAS);
        rebuildTabs();

        // Restore selected tab and any focus
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        final String action = getIntent().getAction();
        if (Intent.ACTION_INSERT.equals(action)) {
            // TODO: show account disambig dialog before creating

            final ContentValues values = new ContentValues();
            values.put(RawContacts.ACCOUNT_TYPE, Sources.ACCOUNT_TYPE_GOOGLE);

            final EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
            mEntities.add(insert);

        }

        // TODO: if insert, handle account disambig if not already done
    }

    protected void readEntities() {
        // TODO: handle saving the previous values before replacing, in some cases
        try {
            final ContentResolver resolver = this.getContentResolver();
            final long aggId = ContentUris.parseId(mUri);
            final EntityIterator iterator = resolver.queryEntities(
                    ContactsContract.RawContacts.CONTENT_URI,
                    ContactsContract.RawContacts.CONTACT_ID + "=" + aggId, null, null);
            while (iterator.hasNext()) {
                final Entity before = iterator.next();
                final EntityDelta entity = EntityDelta.fromBefore(before);
                mEntities.add(entity);
            }
            iterator.close();
        } catch (RemoteException e) {
            Log.d(TAG, "Problem reading aggregate", e);
        }
    }

    protected void rebuildTabs() {
        // TODO: hook up to tabs
        showEntity(0);
    }

    protected void showEntity(int index) {
        // Find entity and source for selected tab
        final EntityDelta entity = mEntities.get(index);
        final String accountType = entity.getValues().getAsString(RawContacts.ACCOUNT_TYPE);

        ContactsSource source = mSources.getSourceForType(accountType);
        if (source == null) {
            // TODO: remove and place "read only" placard when missing
            source = mSources.getSourceForType(Sources.ACCOUNT_TYPE_GOOGLE);
        }

        // Assign editor state based on entity and source
        mEditor.setState(entity, source);
    }

    public void onTabChanged(String tabId) {
        // Tag is really an array index
        final int index = Integer.parseInt(tabId);
        showEntity(index);
    }


    public View createTabContent(String tag) {
        // Content is identical for all tabs
        return mEditor.getView();
    }



    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done: {
                doSaveAction();
                break;
            }
            case R.id.btn_discard: {
                doRevertAction();
                break;
            }
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

        final ContentResolver resolver = this.getContentResolver();

        for (EntityDelta entity : mEntities) {

            Log.d(TAG, "about to persist " + entity.toString());

            final ArrayList<ContentProviderOperation> diff = entity.buildDiff();

            // TODO: handle failed operations by re-reading entity
            // may also need backoff algorithm to give failed msg after n tries

            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, diff);
            } catch (RemoteException e) {
                Log.w(TAG, "problem writing rawcontact diff", e);
            } catch (OperationApplicationException e) {
                Log.w(TAG, "problem writing rawcontact diff", e);
            }

        }

        this.finish();
    }





    public void onFocusChange(View v, boolean hasFocus) {
        // Because we're emulating a ListView, we need to setSelected() for
        // views as they are focused.
        v.setSelected(hasFocus);
    }


}
