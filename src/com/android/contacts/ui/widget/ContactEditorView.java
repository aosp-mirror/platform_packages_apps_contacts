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

package com.android.contacts.ui.widget;

import com.android.contacts.R;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.EditType;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Custom view that provides all the editor interaction for a specific
 * {@link Contacts} represented through an {@link EntityDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(EntityDelta, ContactsSource)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link Entity} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link EntityModifier} to ensure that {@link ContactsSource} are enforced.
 */
public class ContactEditorView extends ViewHolder {
    private static final int RES_CONTENT = R.layout.act_edit_contact;

    private PhotoEditor mPhoto;
    private DisplayNameEditor mDisplayName;

    private ViewGroup mGeneral;
    private ViewGroup mSecondary;

    public ContactEditorView(Context context) {
        super(context, RES_CONTENT);

        mGeneral = (ViewGroup)mContent.findViewById(R.id.sect_general);
        mSecondary = (ViewGroup)mContent.findViewById(R.id.sect_secondary);

        mPhoto = new PhotoEditor(context);
        mPhoto.swapWith(mContent, R.id.hook_photo);

        mDisplayName = new DisplayNameEditor(context);
        mDisplayName.swapWith(mContent, R.id.hook_displayname);
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    public void setState(EntityDelta state, ContactsSource source) {
        // Remove any existing sections
        mGeneral.removeAllViews();
        mSecondary.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        // Create editor sections for each possible data kind
        for (DataKind kind : source.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mDisplayName.setValues(null, primary, state);
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta firstValue = state.getPrimaryEntry(mimeType);
                mPhoto.setValues(null, firstValue, state);
            } else {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final KindSection section = new KindSection(mContext, kind, state);
                if (kind.secondary) {
                    mSecondary.addView(section.getView());
                } else {
                    mGeneral.addView(section.getView());
                }
            }
        }
    }

    /**
     * Custom view for an entire section of data as segmented by
     * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
     * section header and a trigger for adding new {@link Data} rows.
     */
    protected static class KindSection extends ViewHolder implements OnClickListener, EditorListener {
        private static final int RES_SECTION = R.layout.item_edit_kind;

        private ViewGroup mEditors;
        private View mAdd;
        private TextView mTitle;

        private DataKind mKind;
        private EntityDelta mState;

        public KindSection(Context context, DataKind kind, EntityDelta state) {
            super(context, RES_SECTION);

            mKind = kind;
            mState = state;

            mEditors = (ViewGroup)mContent.findViewById(R.id.kind_editors);

            mAdd = mContent.findViewById(R.id.kind_header);
            mAdd.setOnClickListener(this);

            mTitle = (TextView)mContent.findViewById(R.id.kind_title);
            mTitle.setText(kind.titleRes);

            this.rebuildFromState();
            this.updateAddEnabled();
        }

        public void onDeleted(Editor editor) {
            this.updateAddEnabled();
        }

        /**
         * Build editors for all current {@link #mState} rows.
         */
        public void rebuildFromState() {
            // TODO: build special "stub" entries to help enter first-phone or first-email

            // Remove any existing editors
            mEditors.removeAllViews();

            // Build individual editors for each entry
            if (!mState.hasMimeEntries(mKind.mimeType)) return;
            for (ValuesDelta entry : mState.getMimeEntries(mKind.mimeType)) {
                // Skip entries that aren't visible
                if (!entry.isVisible()) continue;

                final GenericEditor editor = new GenericEditor(mContext);
                editor.setValues(mKind, entry, mState);
                editor.setEditorListener(this);
                mEditors.addView(editor.getView());
            }
        }

        protected void updateAddEnabled() {
            // Set enabled state on the "add" view
            final boolean canInsert = EntityModifier.canInsert(mState, mKind);
            mAdd.setEnabled(canInsert);
        }

        public void onClick(View v) {
            // Insert a new child and rebuild
            EntityModifier.insertChild(mState, mKind);
            this.rebuildFromState();
            this.updateAddEnabled();
        }
    }

    /**
     * Generic definition of something that edits a {@link Data} row through an
     * {@link ValuesDelta} object.
     */
    protected interface Editor {
        /**
         * Prepare this editor for the given {@link ValuesDelta}, which
         * builds any needed views. Any changes performed by the user will be
         * written back to that same object.
         */
        public void setValues(DataKind kind, ValuesDelta values, EntityDelta state);

