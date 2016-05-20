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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupListLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupEditorFragment;
import com.android.contacts.group.GroupMembersListFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.quickcontact.QuickContactActivity;

/**
 * Displays the members of a group and allows the user to edit it.
 */
// TODO(wjang): rename it to GroupActivity since it does both display and edit now.
public class GroupMembersActivity extends AppCompatContactsActivity implements
        ActionBarAdapter.Listener,
        MultiSelectContactsListFragment.OnCheckBoxListActionListener,
        GroupMembersListFragment.GroupMembersListListener,
        GroupEditorFragment.Listener {

    private static final String TAG = "GroupMembersActivity";

    private static final boolean DEBUG = false;

    private static final String KEY_GROUP_METADATA = "groupMetadata";

    private static final int LOADER_GROUP_METADATA = 0;
    private static final int LOADER_GROUP_LIST_DETAILS = 1;

    private static final int FRAGMENT_MEMBERS_LIST = -1;
    private static final int FRAGMENT_EDITOR = -2;

    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";

    private class GroupPagerAdapter extends FragmentPagerAdapter {

        public GroupPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public int getCount() {
            return mIsInsertAction ? 1 : 2;
        }

        public Fragment getItem(int position) {
            if (mIsInsertAction) {
                switch (position) {
                    case 0:
                        mEditorFragment = GroupEditorFragment.newInstance(
                                Intent.ACTION_INSERT, mGroupMetadata, getIntent().getExtras());
                        return mEditorFragment;
                }
                throw new IllegalStateException("Unhandled position " + position);
            } else {
                switch (position) {
                    case 0:
                        mMembersListFragment = GroupMembersListFragment.newInstance(mGroupMetadata);
                        return mMembersListFragment;
                    case 1:
                        // TODO: double check what intent extras need to be supported
                        mEditorFragment = GroupEditorFragment.newInstance(
                                Intent.ACTION_EDIT, mGroupMetadata, getIntent().getExtras());
                        return mEditorFragment;
                }
                throw new IllegalStateException("Unhandled position " + position);
            }
        }

        private boolean isCurrentItem(int fragment) {
            if (mIsInsertAction) {
                return FRAGMENT_EDITOR == fragment;
            }
            int currentItem = mViewPager.getCurrentItem();
            switch (fragment) {
                case FRAGMENT_MEMBERS_LIST:
                    return currentItem == 0;
                case FRAGMENT_EDITOR:
                    return currentItem == 1;
            }
            return false;
        }

        private void setCurrentItem(int fragment) {
            if (mIsInsertAction) {
                switch (fragment) {
                    case FRAGMENT_EDITOR:
                        mViewPager.setCurrentItem(0);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported fragment " + fragment);
                }
            } else {
                switch (fragment) {
                    case FRAGMENT_MEMBERS_LIST:
                        mViewPager.setCurrentItem(0);
                        break;
                    case FRAGMENT_EDITOR:
                        mViewPager.setCurrentItem(1);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported fragment " + fragment);
                }
            }
        }
    }

    /** Step 1 of loading group metadata. */
    private final LoaderCallbacks<Cursor> mGroupMetadataCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(GroupMembersActivity.this, mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || cursor.isClosed()) {
                Log.e(TAG, "Failed to load group metadata");
                return;
            }
            if (cursor.moveToNext()) {
                final boolean deleted = cursor.getInt(GroupMetaDataLoader.DELETED) == 1;
                if (!deleted) {
                    mGroupMetadata = new GroupMetadata();
                    mGroupMetadata.uri = mGroupUri;
                    mGroupMetadata.accountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
                    mGroupMetadata.accountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
                    mGroupMetadata.dataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
                    mGroupMetadata.groupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
                    mGroupMetadata.groupName = cursor.getString(GroupMetaDataLoader.TITLE);
                    mGroupMetadata.readOnly = cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1;

                    final AccountTypeManager accountTypeManager =
                            AccountTypeManager.getInstance(GroupMembersActivity.this);
                    final AccountType accountType = accountTypeManager.getAccountType(
                            mGroupMetadata.accountType, mGroupMetadata.dataSet);
                    mGroupMetadata.editable = accountType.isGroupMembershipEditable();

                    getLoaderManager().restartLoader(LOADER_GROUP_LIST_DETAILS, null,
                            mGroupListCallbacks);
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /** Step 2 of loading group metadata. */
    private final LoaderCallbacks<Cursor> mGroupListCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final GroupListLoader groupListLoader = new GroupListLoader(GroupMembersActivity.this);

            // TODO(wjang): modify GroupListLoader to accept this selection criteria more naturally
            groupListLoader.setSelection(groupListLoader.getSelection()
                    + " AND " + ContactsContract.Groups._ID + "=?");

            final String[] selectionArgs = new String[1];
            selectionArgs[0] = Long.toString(mGroupMetadata.groupId);
            groupListLoader.setSelectionArgs(selectionArgs);

            return groupListLoader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || cursor.isClosed()) {
                Log.e(TAG, "Failed to load group list details");
                return;
            }
            if (cursor.moveToNext()) {
                mGroupMetadata.memberCount = cursor.getInt(GroupListLoader.MEMBER_COUNT);
            }
            onGroupMetadataLoaded();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private ActionBarAdapter mActionBarAdapter;
    private ViewPager mViewPager;

    private GroupPagerAdapter mPagerAdapter;

    private Uri mGroupUri;
    private GroupMetadata mGroupMetadata;

    private GroupMembersListFragment mMembersListFragment;
    private GroupEditorFragment mEditorFragment;

    private boolean mIsInsertAction;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mIsInsertAction = Intent.ACTION_INSERT.equals(getIntent().getAction());

        mGroupUri = getIntent().getData();
        if (savedState != null) {
            mGroupMetadata = savedState.getParcelable(KEY_GROUP_METADATA);
        }

        // Setup the view
        setContentView(R.layout.group_members_activity);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        // Set up the action bar
        final Toolbar toolbar = getView(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ContactsRequest contactsRequest = new ContactsRequest();
        contactsRequest.setActionCode(ContactsRequest.ACTION_GROUP);
        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(),
                /* portraitTabs */ null, /* landscapeTabs */ null, toolbar,
                R.string.enter_contact_name);
        mActionBarAdapter.setShowHomeIcon(true);
        mActionBarAdapter.setShowHomeAsUp(true);
        mActionBarAdapter.initialize(savedState, contactsRequest);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_METADATA, mGroupMetadata);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mIsInsertAction) {
            mGroupMetadata = new GroupMetadata();
            onGroupMetadataLoaded();
        } else {
            if (mGroupMetadata == null) {
                getLoaderManager().restartLoader(
                        LOADER_GROUP_METADATA, null, mGroupMetadataCallbacks);
            } else {
                onGroupMetadataLoaded();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);

        if (ACTION_SAVE_COMPLETED.equals(newIntent.getAction())) {
            final Uri groupUri = newIntent.getData();
            if (groupUri == null) {
                Toast.makeText(this, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            } else {
                Toast.makeText(this, R.string.groupSavedToast,Toast.LENGTH_SHORT).show();

                final Intent intent = GroupUtil.createViewGroupIntent(this, groupUri);
                finish();
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mGroupMetadata == null || mGroupMetadata.memberCount < 0) {
            // Hide menu options until metatdata is fully loaded
            return false;
        }
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.view_group, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean isSelectionMode = mActionBarAdapter.isSelectionMode();
        final boolean isSearchMode = false;

        final boolean isListFragment = mPagerAdapter.isCurrentItem(FRAGMENT_MEMBERS_LIST);
        final boolean isEditorFragment = mPagerAdapter.isCurrentItem(FRAGMENT_EDITOR);

        final boolean isGroupEditable = mGroupMetadata.editable;
        final boolean isGroupReadOnly = mGroupMetadata.readOnly;

        setVisible(menu, R.id.menu_edit_group, isGroupEditable && !isEditorFragment &&
                !isSelectionMode && !isSearchMode);

        setVisible(menu, R.id.menu_delete_group, !isGroupReadOnly && !isEditorFragment &&
                !isSelectionMode && !isSearchMode);

        setVisible(menu, R.id.menu_remove_from_group,
                isGroupEditable && isSelectionMode && isListFragment);

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
            case R.id.menu_edit_group: {
                mPagerAdapter.setCurrentItem(FRAGMENT_EDITOR);
                return true;
            }
            case R.id.menu_delete_group: {
                GroupDeletionDialogFragment.show(getFragmentManager(), mGroupMetadata.groupId,
                        mGroupMetadata.groupName, /* endActivity */ true);
                return true;
            }
            case R.id.menu_remove_from_group: {
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
                GroupMembersActivity.ACTION_SAVE_COMPLETED);
        startService(intent);
    }

    private void onGroupMetadataLoaded() {
        if (DEBUG) Log.d(TAG, "Loaded " + mGroupMetadata);

        if (mPagerAdapter == null) {
            mPagerAdapter = new GroupPagerAdapter(getFragmentManager());
            mViewPager.setAdapter(mPagerAdapter);
        }

        if (mIsInsertAction) {
            mPagerAdapter.setCurrentItem(FRAGMENT_EDITOR);
            getSupportActionBar().setTitle(getString(R.string.editGroupDescription));
        } else {
            getSupportActionBar().setTitle(mGroupMetadata.groupName);
            mPagerAdapter.setCurrentItem(FRAGMENT_MEMBERS_LIST);
        }
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
        } else if (mPagerAdapter.isCurrentItem(FRAGMENT_EDITOR)) {
            mPagerAdapter.setCurrentItem(FRAGMENT_MEMBERS_LIST);
        } else {
            super.onBackPressed();
        }
    }

    // GroupsMembersListFragment callbacks

    @Override
    public void onGroupMemberListItemClicked(Uri contactLookupUri) {
        startActivity(ImplicitIntentsUtil.composeQuickContactIntent(
                contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED));
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
        invalidateOptionsMenu();
    }

    @Override
    public void onSelectedContactIdsChanged() {
        if (mActionBarAdapter.isSelectionMode() && mMembersListFragment != null) {
            mActionBarAdapter.setSelectionCount(
                    mMembersListFragment.getSelectedContactIds().size());
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onStopDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(false);
        invalidateOptionsMenu();
    }

    // GroupEditorFragment.Listener callbacks

    @Override
    public void onGroupNotFound() {
        finish();
    }

    @Override
    public void onReverted() {
        if (mIsInsertAction) {
            finish();
        } else {
            mPagerAdapter.setCurrentItem(FRAGMENT_MEMBERS_LIST);
        }
    }

    @Override
    public void onSaveFinished(int resultCode, Intent resultIntent) {
        if (mIsInsertAction) {
            final Intent intent = GroupUtil.createViewGroupIntent(this, resultIntent.getData());
            finish();
            startActivity(intent);
        }
    }

    @Override
    public void onAccountsNotFound() {
        finish();
    }

    @Override
    public void onGroupMemberClicked(Uri contactLookupUri) {
        startActivity(ImplicitIntentsUtil.composeQuickContactIntent(
                contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED));
    }

    @Override
    public AutoCompleteTextView getSearchView() {
        return mActionBarAdapter == null
                ? null : (AutoCompleteTextView) mActionBarAdapter.getSearchView();
    }

    @Override
    public boolean isSearchMode() {
        return mActionBarAdapter == null ? false : mActionBarAdapter.isSearchMode();
    }

    @Override
    public void setSearchMode(boolean searchMode) {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setSearchMode(searchMode);
        }
    }
}
