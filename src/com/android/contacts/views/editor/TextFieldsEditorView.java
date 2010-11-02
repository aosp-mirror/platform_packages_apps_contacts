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

package com.android.contacts.views.editor;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.BaseAccountType.DataKind;
import com.android.contacts.model.BaseAccountType.EditField;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.Context;
import android.content.Entity;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * Simple editor that handles labels and any {@link EditField} defined for
 * the entry. Uses {@link ValuesDelta} to read any existing
 * {@link Entity} values, and to correctly write any changes values.
 */
public class TextFieldsEditorView extends LabeledEditorView {
    private EditText[] mFieldEditTexts = null;
    private ImageButton mMoreOrLess;
    private boolean mHideOptional = true;
    private boolean mHasShortAndLongForms;

    public TextFieldsEditorView(Context context) {
        super(context);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int l1 = getPaddingLeft();
        int t1 = getPaddingTop();
        int r1 = getMeasuredWidth() - getPaddingRight();
        int b1 = getMeasuredHeight() - getPaddingBottom();

        // MoreOrLess Button
        final boolean hasMoreOrLess = mMoreOrLess != null;
        if (hasMoreOrLess) {
            mMoreOrLess.layout(
                    r1 - mMoreOrLess.getMeasuredWidth(), b1 - mMoreOrLess.getMeasuredHeight(),
                    r1, b1);
        }

        // Fields
        // Subtract buttons left and right if necessary
        final int l2 = (getLabel() != null) ? l1 + getLabel().getMeasuredWidth() : l1;
        final int r2 = r1 - Math.max(
                (getDelete() != null) ? getDelete().getMeasuredWidth() : 0,
                hasMoreOrLess ? mMoreOrLess.getMeasuredWidth() : 0);
        int y = t1;
        if (mFieldEditTexts != null) {
            for (EditText editText : mFieldEditTexts) {
                if (editText.getVisibility() != View.GONE) {
                    int height = editText.getMeasuredHeight();
                    editText.layout(
                            l2, t1 + y,
                            r2, t1 + y + height);
                    y += height;
                }
            }
        }
    }

    @Override
    protected int getEditorHeight() {
        int result = 0;
        // summarize the EditText heights
        if (mFieldEditTexts != null) {
            for (EditText editText : mFieldEditTexts) {
                if (editText.getVisibility() != View.GONE) {
                    result += editText.getMeasuredHeight();
                }
            }
        }
        // Ensure there is enough space for the minus button
        if (mMoreOrLess != null) {
            result = Math.max(mMoreOrLess.getMeasuredHeight(), result);
        }
        return result;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (mFieldEditTexts != null) {
            for (int index = 0; index < mFieldEditTexts.length; index++) {
                mFieldEditTexts[index].setEnabled(enabled);
            }
        }
        if (mMoreOrLess != null) mMoreOrLess.setEnabled(enabled);
    }

