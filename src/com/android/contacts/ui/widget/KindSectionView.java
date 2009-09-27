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
import com.android.contacts.model.Editor;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.Context;
import android.provider.ContactsContract.Data;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout implements OnClickListener, EditorListener {
    private static final String TAG = "KindSectionView";

    private LayoutInflater mInflater;

    private ViewGroup mEditors;
    private View mAdd;
    private TextView mTitle;

    private DataKind mKind;
    private EntityDelta mState;
    private boolean mReadOnly;

    public KindSectionView(Context context) {
        super(context);
    }

    public KindSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mEditors = (ViewGroup)findViewById(R.id.kind_editors);

        mAdd = findViewById(R.id.kind_header);
        mAdd.setOnClickListener(this);

        mTitle = (TextView)findViewById(R.id.kind_title);
    }

    /** {@inheritDoc} */
    public void onDeleted(Editor editor) {
        this.updateAddEnabled();
        this.updateEditorsVisible();
    }

    /** {@inheritDoc} */
    public void onRequest(int request) {
        // Ignore requests
    }

    public void setState(DataKind kind, EntityDelta state, boolean readOnly) {
        mKind = kind;
        mState = state;
        mReadOnly = readOnly;

        // TODO: handle resources from remote packages
        mTitle.setText(kind.titleRes);

        this.rebuildFromState();
        this.updateAddEnabled();
        this.updateEditorsVisible();
    }

    /**
     * Build editors for all current {@link #mState} rows.
     */
    public void rebuildFromState() {
        // Remove any existing editors
        mEditors.removeAllViews();

        // Build individual editors for each entry
        if (!mState.hasMimeEntries(mKind.mimeType)) return;
        for (ValuesDelta entry : mState.getMimeEntries(mKind.mimeType)) {
            // Skip entries that aren't visible
            if (!entry.isVisible()) continue;

            final GenericEditorView editor = (GenericEditorView)mInflater.inflate(
                    R.layout.item_generic_editor, mEditors, false);
            editor.setValues(mKind, entry, mState, mReadOnly);
            editor.setEditorListener(this);
            editor.setId(entry.getViewId());
            mEditors.addView(editor);
        }
    }

    protected void updateEditorsVisible() {
        final boolean hasChildren = mEditors.getChildCount() > 0;
        mEditors.setVisibility(hasChildren ? View.VISIBLE : View.GONE);
    }

    protected void updateAddEnabled() {
        // Set enabled state on the "add" view
        final boolean canInsert = EntityModifier.canInsert(mState, mKind);
	final boolean isEnabled = !mReadOnly && canInsert;
        mAdd.setEnabled(isEnabled);
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        // Insert a new child and rebuild
        EntityModifier.insertChild(mState, mKind);
        this.rebuildFromState();
        this.updateAddEnabled();
        this.updateEditorsVisible();
    }
}
