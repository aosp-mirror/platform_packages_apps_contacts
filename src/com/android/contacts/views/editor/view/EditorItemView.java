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

package com.android.contacts.views.editor.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditorItemView extends LinearLayout implements OnClickListener, OnFocusChangeListener {
    private final int CAPTION_WIDTH_DIP = 70;
    private final int TYPE_BUTTON_WIDTH_DIP = 50;

    private TextView mCaptionTextView;
    private LinearLayout mFieldContainer;
    private Button mTypeButton;

    private Listener mListener;

    private int[] mTypeResIds;
    private int mCustomTypeIndex;

    private boolean mHasFocus;

    public EditorItemView(Context context) {
        super(context);
        createEmptyLayout();
    }

    public EditorItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        createEmptyLayout();
    }

    public EditorItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        createEmptyLayout();
    }

    private void createEmptyLayout() {
        final int captionWidthPixels =
                (int) (getResources().getDisplayMetrics().density * CAPTION_WIDTH_DIP);

        // Caption
        mCaptionTextView = new TextView(getContext());
        mCaptionTextView.setLayoutParams(new LinearLayout.LayoutParams(captionWidthPixels,
                LayoutParams.WRAP_CONTENT));
        addView(mCaptionTextView);

        // Text Fields
        mFieldContainer = new LinearLayout(getContext());
        mFieldContainer.setOrientation(LinearLayout.VERTICAL);
        mFieldContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT,
                1.0f));
        addView(mFieldContainer);
    }

    /**
     * Configures the View. This function must only be called once after construction
     * @param captionResId
     *         The caption of this item
     * @param fieldResIds
     *         Ressource Ids of the editable fields
     * @param typeResIds
     *         A list of user selectable type-ressource-ids. If this parameter is null,
     *         no type can be selected
     * @param customTypeIndex
     *         The index of the type representing "Custom" (allowing the user to type
     *         a custom type. If this is -1, no type is custom. If types is null, this
     *         parameter is ignored.
     */
    public void configure(int captionResId, int[] fieldResIds, int[] typeResIds,
            int customTypeIndex) {
        mCaptionTextView.setText(captionResId);

        // Create Fields
        for (int fieldResId : fieldResIds) {
            final EditText fieldEditText = new EditText(getContext());
            fieldEditText.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            fieldEditText.setContentDescription(getResources().getString(fieldResId));
            fieldEditText.setOnFocusChangeListener(this);
            mFieldContainer.addView(fieldEditText);
        }

        // Configure Type Button
        mCustomTypeIndex = customTypeIndex;
        mTypeResIds = typeResIds;
        if (typeResIds != null) {
            final int typeButtonWidthPixels =
                (int) (getResources().getDisplayMetrics().density * TYPE_BUTTON_WIDTH_DIP);
            mTypeButton = new Button(getContext());
            mTypeButton.setLayoutParams(new LinearLayout.LayoutParams(typeButtonWidthPixels,
                    LayoutParams.WRAP_CONTENT));
        }
    }

    public void setFieldValue(int index, CharSequence value) {
        final EditText editText = (EditText) mFieldContainer.getChildAt(index);
        editText.setText(value);
    }

    public String getFieldValue(int index) {
        final EditText editText = (EditText) mFieldContainer.getChildAt(index);
        final CharSequence resultCharSequence = editText.getText();
        if (resultCharSequence == null) return "";
        return resultCharSequence.toString();
    }

    public void setType(int typeValue, String labelValue) {
        if (mTypeButton == null) {
            return;
        }
        if (typeValue == mCustomTypeIndex) {
            mTypeButton.setText(labelValue);
        } else {
            final int typeResId = mTypeResIds[typeValue];
            mTypeButton.setText(getResources().getString(typeResId));
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mTypeButton) {

            return;
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // Focus was gained, lost etc. We should only fire an event if we lost focus to another
        // control
        if (mHasFocus && !hasFocus && mListener != null) {
            mListener.onFocusLost();
        }
        mHasFocus = hasFocus;
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public interface Listener {
        /**
         * Called when the user has changed the Type.
         * @param newIndex
         *         The index of the newly selected type (corresponds to the array passed as
         *         typeResIds in configure.
         * @param customText
         *         If this is the type "Custom", this field contains the user-entered text.
         *         Otherwise this parameter is null
         */
        void onTypeChanged(int newIndex, String customText);

        /**
         * Called when the user has navigated away from this editor (this is not raised if
         * the focus switches between fields of the same editor).
         */
        void onFocusLost();
    }
}
