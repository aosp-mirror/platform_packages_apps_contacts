/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.tests.quickcontact;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.tests.R;

public class QuickContactTestsActivity extends Activity {
    private static final int REQUEST_CODE_PICK = 1;
    private static final String PREF_NAME = "quick_contact_prefs";
    private static final String PREF_SETTING_URI = "uri";

    private Button mPickContact;
    private TextView mUriTextView;
    private QuickContactBadge mSmallBadge1;
    private QuickContactBadge mSmallBadge2;
    private QuickContactBadge mMediumBadge1;
    private QuickContactBadge mMediumBadge2;
    private QuickContactBadge mLargeBadge1;
    private QuickContactBadge mLargeBadge2;

    private Uri mContactUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.quick_contact_tests);

        mPickContact = (Button) findViewById(R.id.pick_contact);
        mUriTextView = (TextView) findViewById(R.id.uri);
        mSmallBadge1 = (QuickContactBadge) findViewById(R.id.small_badge1);
        mSmallBadge2 = (QuickContactBadge) findViewById(R.id.small_badge2);
        mMediumBadge1 = (QuickContactBadge) findViewById(R.id.medium_badge1);
        mMediumBadge2 = (QuickContactBadge) findViewById(R.id.medium_badge2);
        mLargeBadge1 = (QuickContactBadge) findViewById(R.id.large_badge1);
        mLargeBadge2 = (QuickContactBadge) findViewById(R.id.large_badge2);

        mPickContact.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
                startActivityForResult(intent , REQUEST_CODE_PICK);
            }
        });

        // Load Uri if known
        final SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        final String uriString = sharedPreferences.getString(PREF_SETTING_URI, null);
        if (uriString != null) {
            mContactUri = Uri.parse(uriString);
            assignUri();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_CANCELED) return;
        switch (requestCode) {
            case REQUEST_CODE_PICK: {
                mContactUri = data.getData();
                assignUri();
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        final SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        final Editor editor = sharedPreferences.edit();
        editor.putString(PREF_SETTING_URI, mContactUri == null ? null : mContactUri.toString());
        editor.apply();
    }

    private void assignUri() {
        mUriTextView.setText(mContactUri.toString());
        mSmallBadge1.assignContactUri(mContactUri);
        mSmallBadge2.assignContactUri(mContactUri);
        mMediumBadge1.assignContactUri(mContactUri);
        mMediumBadge2.assignContactUri(mContactUri);
        mLargeBadge1.assignContactUri(mContactUri);
        mLargeBadge2.assignContactUri(mContactUri);
    }
}
