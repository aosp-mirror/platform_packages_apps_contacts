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

import com.android.contacts.BaseContactCardActivity;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.ScrollingTabWidget;
import com.android.contacts.ViewContactActivity;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.HardCodedSources;
import com.android.contacts.model.Sources;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.widget.ContactEditorView;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.contacts.util.WeakAsyncTask;
import com.android.internal.widget.ContactHeaderWidget;

import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.util.Log;
import android.util.SparseArray;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        ScrollingTabWidget.OnTabSelectionChangedListener, ContactHeaderWidget.ContactHeaderListener {
    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    private static final int TOKEN_ENTITY = 41;

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_EDITOR_STATE = "editor";
    private static final String KEY_SELECTED_TAB = "tab";
    private static final String KEY_SELECTED_TAB_ID = "tabId";
    private static final String KEY_CONTACT_ID = "contactId";

    private long mSelectedRawContactId = -1;
    private long mContactId = -1;

    private ScrollingTabWidget mTabWidget;
    private ContactHeaderWidget mHeader;

    private View mTabContent;
    private ContactEditorView mEditor;

    private EditState mState = new EditState();

    private static class EditState extends ArrayList<EntityDelta> implements Parcelable {
        public long getAggregateId() {
            if (this.size() > 0) {
                // Assume the aggregate tied to first child
                final EntityDelta first = this.get(0);
                return first.getValues().getAsLong(RawContacts.CONTACT_ID);
            } else {
                // Otherwise return invalid value
                return -1;
            }
        }

        /** {@inheritDoc} */
        public int describeContents() {
            // Nothing special about this parcel
            return 0;
        }

        /** {@inheritDoc} */
        public void writeToParcel(Parcel dest, int flags) {
            final int size = this.size();
            dest.writeInt(size);
            for (EntityDelta delta : this) {
                dest.writeParcelable(delta, flags);
            }
        }

        public void readFromParcel(Parcel source) {
            final int size = source.readInt();
            for (int i = 0; i < size; i++) {
                this.add(source.<EntityDelta> readParcelable(null));
            }
        }

        public static final Parcelable.Creator<EditState> CREATOR = new Parcelable.Creator<EditState>() {
            public EditState createFromParcel(Parcel in) {
                final EditState state = new EditState();
                state.readFromParcel(in);
                return state;
            }

            public EditState[] newArray(int size) {
                return new EditState[size];
            }
        };
    }

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

        mTabWidget = (ScrollingTabWidget)this.findViewById(R.id.tab_widget);
        mTabWidget.setTabSelectionListener(this);

        mTabContent = this.findViewById(android.R.id.tabcontent);

        mEditor = new ContactEditorView(context);
        mEditor.swapWith(mTabContent);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        if (Intent.ACTION_EDIT.equals(action) && icicle == null) {
            // Read initial state from database
            new QueryEntitiesTask(this).execute(intent);

        } else if (Intent.ACTION_INSERT.equals(action)) {
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
            String selection = "0";
            final Uri data = intent.getData();
            final String authority = data.getAuthority();
            if (ContactsContract.AUTHORITY.equals(authority)) {
                final long rawContactId = ContentUris.parseId(data);
                target.mSelectedRawContactId = rawContactId;
                target.mContactId = ContactsUtils.queryForContactId(target.getContentResolver(),
                        rawContactId);
                selection = RawContacts.CONTACT_ID + "=" + target.mContactId;
            } else if (Contacts.AUTHORITY.equals(authority)) {
                final long rawContactId = ContentUris.parseId(data);
                target.mSelectedRawContactId = rawContactId;
                selection = RawContacts._ID + "=" + rawContactId;
            }

            EntityIterator iterator = null;
            final EditState state = new EditState();
            try {
                // Perform background query to pull contact details
                iterator = resolver.queryEntities(RawContacts.CONTENT_URI,
                        selection, null, null);
                while (iterator.hasNext()) {
                    // Read all contacts into local deltas to prepare for edits
                    final Entity before = iterator.next();
                    final EntityDelta entity = EntityDelta.fromBefore(before);
                    state.add(entity);
                }
            } catch (RemoteException e) {
                throw new IllegalStateException("Problem querying contact details", e);
            } finally {
                if (iterator != null) {
                    iterator.close();
                }
            }

            target.mState = state;
            return null;
        }

        @Override
        protected void onPostExecute(EditContactActivity target, Void result) {
            // Bind UI to new background state
            target.bindTabs();
            target.bindHeader();
        }
    }


