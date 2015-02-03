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

package com.android.contacts.editor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorAccountsChangedActivity;
import com.android.contacts.activities.ContactEditorBaseActivity;
import com.android.contacts.activities.ContactEditorBaseActivity.ContactEditor;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.HelpUtils;
import com.android.contacts.util.PhoneCapabilityTester;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Rect;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Base Fragment for contact editors.
 */
abstract public class ContactEditorBaseFragment extends Fragment implements
        ContactEditor, SplitContactConfirmationDialogFragment.Listener {

    protected static final String TAG = "ContactEditor";

    protected static final int LOADER_DATA = 1;
    protected static final int LOADER_GROUPS = 2;

    private static final String KEY_ACTION = "action";
    private static final String KEY_URI = "uri";
    private static final String KEY_AUTO_ADD_TO_DEFAULT_GROUP = "autoAddToDefaultGroup";
    private static final String KEY_DISABLE_DELETE_MENU_OPTION = "disableDeleteMenuOption";
    private static final String KEY_NEW_LOCAL_PROFILE = "newLocalProfile";

    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";

    private static final String KEY_RAW_CONTACTS = "rawContacts";

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_STATUS = "status";

    private static final String KEY_HAS_NEW_CONTACT = "hasNewContact";
    private static final String KEY_NEW_CONTACT_READY = "newContactDataReady";

    private static final String KEY_IS_EDIT = "isEdit";
    private static final String KEY_EXISTING_CONTACT_READY = "existingContactDataReady";

    // Phone option menus
    private static final String KEY_SEND_TO_VOICE_MAIL_STATE = "sendToVoicemailState";
    private static final String KEY_ARE_PHONE_OPTIONS_CHANGEABLE = "arePhoneOptionsChangable";
    private static final String KEY_CUSTOM_RINGTONE = "customRingtone";

    private static final String KEY_IS_USER_PROFILE = "isUserProfile";

    private static final String KEY_ENABLED = "enabled";

    protected static final int REQUEST_CODE_JOIN = 0;
    protected static final int REQUEST_CODE_ACCOUNTS_CHANGED = 1;
    protected static final int REQUEST_CODE_PICK_RINGTONE = 2;

    /**
     * An intent extra that forces the editor to add the edited contact
     * to the default group (e.g. "My Contacts").
     */
    public static final String INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY = "addToDefaultDirectory";

    public static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    public static final String INTENT_EXTRA_DISABLE_DELETE_MENU_OPTION =
            "disableDeleteMenuOption";

    /**
     * Callbacks for Activities that host contact editors Fragments.
     */
    public interface Listener {

        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         *
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         *                     The editor tries best to chose the most natural contact here.
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
        void onEditOtherContactRequested(Uri contactLookupUri,
                ArrayList<ContentValues> contentValues);

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
         *                 before the custom editor is shown.
         */
        void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri,
                Bundle intentExtras, boolean redirect);

        /**
         * User has requested that contact be deleted.
         */
        void onDeleteRequested(Uri contactUri);
    }

    protected Context mContext;
    protected Listener mListener;

    protected LinearLayout mContent;

    //
    // Parameters passed in on {@link #load}
    //
    protected String mAction;
    protected Uri mLookupUri;
    protected Bundle mIntentExtras;
    protected boolean mAutoAddToDefaultGroup;
    protected boolean mDisableDeleteMenuOption;
    protected boolean mNewLocalProfile;

    //
    // Helpers
    //
    protected ContactEditorUtils mEditorUtils;
    protected RawContactDeltaComparator mComparator;
    protected ViewIdGenerator mViewIdGenerator;

    //
    // Loaded data
    //
    // Used to temporarily store existing contact data during a rebind call (i.e. account switch)
    protected ImmutableList<RawContact> mRawContacts;
    protected Cursor mGroupMetaData;

    //
    // Contact editor state
    //
    protected RawContactDeltaList mState;
    protected int mStatus;

    // Whether to show the new contact blank form and if it's corresponding delta is ready.
    protected boolean mHasNewContact;
    protected boolean mNewContactDataReady;

    // Whether it's an edit of existing contact and if it's corresponding delta is ready.
    protected boolean mIsEdit;
    protected boolean mExistingContactDataReady;

    // Phone specific option menus
    private boolean mSendToVoicemailState;
    private boolean mArePhoneOptionsChangable;
    private String mCustomRingtone;

    protected boolean mIsUserProfile;

    // Whether editors and options menu items are enabled
    protected boolean mEnabled = true;

    /**
     * The contact data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<Contact> mDataLoaderListener =
            new LoaderManager.LoaderCallbacks<Contact>() {

                protected long mLoaderStartTime;

                @Override
                public Loader<Contact> onCreateLoader(int id, Bundle args) {
                    mLoaderStartTime = SystemClock.elapsedRealtime();
                    return new ContactLoader(mContext, mLookupUri, true);
                }

                @Override
                public void onLoadFinished(Loader<Contact> loader, Contact data) {
                    final long loaderCurrentTime = SystemClock.elapsedRealtime();
                    Log.v(TAG, "Time needed for loading: " + (loaderCurrentTime-mLoaderStartTime));
                    if (!data.isLoaded()) {
                        // Item has been deleted. Close activity without saving again.
                        Log.i(TAG, "No contact found. Closing activity");
                        mStatus = Status.CLOSING;
                        if (mListener != null) mListener.onContactNotFound();
                        return;
                    }

                    mStatus = Status.EDITING;
                    mLookupUri = data.getLookupUri();
                    final long setDataStartTime = SystemClock.elapsedRealtime();
                    setData(data);
                    final long setDataEndTime = SystemClock.elapsedRealtime();

                    Log.v(TAG, "Time needed for setting UI: " + (setDataEndTime - setDataStartTime));
                }

                @Override
                public void onLoaderReset(Loader<Contact> loader) {
                }
            };

    /**
     * The group meta data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public CursorLoader onCreateLoader(int id, Bundle args) {
                    return new GroupMetaDataLoader(mContext, ContactsContract.Groups.CONTENT_URI);
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                    mGroupMetaData = data;
                    bindGroupMetaData();
                }

                @Override
                public void onLoaderReset(Loader<Cursor> loader) {
                }
            };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mEditorUtils = ContactEditorUtils.getInstance(mContext);
        mComparator = new RawContactDeltaComparator(mContext);
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            // Restore mUri before calling super.onCreate so that onInitializeLoaders
            // would already have a uri and an action to work with
            mAction = savedState.getString(KEY_ACTION);
            mLookupUri = savedState.getParcelable(KEY_URI);
        }

        super.onCreate(savedState);

        if (savedState == null) {
            mViewIdGenerator = new ViewIdGenerator();
        } else {
            mViewIdGenerator = savedState.getParcelable(KEY_VIEW_ID_GENERATOR);

            mAutoAddToDefaultGroup = savedState.getBoolean(KEY_AUTO_ADD_TO_DEFAULT_GROUP);
            mDisableDeleteMenuOption = savedState.getBoolean(KEY_DISABLE_DELETE_MENU_OPTION);
            mNewLocalProfile = savedState.getBoolean(KEY_NEW_LOCAL_PROFILE);

            mRawContacts = ImmutableList.copyOf(savedState.<RawContact>getParcelableArrayList(
                    KEY_RAW_CONTACTS));
            // NOTE: mGroupMetaData is not saved/restored

            // Read state from savedState. No loading involved here
            mState = savedState.<RawContactDeltaList> getParcelable(KEY_EDIT_STATE);
            mStatus = savedState.getInt(KEY_STATUS);

            mHasNewContact = savedState.getBoolean(KEY_HAS_NEW_CONTACT);
            mNewContactDataReady = savedState.getBoolean(KEY_NEW_CONTACT_READY);

            mIsEdit = savedState.getBoolean(KEY_IS_EDIT);
            mExistingContactDataReady = savedState.getBoolean(KEY_EXISTING_CONTACT_READY);

            // Phone specific options menus
            mSendToVoicemailState = savedState.getBoolean(KEY_SEND_TO_VOICE_MAIL_STATE);
            mArePhoneOptionsChangable = savedState.getBoolean(KEY_ARE_PHONE_OPTIONS_CHANGEABLE);
            mCustomRingtone = savedState.getString(KEY_CUSTOM_RINGTONE);

            mIsUserProfile = savedState.getBoolean(KEY_IS_USER_PROFILE);

            mEnabled = savedState.getBoolean(KEY_ENABLED);
        }

        // mState can still be null because it may not have have finished loading before
        // onSaveInstanceState was called.
        if (mState == null) {
            mState = new RawContactDeltaList();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        validateAction(mAction);

        if (mState.isEmpty()) {
            // The delta list may not have finished loading before orientation change happens.
            // In this case, there will be a saved state but deltas will be missing.  Reload from
            // database.
            if (Intent.ACTION_EDIT.equals(mAction)) {
                // Either...
                // 1) orientation change but load never finished.
                // or
                // 2) not an orientation change.  data needs to be loaded for first time.
                getLoaderManager().initLoader(LOADER_DATA, null, mDataLoaderListener);
            }
        } else {
            // Orientation change, we already have mState, it was loaded by onCreate
            bindEditors();
        }

        // Handle initial actions only when existing state missing
        if (savedInstanceState == null) {
            if (Intent.ACTION_EDIT.equals(mAction)) {
                mIsEdit = true;
            } else if (Intent.ACTION_INSERT.equals(mAction)) {
                mHasNewContact = true;
                final Account account = mIntentExtras == null ? null :
                        (Account) mIntentExtras.getParcelable(Intents.Insert.EXTRA_ACCOUNT);
                final String dataSet = mIntentExtras == null ? null :
                        mIntentExtras.getString(Intents.Insert.EXTRA_DATA_SET);

                if (account != null) {
                    // Account specified in Intent
                    createContact(new AccountWithDataSet(account.name, account.type, dataSet));
                } else {
                    // No Account specified. Let the user choose
                    // Load Accounts async so that we can present them
                    selectAccountAndCreateContact();
                }
            }
        }
    }

    /**
     * Checks if the requested action is valid.
     *
     * @param action The action to test.
     * @throws IllegalArgumentException when the action is invalid.
     */
    private static void validateAction(String action) {
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_INSERT.equals(action) ||
                ContactEditorBaseActivity.ACTION_SAVE_COMPLETED.equals(action)) {
            return;
        }
        throw new IllegalArgumentException("Unknown Action String " + action +
                ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT + " or " +
                ContactEditorBaseActivity.ACTION_SAVE_COMPLETED);
    }

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_ACTION, mAction);
        outState.putParcelable(KEY_URI, mLookupUri);
        outState.putBoolean(KEY_AUTO_ADD_TO_DEFAULT_GROUP, mAutoAddToDefaultGroup);
        outState.putBoolean(KEY_DISABLE_DELETE_MENU_OPTION, mDisableDeleteMenuOption);
        outState.putBoolean(KEY_NEW_LOCAL_PROFILE, mNewLocalProfile);

        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);

        outState.putParcelableArrayList(KEY_RAW_CONTACTS, mRawContacts == null ?
                Lists.<RawContact>newArrayList() : Lists.newArrayList(mRawContacts));
        // NOTE: mGroupMetaData is not saved

        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }
        outState.putInt(KEY_STATUS, mStatus);
        outState.putBoolean(KEY_HAS_NEW_CONTACT, mHasNewContact);
        outState.putBoolean(KEY_NEW_CONTACT_READY, mNewContactDataReady);
        outState.putBoolean(KEY_IS_EDIT, mIsEdit);
        outState.putBoolean(KEY_EXISTING_CONTACT_READY, mExistingContactDataReady);

        // Phone specific options
        outState.putBoolean(KEY_SEND_TO_VOICE_MAIL_STATE, mSendToVoicemailState);
        outState.putBoolean(KEY_ARE_PHONE_OPTIONS_CHANGEABLE, mArePhoneOptionsChangable);
        outState.putString(KEY_CUSTOM_RINGTONE, mCustomRingtone);

        outState.putBoolean(KEY_IS_USER_PROFILE, mIsUserProfile);
        outState.putBoolean(KEY_ENABLED, mEnabled);

        super.onSaveInstanceState(outState);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_ACCOUNTS_CHANGED: {
                // Bail if the account selector was not successful.
                if (resultCode != Activity.RESULT_OK) {
                    mListener.onReverted();
                    return;
                }
                // If there's an account specified, use it.
                if (data != null) {
                    AccountWithDataSet account = data.getParcelableExtra(
                            Intents.Insert.EXTRA_ACCOUNT);
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
            case REQUEST_CODE_PICK_RINGTONE: {
                if (data != null) {
                    final Uri pickedUri = data.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    handleRingtonePicked(pickedUri);
                }
                break;
            }
        }
    }

    private void handleRingtonePicked(Uri pickedUri) {
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            mCustomRingtone = null;
        } else {
            mCustomRingtone = pickedUri.toString();
        }
        Intent intent = ContactSaveService.createSetRingtone(
                mContext, mLookupUri, mCustomRingtone);
        mContext.startService(intent);
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
        final MenuItem doneMenu = menu.findItem(R.id.menu_done);
        final MenuItem splitMenu = menu.findItem(R.id.menu_split);
        final MenuItem joinMenu = menu.findItem(R.id.menu_join);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);
        final MenuItem discardMenu = menu.findItem(R.id.menu_discard);
        final MenuItem sendToVoiceMailMenu = menu.findItem(R.id.menu_send_to_voicemail);
        final MenuItem ringToneMenu = menu.findItem(R.id.menu_set_ringtone);
        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        deleteMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        deleteMenu.setIcon(R.drawable.ic_delete_white_24dp);

        // Set visibility of menus
        doneMenu.setVisible(false);

        // Discard menu is only available if at least one raw contact is editable
        discardMenu.setVisible(mState != null &&
                mState.getFirstWritableRawContact(mContext) != null);

        // help menu depending on whether this is inserting or editing
        if (Intent.ACTION_INSERT.equals(mAction)) {
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_add);
            splitMenu.setVisible(false);
            joinMenu.setVisible(false);
            deleteMenu.setVisible(false);
        } else if (Intent.ACTION_EDIT.equals(mAction)) {
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_edit);
            // Split only if more than one raw profile and not a user profile
            splitMenu.setVisible(mState.size() > 1 && !isEditingUserProfile());
            // Cannot join a user profile
            joinMenu.setVisible(!isEditingUserProfile());
            deleteMenu.setVisible(!mDisableDeleteMenuOption);
        } else {
            // something else, so don't show the help menu
            helpMenu.setVisible(false);
        }

        // Hide telephony-related settings (ringtone, send to voicemail)
        // if we don't have a telephone or are editing a new contact.
        sendToVoiceMailMenu.setChecked(mSendToVoicemailState);
        sendToVoiceMailMenu.setVisible(mArePhoneOptionsChangable);
        ringToneMenu.setVisible(mArePhoneOptionsChangable);

        int size = menu.size();
        for (int i = 0; i < size; i++) {
            menu.getItem(i).setEnabled(mEnabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.menu_done:
                return save(SaveMode.CLOSE);
            case R.id.menu_discard:
                return revert();
            case R.id.menu_delete:
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            case R.id.menu_split:
                return doSplitContactAction();
            case R.id.menu_join:
                return doJoinContactAction();
            case R.id.menu_set_ringtone:
                doPickRingtone();
                return true;
            case R.id.menu_send_to_voicemail:
                // Update state and save
                mSendToVoicemailState = !mSendToVoicemailState;
                item.setChecked(mSendToVoicemailState);
                final Intent intent = ContactSaveService.createSetSendToVoicemail(
                        mContext, mLookupUri, mSendToVoicemailState);
                mContext.startService(intent);
                return true;
        }

        return false;
    }

    private boolean revert() {
        if (mState.isEmpty() || !hasPendingChanges()) {
            onSplitContactConfirmed();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    @Override
    public void onSplitContactConfirmed() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) mListener.onReverted();
    }

    private boolean doSplitContactAction() {
        if (!hasValidState()) return false;

        SplitContactConfirmationDialogFragment.show(this);
        return true;
    }

    private boolean doJoinContactAction() {
        if (!hasValidState()) {
            return false;
        }

        // If we just started creating a new contact and haven't added any data, it's too
        // early to do a join
        if (mState.size() == 1 && mState.get(0).isContactInsert() && !hasPendingChanges()) {
            Toast.makeText(mContext, R.string.toast_join_with_empty_contact,
                    Toast.LENGTH_LONG).show();
            return true;
        }

        return save(SaveMode.JOIN);
    }

    private void doPickRingtone() {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Allow the user to pick a silent ringtone
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);

        final Uri ringtoneUri;
        if (mCustomRingtone != null) {
            ringtoneUri = Uri.parse(mCustomRingtone);
        } else {
            // Otherwise pick default ringtone Uri so that something is selected.
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);

        // Launch!
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(mContext, R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    protected boolean hasValidState() {
        return mState.size() > 0;
    }

    protected boolean isEditingUserProfile() {
        return mNewLocalProfile || mIsUserProfile;
    }

    /**
     * Return true if there are any edits to the current contact which need to
     * be saved.
     */
    protected boolean hasPendingChanges() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        return RawContactModifier.hasChanges(mState, accountTypes);
    }

    //
    // Account creation
    //

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
            createContact(defaultAccount);
        }
    }

    /**
     * Create a contact by automatically selecting the first account. If there's no available
     * account, a device-local contact should be created.
     */
    protected void createContact() {
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
    protected void createContact(AccountWithDataSet account) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        final AccountType accountType = accountTypes.getAccountTypeForAccount(account);

        if (accountType.getCreateContactActivityClassName() != null) {
            if (mListener != null) {
                mListener.onCustomCreateContactActivityRequested(account, mIntentExtras);
            }
        } else {
            bindEditorsForNewContact(account, accountType);
        }
    }

    //
    // Data binding
    //

    private void setData(Contact contact) {

        // If we have already loaded data, we do not want to change it here to not confuse the user
        if (!mState.isEmpty()) {
            Log.v(TAG, "Ignoring background change. This will have to be rebased later");
            return;
        }

        // See if this edit operation needs to be redirected to a custom editor
        mRawContacts = contact.getRawContacts();
        if (mRawContacts.size() == 1) {
            RawContact rawContact = mRawContacts.get(0);
            String type = rawContact.getAccountTypeString();
            String dataSet = rawContact.getDataSet();
            AccountType accountType = rawContact.getAccountType(mContext);
            if (accountType.getEditContactActivityClassName() != null &&
                    !accountType.areContactsWritable()) {
                if (mListener != null) {
                    String name = rawContact.getAccountName();
                    long rawContactId = rawContact.getId();
                    mListener.onCustomEditContactActivityRequested(
                            new AccountWithDataSet(name, type, dataSet),
                            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                            mIntentExtras, true);
                }
                return;
            }
        }

        String displayName = null;
        // Check for writable raw contacts.  If there are none, then we need to create one so user
        // can edit.  For the user profile case, there is already an editable contact.
        if (!contact.isUserProfile() && !contact.isWritableContact(mContext)) {
            mHasNewContact = true;

            // This is potentially an asynchronous call and will add deltas to list.
            selectAccountAndCreateContact();
            displayName = contact.getDisplayName();
        }

        // This also adds deltas to list
        // If displayName is null at this point it is simply ignored later on by the editor.
        bindEditorsForExistingContact(displayName, contact.isUserProfile(),
                mRawContacts);

        bindMenuItemsForPhone(contact);
    }

    private void bindMenuItemsForPhone(Contact contact) {
        if (contact != null) {
            mSendToVoicemailState = contact.isSendToVoicemail();
            mCustomRingtone = contact.getCustomRingtone();
            mArePhoneOptionsChangable = !contact.isDirectoryEntry()
                    && PhoneCapabilityTester.isPhone(mContext);
        }
    }

    // TODO: add javadocs after these are finalized
    abstract protected void bindEditorsForExistingContact(String displayName, boolean isUserProfile,
            ImmutableList<RawContact> rawContacts);
    abstract protected void bindEditorsForNewContact(AccountWithDataSet account,
            final AccountType accountType);
    abstract protected void bindEditors();
    abstract protected void bindGroupMetaData();

    //
    // ContactEditor
    //

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        mAction = action;
        mLookupUri = lookupUri;
        mIntentExtras = intentExtras;

        if (mIntentExtras != null) {
            mAutoAddToDefaultGroup =
                    mIntentExtras.containsKey(INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY);
            mNewLocalProfile =
                    mIntentExtras.getBoolean(INTENT_EXTRA_NEW_LOCAL_PROFILE);
            mDisableDeleteMenuOption =
                    mIntentExtras.getBoolean(INTENT_EXTRA_DISABLE_DELETE_MENU_OPTION);
        }
    }

    /**
     * Returns a legacy version of the given contactLookupUri if a legacy Uri was originally
     * passed to the contact editor.
     *
     * @param contactLookupUri The Uri to possibly convert to legacy format.
     * @param requestLookupUri The lookup Uri originally passed to the contact editor
     *                         (via Intent data), may be null.
     */
    protected static Uri maybeConvertToLegacyLookupUri(Context context, Uri contactLookupUri,
            Uri requestLookupUri) {
        final String legacyAuthority = "contacts";
        final String requestAuthority = requestLookupUri == null
                ? null : requestLookupUri.getAuthority();
        if (legacyAuthority.equals(requestAuthority)) {
            // Build a legacy Uri if that is what was requested by caller
            final long contactId = ContentUris.parseId(Contacts.lookupContact(
                    context.getContentResolver(), contactLookupUri));
            final Uri legacyContentUri = Uri.parse("content://contacts/people");
            return ContentUris.withAppendedId(legacyContentUri, contactId);
        }
        // Otherwise pass back a lookup-style Uri
        return contactLookupUri;
    }

    /**
     * Creates the result Intent for the given contactLookupUri that should started after a
     * successful saving a contact.
     */
    protected static Intent composeQuickContactsIntent(Context context, Uri contactLookupUri) {
        final Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
        intent.setData(contactLookupUri);
        intent.putExtra(QuickContact.EXTRA_MODE, QuickContactActivity.MODE_FULLY_EXPANDED);
        // Make sure not to show QuickContacts on top of another QuickContacts.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
