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

package com.android.contacts.editor;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.model.AccountType.EditField;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.util.ThemeUtils;

import android.content.Context;
import android.content.Entity;
import android.content.res.Resources;
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
    private int mEditorTextSize;

    public TextFieldsEditorView(Context context) {
        super(context);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setEditorTextSize(int textSize) {
        this.mEditorTextSize = textSize;
    }

    @Override
    protected int getLineItemCount() {
        int count = mFieldEditTexts == null ? 0 : mFieldEditTexts.length;
        return Math.max(count, super.getLineItemCount());
    }

    @Override
    protected boolean isLineItemVisible(int row) {
        return mFieldEditTexts != null && mFieldEditTexts[row].getVisibility() != View.GONE;
    }

    @Override
    public int getBaseline(int row) {
        int baseline = super.getBaseline(row);
        if (mFieldEditTexts != null) {
            EditText editText = mFieldEditTexts[row];
            // The text field will be centered vertically in the corresponding line item
            int lineItemHeight = getLineItemHeight(row);
            int offset = (lineItemHeight - editText.getMeasuredHeight()) / 2;
            baseline = Math.max(baseline, offset + editText.getBaseline());
        }
        return baseline;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int l1 = getPaddingLeft();
        int t1 = getPaddingTop();
        int r1 = getMeasuredWidth() - getPaddingRight();

        if ((mMoreOrLess != null)) {
            mMoreOrLess.layout(
                    r1 - mMoreOrLess.getMeasuredWidth(), t1,
                    r1, t1 + mMoreOrLess.getMeasuredHeight());
        }

        // Subtract buttons if necessary
        final int labelWidth = (getLabel() != null) ? getLabel().getMeasuredWidth() : 0;
        final int deleteWidth = (getDelete() != null) ? getDelete().getMeasuredWidth() : 0;
        final int moreOrLessWidth = mMoreOrLess != null ? mMoreOrLess.getMeasuredWidth() : 0;
        final int r2 = r1 - Math.max(deleteWidth, moreOrLessWidth) - labelWidth;

        // Layout text fields
        int y = t1;
        if (mFieldEditTexts != null) {
            for (int i = 0; i < mFieldEditTexts.length; i++) {
                int baseline = getBaseline(i);
                EditText editText = mFieldEditTexts[i];
                if (editText.getVisibility() != View.GONE) {
                    int height = editText.getMeasuredHeight();
                    int top = t1 + y + baseline - editText.getBaseline();
                    editText.layout(
                            l1, top,
                            r2, top + height);
                    y += getLineItemHeight(i);
                }
            }
        }
    }

    @Override
    protected int getLineItemHeight(int row) {
        int fieldHeight = 0;
        int buttonHeight = 0;

        boolean lastLineItem = true;
        if (mFieldEditTexts != null) {
            fieldHeight = mFieldEditTexts[row].getMeasuredHeight();
            lastLineItem = (row == mFieldEditTexts.length - 1);
        }

        // Ensure there is enough space for the more/less button
        if (row == 0) {
            final int moreOrLessHeight = mMoreOrLess != null ? mMoreOrLess.getMeasuredHeight() : 0;
            buttonHeight += moreOrLessHeight;
        }

        // Ensure there is enough space for the minus button
        if (lastLineItem) {
            View deleteButton = getDelete();
            final int deleteHeight = (deleteButton != null) ? deleteButton.getMeasuredHeight() : 0;
            buttonHeight += deleteHeight;
        }

        return Math.max(Math.max(buttonHeight, fieldHeight), super.getLineItemHeight(row));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (mFieldEditTexts != null) {
            for (int index = 0; index < mFieldEditTexts.length; index++) {
                mFieldEditTexts[index].setEnabled(!isReadOnly() && enabled);
            }
        }
        if (mMoreOrLess != null) mMoreOrLess.setEnabled(!isReadOnly() && enabled);
    }

    /**
     * Creates or removes the type/label button. Doesn't do anything if already correctly configured
     */
    private void setupMoreOrLessButton(boolean shouldExist, boolean collapsed) {
        if (shouldExist) {
            if (mMoreOrLess == null) {
                mMoreOrLess = new ImageButton(mContext);
                mMoreOrLess.setBackgroundResource(
                        ThemeUtils.getSelectableItemBackground(mContext.getTheme()));
                final Resources resources = mContext.getResources();
                mMoreOrLess.setPadding(
                        resources.getDimensionPixelOffset(
                                R.dimen.editor_round_button_padding_left),
                        resources.getDimensionPixelOffset(
                                R.dimen.editor_round_button_padding_top),
                        resources.getDimensionPixelOffset(
                                R.dimen.editor_round_button_padding_right),
                        resources.getDimensionPixelOffset(
                                R.dimen.editor_round_button_padding_bottom));
                mMoreOrLess.setLayoutParams(
                        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
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
                        newFocusView.requestFocus();
                    }
                });
                addView(mMoreOrLess);
            }
            mMoreOrLess.setImageResource(collapsed
                    ? R.drawable.ic_menu_expander_minimized_holo_light
                    : R.drawable.ic_menu_expander_maximized_holo_light);
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
            if (mEditorTextSize != 0) {
                fieldView.setTextSize(mEditorTextSize);
            }
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

            fieldView.setEnabled(isEnabled() && !readOnly);

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
        if (mMoreOrLess != null) mMoreOrLess.setEnabled(!readOnly && isEnabled());
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

        @SuppressWarnings({"unused", "hiding" })
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
