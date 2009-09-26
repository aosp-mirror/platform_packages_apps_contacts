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

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.collect.Lists;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.ScrollingTabWidget;
import com.android.contacts.model.GoogleSource;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Editor;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.Sources;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.widget.ContactEditorView;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.WeakAsyncTask;
import com.android.internal.widget.ContactHeaderWidget;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        ScrollingTabWidget.OnTabSelectionChangedListener,
        ContactHeaderWidget.ContactHeaderListener, EditorListener {
    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_SELECTED_RAW_CONTACT = "selected";

    private String mQuerySelection;

    private ScrollingTabWidget mTabWidget;
    private ContactHeaderWidget mHeader;

    private ContactEditorView mEditor;

    private EntitySet mState;

    private ArrayList<Dialog> mManagedDialogs = Lists.newArrayList();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = this;
        final LayoutInflater inflater = this.getLayoutInflater();

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        setContentView(R.layout.act_edit);

        // Header bar is filled later after queries finish
        mHeader = (ContactHeaderWidget)this.findViewById(R.id.contact_header_widget);
        mHeader.setContactHeaderListener(this);
        mHeader.showStar(true);
        mHeader.enableClickListeners();

        mTabWidget = (ScrollingTabWidget)this.findViewById(R.id.tab_widget);
        mTabWidget.setTabSelectionListener(this);

        // Build editor and listen for photo requests
        mEditor = (ContactEditorView)this.findViewById(android.R.id.tabcontent);
        mEditor.getPhotoEditor().setEditorListener(this);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        // Handle initial actions only when existing state missing
        final boolean hasIncomingState = icicle != null && icicle.containsKey(KEY_EDIT_STATE);

        if (Intent.ACTION_EDIT.equals(action) && !hasIncomingState) {
            // Read initial state from database
            new QueryEntitiesTask(this).execute(intent);

        } else if (Intent.ACTION_INSERT.equals(action) && !hasIncomingState) {
            // Trigger dialog to pick account type
            doAddAction();
        }
    }

    private static class QueryEntitiesTask extends
            WeakAsyncTask<Intent, Void, Void, EditContactActivity> {
        public QueryEntitiesTask(EditContactActivity target) {
            super(target);
        }

        @Override
        protected Void doInBackground(EditContactActivity target, Intent... params) {
            // Load edit details in background
            final Context context = target;
            final Sources sources = Sources.getInstance(context);
            final Intent intent = params[0];

            final ContentResolver resolver = context.getContentResolver();

            // Handle both legacy and new authorities
            final Uri data = intent.getData();
            final String authority = data.getAuthority();
            final String mimeType = intent.resolveType(resolver);

            String selection = "0";
            if (ContactsContract.AUTHORITY.equals(authority)) {
                if (Contacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Handle selected aggregate
                    final long contactId = ContentUris.parseId(data);
                    selection = RawContacts.CONTACT_ID + "=" + contactId;
                } else if (RawContacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final long rawContactId = ContentUris.parseId(data);
                    final long contactId = ContactsUtils.queryForContactId(resolver, rawContactId);
                    selection = RawContacts.CONTACT_ID + "=" + contactId;
                }
            } else if (android.provider.Contacts.AUTHORITY.equals(authority)) {
                final long rawContactId = ContentUris.parseId(data);
                selection = RawContacts._ID + "=" + rawContactId;
            }

            target.mQuerySelection = selection;
            target.mState = EntitySet.fromQuery(resolver, selection, null, null);

            // Handle any incoming values that should be inserted
            final Bundle extras = intent.getExtras();
            final boolean hasExtras = extras != null && extras.size() > 0;
            final boolean hasState = target.mState.size() > 0;
            if (hasExtras && hasState) {
                // Find source defining the first RawContact found
                final EntityDelta state = target.mState.get(0);
                final String accountType = state.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
                final ContactsSource source = sources.getInflatedSource(accountType,
                        ContactsSource.LEVEL_CONSTRAINTS);
                EntityModifier.parseExtras(context, source, state, extras);
            }

            return null;
        }

        @Override
        protected void onPostExecute(EditContactActivity target, Void result) {
            // Bind UI to new background state
            target.bindTabs();
            target.bindHeader();
        }
    }



    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
            outState.putLong(KEY_SELECTED_RAW_CONTACT, getSelectedRawContactId());
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Read modifications from instance
        mState = savedInstanceState.<EntitySet> getParcelable(KEY_EDIT_STATE);

        bindTabs();
        bindHeader();

        if (hasValidState()) {
            final Long selectedId = savedInstanceState.getLong(KEY_SELECTED_RAW_CONTACT);
            setSelectedRawContactId(selectedId);
        }

        // Restore selected tab and any focus
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (Dialog dialog : mManagedDialogs) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    /**
     * Start managing this {@link Dialog} along with the {@link Activity}.
     */
    private void startManagingDialog(Dialog dialog) {
        synchronized (mManagedDialogs) {
            mManagedDialogs.add(dialog);
        }
    }

    /**
     * Show this {@link Dialog} and manage with the {@link Activity}.
     */
    private void showAndManageDialog(Dialog dialog) {
        startManagingDialog(dialog);
        dialog.show();
    }

    /**
     * Return the {@link RawContacts#_ID} of the currently selected tab.
     */
    protected Long getSelectedRawContactId() {
        final int tabIndex = mTabWidget.getCurrentTab();
        return this.mTabRawContacts.get(tabIndex);
    }

    /**
     * Return the {@link EntityDelta} for the currently selected tab.
     */
    protected EntityDelta getSelectedEntityDelta() {
        final Long rawContactId = getSelectedRawContactId();
        return mState.getByRawContactId(rawContactId);
    }

    /**
     * Set the selected tab based on the given {@link RawContacts#_ID}.
     */
    protected void setSelectedRawContactId(Long rawContactId) {
        int tabIndex = 0;

        // Find index of requested contact
        final int size = mTabRawContacts.size();
        for (int i = 0; i < size; i++) {
            if (mTabRawContacts.valueAt(i) == rawContactId) {
                tabIndex = i;
                break;
            }
        }

        mTabWidget.setCurrentTab(tabIndex);
        this.onTabSelectionChanged(tabIndex, false);
    }

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    protected boolean hasValidState() {
        return mState != null && mState.size() > 0;
    }



    /**
     * Map from {@link #mTabWidget} indexes to {@link RawContacts#_ID}, usually
     * used when mapping to {@link #mState}.
     */
    private SparseArray<Long> mTabRawContacts = new SparseArray<Long>();


    /**
     * Rebuild tabs to match our underlying {@link #mState} object, usually
     * called once we've parsed {@link Entity} data or have inserted a new
     * {@link RawContacts}.
     */
    protected void bindTabs() {
        if (!hasValidState()) return;

        final Sources sources = Sources.getInstance(this);
        final Long selectedRawContactId = this.getSelectedRawContactId();

        // Remove any existing tabs and rebuild any visible
        mTabWidget.removeAllTabs();
        mTabRawContacts.clear();
        for (EntityDelta entity : mState) {
            final ValuesDelta values = entity.getValues();
            if (!values.isVisible()) continue;

            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final Long rawContactId = values.getAsLong(RawContacts._ID);
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_CONSTRAINTS);

            final int tabIndex = mTabWidget.getTabCount();
            final View tabView = ContactsUtils.createTabIndicatorView(
                    mTabWidget.getTabParent(), source);
            mTabWidget.addTab(tabView);
            mTabRawContacts.put(tabIndex, rawContactId);
        }

        final boolean hasActiveTabs = mTabWidget.getTabCount() > 0;
        if (hasActiveTabs) {
            // Focus on last selected contact
            this.setSelectedRawContactId(selectedRawContactId);
        } else {
            // Nothing remains to edit, save and bail entirely
            this.doSaveAction();
        }

        // Show editor now that we've loaded state
        mEditor.setVisibility(View.VISIBLE);
    }

    /**
     * Bind our header based on {@link #mState}, which include any edits.
     * Usually called once {@link Entity} data has been loaded, or after a
     * primary {@link Data} change.
     */
    protected void bindHeader() {
        if (!hasValidState()) return;

        // TODO: rebuild header widget based on internal entities

        // TODO: fill header bar with newly parsed data for speed
        // TODO: handle legacy case correctly instead of assuming _id

//        if (mContactId > 0) {
//            mHeader.bindFromContactId(mContactId);
//        }

//        mHeader.setDisplayName(displayName, phoneticName);
//        mHeader.setPhoto(bitmap);
    }



    /** {@inheritDoc} */
    public void onTabSelectionChanged(int tabIndex, boolean clicked) {
        if (!hasValidState()) return;

        // Find entity and source for selected tab
        final EntityDelta entity = this.getSelectedEntityDelta();
        if (entity == null) return;

        final Sources sources = Sources.getInstance(this);
        final String accountType = entity.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        final ContactsSource source = sources.getInflatedSource(accountType,
                ContactsSource.LEVEL_CONSTRAINTS);

        // Assign editor state based on entity and source
        mEditor.setState(entity, source);
    }

    /** {@inheritDoc} */
    public void onDisplayNameClick(View view) {
        if (!hasValidState()) return;
        showAndManageDialog(createNameDialog());
    }

    /** {@inheritDoc} */
    public void onPhotoClick(View view) {
        if (!hasValidState()) return;
        showAndManageDialog(createPhotoDialog());
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

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        doSaveAction();
    }

    /** {@inheritDoc} */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignore failed requests
        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
                // When reaching this point, we've already inflated our tab
                // state and returned to the last-visible tab.
                final Bitmap photo = data.getParcelableExtra("data");
                mEditor.setPhotoBitmap(photo);
                break;
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
        final boolean hasPhotoEditor = mEditor.hasPhotoEditor();
        final boolean hasSetPhoto = mEditor.hasSetPhoto();

        menu.findItem(R.id.menu_photo_add).setVisible(hasPhotoEditor);
        menu.findItem(R.id.menu_photo_remove).setVisible(hasSetPhoto);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return doSaveAction();
            case R.id.menu_discard:
                return doRevertAction();
            case R.id.menu_add:
                return doAddAction();
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
     * Background task for persisting edited contact data, using the changes
     * defined by a set of {@link EntityDelta}. This task starts
     * {@link EmptyService} to make sure the background thread can finish
     * persisting in cases where the system wants to reclaim our process.
     */
    public static class PersistTask extends
            WeakAsyncTask<EntitySet, Void, Integer, EditContactActivity> {
        private static final int PERSIST_TRIES = 3;

        private static final int RESULT_UNCHANGED = 0;
        private static final int RESULT_SUCCESS = 1;
        private static final int RESULT_FAILURE = 2;

        private long mSavedId;

        private WeakReference<ProgressDialog> progress;

        public PersistTask(EditContactActivity target) {
            super(target);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(EditContactActivity target) {
            this.progress = new WeakReference<ProgressDialog>(ProgressDialog.show(target, null,
                    target.getText(R.string.savingContact)));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            final Context context = target;
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Integer doInBackground(EditContactActivity target, EntitySet... params) {
            final Context context = target;
            final ContentResolver resolver = context.getContentResolver();

            EntitySet state = params[0];

            // Trim any empty fields, and RawContacts, before persisting
            final Sources sources = Sources.getInstance(context);
            EntityModifier.trimEmpty(state, sources);

            // Attempt to persist changes
            int tries = 0;
            Integer result = RESULT_FAILURE;
            while (tries < PERSIST_TRIES) {
                try {
                    // Build operations and try applying
                    final ArrayList<ContentProviderOperation> diff = state.buildDiff();
                    ContentProviderResult[] results;
                    if (!diff.isEmpty()) {
                         results = resolver.applyBatch(ContactsContract.AUTHORITY, diff);
                         Intent intent = new Intent();
                         final long rawContactId = getRawContactId(state, diff, results);
                         final Uri rawContactUri = ContentUris.withAppendedId(
                                 RawContacts.CONTENT_URI, rawContactId);

                         // convert the raw contact URI to a contact URI
                         final Uri contactLookupUri = RawContacts.getContactLookupUri(resolver,
                                 rawContactUri);
                         intent.setData(contactLookupUri);
                         target.setResult(RESULT_OK, intent);
                         target.finish();
                    }
                    result = (diff.size() > 0) ? RESULT_SUCCESS : RESULT_UNCHANGED;
                    break;

                } catch (RemoteException e) {
                    // Something went wrong, bail without success
                    Log.e(TAG, "Problem persisting user edits", e);
                    break;

                } catch (OperationApplicationException e) {
                    // Version consistency failed, re-parent change and try again
                    Log.w(TAG, "Version consistency failed, re-parenting", e);
                    final EntitySet newState = EntitySet.fromQuery(resolver,
                            target.mQuerySelection, null, null);
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
        protected void onPostExecute(EditContactActivity target, Integer result) {
            final Context context = target;

            if (result == RESULT_SUCCESS) {
                Toast.makeText(context, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            } else if (result == RESULT_FAILURE) {
                Toast.makeText(context, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }

            progress.get().dismiss();
            target.finish();
            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));
        }
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    private boolean doSaveAction() {
        if (!hasValidState()) return false;

        final PersistTask task = new PersistTask(this);
        task.execute(mState);

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
     * Create a new {@link RawContacts} which will exist as another
     * {@link EntityDelta} under the currently edited {@link Contacts}.
     */
    private boolean doAddAction() {
        // Adding is okay when missing state
        new AddContactTask(this).execute();
        return true;
    }

    /**
     * Delete the entire contact currently being edited, which usually asks for
     * user confirmation before continuing.
     */
    private boolean doDeleteAction() {
        if (!hasValidState()) return false;

        showAndManageDialog(createDeleteDialog());
        return true;
    }

    /**
     * Pick a specific photo to be added under the currently selected tab.
     */
    private boolean doPickPhotoAction() {
        if (!hasValidState()) return false;

        try {
            // Launch picker to choose photo for selected contact
            final Intent intent = ContactsUtils.getPhotoPickIntent();
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * Clear any existing photo under the currently selected tab.
     */
    public boolean doRemovePhotoAction() {
        if (!hasValidState()) return false;

        // Remove photo from selected contact
        mEditor.setPhotoBitmap(null);
        return true;
    }

    /** {@inheritDoc} */
    public void onDeleted(Editor editor) {
        // Ignore any editor deletes
    }

    /** {@inheritDoc} */
    public void onRequest(int request) {
        if (!hasValidState()) return;

        switch (request) {
            case EditorListener.REQUEST_PICK_PHOTO: {
                doPickPhotoAction();
                break;
            }
        }
    }








    /**
     * Build dialog that handles adding a new {@link RawContacts} after the user
     * picks a specific {@link ContactsSource}.
     */
    private static class AddContactTask extends
            WeakAsyncTask<Void, Void, AlertDialog.Builder, EditContactActivity> {
        public AddContactTask(EditContactActivity target) {
            super(target);
        }

        @Override
        protected AlertDialog.Builder doInBackground(final EditContactActivity target,
                Void... params) {
            final Sources sources = Sources.getInstance(target);

            // Wrap our context to inflate list items using correct theme
            final Context dialogContext = new ContextThemeWrapper(target, android.R.style.Theme_Light);
            final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            final ArrayList<Account> writable = sources.getAccounts(true);

            // No Accounts available.  Create a phone-local contact.
            if (writable.isEmpty()) {
                selectAccount(null);
                return null;  // Don't show a dialog.
            }

            // In the common case of a single account being writable, auto-select
            // it without showing a dialog.
            if (writable.size() == 1) {
                selectAccount(writable.get(0));
                return null;  // Don't show a dialog.
            }

            final ArrayAdapter<Account> accountAdapter = new ArrayAdapter<Account>(target,
                    android.R.layout.simple_list_item_2, writable) {
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
                    text2.setText(source.getDisplayLabel(target));

                    return convertView;
                }
            };

            final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    // Create new contact based on selected source
                    final Account account = accountAdapter.getItem(which);
                    selectAccount(account);

                    // Update the UI.
                    EditContactActivity target = mTarget.get();
                    if (target != null) {
                        target.bindTabs();
                        target.bindHeader();
                    }
                }
            };

            final DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    // If nothing remains, close activity
                    if (!target.hasValidState()) {
                        target.finish();
                    }
                }
            };

            // TODO: when canceled and was single add, finish()
            final AlertDialog.Builder builder = new AlertDialog.Builder(target);
            builder.setTitle(R.string.dialog_new_contact_account);
            builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
            builder.setOnCancelListener(cancelListener);
            return builder;
        }

        /**
         * Sets up EditContactActivity's mState for the account selected.
         * Runs from a background thread.
         *
         * @param account may be null to signal a device-local contact should
         *     be created.
         */
        private void selectAccount(Account account) {
            EditContactActivity target = mTarget.get();
            if (target == null) {
                return;
            }
            final Sources sources = Sources.getInstance(target);
            final ContentValues values = new ContentValues();
            if (account != null) {
                values.put(RawContacts.ACCOUNT_NAME, account.name);
                values.put(RawContacts.ACCOUNT_TYPE, account.type);
            } else {
                values.putNull(RawContacts.ACCOUNT_NAME);
                values.putNull(RawContacts.ACCOUNT_TYPE);
            }

            // Parse any values from incoming intent
            final EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
            final ContactsSource source = sources.getInflatedSource(
                account != null ? account.type : null,
                ContactsSource.LEVEL_CONSTRAINTS);
            final Bundle extras = target.getIntent().getExtras();
            EntityModifier.parseExtras(target, source, insert, extras);

            // Ensure we have some default fields
            EntityModifier.ensureKindExists(insert, source, Phone.CONTENT_ITEM_TYPE);
            EntityModifier.ensureKindExists(insert, source, Email.CONTENT_ITEM_TYPE);

            // Create "My Contacts" membership for Google contacts
            // TODO: move this off into "templates" for each given source
            if (GoogleSource.ACCOUNT_TYPE.equals(source.accountType)) {
                GoogleSource.attemptMyContactsMembership(insert, target);
            }

	    // TODO: no synchronization here on target.mState.  This
	    // runs in the background thread, but it's accessed from
	    // multiple thread, including the UI thread.
            if (target.mState == null) {
                // Create state if none exists yet
                target.mState = EntitySet.fromSingle(insert);
            } else {
                // Add contact onto end of existing state
                target.mState.add(insert);
            }
        }

        @Override
        protected void onPostExecute(EditContactActivity target, AlertDialog.Builder result) {
            if (result != null) {
                // Note: null is returned when no dialog is to be
                // shown (no multiple accounts to select between)
                target.showAndManageDialog(result.create());
            } else {
                // Account was auto-selected on the background thread,
                // but we need to update the UI still in the
                // now-current UI thread.
                target.bindTabs();
                target.bindHeader();
            }
        }
    }



    private Dialog createDeleteDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.deleteConfirmation_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.deleteConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Mark the currently selected contact for deletion
                final EntityDelta delta = getSelectedEntityDelta();
                if (delta == null) return;
                delta.markDeleted();

                bindTabs();
                bindHeader();
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
        final ArrayList<ValuesDelta> allNames = Lists.newArrayList();
        for (EntityDelta entity : mState) {
            final ArrayList<ValuesDelta> displayNames = entity
                    .getMimeEntries(StructuredName.CONTENT_ITEM_TYPE);
            allNames.addAll(displayNames);
        }

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = this.getLayoutInflater()
                .cloneInContext(dialogContext);

        final ListAdapter nameAdapter = new ArrayAdapter<ValuesDelta>(this,
                android.R.layout.simple_list_item_1, allNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                            parent, false);
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

                // Update header based on edited values
                bindHeader();
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_primary_name);
        builder.setSingleChoiceItems(nameAdapter, 0, clickListener);
        return builder.create();
    }

}