        /**
         * Add a specific {@link EditorListener} to this {@link Editor}.
         */
        public void setEditorListener(EditorListener listener);
    }

    /**
     * Listener for an {@link Editor}, usually to handle deleted items.
     */
    protected interface EditorListener {
        /**
         * Called when the given {@link Editor} has been deleted.
         */
        public void onDeleted(Editor editor);
    }

    /**
     * Simple editor that handles labels and any {@link EditField} defined for
     * the entry. Uses {@link ValuesDelta} to read any existing
     * {@link Entity} values, and to correctly write any changes values.
     */
    protected static class GenericEditor extends ViewHolder implements Editor, OnClickListener {
        private static final int RES_EDITOR = R.layout.item_editor;
        private static final int RES_FIELD = R.layout.item_editor_field;
        private static final int RES_LABEL_ITEM = android.R.layout.simple_list_item_1;

        private TextView mLabel;
        private ViewGroup mFields;
        private View mDelete;

        private DataKind mKind;
        private ValuesDelta mEntry;
        private EntityDelta mState;

        private EditType mType;

        public GenericEditor(Context context) {
            super(context, RES_EDITOR);

            mLabel = (TextView)mContent.findViewById(R.id.edit_label);
            mLabel.setOnClickListener(this);

            mFields = (ViewGroup)mContent.findViewById(R.id.edit_fields);

            mDelete = mContent.findViewById(R.id.edit_delete);
            mDelete.setOnClickListener(this);
        }

        private EditorListener mListener;

        public void setEditorListener(EditorListener listener) {
            mListener = listener;
        }

        /**
         * Build the current label state based on selected {@link EditType} and
         * possible custom label string.
         */
        private void rebuildLabel() {
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

        public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state) {
            mKind = kind;
            mEntry = entry;
            mState = state;

            if (!entry.isVisible()) {
                // Hide ourselves entirely if deleted
                mContent.setVisibility(View.GONE);
                return;
            } else {
                mContent.setVisibility(View.VISIBLE);
            }

            // Display label selector if multiple types available
            final boolean hasTypes = EntityModifier.hasEditTypes(kind);
            mLabel.setVisibility(hasTypes ? View.VISIBLE : View.GONE);
            if (hasTypes) {
                mType = EntityModifier.getCurrentType(entry, kind);
                rebuildLabel();
            }

            // Build out set of fields
            mFields.removeAllViews();
            for (EditField field : kind.fieldList) {
                // Inflate field from definition
                EditText fieldView = (EditText)mInflater.inflate(RES_FIELD, mFields, false);
                if (field.titleRes != -1) {
                    fieldView.setHint(field.titleRes);
                }
                fieldView.setInputType(field.inputType);
                fieldView.setMinLines(field.minLines);

                // Read current value from state
                final String column = field.column;
                final String value = entry.getAsString(column);
                fieldView.setText(value);

                // Prepare listener for writing changes
                fieldView.addTextChangedListener(new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        // Write the newly changed value
                        mEntry.put(column, s.toString());
                    }

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                });

                // Hide field when empty and optional value
                boolean shouldHide = (value == null && field.optional);
                fieldView.setVisibility(shouldHide ? View.GONE : View.VISIBLE);

