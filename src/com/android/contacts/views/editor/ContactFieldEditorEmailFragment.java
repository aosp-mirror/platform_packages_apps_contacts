/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.util.ViewGroupAnimator;

import android.content.ContentValues;
import android.content.ContentProviderOperation.Builder;
import android.content.Entity.NamedContentValues;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class ContactFieldEditorEmailFragment extends ContactFieldEditorBaseFragment {
    private EditText mEditText;
    private View mCustomTypeContainer;
    private EditText mCustomTypeTextView;
    private SparseArray<Button> mTypeButtons = new SparseArray<Button>();
    private int mSelectedType;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.contact_field_editor_email_fragment, container,
                false);

        mEditText = (EditText) view.findViewById(R.id.email_edit_text);
        mTypeButtons.append(Email.TYPE_HOME, (Button) view.findViewById(R.id.email_type_home));
        mTypeButtons.append(Email.TYPE_WORK, (Button) view.findViewById(R.id.email_type_work));
        mTypeButtons.append(Email.TYPE_OTHER, (Button) view.findViewById(R.id.email_type_other));
        mTypeButtons.append(Email.TYPE_CUSTOM, (Button) view.findViewById(R.id.email_type_custom));

        mCustomTypeContainer = view.findViewById(R.id.email_edit_custom_type);
        mCustomTypeTextView = (EditText) view.findViewById(R.id.email_edit_type_text);

        for (int i = 0; i < mTypeButtons.size(); i++) {
            final int type = mTypeButtons.keyAt(i);
            final Button button = mTypeButtons.get(type);
            button.setText(Email.getTypeLabelResource(type));
            button.setOnClickListener(mTypeButtonOnClickListener);
        }

        final Button okButton = (Button) view.findViewById(R.id.btn_ok);
        final Button cancelButton = (Button) view.findViewById(R.id.btn_cancel);
        okButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                saveData();

                if (getListener() != null) getListener().onSaved();
            }
        });
        cancelButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (getListener() != null) getListener().onCancel();
            }
        });
        return view;
    }

    private void pushTypeButton(int newType, boolean animate) {
        boolean doAnimation =
                animate && ((mSelectedType == Email.TYPE_CUSTOM) ^ (newType == Email.TYPE_CUSTOM));

        final ViewGroupAnimator animator;
        if (doAnimation) {
            animator = ViewGroupAnimator.captureView(getView().getRootView());
        } else {
            animator = null;
        }
        for (int i = 0; i < mTypeButtons.size(); i++) {
            final int type = mTypeButtons.keyAt(i);
            final Button button = mTypeButtons.get(type);
            button.setTextColor(newType == type ? Color.BLACK : Color.GRAY);
        }

        mCustomTypeContainer.setVisibility(newType == Email.TYPE_CUSTOM ? View.VISIBLE : View.GONE);

        if (doAnimation) animator.animate();

        mSelectedType = newType;
    }

    private OnClickListener mTypeButtonOnClickListener = new OnClickListener() {
        public void onClick(View v) {
            final Button b = (Button) v;
            final int index = mTypeButtons.indexOfValue(b);
            final int type = mTypeButtons.keyAt(index);
            pushTypeButton(type, true);
        }
    };

    @Override
    protected void setupEmpty(ContentValues rawContactEntity) {
        pushTypeButton(Email.TYPE_HOME, false);
    }

    @Override
    protected void loadData(NamedContentValues contentValues) {
        mEditText.setText(contentValues.values.getAsString(Email.ADDRESS));
        pushTypeButton(contentValues.values.getAsInteger(Email.TYPE).intValue(), false);
        mCustomTypeTextView.setText(contentValues.values.getAsString(Email.LABEL));
    }

    @Override
    protected void saveData(Builder builder) {
        builder.withValue(Email.ADDRESS, mEditText.getText().toString());
        builder.withValue(Email.TYPE, mSelectedType);
        builder.withValue(Email.LABEL, mCustomTypeTextView.getText().toString());
    }
}
