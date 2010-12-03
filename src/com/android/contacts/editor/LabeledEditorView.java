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
import com.android.contacts.model.AccountType.EditType;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.ThemeUtils;
import com.android.contacts.util.DialogManager.DialogShowingView;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import java.util.List;

/**
 * Base class for editors that handles labels and values.
 * Uses {@link ValuesDelta} to read any existing
 * {@link Entity} values, and to correctly write any changes values.
 */
public abstract class LabeledEditorView extends ViewGroup implements Editor, DialogShowingView {
    protected static final String DIALOG_ID_KEY = "dialog_id";
    private static final int DIALOG_ID_CUSTOM = 1;

    private static final int INPUT_TYPE_CUSTOM = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

    private Button mLabel;
    private ImageButton mDelete;

    private DataKind mKind;
    private ValuesDelta mEntry;
    private EntityDelta mState;
    private boolean mReadOnly;

    private EditType mType;
    // Used only when a user tries to use custom label.
    private EditType mPendingType;

    private ViewIdGenerator mViewIdGenerator;
    private DialogManager mDialogManager = null;
    private EditorListener mListener;

    public LabeledEditorView(Context context) {
        super(context);
    }

    public LabeledEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LabeledEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Subtract padding from the borders ==> x1 variables
        int t1 = getPaddingTop();
        int r1 = getMeasuredWidth() - getPaddingRight();
        int b1 = getMeasuredHeight() - getPaddingBottom();

        final int r2;
        if (mDelete != null) {
            r2 = r1 - mDelete.getMeasuredWidth();
            mDelete.layout(
                    r2, b1 - mDelete.getMeasuredHeight(),
                    r1, b1);
        } else {
            r2 = r1;
        }

