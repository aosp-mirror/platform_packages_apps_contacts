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

package com.android.contacts.common.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.contacts.common.R;

import java.util.ArrayList;
import java.util.List;

/**
 * List activity which displays the call subject history.  Shows at the bottom of the screen,
 * on top of existing content.
 */
public class CallSubjectHistory extends Activity {
    public static final String EXTRA_CHOSEN_SUBJECT =
            "com.android.contracts.common.dialog.extra.CHOSEN_SUBJECT";

    private View mBackground;
    private ListView mSubjectList;
    private SharedPreferences mSharedPreferences;
    private List<String> mSubjects;

    /**
     * Click listener which handles user clicks outside of the list view.  Dismisses the activity
     * and returns a {@link Activity#RESULT_CANCELED} result code.
     */
    private View.OnClickListener mBackgroundListner = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setResult(RESULT_CANCELED);
            finish();
        }
    };

    /**
     * Item click listener which handles user clicks on the items in the list view.  Dismisses
     * the activity, returning the subject to the caller and closing the activity with the
     * {@link Activity#RESULT_OK} result code.
     */
    private AdapterView.OnItemClickListener mItemClickListener =
            new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
            returnSubject(mSubjects.get(position));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSubjects = CallSubjectDialog.loadSubjectHistory(mSharedPreferences);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.call_subject_history);

        mBackground = findViewById(R.id.background);
        mBackground.setOnClickListener(mBackgroundListner);

        mSubjectList = (ListView) findViewById(R.id.subject_list);
        mSubjectList.setOnItemClickListener(mItemClickListener);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mSubjects);
        mSubjectList.setAdapter(adapter);
    }

    /**
     * Closes the activity and returns the subject chosen by the user to the caller.
     *
     * @param chosenSubject The chosen subject.
     */
    private void returnSubject(String chosenSubject) {
        Intent intent = getIntent();
        intent.putExtra(EXTRA_CHOSEN_SUBJECT, chosenSubject);
        setResult(RESULT_OK, intent);
        finish();
    }
}
