/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.group;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.GroupEditorActivity;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.group.SuggestedMemberListAdapter.SuggestedMember;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.internal.util.Objects;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// TODO: Use savedInstanceState
public class GroupEditorFragment extends Fragment implements SelectAccountDialogFragment.Listener {

    private static final String TAG = "GroupEditorFragment";

    private static final String LEGACY_CONTACTS_AUTHORITY = "contacts";

    public static interface Listener {
        /**
         * Group metadata was not found, close the fragment now.
         */
        public void onGroupNotFound();

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Title has been determined.
         */
        void onTitleLoaded(int resourceId);

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(int resultCode, Intent resultIntent);
    }

    private static final int LOADER_GROUP_METADATA = 1;
    private static final int LOADER_EXISTING_MEMBERS = 2;
    private static final int LOADER_NEW_GROUP_MEMBER = 3;

    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";

    private static final String MEMBER_RAW_CONTACT_ID_KEY = "rawContactId";
    private static final String MEMBER_LOOKUP_URI_KEY = "memberLookupUri";

    protected static final String[] PROJECTION_CONTACT = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.CONTACT_CHAT_CAPABILITY,       // 6
        Contacts.PHOTO_ID,                      // 7
        Contacts.PHOTO_THUMBNAIL_URI,           // 8
        Contacts.LOOKUP_KEY,                    // 9
        Contacts.PHONETIC_NAME,                 // 10
        Contacts.HAS_PHONE_NUMBER,              // 11
        Contacts.IS_USER_PROFILE,               // 12
    };

    protected static final int CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    protected static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    protected static final int CONTACT_CHAT_CAPABILITY_COLUMN_INDEX = 6;
    protected static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int CONTACT_PHOTO_URI_COLUMN_INDEX = 8;
    protected static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 9;
    protected static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 10;
    protected static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 11;
    protected static final int CONTACT_IS_USER_PROFILE = 12;

    /**
     * Modes that specify the status of the editor
     */
    public enum Status {
        LOADING,    // Loader is fetching the data
        EDITING,    // Not currently busy. We are waiting forthe user to enter data.
        SAVING,     // Data is currently being saved
        CLOSING     // Prevents any more saves
    }

    private Context mContext;
    private String mAction;
    private Bundle mIntentExtras;
    private Uri mGroupUri;
    private long mGroupId;
    private Listener mListener;

    private Status mStatus;

    private View mRootView;
    private ListView mListView;
    private LayoutInflater mLayoutInflater;

    private EditText mGroupNameView;
    private ImageView mAccountIcon;
    private TextView mAccountTypeTextView;
    private TextView mAccountNameTextView;
    private AutoCompleteTextView mAutoCompleteTextView;

    private boolean mGroupNameIsReadOnly;
    private String mAccountName;
    private String mAccountType;
    private String mOriginalGroupName = "";

    private MemberListAdapter mMemberListAdapter;
    private ContactPhotoManager mPhotoManager;

    private ContentResolver mContentResolver;
    private SuggestedMemberListAdapter mAutoCompleteAdapter;

    private List<Member> mListMembersToAdd = new ArrayList<Member>();
    private List<Member> mListMembersToRemove = new ArrayList<Member>();
    private List<Member> mListToDisplay = new ArrayList<Member>();

    public GroupEditorFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);

        mLayoutInflater = inflater;
        mRootView = inflater.inflate(R.layout.group_editor_fragment, container, false);

        mGroupNameView = (EditText) mRootView.findViewById(R.id.group_name);
        mAccountIcon = (ImageView) mRootView.findViewById(R.id.account_icon);
        mAccountTypeTextView = (TextView) mRootView.findViewById(R.id.account_type);
        mAccountNameTextView = (TextView) mRootView.findViewById(R.id.account_name);
        mAutoCompleteTextView = (AutoCompleteTextView) mRootView.findViewById(
                R.id.add_member_field);

        mListView = (ListView) mRootView.findViewById(android.R.id.list);
        mListView.setAdapter(mMemberListAdapter);

        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mPhotoManager = ContactPhotoManager.getInstance(mContext);
        mMemberListAdapter = new MemberListAdapter();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Edit an existing group
        if (Intent.ACTION_EDIT.equals(mAction)) {
            if (mListener != null) {
                mListener.onTitleLoaded(R.string.editGroup_title_edit);
            }
            getLoaderManager().initLoader(LOADER_GROUP_METADATA, null,
                    mGroupMetaDataLoaderListener);
        } else if (Intent.ACTION_INSERT.equals(mAction)) {
            if (mListener != null) {
                mListener.onTitleLoaded(R.string.editGroup_title_insert);
            }

            final Account account = mIntentExtras == null ? null :
                    (Account) mIntentExtras.getParcelable(Intents.Insert.ACCOUNT);

            if (account != null) {
                // Account specified in Intent
                mAccountName = account.name;
                mAccountType = account.type;
                setupEditorForAccount();
            } else {
                // No Account specified. Let the user choose from a disambiguation dialog.
                selectAccountAndCreateGroup();
            }
        } else {
            throw new IllegalArgumentException("Unknown Action String " + mAction +
                    ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT);
        }
    }

    public void setContentResolver(ContentResolver resolver) {
        mContentResolver = resolver;
        if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.setContentResolver(mContentResolver);
        }
    }

    private void selectAccountAndCreateGroup() {
        final ArrayList<Account> accounts =
                AccountTypeManager.getInstance(mContext).getAccounts(true /* writeable */);
        // No Accounts available
        if (accounts.isEmpty()) {
            throw new IllegalStateException("No accounts were found.");
        }

        // In the common case of a single account being writable, auto-select
        // it without showing a dialog.
        if (accounts.size() == 1) {
            mAccountName = accounts.get(0).name;
            mAccountType = accounts.get(0).type;
            setupEditorForAccount();
            return;  // Don't show a dialog.
        }

        final SelectAccountDialogFragment dialog = new SelectAccountDialogFragment(
                R.string.dialog_new_group_account);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), SelectAccountDialogFragment.TAG);
    }

    @Override
    public void onAccountChosen(int requestCode, Account account) {
        mAccountName = account.name;
        mAccountType = account.type;
        setupEditorForAccount();
    }

    @Override
    public void onAccountSelectorCancelled() {
        if (mListener != null) {
            // Exit the fragment because we cannot continue without selecting an account
            mListener.onGroupNotFound();
        }
    }

    /**
     * Sets up the editor based on the group's account name and type.
     */
    private void setupEditorForAccount() {
        // Setup the account header
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(mContext);
        final AccountType accountType = accountTypeManager.getAccountType(mAccountType);
        CharSequence accountTypeDisplayLabel = accountType.getDisplayLabel(mContext);
        if (!TextUtils.isEmpty(mAccountName)) {
            mAccountNameTextView.setText(
                    mContext.getString(R.string.from_account_format, mAccountName));
        }
        mAccountTypeTextView.setText(accountTypeDisplayLabel);
        mAccountIcon.setImageDrawable(accountType.getDisplayIcon(mContext));

        // Setup the autocomplete adapter (for contacts to suggest to add to the group) based on the
        // account name and type
        mAutoCompleteAdapter = new SuggestedMemberListAdapter(mContext,
                android.R.layout.simple_dropdown_item_1line);
        mAutoCompleteAdapter.setContentResolver(mContentResolver);
        mAutoCompleteAdapter.setAccountType(mAccountType);
        mAutoCompleteAdapter.setAccountName(mAccountName);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
        mAutoCompleteTextView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SuggestedMember member = mAutoCompleteAdapter.getItem(position);
                loadMemberToAddToGroup(member.getRawContactId(),
                        String.valueOf(member.getContactId()));

                // Update the autocomplete adapter so the contact doesn't get suggested again
                mAutoCompleteAdapter.addNewMember(member.getContactId());

                // Clear out the text field
                mAutoCompleteTextView.setText("");
            }
        });

        mStatus = Status.EDITING;
    }

    public void load(String action, Uri groupUri, Bundle intentExtras) {
        mAction = action;
        mGroupUri = groupUri;
        mGroupId = (groupUri != null) ? ContentUris.parseId(mGroupUri) : 0;
        mIntentExtras = intentExtras;
    }

    private void bindGroupMetaData(Cursor cursor) {
        if (cursor.getCount() == 0) {
            if (mListener != null) {
                mListener.onGroupNotFound();
            }
        }
        try {
            cursor.moveToFirst();
            mOriginalGroupName = cursor.getString(GroupMetaDataLoader.TITLE);
            mAccountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            mAccountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            mGroupNameIsReadOnly = (cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1);
        } catch (Exception e) {
            Log.i(TAG, "Group not found with URI: " + mGroupUri + " Closing activity now.");
            if (mListener != null) {
                mListener.onGroupNotFound();
            }
        } finally {
            cursor.close();
        }
        // Setup the group metadata display (If the group name is ready only, don't let the user
        // focus on the field).
        mGroupNameView.setText(mOriginalGroupName);
        mGroupNameView.setFocusable(!mGroupNameIsReadOnly);
        setupEditorForAccount();
    }

    public void loadMemberToAddToGroup(long rawContactId, String contactId) {
        Bundle args = new Bundle();
        args.putLong(MEMBER_RAW_CONTACT_ID_KEY, rawContactId);
        args.putString(MEMBER_LOOKUP_URI_KEY, contactId);
        getLoaderManager().restartLoader(LOADER_NEW_GROUP_MEMBER, args, mContactLoaderListener);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_group, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return save(SaveMode.CLOSE);
            case R.id.menu_discard:
                return revert();
        }
        return false;
    }

    private boolean revert() {
        if (!hasNameChange() && !hasMembershipChange()) {
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

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(GroupEditorFragment fragment) {
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
                    .setPositiveButton(R.string.discard,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((GroupEditorFragment) getTargetFragment()).doRevertAction();
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    /**
     * Saves or creates the group based on the mode, and if successful
     * finishes the activity. This actually only handles saving the group name.
     * @return true when successful
     */
    public boolean save(int saveMode) {
        if (!hasValidGroupName() || mStatus != Status.EDITING) {
            return false;
        }

        // If we are about to close the editor - there is no need to refresh the data
        if (saveMode == SaveMode.CLOSE) {
            getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);
        }

        // If there are no changes, then go straight to onSaveCompleted()
        if (!hasNameChange() && !hasMembershipChange()) {
            onSaveCompleted(false, SaveMode.CLOSE, mGroupUri);
            return true;
        }

        mStatus = Status.SAVING;

        Activity activity = getActivity();
        // If the activity is not there anymore, then we can't continue with the save process.
        if (activity == null) {
            return false;
        }
        Intent saveIntent = null;
        if (mAction == Intent.ACTION_INSERT) {
            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToAddArray = convertToArray(mListMembersToAdd);

            // Create the save intent to create the group and add members at the same time
            saveIntent = ContactSaveService.createNewGroupIntent(activity,
                    new Account(mAccountName, mAccountType), mGroupNameView.getText().toString(),
                    membersToAddArray, activity.getClass(),
                    GroupEditorActivity.ACTION_SAVE_COMPLETED);
        } else if (mAction == Intent.ACTION_EDIT) {
            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToAddArray = convertToArray(mListMembersToAdd);

            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToRemoveArray = convertToArray(mListMembersToRemove);

            // Create the update intent (which includes the updated group name if necessary)
            saveIntent = ContactSaveService.createGroupUpdateIntent(activity, mGroupId,
                    getUpdatedName(), membersToAddArray, membersToRemoveArray,
                    activity.getClass(), GroupEditorActivity.ACTION_SAVE_COMPLETED);
        } else {
            throw new IllegalStateException("Invalid intent action type " + mAction);
        }
        activity.startService(saveIntent);
        return true;
    }

    public void onSaveCompleted(boolean hadChanges, int saveMode, Uri groupUri) {
        boolean success = groupUri != null;
        Log.d(TAG, "onSaveCompleted(" + saveMode + ", " + groupUri + ")");
        if (hadChanges) {
            Toast.makeText(mContext, success ? R.string.groupSavedToast :
                    R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
        }
        switch (saveMode) {
            case SaveMode.CLOSE:
            case SaveMode.HOME:
                final Intent resultIntent;
                final int resultCode;
                if (success && groupUri != null) {
                    final String requestAuthority =
                            groupUri == null ? null : groupUri.getAuthority();

                    resultIntent = new Intent();
                    if (LEGACY_CONTACTS_AUTHORITY.equals(requestAuthority)) {
                        // Build legacy Uri when requested by caller
                        final long groupId = ContentUris.parseId(groupUri);
                        final Uri legacyContentUri = Uri.parse("content://contacts/groups");
                        final Uri legacyUri = ContentUris.withAppendedId(
                                legacyContentUri, groupId);
                        resultIntent.setData(legacyUri);
                    } else {
                        // Otherwise pass back the given Uri
                        resultIntent.setData(groupUri);
                    }

                    resultCode = Activity.RESULT_OK;
                } else {
                    resultCode = Activity.RESULT_CANCELED;
                    resultIntent = null;
                }
                // It is already saved, so prevent that it is saved again
                mStatus = Status.CLOSING;
                if (mListener != null) {
                    mListener.onSaveFinished(resultCode, resultIntent);
                }
                break;
            case SaveMode.RELOAD:
                // TODO: Handle reloading the group list
            default:
                throw new IllegalStateException("Unsupported save mode " + saveMode);
        }
    }

    private boolean hasValidGroupName() {
        return !TextUtils.isEmpty(mGroupNameView.getText());
    }

    private boolean hasNameChange() {
        return !mGroupNameView.getText().toString().equals(mOriginalGroupName);
    }

    private boolean hasMembershipChange() {
        return mListMembersToAdd.size() > 0 || mListMembersToRemove.size() > 0;
    }

    /**
     * Returns the group's new name or null if there is no change from the
     * original name that was loaded for the group.
     */
    private String getUpdatedName() {
        String groupNameFromTextView = mGroupNameView.getText().toString();
        if (groupNameFromTextView.equals(mOriginalGroupName)) {
            // No name change, so return null
            return null;
        }
        return groupNameFromTextView;
    }

    private static long[] convertToArray(List<Member> listMembers) {
        int size = listMembers.size();
        long[] membersArray = new long[size];
        for (int i = 0; i < size; i++) {
            membersArray[i] = listMembers.get(i).getRawContactId();
        }
        return membersArray;
    }

    private void addExistingMembers(List<Member> members, List<Long> listContactIds) {
        mListToDisplay.addAll(members);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so these contacts don't get suggested
        mAutoCompleteAdapter.updateExistingMembersList(listContactIds);
    }

    private void addMember(Member member) {
        // Update the display list
        mListMembersToAdd.add(member);
        mListToDisplay.add(member);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so the contact doesn't get suggested again
        mAutoCompleteAdapter.addNewMember(member.getContactId());
    }

    private void removeMember(Member member) {
        // If the contact was just added during this session, remove it from the list of
        // members to add
        if (mListMembersToAdd.contains(member)) {
            mListMembersToAdd.remove(member);
        } else {
            // Otherwise this contact was already part of the existing list of contacts,
            // so we need to do a content provider deletion operation
            mListMembersToRemove.add(member);
        }
        // In either case, update the UI so the contact is no longer in the list of
        // members
        mListToDisplay.remove(member);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so the contact can get suggested again
        mAutoCompleteAdapter.removeMember(member.getContactId());
    }

    /**
     * The listener for the group metadata (i.e. group name, account type, and account name) loader.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMetaDataLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            bindGroupMetaData(data);

            // Load existing members
            getLoaderManager().initLoader(LOADER_EXISTING_MEMBERS, null,
                    mGroupMemberListLoaderListener);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /**
     * The loader listener for the list of existing group members.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMemberLoader(mContext, mGroupId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            List<Long> listContactIds = new ArrayList<Long>();
            List<Member> listExistingMembers = new ArrayList<Member>();
            try {
                data.moveToPosition(-1);
                while (data.moveToNext()) {
                    long contactId = data.getLong(GroupMemberLoader.CONTACT_ID_COLUMN_INDEX);
                    long rawContactId = data.getLong(GroupMemberLoader.RAW_CONTACT_ID_COLUMN_INDEX);
                    String lookupKey = data.getString(
                            GroupMemberLoader.CONTACT_LOOKUP_KEY_COLUMN_INDEX);
                    String displayName = data.getString(
                            GroupMemberLoader.CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                    String photoUri = data.getString(
                            GroupMemberLoader.CONTACT_PHOTO_URI_COLUMN_INDEX);
                    listExistingMembers.add(new Member(rawContactId, lookupKey, contactId,
                            displayName, photoUri));
                    listContactIds.add(contactId);
                }
            } finally {
                data.close();
            }

            // Update the display list
            addExistingMembers(listExistingMembers, listContactIds);

            // No more updates
            // TODO: move to a runnable
            getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /**
     * The listener to load a summary of details for a contact.
     */
    // TODO: Remove this step because showing the aggregate contact can be confusing when the user
    // just selected a raw contact
    private final LoaderManager.LoaderCallbacks<Cursor> mContactLoaderListener =
            new LoaderCallbacks<Cursor>() {

        private long mRawContactId;

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            String memberId = args.getString(MEMBER_LOOKUP_URI_KEY);
            mRawContactId = args.getLong(MEMBER_RAW_CONTACT_ID_KEY);
            return new CursorLoader(mContext, Uri.withAppendedPath(Contacts.CONTENT_URI, memberId),
                    PROJECTION_CONTACT, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            // Retrieve the contact data fields that will be sufficient to update the adapter with
            // a new entry for this contact
            Member member = null;
            try {
                cursor.moveToFirst();
                long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
                String displayName = cursor.getString(CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                String lookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
                String photoUri = cursor.getString(CONTACT_PHOTO_URI_COLUMN_INDEX);
                getLoaderManager().destroyLoader(LOADER_NEW_GROUP_MEMBER);
                member = new Member(mRawContactId, lookupKey, contactId, displayName, photoUri);
            } finally {
                cursor.close();
            }

            if (member == null) {
                return;
            }

            // Otherwise continue adding the member to list of members
            addMember(member);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /**
     * This represents a single member of the current group.
     */
    public static class Member {
        // TODO: Switch to just dealing with raw contact IDs everywhere if possible
        private final long mRawContactId;
        private final long mContactId;
        private final Uri mLookupUri;
        private final String mDisplayName;
        private final Uri mPhotoUri;

        public Member(long rawContactId, String lookupKey, long contactId, String displayName,
                String photoUri) {
            mRawContactId = rawContactId;
            mContactId = contactId;
            mLookupUri = Contacts.getLookupUri(contactId, lookupKey);
            mDisplayName = displayName;
            mPhotoUri = (photoUri != null) ? Uri.parse(photoUri) : null;
        }

        public long getRawContactId() {
            return mRawContactId;
        }

        public long getContactId() {
            return mContactId;
        }

        public Uri getLookupUri() {
            return mLookupUri;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public Uri getPhotoUri() {
            return mPhotoUri;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Member) {
                Member otherMember = (Member) object;
                return otherMember != null && Objects.equal(mLookupUri, otherMember.getLookupUri());
            }
            return false;
        }
    }

    /**
     * This adapter displays a list of members for the current group being edited.
     */
    private final class MemberListAdapter extends BaseAdapter {

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View result;
            if (convertView == null) {
                result = mLayoutInflater.inflate(R.layout.group_member_item, parent, false);
            } else {
                result = convertView;
            }
            final Member member = getItem(position);

            QuickContactBadge badge = (QuickContactBadge) result.findViewById(R.id.badge);
            badge.assignContactUri(member.getLookupUri());

            TextView name = (TextView) result.findViewById(R.id.name);
            name.setText(member.getDisplayName());

            View deleteButton = result.findViewById(R.id.delete_button_container);
            deleteButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeMember(member);
                }
            });

            mPhotoManager.loadPhoto(badge, member.getPhotoUri());
            return result;
        }

        @Override
        public int getCount() {
            return mListToDisplay.size();
        }

        @Override
        public Member getItem(int position) {
            return mListToDisplay.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
