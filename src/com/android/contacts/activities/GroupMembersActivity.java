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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupMembersListFragment;
import com.android.contacts.group.GroupMembersListFragment.GroupMembersListCallbacks;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.quickcontact.QuickContactActivity;

/** Displays the members of a group. */
public class GroupMembersActivity extends AppCompatContactsActivity implements
        ActionBarAdapter.Listener,
        MultiSelectContactsListFragment.OnCheckBoxListActionListener,
        GroupMembersListCallbacks {

    private static final String TAG_GROUP_MEMBERS = "group_members";

    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";

    private GroupMembersListFragment mFragment;
    private ActionBarAdapter mActionBarAdapter;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.group_members_activity);

        // Add the group members list fragment
        final FragmentManager fragmentManager = getFragmentManager();
        mFragment = (GroupMembersListFragment) fragmentManager.findFragmentByTag(TAG_GROUP_MEMBERS);
        if (mFragment == null) {
            mFragment = new GroupMembersListFragment();
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, mFragment, TAG_GROUP_MEMBERS)
                    .commit();
        }
        mFragment.setGroupUri(getIntent().getData());
        mFragment.setCallbacks(this);
        mFragment.setCheckBoxListListener(this);

        // Set up the action bar
        final Toolbar toolbar = getView(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ContactsRequest contactsRequest = new ContactsRequest();
        contactsRequest.setActionCode(ContactsRequest.ACTION_GROUP);
        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(),
                /* portraitTabs */ null, /* landscapeTabs */ null, toolbar);
        mActionBarAdapter.setShowHomeIcon(true);
        mActionBarAdapter.setShowHomeAsUp(true);
        mActionBarAdapter.initialize(savedState, contactsRequest);
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        if (mFragment != null && ACTION_SAVE_COMPLETED.equals(newIntent.getAction())) {
            final Uri groupUri = newIntent.getData();
            Toast.makeText(this,
                    groupUri == null ? R.string.groupSavedErrorToast :R.string.groupSavedToast,
                    Toast.LENGTH_SHORT).show();

            if (groupUri != null) {
                final Intent intent = new Intent(this, GroupMembersActivity.class);
                intent.setData(groupUri);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }

            finish();
        }
    }

    /** Whether the ActionBar is currently in selection mode. */
    public boolean isSelectionMode() {
        return mActionBarAdapter.isSelectionMode();
    }

    @Override
    public void onBackPressed() {
        if (mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
            mFragment.displayCheckBoxes(false);
        } else if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onHomePressed() {
        onBackPressed();
    }

    @Override
    public void onGroupNameLoaded(String groupName) {
        getSupportActionBar().setTitle(groupName);
    }

    @Override
    public void onGroupMemberClicked(Uri contactLookupUri) {
        startActivity(ImplicitIntentsUtil.composeQuickContactIntent(
                contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED));
    }

    @Override
    public void onEditGroup(Uri groupUri) {
        final Intent intent = new Intent(this, GroupEditorActivity.class);
        intent.setData(groupUri);
        intent.setAction(Intent.ACTION_EDIT);
        startActivity(intent);
    }

    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                // TODO(wjang)
                break;
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                mActionBarAdapter.setSearchMode(true);
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ false);
                break;
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                mFragment.displayCheckBoxes(true);
                invalidateOptionsMenu();
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                mActionBarAdapter.setSearchMode(false);
                mFragment.displayCheckBoxes(false);
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

    @Override
    public void onStartDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(true);
        invalidateOptionsMenu();
    }

    @Override
    public void onSelectedContactIdsChanged() {
        mActionBarAdapter.setSelectionCount(mFragment.getSelectedContactIds().size());
        invalidateOptionsMenu();
    }

    @Override
    public void onStopDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(false);
        invalidateOptionsMenu();
    }
}
