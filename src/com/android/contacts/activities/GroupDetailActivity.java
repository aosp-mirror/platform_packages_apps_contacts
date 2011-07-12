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
import com.android.contacts.group.GroupDetailFragment;

import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

public class GroupDetailActivity extends ContactsActivity {

    private static final String TAG = "GroupDetailActivity";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // TODO: Create Intent Resolver to handle the different ways users can get to this list.
        // TODO: Handle search or key down

        setContentView(R.layout.group_detail_activity);

        GroupDetailFragment fragment = (GroupDetailFragment) getFragmentManager().findFragmentById(
                R.id.group_detail_fragment);
        fragment.setListener(mFragmentListener);
        fragment.loadGroup(getIntent().getData());
        fragment.closeActivityAfterDelete(true);

        ActionBar actionBar =  getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
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
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
