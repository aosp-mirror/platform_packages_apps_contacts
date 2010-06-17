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

package com.android.contacts.views.editor.typeViews;

import com.android.contacts.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

public class FooterView extends LinearLayout {
    private Button mAddInformationButton;
    private Button mSeparateButton;
    private Button mDeleteButton;
    private Listener mListener;

    public FooterView(Context context) {
        super(context);
    }

    public FooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FooterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static FooterView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (FooterView) inflater.inflate(R.layout.list_edit_item_footer, parent, attachToRoot);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAddInformationButton = (Button) findViewById(R.id.add_information);
        mAddInformationButton.setOnClickListener(mClickListener);

        mSeparateButton = (Button) findViewById(R.id.separate);
        mSeparateButton.setOnClickListener(mClickListener);

        mDeleteButton = (Button) findViewById(R.id.delete);
        mDeleteButton.setOnClickListener(mClickListener);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    private OnClickListener mClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (mListener == null) return;
            switch (v.getId()) {
                case R.id.add_information:
                    mListener.onAddClicked();
                    break;
                case R.id.separate:
                    mListener.onSeparateClicked();
                    break;
                case R.id.delete:
                    mListener.onDeleteClicked();
                    break;
            }
        }
    };

    public static interface Listener {
        void onAddClicked();
        void onSeparateClicked();
        void onDeleteClicked();
    }
}
