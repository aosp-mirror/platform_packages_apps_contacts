/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.activities;

import android.accounts.Account;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.logging.ListEvent;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.list.UiIntentActions;
import com.android.contacts.quickcontact.QuickContactActivity;

import java.util.List;

/**
 * Displays the members of a group and allows the user to edit it.
 */
public class GroupMembersActivity extends ContactsDrawerActivity implements
        ActionBarAdapter.Listener,
        MultiSelectContactsListFragment.OnCheckBoxListActionListener,
        SelectAccountDialogFragment.Listener,
        GroupMembersFragment.GroupMembersListener,
        GroupNameEditDialogFragment.Listener {

    private static final String TAG = "GroupMembers";

    private static final String KEY_IS_INSERT_ACTION = "isInsertAction";
    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";
    private static final String KEY_IS_EDIT_MODE = "editMode";

    private static final String TAG_GROUP_MEMBERS = "groupMembers";
    private static final String TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    private static final String ACTION_DELETE_GROUP = "deleteGroup";
    private static final String ACTION_CREATE_GROUP = "createGroup";
    private static final String ACTION_UPDATE_GROUP = "updateGroup";
    private static final String ACTION_ADD_TO_GROUP = "addToGroup";
    private static final String ACTION_REMOVE_FROM_GROUP = "removeFromGroup";

    private static final int RESULT_GROUP_ADD_MEMBER = 100;

    /**
     * Starts an Intent to add/remove the raw contacts for the given contact IDs to/from a group.
     * Only the raw contacts that belong to the specified account are added or removed.
     */
    private static class UpdateGroupMembersAsyncTask extends AsyncTask<Void, Void, Intent> {

        static final int TYPE_ADD = 0;
        static final int TYPE_REMOVE = 1;

        private final Context mContext;
        private final int mType;
        private final long[] mContactIds;
        private final long mGroupId;
        private final String mAccountName;
        private final String mAccountType;

        private UpdateGroupMembersAsyncTask(int type, Context context, long[] contactIds,
                long groupId, String accountName, String accountType) {
            mContext = context;
            mType = type;
            mContactIds = contactIds;
            mGroupId = groupId;
            mAccountName = accountName;
            mAccountType = accountType;
        }

        @Override
        protected Intent doInBackground(Void... params) {
            final long[] rawContactIds = getRawContactIds();
            if (rawContactIds.length == 0) {
                return null;
            }
            final long[] rawContactIdsToAdd;
            final long[] rawContactIdsToRemove;
            final String action;
            if (mType == TYPE_ADD) {
                rawContactIdsToAdd = rawContactIds;
                rawContactIdsToRemove = null;
                action = GroupMembersActivity.ACTION_ADD_TO_GROUP;
            } else if (mType == TYPE_REMOVE) {
                rawContactIdsToAdd = null;
                rawContactIdsToRemove = rawContactIds;
                action = GroupMembersActivity.ACTION_REMOVE_FROM_GROUP;
            } else {
                throw new IllegalStateException("Unrecognized type " + mType);
            }
            return ContactSaveService.createGroupUpdateIntent(
                    mContext, mGroupId, /* newLabel */ null, rawContactIdsToAdd,
                    rawContactIdsToRemove, GroupMembersActivity.class, action);
        }

        // TODO(wjang): prune raw contacts that are already in the group; ContactSaveService will
        // log a warning if the raw contact is already a member and keep going but it is not ideal.
        private long[] getRawContactIds() {
            final Uri rawContactUri = RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(RawContacts.ACCOUNT_NAME, mAccountName)
                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE, mAccountType)
                    .build();
            final String[] projection = new String[]{RawContacts._ID};
            final StringBuilder selection = new StringBuilder();
            final String[] selectionArgs = new String[mContactIds.length];
            for (int i = 0; i < mContactIds.length; i++) {
                if (i > 0) {
                    selection.append(" OR ");
                }
                selection.append(RawContacts.CONTACT_ID).append("=?");
                selectionArgs[i] = Long.toString(mContactIds[i]);
            }
            final Cursor cursor = mContext.getContentResolver().query(
                    rawContactUri, projection, selection.toString(), selectionArgs, null, null);
            final long[] rawContactIds = new long[cursor.getCount()];
            try {
                int i = 0;
                while (cursor.moveToNext()) {
                    rawContactIds[i] = cursor.getLong(0);
                    i++;
                }
            } finally {
                cursor.close();
            }
            return rawContactIds;
        }

        @Override
        protected void onPostExecute(Intent intent) {
            if (intent == null) {
                Toast.makeText(mContext, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
            } else {
                mContext.startService(intent);
            }
        }
    }

    private ActionBarAdapter mActionBarAdapter;

    private GroupMembersFragment mMembersFragment;

    private Uri mGroupUri;
    private boolean mIsInsertAction;
    private boolean mIsEditMode;

    private GroupMetadata mGroupMetadata;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Parse the Intent
        if (savedState != null) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
            mIsInsertAction = savedState.getBoolean(KEY_IS_INSERT_ACTION);
            mIsEditMode = savedState.getBoolean(KEY_IS_EDIT_MODE);
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        } else {
            mGroupUri = getIntent().getData();
            mIsInsertAction = Intent.ACTION_INSERT.equals(getIntent().getAction());
        }
        if (!mIsInsertAction && mGroupUri == null) {
            setResultCanceledAndFinish(R.string.groupLoadErrorToast);
            return;
        }

        // Set up the view
        setContentView(R.layout.group_members_activity);

        // Set up the action bar
        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(),
                /* portraitTabs */ null, /* landscapeTabs */ null, mToolbar,
                R.string.enter_contact_name);
        mActionBarAdapter.setShowHomeIcon(true);

        // Avoid showing default "Contacts" title before group metadata is loaded. The title will
        // be changed to group name when onGroupMetadataLoaded() is called.
        setActionBarTitle("");

        // Decide whether to prompt for the account and group name or start loading existing members
        if (mIsInsertAction) {
            // Check if we are in the middle of the insert flow.
            if (!isSelectAccountDialogFound() && !isGroupNameEditDialogFound()) {

                // Create metadata to hold the account info
                mGroupMetadata = new GroupMetadata();

                // Select the account to create the group
                final Bundle extras = getIntent().getExtras();
                final Account account = extras == null ? null :
                        (Account) extras.getParcelable(Intents.Insert.EXTRA_ACCOUNT);
                if (account == null) {
                    selectAccount();
                } else {
                    final String dataSet = extras == null
                            ? null : extras.getString(Intents.Insert.EXTRA_DATA_SET);
                    final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                            account.name, account.type, dataSet);
                    onAccountChosen(accountWithDataSet, /* extraArgs */ null);
                }
            }
        } else {
            final FragmentManager fragmentManager = getFragmentManager();
            // Add the members list fragment
            mMembersFragment = (GroupMembersFragment)
                    fragmentManager.findFragmentByTag(TAG_GROUP_MEMBERS);
            if (mMembersFragment == null) {
                mMembersFragment = GroupMembersFragment.newInstance(getIntent().getData());
                fragmentManager.beginTransaction().replace(R.id.fragment_container_inner,
                        mMembersFragment, TAG_GROUP_MEMBERS).commitAllowingStateLoss();
            }
            mMembersFragment.setListener(this);
            if (mGroupMetadata != null && mGroupMetadata.editable) {
                mMembersFragment.setCheckBoxListListener(this);
            }
        }

        // Delay action bar initialization until after the fragment is added
        final ContactsRequest contactsRequest = new ContactsRequest();
        contactsRequest.setActionCode(ContactsRequest.ACTION_GROUP);
        mActionBarAdapter.initialize(savedState, contactsRequest);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActionBarAdapter != null) {
            mActionBarAdapter.onSaveInstanceState(outState);
        }
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
        outState.putBoolean(KEY_IS_INSERT_ACTION, mIsInsertAction);
        outState.putBoolean(KEY_IS_EDIT_MODE, mIsEditMode);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    private void selectAccount() {
        final List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(this)
                .getAccounts(/* writable */ true);
        if (accounts.isEmpty()) {
            setResultCanceledAndFinish();
            return;
        }
        // If there is a single writable account, use it w/o showing a dialog.
        if (accounts.size() == 1) {
            onAccountChosen(accounts.get(0), /* extraArgs */ null);
            return;
        }
        SelectAccountDialogFragment.show(getFragmentManager(), null,
                R.string.dialog_new_group_account, AccountListFilter.ACCOUNTS_GROUP_WRITABLE,
                /* extraArgs */ null, TAG_SELECT_ACCOUNT_DIALOG);
    }

    // Invoked with results from the ContactSaveService
    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);

        if (isDeleteAction(newIntent.getAction())) {
            toast(R.string.groupDeletedToast);
            setResult(RESULT_OK);
            finish();
        } else if (isSaveAction(newIntent.getAction())) {
            final Uri groupUri = newIntent.getData();
            if (groupUri == null) {
                setResultCanceledAndFinish(R.string.groupSavedErrorToast);
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + groupUri);

            mGroupUri = groupUri;
            mIsInsertAction = false;

            toast(getToastMessageForSaveAction(newIntent.getAction()));

            // If we're editing the group, don't reload the fragment so the user can
            // continue to remove group members one by one
            if (!mIsEditMode && !ACTION_REMOVE_FROM_GROUP.equals(newIntent.getAction())) {
                replaceGroupMembersFragment();
                invalidateOptionsMenu();
            }
        }
    }

    private static boolean isDeleteAction(String action) {
        return ACTION_DELETE_GROUP.equals(action);
    }

    private static boolean isSaveAction(String action) {
        return ACTION_CREATE_GROUP.equals(action)
                || ACTION_UPDATE_GROUP.equals(action)
                || ACTION_ADD_TO_GROUP.equals(action)
                || ACTION_REMOVE_FROM_GROUP.equals(action);
    }

    private static int getToastMessageForSaveAction(String action) {
        if (ACTION_CREATE_GROUP.equals(action)) return R.string.groupCreatedToast;
        if (ACTION_UPDATE_GROUP.equals(action)) return R.string.groupUpdatedToast;
        if (ACTION_ADD_TO_GROUP.equals(action)) return R.string.groupMembersAddedToast;
        if (ACTION_REMOVE_FROM_GROUP.equals(action)) return R.string.groupMembersRemovedToast;
        throw new IllegalArgumentException("Unhanded contact save action " + action);
    }

    private void replaceGroupMembersFragment() {
        mMembersFragment = GroupMembersFragment.newInstance(mGroupUri);
        mMembersFragment.setListener(this);
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        addGroupsAndFiltersFragments(transaction);
        transaction.replace(R.id.fragment_container_inner, mMembersFragment, TAG_GROUP_MEMBERS)
                .commitAllowingStateLoss();
        if (mGroupMetadata != null && mGroupMetadata.editable) {
            mMembersFragment.setCheckBoxListListener(this);
        }
    }

    @Override
    protected void onGroupMenuItemClicked(long groupId) {
        if (mGroupMetadata.groupId != groupId) {
            super.onGroupMenuItemClicked(groupId);
        }
    }

    @Override
    protected boolean shouldFinish() {
        return true;
    }

    @Override
    protected void launchFindDuplicates() {
        super.launchFindDuplicates();
        finish();
    }

    public boolean isEditMode() {
        return mIsEditMode;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mGroupMetadata == null) {
            // Hide menu options until metadata is fully loaded
            return false;
        }
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.view_group, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean isSelectionMode = mActionBarAdapter.isSelectionMode();
        final boolean isGroupEditable = mGroupMetadata != null && mGroupMetadata.editable;
        final boolean isGroupReadOnly = mGroupMetadata != null && mGroupMetadata.readOnly;

        setVisible(menu, R.id.menu_add, isGroupEditable && mIsEditMode);
        setVisible(menu, R.id.menu_rename_group, !isGroupReadOnly && !isSelectionMode);
        setVisible(menu, R.id.menu_delete_group, !isGroupReadOnly && !isSelectionMode);
        setVisible(menu, R.id.menu_edit_group, isGroupEditable && !mIsEditMode && !isSelectionMode);
        setVisible(menu, R.id.menu_remove_from_group, isGroupEditable && isSelectionMode &&
                !mIsEditMode);

        return true;
    }

    private static void setVisible(Menu menu, int id, boolean visible) {
        final MenuItem menuItem = menu.findItem(id);
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactsContract.Groups.CONTENT_ITEM_TYPE);
                intent.putExtra(UiIntentActions.GROUP_ACCOUNT_NAME, mGroupMetadata.accountName);
                intent.putExtra(UiIntentActions.GROUP_ACCOUNT_TYPE, mGroupMetadata.accountType);
                intent.putExtra(UiIntentActions.GROUP_ACCOUNT_DATA_SET, mGroupMetadata.dataSet);
                intent.putExtra(UiIntentActions.GROUP_CONTACT_IDS,
                        mMembersFragment.getMemberContactIds());
                startActivityForResult(intent, RESULT_GROUP_ADD_MEMBER);
                return true;
            }
            case R.id.menu_rename_group: {
                GroupNameEditDialogFragment.showUpdateDialog(
                        getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG, mGroupMetadata.groupName);
                return true;
            }
            case R.id.menu_delete_group: {
                deleteGroup();
                return true;
            }
            case R.id.menu_edit_group: {
                if (mMembersFragment == null) {
                    return false;
                }
                mIsEditMode = true;
                mActionBarAdapter.setSelectionMode(true);
                mMembersFragment.displayDeleteButtons(true);
                return true;
            }
            case R.id.menu_remove_from_group: {
                if (mMembersFragment == null) {
                    return false;
                }
                logListEvent();
                removeSelectedContacts();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteGroup() {
        if (mMembersFragment.getMemberCount() == 0) {
            final Intent intent = ContactSaveService.createGroupDeletionIntent(
                    this, mGroupMetadata.groupId,
                    GroupMembersActivity.class, ACTION_DELETE_GROUP);
            startService(intent);
        } else {
            GroupDeletionDialogFragment.show(getFragmentManager(), mGroupMetadata.groupId,
                    mGroupMetadata.groupName, /* endActivity */ false, ACTION_DELETE_GROUP);
        }
    }

    private void logListEvent() {
        Logger.logListEvent(
                ListEvent.ActionType.REMOVE_LABEL,
                mMembersFragment.getListType(),
                mMembersFragment.getAdapter().getCount(),
                /* clickedIndex */ -1,
                mMembersFragment.getAdapter().getSelectedContactIdsArray().length);
    }

    private void removeSelectedContacts() {
        final long[] contactIds = mMembersFragment.getAdapter().getSelectedContactIdsArray();
        new UpdateGroupMembersAsyncTask(UpdateGroupMembersAsyncTask.TYPE_REMOVE,
                this, contactIds, mGroupMetadata.groupId, mGroupMetadata.accountName,
                mGroupMetadata.accountType).execute();

        mActionBarAdapter.setSelectionMode(false);
    }

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if (mIsInsertAction) {
            finish();
        } else if (mIsEditMode) {
            mIsEditMode = false;
            mActionBarAdapter.setSelectionMode(false);
            if (mMembersFragment != null) {
                mMembersFragment.displayDeleteButtons(false);
            }
        } else if (mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
            if (mMembersFragment != null) {
                mMembersFragment.displayCheckBoxes(false);
            }
        } else if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            switchToAllContacts();
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_GROUP_ADD_MEMBER && resultCode == RESULT_OK && data != null) {
            long[] contactIds = data.getLongArrayExtra(
                    UiIntentActions.TARGET_CONTACT_IDS_EXTRA_KEY);
            if (contactIds == null) {
                final long contactId = data.getLongExtra(
                        UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, -1);
                if (contactId > -1) {
                    contactIds = new long[1];
                    contactIds[0] = contactId;
                }
            }
            new UpdateGroupMembersAsyncTask(UpdateGroupMembersAsyncTask.TYPE_ADD,
                    this, contactIds, mGroupMetadata.groupId, mGroupMetadata.accountName,
                    mGroupMetadata.accountType).execute();
        }
    }

    private boolean isSelectAccountDialogFound() {
        return getFragmentManager().findFragmentByTag(TAG_SELECT_ACCOUNT_DIALOG) != null;
    }

    private boolean isGroupNameEditDialogFound() {
        return getFragmentManager().findFragmentByTag(TAG_GROUP_NAME_EDIT_DIALOG) != null;
    }

    private void setResultCanceledAndFinish() {
        setResultCanceledAndFinish(-1);
    }

    private void setResultCanceledAndFinish(int resId) {
        toast(resId);
        setResult(RESULT_CANCELED);
        finish();
    }

    private void toast(int resId) {
        if (resId >= 0) {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        }
    }

    // SelectAccountDialogFragment.Listener callbacks

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        mGroupMetadata.setGroupAccountMetadata(account);
        GroupNameEditDialogFragment.showInsertDialog(
                getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG);
    }

    @Override
    public void onAccountSelectorCancelled() {
        setResultCanceledAndFinish();
    }

    // ActionBarAdapter callbacks

    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                if (mMembersFragment != null) {
                    if (mIsEditMode) {
                        mMembersFragment.displayDeleteButtons(true);
                    } else {
                        mMembersFragment.displayCheckBoxes(true);
                    }
                }
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ false);
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                mActionBarAdapter.setSearchMode(false);
                if (mMembersFragment != null) {
                    if (mIsEditMode) {
                        mMembersFragment.displayDeleteButtons(false);
                    } else {
                        mMembersFragment.displayCheckBoxes(false);
                    }
                }
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation */ true);
                break;
            case ActionBarAdapter.Listener.Action.BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE:
                showFabWithAnimation(/* showFabWithAnimation */ true);
                break;
        }
    }

    private void showFabWithAnimation(boolean showFab) {
        // TODO: b/28497108
    }

    @Override
    public void onSelectedTabChanged() {
    }

    @Override
    public void onUpButtonPressed() {
        onBackPressed();
    }

    // MultiSelect checkbox callbacks

    @Override
    public void onStartDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(true);
    }

    @Override
    public void onSelectedContactIdsChanged() {
        mActionBarAdapter.setSelectionCount(mMembersFragment.getSelectedContactIds().size());
    }

    @Override
    public void onStopDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(false);
    }

    // GroupNameEditDialogFragment.Listener callbacks

    @Override
    public void onGroupNameEdit(String groupName) {
        final Intent saveIntent;
        if (mIsInsertAction) {
            saveIntent = ContactSaveService.createNewGroupIntent(this,
                    mGroupMetadata.createAccountWithDataSet(), groupName,
                    /* rawContactsToAdd */ null, GroupMembersActivity.class,
                    ACTION_CREATE_GROUP);
        } else {
            saveIntent = ContactSaveService.createGroupRenameIntent(this,
                    mGroupMetadata.groupId, groupName, GroupMembersActivity.class,
                    ACTION_UPDATE_GROUP);
        }
        startService(saveIntent);
    }

    @Override
    public void onGroupNameEditCancelled() {
        if (mIsInsertAction) {
            setResultCanceledAndFinish();
        }
    }

    // GroupMembersFragment callbacks

    @Override
    public void onGroupMetadataLoaded(GroupMetadata groupMetadata) {
        mGroupMetadata = groupMetadata;

        updateGroupMenu(mGroupMetadata);

        if (!mIsInsertAction) {
            setActionBarTitle(mGroupMetadata.groupName);
        }
        invalidateOptionsMenu();
    }

    private void setActionBarTitle(String title) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    @Override
    public void onGroupMetadataLoadFailed() {
        setResultCanceledAndFinish(R.string.groupLoadErrorToast);
    }

    @Override
    protected GroupMetadata getGroupMetadata() {
        return mGroupMetadata;
    }

    @Override
    public void onGroupMemberListItemClicked(int position, Uri contactLookupUri) {
        final int count = mMembersFragment.getAdapter().getCount();
        Logger.logListEvent(ListEvent.ActionType.CLICK, ListEvent.ListType.GROUP, count,
                /* clickedIndex */ position, /* numSelected */ 0);
        final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(
                contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED);
        intent.putExtra(QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE, ScreenType.LIST_GROUP);
        startActivity(intent);
    }

    @Override
    public void onGroupMemberListItemDeleted(int position, long contactId) {
        final long[] contactIds = new long[1];
        contactIds[0] = contactId;
        new UpdateGroupMembersAsyncTask(UpdateGroupMembersAsyncTask.TYPE_REMOVE,
                this, contactIds, mGroupMetadata.groupId, mGroupMetadata.accountName,
                mGroupMetadata.accountType).execute();
    }
}