//    /**
//     * Instance state for {@link #mEditor} from a previous instance.
//     */
//    private SparseArray<Parcelable> mEditorState;
//
//    /**
//     * Save state of the currently selected {@link #mEditor}, usually for
//     * passing across instance boundaries to restore later.
//     */
//    private SparseArray<Parcelable> buildEditorState() {
//        final SparseArray<Parcelable> state = new SparseArray<Parcelable>();
//        if (mEditor != null) {
//            mEditor.getView().saveHierarchyState(state);
//        }
//        return state;
//    }
//
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Store entities with modifications
        outState.putParcelable(KEY_EDIT_STATE, mState);
//        outState.putSparseParcelableArray(KEY_EDITOR_STATE, buildEditorState());
//        outState.putInt(KEY_SELECTED_TAB, mTabWidget.getCurrentTab());
        outState.putLong(KEY_SELECTED_TAB_ID, mSelectedRawContactId);
        outState.putLong(KEY_CONTACT_ID, mContactId);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Read modifications from instance
        mState = savedInstanceState.<EditState> getParcelable(KEY_EDIT_STATE);
        mSelectedRawContactId = savedInstanceState.getLong(KEY_SELECTED_TAB_ID);
        mContactId = savedInstanceState.getLong(KEY_CONTACT_ID);

        Log.d(TAG, "onrestoreinstancestate");

