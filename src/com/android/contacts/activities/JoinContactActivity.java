/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.contacts.activities;


import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.list.JoinContactListFragment;
import com.android.contacts.list.OnContactPickerActionListener;

/**
 * An activity that shows a list of contacts that can be joined with the target contact.
 */
public class JoinContactActivity extends ContactsActivity
        implements OnQueryTextListener, OnCloseListener, OnFocusChangeListener {

    private static final String TAG = "JoinContactActivity";

    /**
     * The action for the join contact activity.
     * <p>
     * Input: extra field {@link #EXTRA_TARGET_CONTACT_ID} is the aggregate ID.
     * TODO: move to {@link ContactsContract}.
     */
    public static final String JOIN_CONTACT = "com.android.contacts.action.JOIN_CONTACT";

    /**
     * Used with {@link #JOIN_CONTACT} to give it the target for aggregation.
     * <p>
     * Type: LONG
     */
    public static final String EXTRA_TARGET_CONTACT_ID = "com.android.contacts.action.CONTACT_ID";

    private static final String KEY_TARGET_CONTACT_ID = "targetContactId";

    private long mTargetContactId;

    private JoinContactListFragment mListFragment;
    private SearchView mSearchView;

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof JoinContactListFragment) {
            mListFragment = (JoinContactListFragment) fragment;
            setupActionListener();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mTargetContactId = intent.getLongExtra(EXTRA_TARGET_CONTACT_ID, -1);
        if (mTargetContactId == -1) {
            Log.e(TAG, "Intent " + intent.getAction() + " is missing required extra: "
                    + EXTRA_TARGET_CONTACT_ID);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        setContentView(R.layout.join_contact_picker);
        setTitle(R.string.titleJoinContactDataWith);

        if (mListFragment == null) {
            mListFragment = new JoinContactListFragment();

            getFragmentManager().beginTransaction()
                    .replace(R.id.list_container, mListFragment)
                    .commitAllowingStateLoss();
        }

        prepareSearchViewAndActionBar();
    }

    private void setupActionListener() {
        mListFragment.setTargetContactId(mTargetContactId);
        mListFragment.setOnContactPickerActionListener(new OnContactPickerActionListener() {
            @Override
            public void onPickContactAction(Uri contactUri) {
                Intent intent = new Intent(null, contactUri);
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onShortcutIntentCreated(Intent intent) {
            }

            @Override
            public void onCreateNewContactAction() {
            }

            @Override
            public void onEditContactAction(Uri contactLookupUri) {
            }
        });
    }

    private void prepareSearchViewAndActionBar() {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            final View searchViewOnLayout = findViewById(R.id.search_view);
            if (searchViewOnLayout != null) {
                searchViewOnLayout.setVisibility(View.GONE);
            }

            final View searchViewLayout = LayoutInflater.from(actionBar.getThemedContext())
                    .inflate(R.layout.custom_action_bar, null);
            mSearchView = (SearchView) searchViewLayout.findViewById(R.id.search_view);

            // In order to make the SearchView look like "shown via search menu", we need to
            // manually setup its state. See also DialtactsActivity.java and ActionBarAdapter.java.
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setQueryHint(getString(R.string.hint_findContacts));
            mSearchView.setIconified(false);

            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);
            mSearchView.setOnQueryTextFocusChangeListener(this);

            actionBar.setCustomView(searchViewLayout,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        } else {
            mSearchView = (SearchView) findViewById(R.id.search_view);
            mSearchView.setQueryHint(getString(R.string.hint_findContacts));
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnQueryTextFocusChangeListener(this);
        }

        // Clear focus and suppress keyboard show-up.
        mSearchView.clearFocus();
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mListFragment.setQueryString(newText, true);
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onClose() {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        return true;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        switch (view.getId()) {
            case R.id.search_view: {
                if (hasFocus) {
                    showInputMethod(mSearchView.findFocus());
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Go back to previous screen, intending "cancel"
                setResult(RESULT_CANCELED);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_TARGET_CONTACT_ID, mTargetContactId);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTargetContactId = savedInstanceState.getLong(KEY_TARGET_CONTACT_ID);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER
                && resultCode == RESULT_OK) {
            mListFragment.onPickerResult(data);
        }
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(view, 0)) {
                Log.w(TAG, "Failed to show soft input method.");
            }
        }
    }
}
