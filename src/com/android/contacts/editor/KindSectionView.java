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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.editor.Editor.EditorListener;
import com.android.contacts.model.DataKind;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityModifier;

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout implements EditorListener {
    private static final String TAG = "KindSectionView";

    private ViewGroup mEditors;
    private View mAddFieldFooter;
    private TextView mAddFieldText;
    private String mTitleString;

    private DataKind mKind;
    private EntityDelta mState;
    private boolean mReadOnly;

    private ViewIdGenerator mViewIdGenerator;

    private LayoutInflater mInflater;

    /**
     * Map of data MIME types to the "add field" footer text resource ID
     * (that for example, maps to "Add new phone number").
     */
    private static final HashMap<String, Integer> sAddFieldFooterTextResourceIds =
            new HashMap<String, Integer>();

    static {
        final HashMap<String, Integer> hashMap = sAddFieldFooterTextResourceIds;
        hashMap.put(Phone.CONTENT_ITEM_TYPE, R.string.add_phone);
        hashMap.put(Email.CONTENT_ITEM_TYPE, R.string.add_email);
        hashMap.put(Im.CONTENT_ITEM_TYPE, R.string.add_im);
        hashMap.put(StructuredPostal.CONTENT_ITEM_TYPE, R.string.add_address);
        hashMap.put(Note.CONTENT_ITEM_TYPE, R.string.add_note);
        hashMap.put(Website.CONTENT_ITEM_TYPE, R.string.add_website);
        hashMap.put(SipAddress.CONTENT_ITEM_TYPE, R.string.add_internet_call);
        hashMap.put(Event.CONTENT_ITEM_TYPE, R.string.add_event);
        hashMap.put(Relation.CONTENT_ITEM_TYPE, R.string.add_relationship);
    }

    /**
     * List of the empty editor views.
     */
    private List<View> mEmptyEditorViews = new ArrayList<View>();

    public KindSectionView(Context context) {
        this(context, null);
    }

    public KindSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mEditors != null) {
            int childCount = mEditors.getChildCount();
            for (int i = 0; i < childCount; i++) {
                mEditors.getChildAt(i).setEnabled(enabled);
            }
        }

        if (enabled && !mReadOnly) {
            mAddFieldFooter.setVisibility(View.VISIBLE);
        } else {
            mAddFieldFooter.setVisibility(View.GONE);
        }
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mEditors = (ViewGroup)findViewById(R.id.kind_editors);
        mAddFieldText = (TextView) findViewById(R.id.add_text);
        mAddFieldFooter = findViewById(R.id.add_field_footer);
        mAddFieldFooter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Setup click listener to add an empty field when the footer is clicked.
                mAddFieldFooter.setVisibility(View.GONE);
                addItem();
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onDeleted(Editor editor) {
        updateAddFooterVisible();
        updateSectionVisible();
    }

    /** {@inheritDoc} */
    @Override
    public void onRequest(int request) {
        // If a field has changed, then check if another row can be added dynamically.
        if (request == FIELD_CHANGED) {
            updateAddFooterVisible();
        }
    }

    public void setState(DataKind kind, EntityDelta state, boolean readOnly, ViewIdGenerator vig) {
        mKind = kind;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;

        setId(mViewIdGenerator.getId(state, kind, null, ViewIdGenerator.NO_VIEW_INDEX));

        // TODO: handle resources from remote packages
        mTitleString = (kind.titleRes == -1 || kind.titleRes == 0)
                ? ""
                : getResources().getString(kind.titleRes);

        // Set "add field" footer message according to MIME type. Some MIME types
        // can only have max 1 field, so the map will return null if these sections
        // should not have an "Add field" option.
        Integer textResourceId = sAddFieldFooterTextResourceIds.get(kind.mimeType);
        if (textResourceId != null) {
            mAddFieldText.setText(textResourceId);
        }

        rebuildFromState();
        updateAddFooterVisible();
        updateSectionVisible();
    }

    public String getTitle() {
        return mTitleString;
    }

    /**
     * Build editors for all current {@link #mState} rows.
     */
    public void rebuildFromState() {
        // Remove any existing editors
        mEditors.removeAllViews();

        // Check if we are displaying anything here
        boolean hasEntries = mState.hasMimeEntries(mKind.mimeType);

        if (hasEntries) {
            for (ValuesDelta entry : mState.getMimeEntries(mKind.mimeType)) {
                // Skip entries that aren't visible
                if (!entry.isVisible()) continue;
                if (isEmptyNoop(entry)) continue;

                createEditorView(entry);
            }
        }
    }


    /**
     * Creates an EditorView for the given entry. This function must be used while constructing
     * the views corresponding to the the object-model. The resulting EditorView is also added
     * to the end of mEditors
     */
    private View createEditorView(ValuesDelta entry) {
        final View view;
        try {
            view = mInflater.inflate(mKind.editorLayoutResourceId, mEditors, false);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot allocate editor with layout resource ID " +
                    mKind.editorLayoutResourceId);
        }

        view.setEnabled(isEnabled());

        if (view instanceof Editor) {
            Editor editor = (Editor) view;
            editor.setValues(mKind, entry, mState, mReadOnly, mViewIdGenerator);
            editor.setEditorListener(this);
            editor.setDeletable(true);
        }
        mEditors.addView(view);
        return view;
    }

    /**
     * Tests whether the given item has no changes (so it exists in the database) but is empty
     */
    private boolean isEmptyNoop(ValuesDelta item) {
        if (!item.isNoop()) return false;
        final int fieldCount = mKind.fieldList.size();
        for (int i = 0; i < fieldCount; i++) {
            final String column = mKind.fieldList.get(i).column;
            final String value = item.getAsString(column);
            if (!TextUtils.isEmpty(value)) return false;
        }
        return true;
    }

    private void updateSectionVisible() {
        setVisibility(getEditorCount() != 0 ? VISIBLE : GONE);
    }

    protected void updateAddFooterVisible() {
        if (!mReadOnly && mKind.isList) {
            // First determine whether there are any existing empty editors.
            updateEmptyEditors();
            // If there are no existing empty editors and it's possible to add
            // another field, then make the "add footer" field visible.
            if (!hasEmptyEditor() && EntityModifier.canInsert(mState, mKind)) {
                mAddFieldFooter.setVisibility(View.VISIBLE);
                return;
            }
        }
        mAddFieldFooter.setVisibility(View.GONE);
    }

    /**
     * Determines a list of {@link Editor} {@link View}s that have an empty
     * field in them and removes extra ones, so there is max 1 empty
     * {@link Editor} {@link View} at a time.
     */
    private void updateEmptyEditors() {
        mEmptyEditorViews.clear();

        // Construct a list of editors that have an empty field in them.
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            View v = mEditors.getChildAt(i);
            if (((Editor) v).hasEmptyField()) {
                mEmptyEditorViews.add(v);
            }
        }

        // If there is more than 1 empty editor, then remove it from the list of editors.
        if (mEmptyEditorViews.size() > 1) {
            for (View emptyEditorView : mEmptyEditorViews) {
                // If no child {@link View}s are being focused on within
                // this {@link View}, then remove this empty editor.
                if (emptyEditorView.findFocus() == null) {
                    mEditors.removeView(emptyEditorView);
                }
            }
        }
    }

    /**
     * Returns true if one of the editors has an empty field, or false
     * otherwise.
     */
    private boolean hasEmptyEditor() {
        return mEmptyEditorViews.size() > 0;
    }

    public void addItem() {
        ValuesDelta values = null;
        // if this is a list, we can freely add. if not, only allow adding the first
        if (!mKind.isList) {
            if (getEditorCount() == 1) {
                return;
            }

            // If we already have an item, just make it visible
            ArrayList<ValuesDelta> entries = mState.getMimeEntries(mKind.mimeType);
            if (entries != null && entries.size() > 0) {
                values = entries.get(0);
            }
        }

        // Insert a new child, create its view and set its focus
        if (values == null) {
            values = EntityModifier.insertChild(mState, mKind);
        }

        final View newField = createEditorView(values);
        post(new Runnable() {

            @Override
            public void run() {
                newField.requestFocus();
            }
        });

        // Hide the "add field" footer because there is now a blank field.
        mAddFieldFooter.setVisibility(View.GONE);

        // Ensure we are visible
        updateSectionVisible();
    }

    public int getEditorCount() {
        return mEditors.getChildCount();
    }

    public DataKind getKind() {
        return mKind;
    }
}