//        mEditorState = savedInstanceState.getSparseParcelableArray(KEY_EDITOR_STATE);
//
//        final int selectedTab = savedInstanceState.getInt(KEY_SELECTED_TAB);
        bindTabs();
        bindHeader();

        // Restore selected tab and any focus
        super.onRestoreInstanceState(savedInstanceState);
    }


    /**
     * Rebuild tabs to match our underlying {@link #mEntities} object, usually
     * called once we've parsed {@link Entity} data or have inserted a new
     * {@link RawContacts}.
     */
    protected void bindTabs() {
        final Sources sources = Sources.getInstance(this);
        int selectedTab = 0;

        mTabWidget.removeAllTabs();
        for (EntityDelta entity : mState) {
            ValuesDelta values = entity.getValues();
            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final Long rawContactId = values.getAsLong(RawContacts._ID);
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_CONSTRAINTS);

            if (rawContactId != null && rawContactId == mSelectedRawContactId) {
                selectedTab = mTabWidget.getTabCount();
            }

            final View tabView = BaseContactCardActivity.createTabIndicatorView(mTabWidget, source);
            mTabWidget.addTab(tabView);
        }
        if (mState.size() > 0) {
            mTabWidget.setCurrentTab(selectedTab);
            this.onTabSelectionChanged(selectedTab, false);
        }
    }

    /**
     * Bind our header based on {@link #mEntities}, which include any edits.
     * Usually called once {@link Entity} data has been loaded, or after a
     * primary {@link Data} change.
     */
    protected void bindHeader() {
        // TODO: rebuild header widget based on internal entities

        // TODO: fill header bar with newly parsed data for speed
        // TODO: handle legacy case correctly instead of assuming _id

        if (mContactId > 0) {
            mHeader.bindFromContactId(mContactId);
        }

//        mHeader.setDisplayName(displayName, phoneticName);
//        mHeader.setPhoto(bitmap);
    }



    /** {@inheritDoc} */
    public void onTabSelectionChanged(int tabIndex, boolean clicked) {
        boolean validTab = mState != null && tabIndex >= 0 && tabIndex < mState.size();
        if (!validTab) return;

        // Find entity and source for selected tab
        final EntityDelta entity = mState.get(tabIndex);
        final String accountType = entity.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        Long rawContactId = entity.getValues().getAsLong(RawContacts._ID);
        if (rawContactId != null) {
            mSelectedRawContactId = rawContactId;
        }

        final Sources sources = Sources.getInstance(this);
        final ContactsSource source = sources.getInflatedSource(accountType,
                ContactsSource.LEVEL_CONSTRAINTS);

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


    /** {@inheritDoc} */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return doSaveAction();
        }
        return super.onKeyDown(keyCode, event);
    }

    /** {@inheritDoc} */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignore failed requests
        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
                // TODO: pass back to requesting tab
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
        // TODO: show or hide photo items based on current tab
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
    public static class PersistTask extends WeakAsyncTask<EditState, Void, Boolean, Context> {
        public PersistTask(Context context) {
            super(context);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(Context context) {
            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Boolean doInBackground(Context context, EditState... params) {
            final EditState state = params[0];
            final ContentResolver resolver = context.getContentResolver();

            boolean savedChanges = false;
            for (EntityDelta entity : state) {
                // TODO: remove this extremely verbose debugging
                Log.d(TAG, "trying to persist " + entity.toString());
                final ArrayList<ContentProviderOperation> diff = entity.buildDiff();

                // Skip updates that don't change
                if (diff.size() == 0) continue;
                savedChanges = true;

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

            return savedChanges;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(Context context, Boolean result) {
            if (result) {
                Toast.makeText(context, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            }

            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));
        }
    }

    /**
     * Timeout for a {@link PersistTask} running on a background thread. This is
     * just shorter than the ANR timeout, so that we hold off user interaction
     * as long as possible.
     */
    private static final long TIMEOUT_PERSIST = 4000;

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    private boolean doSaveAction() {
        try {
            final PersistTask task = new PersistTask(this);
            task.execute(mState);
            task.get(TIMEOUT_PERSIST, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore when someone cancels the operation
        } catch (TimeoutException e) {
            // Ignore when task is taking too long
        } catch (ExecutionException e) {
            // Important exceptions are handled on remote thread
        }

        // Persisting finished, or we timed out waiting on it. Either way,
        // finish this activity, the background task will keep running.
        setResult(RESULT_OK, new Intent().putExtra(ViewContactActivity.RAW_CONTACT_ID_EXTRA,
                mSelectedRawContactId));
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
     * Create a new {@link RawContacts} which will exist as another
     * {@link EntityDelta} under the currently edited {@link Contacts}.
     */
    private boolean doAddAction() {
        new AddContactTask(this).execute();
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
     * Pick a specific photo to be added under this contact.
     */
    private boolean doPickPhotoAction() {
        try {
            final Intent intent = ContactsUtils.getPhotoPickIntent();
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(EditContactActivity.this).setTitle(R.string.errorDialogTitle)
                    .setMessage(R.string.photoPickerNotFoundText).setPositiveButton(
                            android.R.string.ok, null).show();
        }
        return true;
    }

    public boolean doRemovePhotoAction() {
        // TODO: remove photo from current contact
        return true;
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

            final ArrayList<Account> writable = sources.getWritableAccounts();
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
                    if (source.titleRes > 0) {
                        text1.setText(source.titleRes);
                    }
                    text2.setText(account.name);

                    return convertView;
                }
            };

            final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    // Create new contact based on selected source
                    final Account source = accountAdapter.getItem(which);
                    final ContentValues values = new ContentValues();
                    values.put(RawContacts.ACCOUNT_NAME, source.name);
                    values.put(RawContacts.ACCOUNT_TYPE, source.type);

                    // Tie this directly to existing aggregate
                    // TODO: this may need to use aggregation exception rules
                    final long aggregateId = target.mState.getAggregateId();
                    if (aggregateId >= 0) {
                        values.put(RawContacts.CONTACT_ID, aggregateId);
                    }

                    final EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
                    target.mState.add(insert);

                    target.bindTabs();
                    target.bindHeader();
                }
            };

            final DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    // If nothing remains, close activity
                    if (target.mState.size() == 0) {
                        target.finish();
                    }
                }
            };

            // TODO: when canceled and single add, finish()
            final AlertDialog.Builder builder = new AlertDialog.Builder(target);
            builder.setTitle(R.string.dialog_new_contact_account);
            builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
            builder.setOnCancelListener(cancelListener);
            return builder;
        }

        @Override
        protected void onPostExecute(EditContactActivity target, AlertDialog.Builder result) {
            result.create().show();
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
                final int index = mTabWidget.getCurrentTab();
                final EntityDelta delta = mState.get(index);
                delta.markDeleted();

                // TODO: trigger task to update tabs (doesnt need to be background)
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
