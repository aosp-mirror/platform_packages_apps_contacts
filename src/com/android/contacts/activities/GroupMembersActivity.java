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
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMemberLoader.GroupEditorQuery;
import com.android.contacts.R;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.ListEvent;
import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupMembersListFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.group.Member;
import com.android.contacts.group.SuggestedMemberListAdapter;
import com.android.contacts.group.SuggestedMemberListAdapter.SuggestedMember;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.quickcontact.QuickContactActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the members of a group and allows the user to edit it.
 */
// TODO(wjang): rename it to GroupActivity since it does both display and edit now.
public class GroupMembersActivity extends AppCompatContactsActivity implements
        ActionBarAdapter.Listener,
        MultiSelectContactsListFragment.OnCheckBoxListActionListener,
        SelectAccountDialogFragment.Listener,
        GroupMembersListFragment.GroupMembersListListener,
        GroupNameEditDialogFragment.Listener {

    private static final String TAG = "GroupMembers";

    private static final String KEY_IS_INSERT_ACTION = "isInsertAction";
    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final String TAG_GROUP_MEMBERS = "groupMembers";
    private static final String TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    private static final int LOADER_GROUP_MEMBERS = 0;

    private static final String ACTION_CREATE_GROUP = "createGroup";
    private static final String ACTION_UPDATE_GROUP = "updateGroup";
    private static final String ACTION_ADD_TO_GROUP = "addToGroup";
    private static final String ACTION_REMOVE_FROM_GROUP = "removeFromGroup";

    /** Loader callbacks for existing group members for the autocomplete text view. */
    private final LoaderCallbacks<Cursor> mGroupMemberCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return GroupMemberLoader.constructLoaderForGroupEditorQuery(
                    GroupMembersActivity.this, mGroupMetadata.groupId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            final List<Member> members = new ArrayList<>();
            data.moveToPosition(-1);
            while (data.moveToNext()) {
                members.add(new Member(
                        data.getLong(GroupEditorQuery.RAW_CONTACT_ID),
                        data.getString(GroupEditorQuery.CONTACT_LOOKUP_KEY),
                        data.getLong(GroupEditorQuery.CONTACT_ID),
                        data.getString(GroupEditorQuery.CONTACT_DISPLAY_NAME_PRIMARY),
                        data.getString(GroupEditorQuery.CONTACT_PHOTO_URI),
                        data.getLong(GroupEditorQuery.CONTACT_PHOTO_ID)));
            }

            bindAutocompleteGroupMembers(members);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private ActionBarAdapter mActionBarAdapter;

    private GroupMetadata mGroupMetadata;

    private GroupMembersListFragment mMembersListFragment;

    private SuggestedMemberListAdapter mAutoCompleteAdapter;

    private Uri mGroupUri;
    private boolean mIsInsertAction;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Parse the Intent
        if (savedState != null) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
            mIsInsertAction = savedState.getBoolean(KEY_IS_INSERT_ACTION);
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
        final Toolbar toolbar = getView(R.id.toolbar);
        setSupportActionBar(toolbar);
        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(),
                /* portraitTabs */ null, /* landscapeTabs */ null, toolbar,
                R.string.enter_contact_name);
        mActionBarAdapter.setShowHomeIcon(true);
        mActionBarAdapter.setShowHomeAsUp(true);

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
            // Add the members list fragment
            final FragmentManager fragmentManager = getFragmentManager();
            mMembersListFragment = (GroupMembersListFragment)
                    fragmentManager.findFragmentByTag(TAG_GROUP_MEMBERS);
            if (mMembersListFragment == null) {
                mMembersListFragment = GroupMembersListFragment.newInstance(getIntent().getData());
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, mMembersListFragment, TAG_GROUP_MEMBERS)
                        .commit();
            } else {
                getLoaderManager().initLoader(LOADER_GROUP_MEMBERS, null, mGroupMemberCallbacks);
            }
            mMembersListFragment.setListener(this);
            if (mGroupMetadata != null && mGroupMetadata.editable) {
                mMembersListFragment.setCheckBoxListListener(this);
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
        outState.putBoolean(KEY_IS_INSERT_ACTION, mIsInsertAction);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
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

        if (isSaveAction(newIntent.getAction())) {
            final Uri groupUri = newIntent.getData();
            if (groupUri == null) {
                Toast.makeText(this, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
                setResultCanceledAndFinish();
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + groupUri);

            mGroupUri = groupUri;
            mIsInsertAction = false;

            Toast.makeText(this, getToastMessageForSaveAction(newIntent.getAction()),
                    Toast.LENGTH_SHORT).show();

            mMembersListFragment = GroupMembersListFragment.newInstance(groupUri);
            mMembersListFragment.setListener(this);
            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mMembersListFragment, TAG_GROUP_MEMBERS)
                    .commit();
            if (mGroupMetadata != null && mGroupMetadata.editable) {
                mMembersListFragment.setCheckBoxListListener(this);
            }

            invalidateOptionsMenu();
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mGroupMetadata == null || mGroupMetadata.memberCount < 0) {
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
        final boolean isSearchMode = mActionBarAdapter.isSearchMode();

        final boolean isGroupEditable = mGroupMetadata != null && mGroupMetadata.editable;
        final boolean isGroupReadOnly = mGroupMetadata != null && mGroupMetadata.readOnly;

        setVisible(menu, R.id.menu_add,
                isGroupEditable &&!isSelectionMode && !isSearchMode);

        setVisible(menu, R.id.menu_edit_group,
                isGroupEditable && !isSelectionMode && !isSearchMode);

        setVisible(menu, R.id.menu_delete_group,
                !isGroupReadOnly && !isSelectionMode && !isSearchMode);

        setVisible(menu, R.id.menu_remove_from_group,
                isGroupEditable && isSelectionMode);

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
                if (mActionBarAdapter != null) {
                    mActionBarAdapter.setSearchMode(true);
                }
                return true;
            }
            case R.id.menu_edit_group: {
                GroupNameEditDialogFragment.showUpdateDialog(
                        getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG, mGroupMetadata.groupName);
                return true;
            }
            case R.id.menu_delete_group: {
                // TODO(wjang): add a Toast after deletion after deleting the editor fragment
                GroupDeletionDialogFragment.show(getFragmentManager(), mGroupMetadata.groupId,
                        mGroupMetadata.groupName, /* endActivity */ true);
                return true;
            }
            case R.id.menu_remove_from_group: {
                if (mMembersListFragment == null) {
                    return false;
                }
                final int count = mMembersListFragment.getAdapter().getCount();
                final int numSelected =
                        mMembersListFragment.getAdapter().getSelectedContactIdsArray().length;
                Logger.logListEvent(ListEvent.ActionType.REMOVE_LABEL,
                        mMembersListFragment.getListType(), count, /* clickedIndex */ -1,
                        numSelected);
                removeSelectedContacts();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void removeSelectedContacts() {
        final long[] rawContactsToRemove =
                mMembersListFragment.getAdapter().getSelectedContactIdsArray();
        final Intent intent = ContactSaveService.createGroupUpdateIntent(
                this, mGroupMetadata.groupId, /* groupName */ null,
                /* rawContactsToAdd */ null, rawContactsToRemove, getClass(),
                GroupMembersActivity.ACTION_REMOVE_FROM_GROUP);
        startService(intent);

        mActionBarAdapter.setSelectionMode(false);
    }

    @Override
    public void onBackPressed() {
        if (mIsInsertAction) {
            finish();
        } else if (mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
            if (mMembersListFragment != null) {
                mMembersListFragment.displayCheckBoxes(false);
            }
        } else if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
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

    private void setResultCanceledAndFinish(int toastResId) {
        if (toastResId >= 0) {
            Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show();
        }
        setResult(RESULT_CANCELED);
        finish();
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
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                mActionBarAdapter.setSearchMode(true);
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ false);
                break;
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                if (mMembersListFragment != null) {
                    mMembersListFragment.displayCheckBoxes(true);
                }
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ false);
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                mActionBarAdapter.setSearchMode(false);
                if (mMembersListFragment != null) {
                    mMembersListFragment.displayCheckBoxes(false);
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
        // TODO(wjang): b/28497108
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
        mActionBarAdapter.setSelectionCount(mMembersListFragment.getSelectedContactIds().size());
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
                    GroupMembersActivity.ACTION_CREATE_GROUP);
        } else {
            saveIntent = ContactSaveService.createGroupRenameIntent(this,
                    mGroupMetadata.groupId, groupName, GroupMembersActivity.class,
                    GroupMembersActivity.ACTION_UPDATE_GROUP);
        }
        startService(saveIntent);
    }

    @Override
    public void onGroupNameEditCancelled() {
        if (mIsInsertAction) {
            setResultCanceledAndFinish();
        }
    }

    // GroupsMembersListFragment callbacks

    @Override
    public void onGroupMetadataLoaded(GroupMetadata groupMetadata) {
        mGroupMetadata = groupMetadata;

        if (!mIsInsertAction) {
            getSupportActionBar().setTitle(mGroupMetadata.groupName);
        }

        bindAutocompleteTextView();
        getLoaderManager().initLoader(LOADER_GROUP_MEMBERS, null, mGroupMemberCallbacks);

        invalidateOptionsMenu();
    }

    private void bindAutocompleteTextView() {
        final AutoCompleteTextView autoCompleteTextView =
                (AutoCompleteTextView) mActionBarAdapter.getSearchView();
        if (autoCompleteTextView == null) return;
        mAutoCompleteAdapter = createAutocompleteAdapter();
        autoCompleteTextView.setAdapter(mAutoCompleteAdapter);
        autoCompleteTextView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final SuggestedMember member = (SuggestedMember) view.getTag();
                if (member == null) {
                    return;
                }
                final long[] rawContactIdsToAdd = new long[1];
                rawContactIdsToAdd[0] = member.getRawContactId();
                final Intent intent = ContactSaveService.createGroupUpdateIntent(
                        GroupMembersActivity.this, mGroupMetadata.groupId, /* newLabel */ null,
                        rawContactIdsToAdd, /* rawContactIdsToRemove */ null,
                        GroupMembersActivity.class, GroupMembersActivity.ACTION_ADD_TO_GROUP);
                startService(intent);

                // Update the autocomplete adapter so the contact doesn't get suggested again
                mAutoCompleteAdapter.addNewMember(member.getContactId());

                // Clear out the text field
                autoCompleteTextView.setText("");
            }
        });
    }

    private SuggestedMemberListAdapter createAutocompleteAdapter() {
        final SuggestedMemberListAdapter adapter = new SuggestedMemberListAdapter(
                this, android.R.layout.simple_dropdown_item_1line);
        adapter.setContentResolver(this.getContentResolver());
        adapter.setAccountType(mGroupMetadata.accountType);
        adapter.setAccountName(mGroupMetadata.accountName);
        adapter.setDataSet(mGroupMetadata.dataSet);
        return adapter;
    }

    private void bindAutocompleteGroupMembers(List<Member> members) {
        if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.updateExistingMembersList(members);
        }
    }

    @Override
    public void onGroupMetadataLoadFailed() {
        setResultCanceledAndFinish(R.string.groupLoadErrorToast);
    }

    @Override
    public void onGroupMemberListItemClicked(int position, Uri contactLookupUri) {
        final int count = mMembersListFragment.getAdapter().getCount();
        Logger.logListEvent(ListEvent.ActionType.CLICK, ListEvent.ListType.GROUP, count,
                /* clickedIndex */ position, /* numSelected */ 0);
        final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(
                contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED);
        intent.putExtra(QuickContactActivity.EXTRA_PREVIOUS_SCREEN_TYPE, ScreenType.LIST_GROUP);
        startActivity(intent);
    }
}
