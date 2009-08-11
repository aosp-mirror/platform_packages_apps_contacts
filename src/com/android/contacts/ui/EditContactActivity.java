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
import com.android.contacts.ScrollingTabWidget;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.widget.ContactEditorView;
import com.android.internal.widget.ContactHeaderWidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        ScrollingTabWidget.OnTabSelectionChangedListener, ContactHeaderWidget.ContactHeaderListener {
    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    private static final String KEY_DELTAS = "deltas";

    private ScrollingTabWidget mTabWidget;
    private ContactHeaderWidget mHeader;

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

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        mUri = intent.getData();
        mSources = Sources.getPartialInstance(this);

        setContentView(R.layout.act_edit);

        mHeader = (ContactHeaderWidget)this.findViewById(R.id.contact_header_widget);
        mHeader.setContactHeaderListener(this);
        mHeader.showStar(true);

        mTabWidget = (ScrollingTabWidget)this.findViewById(R.id.tab_widget);
        mTabWidget.setTabSelectionListener(this);

        mTabContent = this.findViewById(android.R.id.tabcontent);

        mEditor = new ContactEditorView(context);
        mEditor.swapWith(mTabContent);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        if (Intent.ACTION_EDIT.equals(action) && icicle == null) {
            // Read initial state from database
            readEntities();
            rebuildTabs();

            final long contactId = ContentUris.parseId(mUri);
            mHeader.bindFromContactId(contactId);
        } else if (Intent.ACTION_INSERT.equals(action)) {
            // TODO: handle insert case for header

        }
    }

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
        mTabWidget.removeAllTabs();
        for (EntityDelta entity : mEntities) {
            final String accountType = entity.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource source = getSourceForEntity(entity);

            final View tabView = createTabView(mTabWidget, source);
            mTabWidget.addTab(tabView);
        }
        if (mEntities.size() > 0) {
            mTabWidget.setCurrentTab(0);
            this.onTabSelectionChanged(0, false);
        }
    }

    /**
     * Create the {@link View} to represent the given {@link ContactsSource}.
     */
    public static View createTabView(ViewGroup parent, ContactsSource source) {
        final Context context = parent.getContext();
        final LayoutInflater inflater = (LayoutInflater)context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final View tabIndicator = inflater.inflate(R.layout.tab_indicator, parent, false);
        final TextView titleView = (TextView)tabIndicator.findViewById(R.id.tab_title);
        final ImageView iconView = (ImageView) tabIndicator.findViewById(R.id.tab_icon);

        if (source.titleRes > 0) {
            titleView.setText(source.titleRes);
        }
        if (source.iconRes > 0) {
            iconView.setImageResource(source.iconRes);
        }

        return tabIndicator;
    }

    /**
     * Find the {@link ContactsSource} that describes the structure for the
     * given {@link EntityDelta}, or null if no matching source found.
     */
    private ContactsSource getSourceForEntity(EntityDelta entity) {
        final String accountType = entity.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        ContactsSource source = mSources.getSourceForType(accountType);
        if (source == null) {
            // TODO: remove and place "read only" placard when missing
            source = mSources.getSourceForType(Sources.ACCOUNT_TYPE_GOOGLE);
        }
        return source;
    }


    /** {@inheritDoc} */
    public void onTabSelectionChanged(int tabIndex, boolean clicked) {
        // Find entity and source for selected tab
        final EntityDelta entity = mEntities.get(tabIndex);
        final ContactsSource source = getSourceForEntity(entity);

        // Assign editor state based on entity and source
        mEditor.setState(entity, source);
    }

    /** {@inheritDoc} */
    public void onDisplayNameLongClick(View view) {
        this.createNameDialog().show();
    }

    /** {@inheritDoc} */
    public void onPhotoLongClick(View view) {
        this.createPhotoDialog().show();
    }




    /** {@inheritDoc} */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done:
                doSaveAction();
                break;
            case R.id.btn_discard:
                doRevertAction();
                break;
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return doSaveAction();
        }
        return super.onKeyDown(keyCode, event);
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignore failed requests
        if (resultCode != RESULT_OK) return;

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

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // show or hide photo item based on current tab
        // hide photo stuff entirely if on read-only source

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return doSaveAction();
            case R.id.menu_discard:
                return doRevertAction();
            case R.id.menu_delete:
                return doDeleteAction();
            case R.id.menu_photo_add:
                return doPickPhotoAction();
            case R.id.menu_photo_remove:
                return doRemovePhotoAction();
        }
        return false;
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    private boolean doSaveAction() {
        final ContentResolver resolver = this.getContentResolver();
        boolean savedChanges = false;
        for (EntityDelta entity : mEntities) {
            final ArrayList<ContentProviderOperation> diff = entity.buildDiff();

            // Skip updates that don't change
            if (diff.size() == 0) continue;
            savedChanges = true;

            // TODO: handle failed operations by re-reading entity
            // may also need backoff algorithm to give failed msg after n tries

            try {
                Log.d(TAG, "about to persist " + entity.toString());
                resolver.applyBatch(ContactsContract.AUTHORITY, diff);
            } catch (RemoteException e) {
                Log.w(TAG, "problem writing rawcontact diff", e);
            } catch (OperationApplicationException e) {
                Log.w(TAG, "problem writing rawcontact diff", e);
            }
        }

        if (savedChanges) {
            Toast.makeText(this, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
        }

        this.finish();
        return true;
    }

    /**
     * Revert any changes the user has made, and finish the activity.
     */
    private boolean doRevertAction() {
        finish();
        return true;
    }

    /**
     * Delete the entire contact currently being edited, which usually asks for
     * user confirmation before continuing.
     */
    private boolean doDeleteAction() {
        this.createDeleteDialog().show();
        return true;
    }

    /**
     * Delete the entire contact currently being edited.
     */
    private void onDeleteActionConfirmed() {
        // TODO: delete entire contact
    }


    /**
     * Pick a specific photo to be added under this contact.
     */
    private boolean doPickPhotoAction() {
        try {
            final Intent intent = getPhotoPickIntent();
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(EditContactActivity.this).setTitle(R.string.errorDialogTitle)
                    .setMessage(R.string.photoPickerNotFoundText).setPositiveButton(
                            android.R.string.ok, null).show();
        }
        return true;
    }

    public static Intent getPhotoPickIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 96);
        intent.putExtra("outputY", 96);
        intent.putExtra("return-data", true);
        return intent;
    }

    public boolean doRemovePhotoAction() {
        // TODO: remove photo from current contact
        return true;
    }




    private Dialog createDeleteDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.deleteConfirmation_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.deleteConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                onDeleteActionConfirmed();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }

    private Dialog createPhotoDialog() {
        // TODO: build dialog for picking primary photo
        return null;
    }

    /**
     * Create dialog for selecting primary display name.
     */
    private Dialog createNameDialog() {
        // Build set of all available display names
        final ArrayList<ValuesDelta> allNames = new ArrayList<ValuesDelta>();
        for (EntityDelta entity : this.mEntities) {
            final ArrayList<ValuesDelta> displayNames = entity
                    .getMimeEntries(StructuredName.CONTENT_ITEM_TYPE);
            allNames.addAll(displayNames);
        }

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = this.getLayoutInflater().cloneInContext(dialogContext);

        final ListAdapter nameAdapter = new ArrayAdapter<ValuesDelta>(this,
                android.R.layout.simple_list_item_1, allNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(
                            android.R.layout.simple_expandable_list_item_1, parent, false);
                }

                final ValuesDelta structuredName = this.getItem(position);
                final String displayName = structuredName.getAsString(StructuredName.DISPLAY_NAME);

                ((TextView)convertView).setText(displayName);

                return convertView;
            }
        };

        final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                // User picked display name, so make super-primary
                final ValuesDelta structuredName = allNames.get(which);
                structuredName.put(Data.IS_PRIMARY, 1);
                structuredName.put(Data.IS_SUPER_PRIMARY, 1);

                // TODO: include last social snippet after update
                final String displayName = structuredName.getAsString(StructuredName.DISPLAY_NAME);
                mHeader.bindStatic(displayName, null);
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_primary_name);
        builder.setSingleChoiceItems(nameAdapter, 0, clickListener);
        return builder.create();
    }

}