        if (mLabel != null) {
            mLabel.layout(
                    r2 - mLabel.getMeasuredWidth(), t1,
                    r2, t1 + mLabel.getMeasuredHeight());
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        final int padding = getPaddingTop() + getPaddingBottom();
        final int deleteHeight = mDelete != null ? mDelete.getMeasuredHeight() : 0;
        final int labelHeight = mLabel != null ? mLabel.getMeasuredHeight() : 0;

        final int height = padding +
                Math.max(Math.max(deleteHeight, labelHeight), getEditorHeight());

        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    protected abstract int getEditorHeight();

    /**
     * Creates or removes the type/label button. Doesn't do anything if already correctly configured
     */
    private void setupLabelButton(boolean shouldExist) {
        if (shouldExist && mLabel == null) {
            mLabel = new Button(mContext, null, android.R.attr.dropDownSpinnerStyle);
            final int width =
                    mContext.getResources().getDimensionPixelSize(R.dimen.editor_type_label_width);
            mLabel.setLayoutParams(new LayoutParams(width, LayoutParams.WRAP_CONTENT));
            mLabel.setGravity(Gravity.RIGHT);
            mLabel.setTextColor(getResources().getColor(R.color.editor_label_text_color));
            mLabel.setFocusable(true);
            mLabel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLabelPopupMenu();
                }
            });
            addView(mLabel);
        } else if (!shouldExist && mLabel != null) {
            removeView(mLabel);
            mLabel = null;
        }
    }

    /**
     * Creates or removes the remove button. Doesn't do anything if already correctly configured
     */
    private void setupDeleteButton(boolean shouldExist) {
        if (shouldExist && mDelete == null) {
            mDelete = new ImageButton(mContext);
            mDelete.setImageResource(R.drawable.ic_menu_remove_field_holo_light);
            mDelete.setBackgroundResource(
                    ThemeUtils.getSelectableItemBackground(mContext.getTheme()));
            final Resources resources = mContext.getResources();
            mDelete.setPadding(
                    resources.getDimensionPixelOffset(R.dimen.editor_round_button_padding_left),
                    resources.getDimensionPixelOffset(R.dimen.editor_round_button_padding_top),
                    resources.getDimensionPixelOffset(R.dimen.editor_round_button_padding_right),
                    resources.getDimensionPixelOffset(R.dimen.editor_round_button_padding_bottom));
            mDelete.setContentDescription(
                    getResources().getText(R.string.description_minus_button));
            mDelete.setLayoutParams(
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            mDelete.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // defer removal of this button so that the pressed state is visible shortly
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            // Keep around in model, but mark as deleted
                            mEntry.markDeleted();

                            ((ViewGroup) getParent()).removeView(LabeledEditorView.this);

                            if (mListener != null) {
                                // Notify listener when present
                                mListener.onDeleted(LabeledEditorView.this);
                            }
                        }
                    });
                }
            });
            addView(mDelete);
        } else if (!shouldExist && mDelete != null) {
            removeView(mDelete);
            mDelete = null;
        }
    }

    protected void onOptionalFieldVisibilityChange() {
        if (mListener != null) {
            mListener.onRequest(EditorListener.EDITOR_FORM_CHANGED);
        }
    }

    @Override
    public void setEditorListener(EditorListener listener) {
        mListener = listener;
    }

    @Override
    public void setDeletable(boolean deletable) {
        setupDeleteButton(deletable);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mLabel != null) mLabel.setEnabled(enabled);

        if (mDelete != null) mDelete.setEnabled(enabled);
    }

    public Button getLabel() {
        return mLabel;
    }

    public ImageButton getDelete() {
        return mDelete;
    }

    protected DataKind getKind() {
        return mKind;
    }

    protected ValuesDelta getEntry() {
        return mEntry;
    }

    protected EditType getType() {
        return mType;
    }

    /**
     * Build the current label state based on selected {@link EditType} and
     * possible custom label string.
     */
    private void rebuildLabel() {
        if (mLabel == null) return;
        // Handle undetected types
        if (mType == null) {
            mLabel.setText(R.string.unknown);
            return;
        }

        if (mType.customColumn != null) {
            // Use custom label string when present
            final String customText = mEntry.getAsString(mType.customColumn);
            if (customText != null) {
                mLabel.setText(customText);
                return;
            }
        }

        // Otherwise fall back to using default label
        mLabel.setText(mType.labelRes);
    }

    /** {@inheritDoc} */
    @Override
    public void onFieldChanged(String column, String value) {
        // Field changes are saved directly
        mEntry.put(column, value);
        if (mListener != null) {
            mListener.onRequest(EditorListener.FIELD_CHANGED);
        }
    }

    protected void rebuildValues() {
        setValues(mKind, mEntry, mState, mReadOnly, mViewIdGenerator);
    }

    /**
     * Prepare this editor using the given {@link DataKind} for defining
     * structure and {@link ValuesDelta} describing the content to edit.
     */
    @Override
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        mKind = kind;
        mEntry = entry;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;
        setId(vig.getId(state, kind, entry, ViewIdGenerator.NO_VIEW_INDEX));

        if (!entry.isVisible()) {
            // Hide ourselves entirely if deleted
            setVisibility(View.GONE);
            return;
        }
        setVisibility(View.VISIBLE);

        // Display label selector if multiple types available
        final boolean hasTypes = EntityModifier.hasEditTypes(kind);
        setupLabelButton(hasTypes);
        if (mLabel != null) mLabel.setEnabled(!readOnly);
        if (hasTypes) {
            mType = EntityModifier.getCurrentType(entry, kind);
            rebuildLabel();
        }
    }

    public ValuesDelta getValues() {
        return mEntry;
    }

    /**
     * Prepare dialog for entering a custom label. The input value is trimmed: white spaces before
     * and after the input text is removed.
     * <p>
     * If the final value is empty, this change request is ignored;
     * no empty text is allowed in any custom label.
     */
    private Dialog createCustomDialog() {
        final EditText customType = new EditText(mContext);
        customType.setInputType(INPUT_TYPE_CUSTOM);
        customType.requestFocus();

        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.customLabelPickerTitle);
        builder.setView(customType);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String customText = customType.getText().toString().trim();
                if (ContactsUtils.isGraphic(customText)) {
                    // Now we're sure it's ok to actually change the type value.
                    mType = mPendingType;
                    mPendingType = null;
                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    mEntry.put(mType.customColumn, customText);
                    rebuildLabel();
                    requestFocusForFirstEditField();
                    onLabelRebuilt();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    /**
     * Called after the label has changed (either chosen from the list or entered in the Dialog)
     */
    protected void onLabelRebuilt() {
    }

    /**
     * Show PopupMenu for picking a new {@link EditType} or entering a
     * custom label. This dialog is limited to the valid types as determined
     * by {@link EntityModifier}.
     */
    public void showLabelPopupMenu() {
        // Build list of valid types, including the current value
        final List<EditType> validTypes = EntityModifier.getValidTypes(mState, mKind, mType);

        final PopupMenu popupMenu = new PopupMenu(getContext(), mLabel);
        final Menu menu = popupMenu.getMenu();

        for (int i = 0; i < validTypes.size(); i++) {
            final EditType type = validTypes.get(i);
            menu.add(Menu.NONE, i, Menu.NONE, type.labelRes);
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final EditType selected = validTypes.get(item.getItemId());
                if (selected.customColumn != null) {
                    // Show custom label dialog if requested by type.
                    //
                    // Only when the custum value input in the next step is correct one.
                    // this method also set the type value to what the user requested here.
                    mPendingType = selected;
                    showDialog(DIALOG_ID_CUSTOM);
                } else {
                    // User picked type, and we're sure it's ok to actually write the entry.
                    mType = selected;
                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    rebuildLabel();
                    requestFocusForFirstEditField();
                    onLabelRebuilt();
                }
                return true;
            }
        });

        popupMenu.show();
    }

    /* package */
    void showDialog(int bundleDialogId) {
        Bundle bundle = new Bundle();
        bundle.putInt(DIALOG_ID_KEY, bundleDialogId);
        getDialogManager().showDialogInView(this, bundle);
    }

    private DialogManager getDialogManager() {
        if (mDialogManager == null) {
            Context context = getContext();
            if (!(context instanceof DialogManager.DialogShowingViewActivity)) {
                throw new IllegalStateException(
                        "View must be hosted in an Activity that implements " +
                        "DialogManager.DialogShowingViewActivity");
            }
            mDialogManager = ((DialogManager.DialogShowingViewActivity)context).getDialogManager();
        }
        return mDialogManager;
    }

    @Override
    public Dialog createDialog(Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        int dialogId = bundle.getInt(DIALOG_ID_KEY);
        switch (dialogId) {
            case DIALOG_ID_CUSTOM:
                return createCustomDialog();
            default:
                throw new IllegalArgumentException("Invalid dialogId: " + dialogId);
        }
    }

    protected abstract void requestFocusForFirstEditField();
}