    /**
     * Creates or removes the type/label button. Doesn't do anything if already correctly configured
     */
    private void setupMoreOrLessButton(boolean shouldExist, boolean collapsed) {
        if (shouldExist) {
            if (mMoreOrLess == null) {
                // Unfortunately, the style passed as constructor-parameter is mostly ignored,
                // so we have to set the Background and Image seperately. However, if it is not
                // given, the size of the control is wrong
                mMoreOrLess = new ImageButton(mContext, null, R.style.EmptyButton);
                mMoreOrLess.setLayoutParams(
                        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                mMoreOrLess.setBackgroundResource(R.drawable.btn_circle);
                mMoreOrLess.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Save focus
                        final View focusedChild = getFocusedChild();
                        final int focusedViewId = focusedChild == null ? -1 : focusedChild.getId();

                        // Reconfigure GUI
                        mHideOptional = !mHideOptional;
                        onOptionalFieldVisibilityChange();
                        rebuildValues();

                        // Restore focus
                        View newFocusView = findViewById(focusedViewId);
                        if (newFocusView == null || newFocusView.getVisibility() == GONE) {
                            // find first visible child
                            newFocusView = TextFieldsEditorView.this;
                        }
                        if (newFocusView != null) {
                            newFocusView.requestFocus();
                        }
                    }
                });
                addView(mMoreOrLess);
            }
            mMoreOrLess.setImageResource(
                    collapsed ? R.drawable.ic_btn_round_more : R.drawable.ic_btn_round_less);
        } else if (mMoreOrLess != null) {
            removeView(mMoreOrLess);
            mMoreOrLess = null;
        }
    }

    @Override
    protected void requestFocusForFirstEditField() {
        if (mFieldEditTexts != null && mFieldEditTexts.length != 0) {
            EditText firstField = null;
            boolean anyFieldHasFocus = false;
            for (EditText editText : mFieldEditTexts) {
                if (firstField == null && editText.getVisibility() == View.VISIBLE) {
                    firstField = editText;
                }
                if (editText.hasFocus()) {
                    anyFieldHasFocus = true;
                    break;
                }
            }
            if (!anyFieldHasFocus && firstField != null) {
                firstField.requestFocus();
            }
        }
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        // Remove edit texts that we currently have
        if (mFieldEditTexts != null) {
            for (EditText fieldEditText : mFieldEditTexts) {
                removeView(fieldEditText);
            }
        }
        boolean hidePossible = false;

        int fieldCount = kind.fieldList.size();
        mFieldEditTexts = new EditText[fieldCount];
        for (int index = 0; index < fieldCount; index++) {
            final EditField field = kind.fieldList.get(index);
            final EditText fieldView = new EditText(mContext);
            fieldView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            fieldView.setGravity(Gravity.TOP);
            mFieldEditTexts[index] = fieldView;
            fieldView.setId(vig.getId(state, kind, entry, index));
            if (field.titleRes > 0) {
                fieldView.setHint(field.titleRes);
            }
            int inputType = field.inputType;
            fieldView.setInputType(inputType);
            if (inputType == InputType.TYPE_CLASS_PHONE) {
                fieldView.addTextChangedListener(new PhoneNumberFormattingTextWatcher(
                        ContactsUtils.getCurrentCountryIso(mContext)));
            }
            fieldView.setMinLines(field.minLines);

            // Read current value from state
            final String column = field.column;
            final String value = entry.getAsString(column);
            fieldView.setText(value);

            // Prepare listener for writing changes
            fieldView.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    // Trigger event for newly changed value
                    onFieldChanged(column, s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            fieldView.setEnabled(!readOnly);

            if (field.shortForm) {
                hidePossible = true;
                mHasShortAndLongForms = true;
                fieldView.setVisibility(mHideOptional ? View.VISIBLE : View.GONE);
            } else if (field.longForm) {
                hidePossible = true;
                mHasShortAndLongForms = true;
                fieldView.setVisibility(mHideOptional ? View.GONE : View.VISIBLE);
            } else {
                // Hide field when empty and optional value
                final boolean couldHide = (!ContactsUtils.isGraphic(value) && field.optional);
                final boolean willHide = (mHideOptional && couldHide);
                fieldView.setVisibility(willHide ? View.GONE : View.VISIBLE);
                hidePossible = hidePossible || couldHide;
            }

            addView(fieldView);
        }

        // When hiding fields, place expandable
        setupMoreOrLessButton(hidePossible, mHideOptional);
        if (mMoreOrLess != null) mMoreOrLess.setEnabled(!readOnly);
    }

    /**
     * Returns true if the editor is currently configured to show optional fields.
     */
    public boolean areOptionalFieldsVisible() {
        return !mHideOptional;
    }

    public boolean hasShortAndLongForms() {
        return mHasShortAndLongForms;
    }

    /**
     * Populates the bound rectangle with the bounds of the last editor field inside this view.
     */
    public void acquireEditorBounds(Rect bounds) {
        if (mFieldEditTexts != null) {
            for (int i = mFieldEditTexts.length; --i >= 0;) {
                EditText editText = mFieldEditTexts[i];
                if (editText.getVisibility() == View.VISIBLE) {
                    bounds.set(editText.getLeft(), editText.getTop(), editText.getRight(),
                            editText.getBottom());
                    return;
                }
            }
        }
    }

    /**
     * Saves the visibility of the child EditTexts, and mHideOptional.
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);

        ss.mHideOptional = mHideOptional;

        final int numChildren = mFieldEditTexts.length;
        ss.mVisibilities = new int[numChildren];
        for (int i = 0; i < numChildren; i++) {
            ss.mVisibilities[i] = mFieldEditTexts[i].getVisibility();
        }

        return ss;
    }

    /**
     * Restores the visibility of the child EditTexts, and mHideOptional.
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mHideOptional = ss.mHideOptional;

        int numChildren = Math.min(mFieldEditTexts.length, ss.mVisibilities.length);
        for (int i = 0; i < numChildren; i++) {
            mFieldEditTexts[i].setVisibility(ss.mVisibilities[i]);
        }
    }

    private static class SavedState extends BaseSavedState {
        public boolean mHideOptional;
        public int[] mVisibilities;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mVisibilities = new int[in.readInt()];
            in.readIntArray(mVisibilities);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mVisibilities.length);
            out.writeIntArray(mVisibilities);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
