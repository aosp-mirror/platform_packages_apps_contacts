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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.group.GroupMetadata;
import com.android.contacts.group.GroupUtil;

/**
 * Displays the members of a group and allows the user to edit it.
 */
public class GroupMembersActivity extends ContactsDrawerActivity {

    private static final String TAG = "GroupMembers";

    private static final String KEY_GROUP_URI = "groupUri";

    private static final String TAG_GROUP_MEMBERS = "groupMembers";

    private GroupMembersFragment mMembersFragment;

    private Uri mGroupUri;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Parse the Intent
        if (savedState != null) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
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

        // Add the members list fragment
        final FragmentManager fragmentManager = getFragmentManager();
        mMembersFragment = (GroupMembersFragment)
                fragmentManager.findFragmentByTag(TAG_GROUP_MEMBERS);
        if (mMembersFragment == null) {
            mMembersFragment = GroupMembersFragment.newInstance(getIntent().getData());
            fragmentManager.beginTransaction().replace(R.id.fragment_container_inner,
                    mMembersFragment, TAG_GROUP_MEMBERS).commitAllowingStateLoss();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
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

            if (mMembersFragment.isEditMode()) {
                // If we're removing group members one at a time, don't reload the fragment so
                // the user can continue to remove group members one by one
                if (getGroupCount() == 1) {
                    // If we're deleting the last group member, exit edit mode
                    onBackPressed();
                }
            } else if (!GroupUtil.ACTION_REMOVE_FROM_GROUP.equals(newIntent.getAction())) {
                replaceGroupMembersFragment();
                invalidateOptionsMenu();
            }
        }
    }

    private static boolean isDeleteAction(String action) {
        return GroupUtil.ACTION_DELETE_GROUP.equals(action);
    }

    private static boolean isSaveAction(String action) {
        return GroupUtil.ACTION_UPDATE_GROUP.equals(action)
                || GroupUtil.ACTION_ADD_TO_GROUP.equals(action)
                || GroupUtil.ACTION_REMOVE_FROM_GROUP.equals(action);
    }

    private static int getToastMessageForSaveAction(String action) {
        if (GroupUtil.ACTION_UPDATE_GROUP.equals(action)) return R.string.groupUpdatedToast;
        if (GroupUtil.ACTION_ADD_TO_GROUP.equals(action)) return R.string.groupMembersAddedToast;
        if (GroupUtil.ACTION_REMOVE_FROM_GROUP.equals(action))
            return R.string.groupMembersRemovedToast;
        throw new IllegalArgumentException("Unhanded contact save action " + action);
    }

    private int getGroupCount() {
        return mMembersFragment != null && mMembersFragment.getAdapter() != null
                ? mMembersFragment.getAdapter().getCount() : -1;
    }

    private void replaceGroupMembersFragment() {
        mMembersFragment = GroupMembersFragment.newInstance(mGroupUri);
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        addGroupsAndFiltersFragments(transaction);
        transaction.replace(R.id.fragment_container_inner, mMembersFragment, TAG_GROUP_MEMBERS)
                .commitAllowingStateLoss();
    }

    @Override
    protected void onGroupMenuItemClicked(long groupId, String title) {
        if (mMembersFragment.getGroupMetadata().groupId != groupId) {
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

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if (mMembersFragment.isEditMode()) {
            mMembersFragment.setEditMode(false);
            mMembersFragment.getActionBarAdapter().setSelectionMode(false);
            mMembersFragment.displayDeleteButtons(false);
        } else if (mMembersFragment.getActionBarAdapter().isSelectionMode()) {
            mMembersFragment.getActionBarAdapter().setSelectionMode(false);
            mMembersFragment.displayCheckBoxes(false);
        } else if (mMembersFragment.getActionBarAdapter().isSearchMode()) {
            mMembersFragment.getActionBarAdapter().setSearchMode(false);
        } else {
            switchToAllContacts();
            super.onBackPressed();
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

    @Override
    protected GroupMetadata getGroupMetadata() {
        return mMembersFragment.getGroupMetadata();
    }
}
