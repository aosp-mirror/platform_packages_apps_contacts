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

import android.app.ActionBar;
import android.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.group.GroupMembersListFragment;
import com.android.contacts.group.GroupMembersListFragment.GroupMembersCallbacks;
import com.android.contacts.quickcontact.QuickContactActivity;

/** Displays the members of a group. */
public class GroupMembersActivity extends ContactsActivity implements GroupMembersCallbacks {

    private static final String TAG_GROUP_MEMBERS = "group_members";

    public static final String EXTRA_MEMBERS_COUNT = "membersCount";

    private GroupMembersListFragment mFragment;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.group_members_activity);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        final FragmentManager fragmentManager = getFragmentManager();
        mFragment = (GroupMembersListFragment) fragmentManager.findFragmentByTag(TAG_GROUP_MEMBERS);
        if (mFragment == null) {
            mFragment = new GroupMembersListFragment();
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, mFragment, TAG_GROUP_MEMBERS)
                    .commit();
        }
        mFragment.setGroupUri(getIntent().getData());
        mFragment.setMembersCount(getIntent().getIntExtra(EXTRA_MEMBERS_COUNT, -1));
        mFragment.setCallbacks(this);
    }

    @Override
    public void onHomePressed() {
        onBackPressed();
    }

    @Override
    public void onGroupNameLoaded(String groupName) {
        setTitle(groupName);
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
}