                mFields.addView(fieldView);
            }
        }

        private static final int INPUT_TYPE_CUSTOM = EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

        /**
         * Show dialog for entering a custom label.
         */
        private void showCustomDialog() {
            final EditText customType = new EditText(mContext);
            customType.setInputType(INPUT_TYPE_CUSTOM);
            customType.requestFocus();

            final DialogInterface.OnClickListener clickPositive = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final String customText = customType.getText().toString();
                    mEntry.put(mType.customColumn, customText);
                    rebuildLabel();
                }
            };

            // TODO: handle canceled case by reverting to previous type?

            new AlertDialog.Builder(mContext).setView(customType).setTitle(
                    R.string.customLabelPickerTitle).setPositiveButton(android.R.string.ok,
                    clickPositive).setNegativeButton(android.R.string.cancel, null).show();
        }

        /**
         * Show dialog for picking a new {@link EditType} or entering a custom
         * label. This dialog is limited to the valid types as determined by
         * {@link EntityModifier}.
         */
        private void showTypeDialog() {
            // Build list of valid types, including the current value
            final List<EditType> validTypes = EntityModifier.getValidTypes(mState, mKind, mType);

            // Wrap our context to inflate list items using correct theme
            final Context dialogContext = new ContextThemeWrapper(mContext,
                    android.R.style.Theme_Light);
            final LayoutInflater dialogInflater = mInflater.cloneInContext(dialogContext);

            final ListAdapter typeAdapter = new ArrayAdapter<EditType>(mContext, RES_LABEL_ITEM,
                    validTypes) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = dialogInflater.inflate(RES_LABEL_ITEM, parent, false);
                    }

                    final EditType type = this.getItem(position);
                    final TextView textView = (TextView)convertView;
                    textView.setText(type.labelRes);
                    return textView;
                }
            };

            final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    // User picked type, so write to entry
                    mType = validTypes.get(which);
                    mEntry.put(mKind.typeColumn, mType.rawValue);

                    if (mType.customColumn != null) {
                        // Show custom label dialog if requested by type
                        showCustomDialog();
                    } else {
                        rebuildLabel();
                    }
                }
            };

            new AlertDialog.Builder(mContext).setSingleChoiceItems(typeAdapter, 0, clickListener)
                    .setTitle(R.string.selectLabel).show();
        }


        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.edit_label: {
                    showTypeDialog();
                    break;
                }
                case R.id.edit_delete: {
                    // Mark as deleted and hide this editor
                    mEntry.markDeleted();
                    mContent.setVisibility(View.GONE);

                    if (mListener != null) {
                        // Notify listener when present
                        mListener.onDeleted(this);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Simple editor for {@link Photo}.
     */
    protected static class PhotoEditor extends ViewHolder implements Editor {
        private static final int RES_PHOTO = R.layout.item_editor_photo;

        public PhotoEditor(Context context) {
            super(context, RES_PHOTO);
        }

//      private void setPhotoPresent(boolean present) {
//      mPhotoPresent = present;
//
//      // Correctly scale the contact photo if present, otherwise just center
//      // the photo placeholder icon.
//      if (mPhotoPresent) {
//          mPhotoImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
//      } else {
//          mPhotoImageView.setImageResource(R.drawable.ic_menu_add_picture);
//          mPhotoImageView.setScaleType(ImageView.ScaleType.CENTER);
//      }
//
//      if (mPhotoMenuItem != null) {
//          if (present) {
//              mPhotoMenuItem.setTitle(R.string.removePicture);
//              mPhotoMenuItem.setIcon(android.R.drawable.ic_menu_delete);
//          } else {
//              mPhotoMenuItem.setTitle(R.string.addPicture);
//              mPhotoMenuItem.setIcon(R.drawable.ic_menu_add_picture);
//          }
//      }
//  }

//        private void doPickPhotoAction() {
//            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
//            // TODO: get these values from constants somewhere
//            intent.setType("image/*");
//            intent.putExtra("crop", "true");
//            intent.putExtra("aspectX", 1);
//            intent.putExtra("aspectY", 1);
//            intent.putExtra("outputX", 96);
//            intent.putExtra("outputY", 96);
//            try {
//                intent.putExtra("return-data", true);
//                startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
//            } catch (ActivityNotFoundException e) {
//                new AlertDialog.Builder(EditContactActivity.this)
//                    .setTitle(R.string.errorDialogTitle)
//                    .setMessage(R.string.photoPickerNotFoundText)
//                    .setPositiveButton(android.R.string.ok, null)
//                    .show();
//            }
//        }

//        private void doRemovePhotoAction() {
//            mPhoto = null;
//            mPhotoChanged = true;
//            setPhotoPresent(false);
//        }

        public void setValues(DataKind kind, ValuesDelta values, EntityDelta state) {
        }

        public void setEditorListener(EditorListener listener) {
        }
    }

    /**
     * Simple editor for {@link StructuredName}.
     */
    protected static class DisplayNameEditor extends ViewHolder implements Editor {
        private static final int RES_DISPLAY_NAME = R.layout.item_editor_displayname;

        public DisplayNameEditor(Context context) {
            super(context, RES_DISPLAY_NAME);
        }

        public void setValues(DataKind kind, ValuesDelta values, EntityDelta state) {
        }

        public void setEditorListener(EditorListener listener) {
        }
    }

}
