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

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.view.GravityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.common.GroupMetaData;
import com.android.contacts.common.logging.ListEvent;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.ScreenEvent.ScreenType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.list.UiIntentActions;
import com.android.contacts.quickcontact.QuickContactActivity;

/**
 * Displays the members of a group and allows the user to edit it.
 */
public class GroupMembersActivity extends ContactsDrawerActivity implements
        ActionBarAdapter.Listener,
        MultiSelectContactsListFragment.OnCheckBoxListActionListener,
        GroupMembersFragment.GroupMembersListener {

    private static final String TAG = "GroupMembers";

    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_METADATA = "groupMetadata";
    private static final String KEY_IS_EDIT_MODE = "editMode";

    private static final String TAG_GROUP_MEMBERS = "groupMembers";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    private static final String ACTION_DELETE_GROUP = "deleteGroup";
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
    private boolean mIsEditMode;

    private GroupMetadata mGroupMetadata;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Parse the Intent
        if (savedState != null) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
            mIsEditMode = savedState.getBoolean(KEY_IS_EDIT_MODE);
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        } else {
            mGroupUri = getIntent().getData();
            setTitle(getIntent().getStringExtra(GroupUtil.EXTRA_GROUP_NAME));
        }
        if (mGroupUri == null) {
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

        // Add the members list fragment
        final FragmentManager fragmentManager = getFragmentManager();
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
        outState.putBoolean(KEY_IS_EDIT_MODE, mIsEditMode);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    // Invoked with results from the ContactSaveService
    @Override
    protected void onNewIntent(Intent newIntent) {
        if (ContactsDrawerActivity.ACTION_CREATE_GROUP.equals(newIntent.getAction())) {
            super.onNewIntent(newIntent);
            return;
        }
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

            toast(getToastMessageForSaveAction(newIntent.getAction()));

            if (mIsEditMode) {
                // If we're removing group members one at a time, don't reload the fragment so
                // the user can continue to remove group members one by one
                if (getGroupCount() == 1) {
                    // If we're deleting the last group member, exit edit mode
                    onBackPressed();
                }
            } else if (!ACTION_REMOVE_FROM_GROUP.equals(newIntent.getAction())) {
                replaceGroupMembersFragment();
                invalidateOptionsMenu();
            }
        }
    }

    private static boolean isDeleteAction(String action) {
        return ACTION_DELETE_GROUP.equals(action);
    }

    private static boolean isSaveAction(String action) {
        return ACTION_UPDATE_GROUP.equals(action)
                || ACTION_ADD_TO_GROUP.equals(action)
                || ACTION_REMOVE_FROM_GROUP.equals(action);
    }

    private static int getToastMessageForSaveAction(String action) {
        if (ACTION_UPDATE_GROUP.equals(action)) return R.string.groupUpdatedToast;
        if (ACTION_ADD_TO_GROUP.equals(action)) return R.string.groupMembersAddedToast;
        if (ACTION_REMOVE_FROM_GROUP.equals(action)) return R.string.groupMembersRemovedToast;
        throw new IllegalArgumentException("Unhanded contact save action " + action);
    }

    private int getGroupCount() {
        return mMembersFragment != null && mMembersFragment.getAdapter() != null
                ? mMembersFragment.getAdapter().getCount() : -1;
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
    protected void onGroupMenuItemClicked(long groupId, String title) {
        if (mGroupMetadata.groupId != groupId) {
            super.onGroupMenuItemClicked(groupId, title);
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

        setVisible(menu, R.id.menu_add, isGroupEditable && !isSelectionMode);
        setVisible(menu, R.id.menu_rename_group, !isGroupReadOnly && !isSelectionMode);
        setVisible(menu, R.id.menu_delete_group, !isGroupReadOnly && !isSelectionMode);
        setVisible(menu, R.id.menu_edit_group, isGroupEditable && !mIsEditMode && !isSelectionMode
                && !isGroupEmpty());
        setVisible(menu, R.id.menu_remove_from_group, isGroupEditable && isSelectionMode &&
                !mIsEditMode);

        return true;
    }

    private boolean isGroupEmpty() {
        return mMembersFragment != null && mMembersFragment.getAdapter() != null &&
                mMembersFragment.getAdapter().isEmpty();
    }

    private static void setVisible(Menu menu, int id, boolean visible) {
        final MenuItem menuItem = menu.findItem(id);
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    public void startGroupAddMemberActivity() {
        startActivityForResult(GroupUtil.createPickMemberIntent(mGroupMetadata,
                mMembersFragment.getMemberContactIds()), RESULT_GROUP_ADD_MEMBER);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.menu_add: {
                startGroupAddMemberActivity();
                return true;
            }
            case R.id.menu_rename_group: {
                GroupNameEditDialogFragment.newInstanceForUpdate(
                        new AccountWithDataSet(mGroupMetadata.accountName,
                                mGroupMetadata.accountType, mGroupMetadata.dataSet),
                        ACTION_UPDATE_GROUP, mGroupMetadata.groupId, mGroupMetadata.groupName)
                        .show(getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG);
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
            final Intent intent = ContactSaveService.createGroupDeletionIntent(this,
                    mGroupMetadata.groupId);
            startService(intent);
            finish();
        } else {
            GroupDeletionDialogFragment.show(getFragmentManager(), mGroupMetadata.groupId,
                    mGroupMetadata.groupName);
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

    // ActionBarAdapter callbacks

    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                if (mMembersFragment != null) {
                    if (mIsEditMode) {
                        mMembersFragment.displayDeleteButtons(true);
                        mActionBarAdapter.setActionBarTitle(getString(R.string.title_edit_group));
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
        if (mIsEditMode) {
            mActionBarAdapter.setActionBarTitle(getString(R.string.title_edit_group));
        } else {
            mActionBarAdapter.setSelectionCount(mMembersFragment.getSelectedContactIds().size());
        }
    }

    @Override
    public void onStopDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(false);
    }

    // GroupMembersFragment callbacks

    @Override
    public void onGroupMetadataLoaded(GroupMetadata groupMetadata) {
        mGroupMetadata = groupMetadata;
        updateGroupMenu(mGroupMetadata);
        setTitle(mGroupMetadata.groupName);
        invalidateOptionsMenu();
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
