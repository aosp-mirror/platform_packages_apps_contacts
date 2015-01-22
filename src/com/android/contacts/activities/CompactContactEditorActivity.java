/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.R;
import com.android.contacts.editor.CompactContactEditorFragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorActivity extends ContactEditorBaseActivity {

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.compact_contact_editor_activity);

        mFragment = (CompactContactEditorFragment) getFragmentManager().findFragmentById(
                R.id.compact_contact_editor_fragment);
        mFragment.setListener(mFragmentListener);

        final String action = getIntent().getAction();
        final Uri uri = (Intent.ACTION_EDIT + "_COMPACT").equals(action)
                ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());
    }
}
