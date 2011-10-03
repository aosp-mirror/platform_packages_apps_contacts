/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.editor;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorAccountsChangedActivity;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.activities.JoinContactActivity;
import com.android.contacts.editor.AggregationSuggestionEngine.Suggestion;
import com.android.contacts.editor.Editor.EditorListener;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityDeltaList;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.GoogleAccountType;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.AccountsListAdapter.AccountListFilter;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ContactEditorFragment extends Fragment implements
        SplitContactConfirmationDialogFragment.Listener,
        AggregationSuggestionEngine.Listener, AggregationSuggestionView.Listener,
        RawContactReadOnlyEditorView.Listener {

    private static final String TAG = ContactEditorFragment.class.getSimpleName();

    private static final int LOADER_DATA = 1;
    private static final int LOADER_GROUPS = 2;

    private static final String KEY_URI = "uri";
    private static final String KEY_ACTION = "action";
    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_RAW_CONTACT_ID_REQUESTING_PHOTO = "photorequester";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";
    private static final String KEY_CURRENT_PHOTO_FILE = "currentphotofile";
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";
    private static final String KEY_CONTACT_WRITABLE_FOR_JOIN = "contactwritableforjoin";
    private static final String KEY_SHOW_JOIN_SUGGESTIONS = "showJoinSuggestions";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NEW_LOCAL_PROFILE = "newLocalProfile";
    private static final String KEY_IS_USER_PROFILE = "isUserProfile";

    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";

    /**
     * An intent extra that forces the editor to add the edited contact
     * to the default group (e.g. "My Contacts").
     */
    public static final String INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY = "addToDefaultDirectory";

    public static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    /**
     * Modes that specify what the AsyncTask has to perform after saving
     */
    // TODO: Move this into a common utils class or the save service because the contact and
    // group editors need to use this interface definition
    public interface SaveMode {
        /**
         * Close the editor after saving
         */
        public static final int CLOSE = 0;

        /**
         * Reload the data so that the user can continue editing
         */
        public static final int RELOAD = 1;

        /**
         * Split the contact after saving
         */
        public static final int SPLIT = 2;

        /**
         * Join another contact after saving
         */
        public static final int JOIN = 3;

        /**
         * Navigate to Contacts Home activity after saving.
         */
        public static final int HOME = 4;
    }

    private interface Status {
        /**
         * The loader is fetching data
         */
        public static final int LOADING = 0;

        /**
         * Not currently busy. We are waiting for the user to enter data
         */
        public static final int EDITING = 1;

        /**
         * The data is currently being saved. This is used to prevent more
         * auto-saves (they shouldn't overlap)
         */
        public static final int SAVING = 2;

        /**
         * Prevents any more saves. This is used if in the following cases:
         * - After Save/Close
         * - After Revert
         * - After the user has accepted an edit suggestion
         */
        public static final int CLOSING = 3;

        /**
         * Prevents saving while running a child activity.
         */
        public static final int SUB_ACTIVITY = 4;
    }

    private static final int REQUEST_CODE_JOIN = 0;
    private static final int REQUEST_CODE_CAMERA_WITH_DATA = 1;
    private static final int REQUEST_CODE_PHOTO_PICKED_WITH_DATA = 2;
    private static final int REQUEST_CODE_ACCOUNTS_CHANGED = 3;

    private Bitmap mPhoto = null;
    private long mRawContactIdRequestingPhoto = -1;
    private long mRawContactIdRequestingPhotoAfterLoad = -1;

    private final EntityDeltaComparator mComparator = new EntityDeltaComparator();

    private static final File PHOTO_DIR = new File(
            Environment.getExternalStorageDirectory() + "/DCIM/Camera");

    private Cursor mGroupMetaData;

    private File mCurrentPhotoFile;

    // Height/width (in pixels) to request for the photo - queried from the provider.
    private int mPhotoPickSize;

    private Context mContext;
    private String mAction;
    private Uri mLookupUri;
    private Bundle mIntentExtras;
    private Listener mListener;

    private long mContactIdForJoin;
    private boolean mContactWritableForJoin;

    private ContactEditorUtils mEditorUtils;

    private LinearLayout mContent;
    private EntityDeltaList mState;

    private ViewIdGenerator mViewIdGenerator;

    private long mLoaderStartTime;

    private int mStatus;

    private AggregationSuggestionEngine mAggregationSuggestionEngine;
    private long mAggregationSuggestionsRawContactId;
    private View mAggregationSuggestionView;

    private ListPopupWindow mAggregationSuggestionPopup;

    private static final class AggregationSuggestionAdapter extends BaseAdapter {
        private final Activity mActivity;
        private final boolean mSetNewContact;
        private final AggregationSuggestionView.Listener mListener;
        private final List<Suggestion> mSuggestions;

        public AggregationSuggestionAdapter(Activity activity, boolean setNewContact,
                AggregationSuggestionView.Listener listener, List<Suggestion> suggestions) {
            mActivity = activity;
            mSetNewContact = setNewContact;
            mListener = listener;
            mSuggestions = suggestions;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Suggestion suggestion = (Suggestion) getItem(position);
            LayoutInflater inflater = mActivity.getLayoutInflater();
            AggregationSuggestionView suggestionView =
                    (AggregationSuggestionView) inflater.inflate(
                            R.layout.aggregation_suggestions_item, null);
            suggestionView.setNewContact(mSetNewContact);
            suggestionView.setListener(mListener);
            suggestionView.bindSuggestion(suggestion);
            return suggestionView;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mSuggestions.get(position);
        }

        @Override
        public int getCount() {
            return mSuggestions.size();
        }
    }

    private OnItemClickListener mAggregationSuggestionItemClickListener =
            new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final AggregationSuggestionView suggestionView = (AggregationSuggestionView) view;
            suggestionView.handleItemClickEvent();
            mAggregationSuggestionPopup.dismiss();
            mAggregationSuggestionPopup = null;
        }
    };

    private boolean mAutoAddToDefaultGroup;

    private boolean mEnabled = true;
    private boolean mRequestFocus;
    private boolean mNewLocalProfile = false;
    private boolean mIsUserProfile = false;

    public ContactEditorFragment() {
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mContent != null) {
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    mContent.getChildAt(i).setEnabled(enabled);
                }
            }
            setAggregationSuggestionViewEnabled(enabled);
            final Activity activity = getActivity();
            if (activity != null) activity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mEditorUtils = ContactEditorUtils.getInstance(mContext);
        loadPhotoPickSize();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAggregationSuggestionEngine != null) {
            mAggregationSuggestionEngine.quit();
        }

        // If anything was left unsaved, save it now but keep the editor open.
        if (!getActivity().isChangingConfigurations() && mStatus == Status.EDITING) {
            save(SaveMode.RELOAD);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_editor_fragment, container, false);

        mContent = (LinearLayout) view.findViewById(R.id.editors);

        setHasOptionsMenu(true);

        // If we are in an orientation change, we already have mState (it was loaded by onCreate)
        if (mState != null) {
            bindEditors();
        }

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Handle initial actions only when existing state missing
        final boolean hasIncomingState = savedInstanceState != null;

        if (!hasIncomingState) {
            if (Intent.ACTION_EDIT.equals(mAction)) {
                getLoaderManager().initLoader(LOADER_DATA, null, mDataLoaderListener);
            } else if (Intent.ACTION_INSERT.equals(mAction)) {
                final Account account = mIntentExtras == null ? null :
                        (Account) mIntentExtras.getParcelable(Intents.Insert.ACCOUNT);
                final String dataSet = mIntentExtras == null ? null :
                        mIntentExtras.getString(Intents.Insert.DATA_SET);

                if (account != null) {
                    // Account specified in Intent
                    createContact(new AccountWithDataSet(account.name, account.type, dataSet));
                } else {
                    // No Account specified. Let the user choose
                    // Load Accounts async so that we can present them
                    selectAccountAndCreateContact();
                }
            } else if (ContactEditorActivity.ACTION_SAVE_COMPLETED.equals(mAction)) {
                // do nothing
            } else throw new IllegalArgumentException("Unknown Action String " + mAction +
                    ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT);
        }
    }

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();
    }

    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        mAction = action;
        mLookupUri = lookupUri;
        mIntentExtras = intentExtras;
        mAutoAddToDefaultGroup = mIntentExtras != null
                && mIntentExtras.containsKey(INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY);
        mNewLocalProfile = mIntentExtras != null
                && mIntentExtras.getBoolean(INTENT_EXTRA_NEW_LOCAL_PROFILE);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            // Restore mUri before calling super.onCreate so that onInitializeLoaders
            // would already have a uri and an action to work with
            mLookupUri = savedState.getParcelable(KEY_URI);
            mAction = savedState.getString(KEY_ACTION);
        }

        super.onCreate(savedState);

        if (savedState == null) {
            // If savedState is non-null, onRestoreInstanceState() will restore the generator.
            mViewIdGenerator = new ViewIdGenerator();
        } else {
            // Read state from savedState. No loading involved here
            mState = savedState.<EntityDeltaList> getParcelable(KEY_EDIT_STATE);
            mRawContactIdRequestingPhoto = savedState.getLong(
                    KEY_RAW_CONTACT_ID_REQUESTING_PHOTO);
            mViewIdGenerator = savedState.getParcelable(KEY_VIEW_ID_GENERATOR);
            String fileName = savedState.getString(KEY_CURRENT_PHOTO_FILE);
            if (fileName != null) {
                mCurrentPhotoFile = new File(fileName);
            }
            mContactIdForJoin = savedState.getLong(KEY_CONTACT_ID_FOR_JOIN);
            mContactWritableForJoin = savedState.getBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN);
            mAggregationSuggestionsRawContactId = savedState.getLong(KEY_SHOW_JOIN_SUGGESTIONS);
            mEnabled = savedState.getBoolean(KEY_ENABLED);
            mStatus = savedState.getInt(KEY_STATUS);
            mNewLocalProfile = savedState.getBoolean(KEY_NEW_LOCAL_PROFILE);
            mIsUserProfile = savedState.getBoolean(KEY_IS_USER_PROFILE);
        }
    }

    public void setData(ContactLoader.Result data) {
        // If we have already loaded data, we do not want to change it here to not confuse the user
        if (mState != null) {
            Log.v(TAG, "Ignoring background change. This will have to be rebased later");
            return;
        }

        // See if this edit operation needs to be redirected to a custom editor
        ArrayList<Entity> entities = data.getEntities();
        if (entities.size() == 1) {
            Entity entity = entities.get(0);
            ContentValues entityValues = entity.getEntityValues();
            String type = entityValues.getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet = entityValues.getAsString(RawContacts.DATA_SET);
            AccountType accountType = AccountTypeManager.getInstance(mContext).getAccountType(
                    type, dataSet);
            if (accountType.getEditContactActivityClassName() != null &&
                    !accountType.areContactsWritable()) {
                if (mListener != null) {
                    String name = entityValues.getAsString(RawContacts.ACCOUNT_NAME);
                    long rawContactId = entityValues.getAsLong(RawContacts.Entity._ID);
                    mListener.onCustomEditContactActivityRequested(
                            new AccountWithDataSet(name, type, dataSet),
                            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                            mIntentExtras, true);
                }
                return;
            }
        }

        bindEditorsForExistingContact(data);
    }

    @Override
    public void onExternalEditorRequest(AccountWithDataSet account, Uri uri) {
        mListener.onCustomEditContactActivityRequested(account, uri, null, false);
    }

    private void bindEditorsForExistingContact(ContactLoader.Result data) {
        setEnabled(true);

        mState = EntityDeltaList.fromIterator(data.getEntities().iterator());
        setIntentExtras(mIntentExtras);
        mIntentExtras = null;

        // For user profile, change the contacts query URI
        mIsUserProfile = data.isUserProfile();
        boolean localProfileExists = false;

        if (mIsUserProfile) {
            for (EntityDelta state : mState) {
                // For profile contacts, we need a different query URI
                state.setProfileQueryUri();
                // Try to find a local profile contact
                if (state.getValues().getAsString(RawContacts.ACCOUNT_TYPE) == null) {
                    localProfileExists = true;
                }
            }
            // Editor should always present a local profile for editing
            if (!localProfileExists) {
                final ContentValues values = new ContentValues();
                values.putNull(RawContacts.ACCOUNT_NAME);
                values.putNull(RawContacts.ACCOUNT_TYPE);
                values.putNull(RawContacts.DATA_SET);
                EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
                insert.setProfileQueryUri();
                mState.add(insert);
            }
        }
        mRequestFocus = true;

        bindEditors();
    }

    /**
     * Merges extras from the intent.
     */
    public void setIntentExtras(Bundle extras) {
        if (extras == null || extras.size() == 0) {
            return;
        }

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        for (EntityDelta state : mState) {
            final String accountType = state.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final String dataSet = state.getValues().getAsString(RawContacts.DATA_SET);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            if (type.areContactsWritable()) {
                // Apply extras to the first writable raw contact only
                EntityModifier.parseExtras(mContext, type, state, extras);
                break;
            }
        }
    }

    private void selectAccountAndCreateContact() {
        // If this is a local profile, then skip the logic about showing the accounts changed
        // activity and create a phone-local contact.
        if (mNewLocalProfile) {
            createContact(null);
            return;
        }

        // If there is no default account or the accounts have changed such that we need to
        // prompt the user again, then launch the account prompt.
        if (mEditorUtils.shouldShowAccountChangedNotification()) {
            Intent intent = new Intent(mContext, ContactEditorAccountsChangedActivity.class);
            mStatus = Status.SUB_ACTIVITY;
            startActivityForResult(intent, REQUEST_CODE_ACCOUNTS_CHANGED);
        } else {
            // Otherwise, there should be a default account. Then either create a local contact
            // (if default account is null) or create a contact with the specified account.
            AccountWithDataSet defaultAccount = mEditorUtils.getDefaultAccount();
            if (defaultAccount == null) {
                createContact(null);
            } else {
                createContact(defaultAccount);
            }
        }
    }

    /**
     * Create a contact by automatically selecting the first account. If there's no available
     * account, a device-local contact should be created.
     */
    private void createContact() {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(mContext).getAccounts(true);
        // No Accounts available. Create a phone-local contact.
        if (accounts.isEmpty()) {
            createContact(null);
            return;
        }

        // We have an account switcher in "create-account" screen, so don't need to ask a user to
        // select an account here.
        createContact(accounts.get(0));
    }

    /**
     * Shows account creation screen associated with a given account.
     *
     * @param account may be null to signal a device-local contact should be created.
     */
    private void createContact(AccountWithDataSet account) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        final AccountType accountType =
                accountTypes.getAccountType(account != null ? account.type : null,
                        account != null ? account.dataSet : null);

        if (accountType.getCreateContactActivityClassName() != null) {
            if (mListener != null) {
                mListener.onCustomCreateContactActivityRequested(account, mIntentExtras);
            }
        } else {
            bindEditorsForNewContact(account, accountType);
        }
    }

    /**
     * Removes a current editor ({@link #mState}) and rebinds new editor for a new account.
     * Some of old data are reused with new restriction enforced by the new account.
     *
     * @param oldState Old data being edited.
     * @param oldAccount Old account associated with oldState.
     * @param newAccount New account to be used.
     */
    private void rebindEditorsForNewContact(
            EntityDelta oldState, AccountWithDataSet oldAccount, AccountWithDataSet newAccount) {
        AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        AccountType oldAccountType = accountTypes.getAccountType(
                oldAccount.type, oldAccount.dataSet);
        AccountType newAccountType = accountTypes.getAccountType(
                newAccount.type, newAccount.dataSet);

        if (newAccountType.getCreateContactActivityClassName() != null) {
            Log.w(TAG, "external activity called in rebind situation");
            if (mListener != null) {
                mListener.onCustomCreateContactActivityRequested(newAccount, mIntentExtras);
            }
        } else {
            mState = null;
            bindEditorsForNewContact(newAccount, newAccountType, oldState, oldAccountType);
        }
    }

    private void bindEditorsForNewContact(AccountWithDataSet account,
            final AccountType accountType) {
        bindEditorsForNewContact(account, accountType, null, null);
    }

    private void bindEditorsForNewContact(AccountWithDataSet newAccount,
            final AccountType newAccountType, EntityDelta oldState, AccountType oldAccountType) {
        mStatus = Status.EDITING;

        final ContentValues values = new ContentValues();
        if (newAccount != null) {
            values.put(RawContacts.ACCOUNT_NAME, newAccount.name);
            values.put(RawContacts.ACCOUNT_TYPE, newAccount.type);
            values.put(RawContacts.DATA_SET, newAccount.dataSet);
        } else {
            values.putNull(RawContacts.ACCOUNT_NAME);
            values.putNull(RawContacts.ACCOUNT_TYPE);
            values.putNull(RawContacts.DATA_SET);
        }

        EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
        if (oldState == null) {
            // Parse any values from incoming intent
            EntityModifier.parseExtras(mContext, newAccountType, insert, mIntentExtras);
        } else {
            EntityModifier.migrateStateForNewContact(mContext, oldState, insert,
                    oldAccountType, newAccountType);
        }

        // Ensure we have some default fields (if the account type does not support a field,
        // ensureKind will not add it, so it is safe to add e.g. Event)
        EntityModifier.ensureKindExists(insert, newAccountType, Phone.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, newAccountType, Email.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, newAccountType, Organization.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, newAccountType, Event.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, newAccountType, StructuredPostal.CONTENT_ITEM_TYPE);

        // Set the correct URI for saving the contact as a profile
        if (mNewLocalProfile) {
            insert.setProfileQueryUri();
        }

        if (mState == null) {
            // Create state if none exists yet
            mState = EntityDeltaList.fromSingle(insert);
        } else {
            // Add contact onto end of existing state
            mState.add(insert);
        }

        mRequestFocus = true;

        bindEditors();
    }

    private void bindEditors() {
        // Sort the editors
        Collections.sort(mState, mComparator);

        // Remove any existing editors and rebuild any visible
        mContent.removeAllViews();

        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        int numRawContacts = mState.size();
        for (int i = 0; i < numRawContacts; i++) {
            // TODO ensure proper ordering of entities in the list
            final EntityDelta entity = mState.get(i);
            final ValuesDelta values = entity.getValues();
            if (!values.isVisible()) continue;

            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final String dataSet = values.getAsString(RawContacts.DATA_SET);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            final long rawContactId = values.getAsLong(RawContacts._ID);

            final BaseRawContactEditorView editor;
            if (!type.areContactsWritable()) {
                editor = (BaseRawContactEditorView) inflater.inflate(
                        R.layout.raw_contact_readonly_editor_view, mContent, false);
                ((RawContactReadOnlyEditorView) editor).setListener(this);
            } else {
                editor = (RawContactEditorView) inflater.inflate(R.layout.raw_contact_editor_view,
                        mContent, false);
            }
            if (Intent.ACTION_INSERT.equals(mAction) && numRawContacts == 1) {
                final List<AccountWithDataSet> accounts =
                        AccountTypeManager.getInstance(mContext).getAccounts(true);
                if (accounts.size() > 1 && !mNewLocalProfile) {
                    addAccountSwitcher(mState.get(0), editor);
                } else {
                    disableAccountSwitcher(editor);
                }
            } else {
                disableAccountSwitcher(editor);
            }

            editor.setEnabled(mEnabled);

            mContent.addView(editor);

            editor.setState(entity, type, mViewIdGenerator, isEditingUserProfile());

            editor.getPhotoEditor().setEditorListener(
                    new PhotoEditorListener(editor, type.areContactsWritable()));
            if (editor instanceof RawContactEditorView) {
                final RawContactEditorView rawContactEditor = (RawContactEditorView) editor;
                EditorListener listener = new EditorListener() {

                    @Override
                    public void onRequest(int request) {
                        if (request == EditorListener.FIELD_CHANGED && !isEditingUserProfile()) {
                            acquireAggregationSuggestions(rawContactEditor);
                        }
                    }

                    @Override
                    public void onDeleteRequested(Editor removedEditor) {
                    }
                };

                final TextFieldsEditorView nameEditor = rawContactEditor.getNameEditor();
                if (mRequestFocus) {
                    nameEditor.requestFocus();
                    mRequestFocus = false;
                }
                nameEditor.setEditorListener(listener);

                final TextFieldsEditorView phoneticNameEditor =
                        rawContactEditor.getPhoneticNameEditor();
                phoneticNameEditor.setEditorListener(listener);
                rawContactEditor.setAutoAddToDefaultGroup(mAutoAddToDefaultGroup);

                if (rawContactId == mAggregationSuggestionsRawContactId) {
                    acquireAggregationSuggestions(rawContactEditor);
                }
            }
        }

        mRequestFocus = false;

        bindGroupMetaData();

        // Show editor now that we've loaded state
        mContent.setVisibility(View.VISIBLE);

        // Refresh Action Bar as the visibility of the join command
        // Activity can be null if we have been detached from the Activity
        final Activity activity = getActivity();
        if (activity != null) activity.invalidateOptionsMenu();

    }

    private void bindGroupMetaData() {
        if (mGroupMetaData == null) {
            return;
        }

        int editorCount = mContent.getChildCount();
        for (int i = 0; i < editorCount; i++) {
            BaseRawContactEditorView editor = (BaseRawContactEditorView) mContent.getChildAt(i);
            editor.setGroupMetaData(mGroupMetaData);
        }
    }

    private void saveDefaultAccountIfNecessary() {
        // Verify that this is a newly created contact, that the contact is composed of only
        // 1 raw contact, and that the contact is not a user profile.
        if (!Intent.ACTION_INSERT.equals(mAction) && mState.size() == 1 &&
                !isEditingUserProfile()) {
            return;
        }

        // Find the associated account for this contact (retrieve it here because there are
        // multiple paths to creating a contact and this ensures we always have the correct
        // account).
        final EntityDelta entity = mState.get(0);
        final ValuesDelta values = entity.getValues();
        String name = values.getAsString(RawContacts.ACCOUNT_NAME);
        String type = values.getAsString(RawContacts.ACCOUNT_TYPE);
        String dataSet = values.getAsString(RawContacts.DATA_SET);

        AccountWithDataSet account = (name == null || type == null) ? null :
                new AccountWithDataSet(name, type, dataSet);
        mEditorUtils.saveDefaultAndAllAccounts(account);
    }

    private void addAccountSwitcher(
            final EntityDelta currentState, BaseRawContactEditorView editor) {
        ValuesDelta values = currentState.getValues();
        final AccountWithDataSet currentAccount = new AccountWithDataSet(
                values.getAsString(RawContacts.ACCOUNT_NAME),
                values.getAsString(RawContacts.ACCOUNT_TYPE),
                values.getAsString(RawContacts.DATA_SET));
        final View accountView = editor.findViewById(R.id.account);
        final View anchorView = editor.findViewById(R.id.account_container);
        accountView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(mContext, null);
                final AccountsListAdapter adapter =
                        new AccountsListAdapter(mContext,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, currentAccount);
                popup.setWidth(anchorView.getWidth());
                popup.setAnchorView(anchorView);
                popup.setAdapter(adapter);
                popup.setModal(true);
                popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        popup.dismiss();
                        AccountWithDataSet newAccount = adapter.getItem(position);
                        if (!newAccount.equals(currentAccount)) {
                            rebindEditorsForNewContact(currentState, currentAccount, newAccount);
                        }
                    }
                });
                popup.show();
            }
        });
    }

    private void disableAccountSwitcher(BaseRawContactEditorView editor) {
        // Remove the pressed state from the account header because the user cannot switch accounts
        // on an existing contact
        final View accountView = editor.findViewById(R.id.account);
        accountView.setBackgroundDrawable(null);
        accountView.setEnabled(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_contact, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // This supports the keyboard shortcut to save changes to a contact but shouldn't be visible
        // because the custom action bar contains the "save" button now (not the overflow menu).
        // TODO: Find a better way to handle shortcuts, i.e. onKeyDown()?
        menu.findItem(R.id.menu_done).setVisible(false);

        // Split only if more than one raw profile and not a user profile
        menu.findItem(R.id.menu_split).setVisible(mState != null && mState.size() > 1 &&
                !isEditingUserProfile());
        // Cannot join a user profile
        menu.findItem(R.id.menu_join).setVisible(!isEditingUserProfile());


        int size = menu.size();
        for (int i = 0; i < size; i++) {
            menu.getItem(i).setEnabled(mEnabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return save(SaveMode.CLOSE);
            case R.id.menu_discard:
                return revert();
            case R.id.menu_split:
                return doSplitContactAction();
            case R.id.menu_join:
                return doJoinContactAction();
        }
        return false;
    }

    private boolean doSplitContactAction() {
        if (!hasValidState()) return false;

        final SplitContactConfirmationDialogFragment dialog =
                new SplitContactConfirmationDialogFragment();
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), SplitContactConfirmationDialogFragment.TAG);
        return true;
    }

    private boolean doJoinContactAction() {
        if (!hasValidState()) {
            return false;
        }

        // If we just started creating a new contact and haven't added any data, it's too
        // early to do a join
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        if (mState.size() == 1 && mState.get(0).isContactInsert()
                && !EntityModifier.hasChanges(mState, accountTypes)) {
            Toast.makeText(getActivity(), R.string.toast_join_with_empty_contact,
                            Toast.LENGTH_LONG).show();
            return true;
        }

        return save(SaveMode.JOIN);
    }

    private void loadPhotoPickSize() {
        Cursor c = mContext.getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                new String[]{DisplayPhoto.DISPLAY_MAX_DIM}, null, null, null);
        try {
            c.moveToFirst();
            mPhotoPickSize = c.getInt(0);
        } finally {
            c.close();
        }
    }

    /**
     * Constructs an intent for picking a photo from Gallery, cropping it and returning the bitmap.
     */
    public Intent getPhotoPickIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", mPhotoPickSize);
        intent.putExtra("outputY", mPhotoPickSize);
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
     * Constructs an intent for capturing a photo and storing it in a temporary file.
     */
    public static Intent getTakePickIntent(File f) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        return intent;
    }

    /**
     * Sends a newly acquired photo to Gallery for cropping
     */
    protected void doCropPhoto(File f) {
        try {
            // Add the image to the media store
            MediaScannerConnection.scanFile(
                    mContext,
                    new String[] { f.getAbsolutePath() },
                    new String[] { null },
                    null);

            // Launch gallery to crop the photo
            final Intent intent = getCropImageIntent(Uri.fromFile(f));
            mStatus = Status.SUB_ACTIVITY;
            startActivityForResult(intent, REQUEST_CODE_PHOTO_PICKED_WITH_DATA);
        } catch (Exception e) {
            Log.e(TAG, "Cannot crop image", e);
            Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for image cropping.
     */
    public Intent getCropImageIntent(Uri photoUri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(photoUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", mPhotoPickSize);
        intent.putExtra("outputY", mPhotoPickSize);
        intent.putExtra("return-data", true);
        return intent;
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    public boolean save(int saveMode) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return false;
        }

        // If we are about to close the editor - there is no need to refresh the data
        if (saveMode == SaveMode.CLOSE || saveMode == SaveMode.SPLIT) {
            getLoaderManager().destroyLoader(LOADER_DATA);
        }

        mStatus = Status.SAVING;

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        if (!EntityModifier.hasChanges(mState, accountTypes)) {
            onSaveCompleted(false, saveMode, mLookupUri != null, mLookupUri);
            return true;
        }

        setEnabled(false);

        // Store account as default account, only if this is a new contact
        saveDefaultAccountIfNecessary();

        // Save contact
        Intent intent = ContactSaveService.createSaveContactIntent(getActivity(), mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                getActivity().getClass(), ContactEditorActivity.ACTION_SAVE_COMPLETED);
        getActivity().startService(intent);
        return true;
    }

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(ContactEditorFragment fragment) {
            CancelEditDialogFragment dialog = new CancelEditDialogFragment();
            dialog.setTargetFragment(fragment, 0);
            dialog.show(fragment.getFragmentManager(), "cancelEditor");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.cancel_confirmation_dialog_title)
                    .setMessage(R.string.cancel_confirmation_dialog_message)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((ContactEditorFragment)getTargetFragment()).doRevertAction();
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    private boolean revert() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        if (mState == null || !EntityModifier.hasChanges(mState, accountTypes)) {
            doRevertAction();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    private void doRevertAction() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) mListener.onReverted();
    }

    public void doSaveAction() {
        save(SaveMode.CLOSE);
    }

    public void onJoinCompleted(Uri uri) {
        onSaveCompleted(false, SaveMode.RELOAD, uri != null, uri);
    }

    public void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
            Uri contactLookupUri) {
        Log.d(TAG, "onSaveCompleted(" + saveMode + ", " + contactLookupUri);
        if (hadChanges) {
            if (saveSucceeded) {
                if (saveMode != SaveMode.JOIN) {
                    Toast.makeText(mContext, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }
        }
        switch (saveMode) {
            case SaveMode.CLOSE:
            case SaveMode.HOME:
                final Intent resultIntent;
                if (saveSucceeded && contactLookupUri != null) {
                    final String requestAuthority =
                            mLookupUri == null ? null : mLookupUri.getAuthority();

                    final String legacyAuthority = "contacts";

                    resultIntent = new Intent();
                    resultIntent.setAction(Intent.ACTION_VIEW);
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

                } else {
                    resultIntent = null;
                }
                // It is already saved, so prevent that it is saved again
                mStatus = Status.CLOSING;
                if (mListener != null) mListener.onSaveFinished(resultIntent);
                break;

            case SaveMode.RELOAD:
            case SaveMode.JOIN:
                if (saveSucceeded && contactLookupUri != null) {
                    // If it was a JOIN, we are now ready to bring up the join activity.
                    if (saveMode == SaveMode.JOIN) {
                        showJoinAggregateActivity(contactLookupUri);
                    }

                    // If this was in INSERT, we are changing into an EDIT now.
                    // If it already was an EDIT, we are changing to the new Uri now
                    mState = null;
                    load(Intent.ACTION_EDIT, contactLookupUri, null);
                    mStatus = Status.LOADING;
                    getLoaderManager().restartLoader(LOADER_DATA, null, mDataLoaderListener);
                }
                break;

            case SaveMode.SPLIT:
                mStatus = Status.CLOSING;
                if (mListener != null) {
                    mListener.onContactSplit(contactLookupUri);
                } else {
                    Log.d(TAG, "No listener registered, can not call onSplitFinished");
                }
                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
    private void showJoinAggregateActivity(Uri contactLookupUri) {
        if (contactLookupUri == null || !isAdded()) {
            return;
        }

        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
        mContactWritableForJoin = isContactWritable();
        final Intent intent = new Intent(JoinContactActivity.JOIN_CONTACT);
        intent.putExtra(JoinContactActivity.EXTRA_TARGET_CONTACT_ID, mContactIdForJoin);
        startActivityForResult(intent, REQUEST_CODE_JOIN);
    }

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
    private void joinAggregate(final long contactId) {
        Intent intent = ContactSaveService.createJoinContactsIntent(mContext, mContactIdForJoin,
                contactId, mContactWritableForJoin,
                ContactEditorActivity.class, ContactEditorActivity.ACTION_JOIN_COMPLETED);
        mContext.startService(intent);
    }

    /**
     * Returns true if there is at least one writable raw contact in the current contact.
     */
    private boolean isContactWritable() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        int size = mState.size();
        for (int i = 0; i < size; i++) {
            ValuesDelta values = mState.get(i).getValues();
            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final String dataSet = values.getAsString(RawContacts.DATA_SET);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            if (type.areContactsWritable()) {
                return true;
            }
        }
        return false;
    }

    private boolean isEditingUserProfile() {
        return mNewLocalProfile || mIsUserProfile;
    }

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete (unless it was a new contact)
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         * The editor tries best to chose the most natural contact here.
         */
        void onContactSplit(Uri newLookupUri);

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(Intent resultIntent);

        /**
         * User switched to editing a different contact (a suggestion from the
         * aggregation engine).
         */
        void onEditOtherContactRequested(
                Uri contactLookupUri, ArrayList<ContentValues> contentValues);

        /**
         * Contact is being created for an external account that provides its own
         * new contact activity.
         */
        void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                Bundle intentExtras);

        /**
         * The edited raw contact belongs to an external account that provides
         * its own edit activity.
         *
         * @param redirect indicates that the current editor should be closed
         *            before the custom editor is shown.
         */
        void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri,
                Bundle intentExtras, boolean redirect);
    }

    private class EntityDeltaComparator implements Comparator<EntityDelta> {
        /**
         * Compare EntityDeltas for sorting the stack of editors.
         */
        @Override
        public int compare(EntityDelta one, EntityDelta two) {
            // Check direct equality
            if (one.equals(two)) {
                return 0;
            }

            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
            String accountType1 = one.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet1 = one.getValues().getAsString(RawContacts.DATA_SET);
            final AccountType type1 = accountTypes.getAccountType(accountType1, dataSet1);
            String accountType2 = two.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet2 = two.getValues().getAsString(RawContacts.DATA_SET);
            final AccountType type2 = accountTypes.getAccountType(accountType2, dataSet2);

            // Check read-only
            if (!type1.areContactsWritable() && type2.areContactsWritable()) {
                return 1;
            } else if (type1.areContactsWritable() && !type2.areContactsWritable()) {
                return -1;
            }

            // Check account type
            boolean skipAccountTypeCheck = false;
            boolean isGoogleAccount1 = type1 instanceof GoogleAccountType;
            boolean isGoogleAccount2 = type2 instanceof GoogleAccountType;
            if (isGoogleAccount1 && !isGoogleAccount2) {
                return -1;
            } else if (!isGoogleAccount1 && isGoogleAccount2) {
                return 1;
            } else if (isGoogleAccount1 && isGoogleAccount2){
                skipAccountTypeCheck = true;
            }

            int value;
            if (!skipAccountTypeCheck) {
                if (type1.accountType == null) {
                    return 1;
                }
                value = type1.accountType.compareTo(type2.accountType);
                if (value != 0) {
                    return value;
                } else {
                    // Fall back to data set.
                    if (type1.dataSet != null) {
                        value = type1.dataSet.compareTo(type2.dataSet);
                        if (value != 0) {
                            return value;
                        }
                    } else if (type2.dataSet != null) {
                        return 1;
                    }
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
     * Returns the contact ID for the currently edited contact or 0 if the contact is new.
     */
    protected long getContactId() {
        if (mState != null) {
            for (EntityDelta rawContact : mState) {
                Long contactId = rawContact.getValues().getAsLong(RawContacts.CONTACT_ID);
                if (contactId != null) {
                    return contactId;
                }
            }
        }
        return 0;
    }

    /**
     * Triggers an asynchronous search for aggregation suggestions.
     */
    public void acquireAggregationSuggestions(RawContactEditorView rawContactEditor) {
        long rawContactId = rawContactEditor.getRawContactId();
        if (mAggregationSuggestionsRawContactId != rawContactId
                && mAggregationSuggestionView != null) {
            mAggregationSuggestionView.setVisibility(View.GONE);
            mAggregationSuggestionView = null;
            mAggregationSuggestionEngine.reset();
        }

        mAggregationSuggestionsRawContactId = rawContactId;

        if (mAggregationSuggestionEngine == null) {
            mAggregationSuggestionEngine = new AggregationSuggestionEngine(getActivity());
            mAggregationSuggestionEngine.setListener(this);
            mAggregationSuggestionEngine.start();
        }

        mAggregationSuggestionEngine.setContactId(getContactId());

        LabeledEditorView nameEditor = rawContactEditor.getNameEditor();
        mAggregationSuggestionEngine.onNameChange(nameEditor.getValues());
    }

    @Override
    public void onAggregationSuggestionChange() {
        if (!isAdded() || mState == null || mStatus != Status.EDITING) {
            return;
        }

        if (mAggregationSuggestionPopup != null && mAggregationSuggestionPopup.isShowing()) {
            mAggregationSuggestionPopup.dismiss();
        }

        if (mAggregationSuggestionEngine.getSuggestedContactCount() == 0) {
            return;
        }

        final RawContactEditorView rawContactView =
                (RawContactEditorView)getRawContactEditorView(mAggregationSuggestionsRawContactId);
        if (rawContactView == null) {
            return; // Raw contact deleted?
        }
        final View anchorView = rawContactView.findViewById(R.id.anchor_view);
        mAggregationSuggestionPopup = new ListPopupWindow(mContext, null);
        mAggregationSuggestionPopup.setAnchorView(anchorView);
        mAggregationSuggestionPopup.setWidth(anchorView.getWidth());
        mAggregationSuggestionPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        mAggregationSuggestionPopup.setModal(true);
        mAggregationSuggestionPopup.setAdapter(
                new AggregationSuggestionAdapter(getActivity(),
                        mState.size() == 1 && mState.get(0).isContactInsert(),
                        this, mAggregationSuggestionEngine.getSuggestions()));
        mAggregationSuggestionPopup.setOnItemClickListener(mAggregationSuggestionItemClickListener);
        mAggregationSuggestionPopup.show();
    }

    @Override
    public void onJoinAction(long contactId, List<Long> rawContactIdList) {
        long rawContactIds[] = new long[rawContactIdList.size()];
        for (int i = 0; i < rawContactIds.length; i++) {
            rawContactIds[i] = rawContactIdList.get(i);
        }
        JoinSuggestedContactDialogFragment dialog =
                new JoinSuggestedContactDialogFragment();
        Bundle args = new Bundle();
        args.putLongArray("rawContactIds", rawContactIds);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, 0);
        try {
            dialog.show(getFragmentManager(), "join");
        } catch (Exception ex) {
            // No problem - the activity is no longer available to display the dialog
        }
    }

    public static class JoinSuggestedContactDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.aggregation_suggestion_join_dialog_title)
                    .setMessage(R.string.aggregation_suggestion_join_dialog_message)
                    .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ContactEditorFragment targetFragment =
                                        (ContactEditorFragment) getTargetFragment();
                                long rawContactIds[] =
                                        getArguments().getLongArray("rawContactIds");
                                targetFragment.doJoinSuggestedContact(rawContactIds);
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.no, null)
                    .create();
        }
    }

    /**
     * Joins the suggested contact (specified by the id's of constituent raw
     * contacts), save all changes, and stay in the editor.
     */
    protected void doJoinSuggestedContact(long[] rawContactIds) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return;
        }

        mState.setJoinWithRawContacts(rawContactIds);
        save(SaveMode.RELOAD);
    }

    @Override
    public void onEditAction(Uri contactLookupUri) {
        SuggestionEditConfirmationDialogFragment dialog =
                new SuggestionEditConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("contactUri", contactLookupUri);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "edit");
    }

    public static class SuggestionEditConfirmationDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.aggregation_suggestion_edit_dialog_title)
                    .setMessage(R.string.aggregation_suggestion_edit_dialog_message)
                    .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ContactEditorFragment targetFragment =
                                        (ContactEditorFragment) getTargetFragment();
                                Uri contactUri =
                                        getArguments().getParcelable("contactUri");
                                targetFragment.doEditSuggestedContact(contactUri);
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.no, null)
                    .create();
        }
    }

    /**
     * Abandons the currently edited contact and switches to editing the suggested
     * one, transferring all the data there
     */
    protected void doEditSuggestedContact(Uri contactUri) {
        if (mListener != null) {
            // make sure we don't save this contact when closing down
            mStatus = Status.CLOSING;
            mListener.onEditOtherContactRequested(
                    contactUri, mState.get(0).getContentValues());
        }
    }

    public void setAggregationSuggestionViewEnabled(boolean enabled) {
        if (mAggregationSuggestionView == null) {
            return;
        }

        LinearLayout itemList = (LinearLayout) mAggregationSuggestionView.findViewById(
                R.id.aggregation_suggestions);
        int count = itemList.getChildCount();
        for (int i = 0; i < count; i++) {
            itemList.getChildAt(i).setEnabled(enabled);
        }
    }

    /**
     * Computes bounds of the supplied view relative to its ascendant.
     */
    private Rect getRelativeBounds(View ascendant, View view) {
        Rect rect = new Rect();
        rect.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());

        View parent = (View) view.getParent();
        while (parent != ascendant) {
            rect.offset(parent.getLeft(), parent.getTop());
            parent = (View) parent.getParent();
        }
        return rect;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_URI, mLookupUri);
        outState.putString(KEY_ACTION, mAction);

        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }

        outState.putLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO, mRawContactIdRequestingPhoto);
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);
        if (mCurrentPhotoFile != null) {
            outState.putString(KEY_CURRENT_PHOTO_FILE, mCurrentPhotoFile.toString());
        }
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);
        outState.putBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN, mContactWritableForJoin);
        outState.putLong(KEY_SHOW_JOIN_SUGGESTIONS, mAggregationSuggestionsRawContactId);
        outState.putBoolean(KEY_ENABLED, mEnabled);
        outState.putBoolean(KEY_NEW_LOCAL_PROFILE, mNewLocalProfile);
        outState.putBoolean(KEY_IS_USER_PROFILE, mIsUserProfile);
        outState.putInt(KEY_STATUS, mStatus);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mStatus == Status.SUB_ACTIVITY) {
            mStatus = Status.EDITING;
        }

        switch (requestCode) {
            case REQUEST_CODE_PHOTO_PICKED_WITH_DATA: {
                // Ignore failed requests
                if (resultCode != Activity.RESULT_OK) return;
                // As we are coming back to this view, the editor will be reloaded automatically,
                // which will cause the photo that is set here to disappear. To prevent this,
                // we remember to set a flag which is interpreted after loading.
                // This photo is set here already to reduce flickering.
                mPhoto = data.getParcelableExtra("data");
                setPhoto(mRawContactIdRequestingPhoto, mPhoto);
                mRawContactIdRequestingPhotoAfterLoad = mRawContactIdRequestingPhoto;
                mRawContactIdRequestingPhoto = -1;

                break;
            }
            case REQUEST_CODE_CAMERA_WITH_DATA: {
                // Ignore failed requests
                if (resultCode != Activity.RESULT_OK) return;
                doCropPhoto(mCurrentPhotoFile);
                break;
            }
            case REQUEST_CODE_JOIN: {
                // Ignore failed requests
                if (resultCode != Activity.RESULT_OK) return;
                if (data != null) {
                    final long contactId = ContentUris.parseId(data.getData());
                    joinAggregate(contactId);
                }
                break;
            }
            case REQUEST_CODE_ACCOUNTS_CHANGED: {
                // Bail if the account selector was not successful.
                if (resultCode != Activity.RESULT_OK) {
                    mListener.onReverted();
                    return;
                }
                // If there's an account specified, use it.
                if (data != null) {
                    AccountWithDataSet account = data.getParcelableExtra(Intents.Insert.ACCOUNT);
                    if (account != null) {
                        createContact(account);
                        return;
                    }
                }
                // If there isn't an account specified, then this is likely a phone-local
                // contact, so we should continue setting up the editor by automatically selecting
                // the most appropriate account.
                createContact();
                break;
            }
        }
    }

    /**
     * Sets the photo stored in mPhoto and writes it to the RawContact with the given id
     */
    private void setPhoto(long rawContact, Bitmap photo) {
        BaseRawContactEditorView requestingEditor = getRawContactEditorView(rawContact);
        if (requestingEditor != null) {
            requestingEditor.setPhotoBitmap(photo);
        } else {
            Log.w(TAG, "The contact that requested the photo is no longer present.");
        }
    }

    /**
     * Finds raw contact editor view for the given rawContactId.
     */
    public BaseRawContactEditorView getRawContactEditorView(long rawContactId) {
        for (int i = 0; i < mContent.getChildCount(); i++) {
            final View childView = mContent.getChildAt(i);
            if (childView instanceof BaseRawContactEditorView) {
                final BaseRawContactEditorView editor = (BaseRawContactEditorView) childView;
                if (editor.getRawContactId() == rawContactId) {
                    return editor;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if there is currently more than one photo on screen.
     */
    private boolean hasMoreThanOnePhoto() {
        int count = mContent.getChildCount();
        int countWithPicture = 0;
        for (int i = 0; i < count; i++) {
            final View childView = mContent.getChildAt(i);
            if (childView instanceof BaseRawContactEditorView) {
                final BaseRawContactEditorView editor = (BaseRawContactEditorView) childView;
                if (editor.hasSetPhoto()) {
                    countWithPicture++;
                    if (countWithPicture > 1) return true;
                }
            }
        }

        return false;
    }

    /**
     * The listener for the data loader
     */
    private final LoaderManager.LoaderCallbacks<ContactLoader.Result> mDataLoaderListener =
            new LoaderCallbacks<ContactLoader.Result>() {
        @Override
        public Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
            mLoaderStartTime = SystemClock.elapsedRealtime();
            return new ContactLoader(mContext, mLookupUri);
        }

        @Override
        public void onLoadFinished(Loader<ContactLoader.Result> loader, ContactLoader.Result data) {
            final long loaderCurrentTime = SystemClock.elapsedRealtime();
            Log.v(TAG, "Time needed for loading: " + (loaderCurrentTime-mLoaderStartTime));
            if (!data.isLoaded()) {
                // Item has been deleted
                Log.i(TAG, "No contact found. Closing activity");
                if (mListener != null) mListener.onContactNotFound();
                return;
            }

            mStatus = Status.EDITING;
            mLookupUri = data.getLookupUri();
            final long setDataStartTime = SystemClock.elapsedRealtime();
            setData(data);
            final long setDataEndTime = SystemClock.elapsedRealtime();

            // If we are coming back from the photo trimmer, this will be set.
            if (mRawContactIdRequestingPhotoAfterLoad != -1) {
                setPhoto(mRawContactIdRequestingPhotoAfterLoad, mPhoto);
                mRawContactIdRequestingPhotoAfterLoad = -1;
                mPhoto = null;
            }
            Log.v(TAG, "Time needed for setting UI: " + (setDataEndTime-setDataStartTime));
        }

        @Override
        public void onLoaderReset(Loader<ContactLoader.Result> loader) {
        }
    };

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, Groups.CONTENT_URI);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mGroupMetaData = data;
            bindGroupMetaData();
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    @Override
    public void onSplitContactConfirmed() {
        if (mState == null) {
            // This may happen when this Fragment is recreated by the system during users
            // confirming the split action (and thus this method is called just before onCreate()),
            // for example.
            Log.e(TAG, "mState became null during the user's confirming split action. " +
                    "Cannot perform the save action.");
            return;
        }

        mState.markRawContactsForSplitting();
        save(SaveMode.SPLIT);
    }

    private final class PhotoEditorListener
            implements EditorListener, PhotoActionPopup.Listener {
        private final BaseRawContactEditorView mEditor;
        private final boolean mAccountWritable;

        private PhotoEditorListener(BaseRawContactEditorView editor, boolean accountWritable) {
            mEditor = editor;
            mAccountWritable = accountWritable;
        }

        @Override
        public void onRequest(int request) {
            if (!hasValidState()) return;

            if (request == EditorListener.REQUEST_PICK_PHOTO) {
                // Determine mode
                final int mode;
                if (mAccountWritable) {
                    if (mEditor.hasSetPhoto()) {
                        if (hasMoreThanOnePhoto()) {
                            mode = PhotoActionPopup.MODE_PHOTO_ALLOW_PRIMARY;
                        } else {
                            mode = PhotoActionPopup.MODE_PHOTO_DISALLOW_PRIMARY;
                        }
                    } else {
                        mode = PhotoActionPopup.MODE_NO_PHOTO;
                    }
                } else {
                    if (mEditor.hasSetPhoto() && hasMoreThanOnePhoto()) {
                        mode = PhotoActionPopup.MODE_READ_ONLY_ALLOW_PRIMARY;
                    } else {
                        // Read-only and either no photo or the only photo ==> no options
                        return;
                    }
                }
                PhotoActionPopup.createPopupMenu(mContext, mEditor.getPhotoEditor(), this, mode)
                        .show();
            }
        }

        @Override
        public void onDeleteRequested(Editor removedEditor) {
            // The picture cannot be deleted, it can only be removed, which is handled by
            // onRemovePictureChosen()
        }

        /**
         * User has chosen to set the selected photo as the (super) primary photo
         */
        @Override
        public void onUseAsPrimaryChosen() {
            // Set the IsSuperPrimary for each editor
            int count = mContent.getChildCount();
            for (int i = 0; i < count; i++) {
                final View childView = mContent.getChildAt(i);
                if (childView instanceof BaseRawContactEditorView) {
                    final BaseRawContactEditorView editor = (BaseRawContactEditorView) childView;
                    final PhotoEditorView photoEditor = editor.getPhotoEditor();
                    photoEditor.setSuperPrimary(editor == mEditor);
                }
            }
        }

        /**
         * User has chosen to remove a picture
         */
        @Override
        public void onRemovePictureChosen() {
            mEditor.setPhotoBitmap(null);
        }

        /**
         * Launches Camera to take a picture and store it in a file.
         */
        @Override
        public void onTakePhotoChosen() {
            mRawContactIdRequestingPhoto = mEditor.getRawContactId();
            try {
                // Launch camera to take photo for selected contact
                PHOTO_DIR.mkdirs();
                mCurrentPhotoFile = new File(PHOTO_DIR, getPhotoFileName());
                final Intent intent = getTakePickIntent(mCurrentPhotoFile);

                mStatus = Status.SUB_ACTIVITY;
                startActivityForResult(intent, REQUEST_CODE_CAMERA_WITH_DATA);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText,
                        Toast.LENGTH_LONG).show();
            }
        }

        /**
         * Launches Gallery to pick a photo.
         */
        @Override
        public void onPickFromGalleryChosen() {
            mRawContactIdRequestingPhoto = mEditor.getRawContactId();
            try {
                // Launch picker to choose photo for selected contact
                final Intent intent = getPhotoPickIntent();
                mStatus = Status.SUB_ACTIVITY;
                startActivityForResult(intent, REQUEST_CODE_PHOTO_PICKED_WITH_DATA);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
