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

package com.android.contacts.activities;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.group.GroupDetailDisplayUtils;
import com.android.contacts.group.GroupDetailFragment;

import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

public class GroupDetailActivity extends ContactsActivity {

    private static final String TAG = "GroupDetailActivity";

    private boolean mShowGroupSourceInActionBar;

    private String mAccountTypeString;
    private String mGroupSourceAction;
    private String mGroupSourceUri;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // TODO: Create Intent Resolver to handle the different ways users can get to this list.
        // TODO: Handle search or key down

        setContentView(R.layout.group_detail_activity);

        mShowGroupSourceInActionBar = getResources().getBoolean(
                R.bool.config_show_group_action_in_action_bar);

        GroupDetailFragment fragment = (GroupDetailFragment) getFragmentManager().findFragmentById(
                R.id.group_detail_fragment);
        fragment.setListener(mFragmentListener);
        fragment.setShowGroupSourceInActionBar(mShowGroupSourceInActionBar);
        fragment.loadGroup(getIntent().getData());
        fragment.closeActivityAfterDelete(true);

        // We want the UP affordance but no app icon.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    private final GroupDetailFragment.Listener mFragmentListener =
            new GroupDetailFragment.Listener() {

        @Override
        public void onGroupSizeUpdated(String size) {
            getActionBar().setSubtitle(size);
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            getActionBar().setTitle(title);
        }

        @Override
        public void onGroupSourceUpdated(
                String accountTypeString, String groupSourceAction, String groupSourceActionUri) {
            mAccountTypeString = accountTypeString;
            mGroupSourceAction = groupSourceAction;
            mGroupSourceUri = groupSourceActionUri;
            invalidateOptionsMenu();
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(GroupDetailActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivity(intent);
        }

        @Override
        public void onContactSelected(Uri contactUri) {
            startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
        }

    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mShowGroupSourceInActionBar) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.group_source, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mShowGroupSourceInActionBar) {
            return false;
        }
        MenuItem groupSourceMenuItem = menu.findItem(R.id.menu_group_source);
        if (groupSourceMenuItem == null) {
            return false;
        }
        if (TextUtils.isEmpty(mAccountTypeString) || TextUtils.isEmpty(mGroupSourceAction) ||
                TextUtils.isEmpty(mGroupSourceUri)) {
            groupSourceMenuItem.setVisible(false);
            return false;
        }
        View groupSourceView = GroupDetailDisplayUtils.getNewGroupSourceView(this);
        GroupDetailDisplayUtils.bindGroupSourceView(this, groupSourceView,
                mAccountTypeString);
        groupSourceView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(mGroupSourceAction, Uri.parse(mGroupSourceUri)));
            }
        });
        groupSourceMenuItem.setActionView(groupSourceView);
        groupSourceMenuItem.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, PeopleActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSearchRequested() {
        return true; // Don't respond to the search key.
    }
}
