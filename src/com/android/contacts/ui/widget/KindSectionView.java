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
import com.android.contacts.ui.ViewIdGenerator;
import com.android.contacts.util.ViewGroupAnimator;

import android.content.Context;
import android.provider.ContactsContract.Data;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout implements EditorListener {
    private static final String TAG = "KindSectionView";
    private static int sCachedThemePaddingRight = -1;

    private ViewGroup mEditors;
    private View mAdd;
    private ImageView mAddPlusButton;
    private TextView mTitle;

    private DataKind mKind;
    private EntityDelta mState;
    private boolean mReadOnly;

    private ViewIdGenerator mViewIdGenerator;

    public KindSectionView(Context context) {
        super(context);
    }

    public KindSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mEditors = (ViewGroup)findViewById(R.id.kind_editors);

        mAdd = findViewById(R.id.kind_header);
        mAdd.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                addItem();
            }
        });

        mAddPlusButton = (ImageView) findViewById(R.id.kind_plus);

        mTitle = (TextView)findViewById(R.id.kind_title);
    }

    /** {@inheritDoc} */
    public void onDeleted(Editor editor) {
        updateAddEnabled();
        updateEditorsVisible();
        updateVisible();
    }

    /** {@inheritDoc} */
    public void onRequest(int request) {
        // Ignore requests
    }

    public void setState(DataKind kind, EntityDelta state, boolean readOnly, ViewIdGenerator vig) {
        mKind = kind;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;

        setId(mViewIdGenerator.getId(state, kind, null, ViewIdGenerator.NO_VIEW_INDEX));

        // TODO: handle resources from remote packages
        mTitle.setText(kind.titleRes);

        // Only show the add button if this is a list
        mAddPlusButton.setVisibility(mKind.isList ? View.VISIBLE : View.GONE);

        rebuildFromState();
        updateAddEnabled();
        updateEditorsVisible();
        updateVisible();
    }

    public CharSequence getTitle() {
        return mTitle.getText();
    }

    public boolean getFieldCount() {
        if (mState == null) {
            return false;
        }

        if (!mState.hasMimeEntries(mKind.mimeType)) {
            return false;
        }

        int editorCount = getEditorCount();
        for (int i = 0; i < editorCount; i++) {
            GenericEditorView editorView = (GenericEditorView) mEditors.getChildAt(i);
            if (editorView.isAnyFieldFilledOut()) {
                return true;
            }
        }

        return false;
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
            int entryIndex = 0;
            for (ValuesDelta entry : mState.getMimeEntries(mKind.mimeType)) {
                // Skip entries that aren't visible
                if (!entry.isVisible()) continue;

                final GenericEditorView editor = new GenericEditorView(mContext);

                editor.setPadding(0, 0, getThemeScrollbarSize(mContext), 0);
                editor.setValues(mKind, entry, mState, mReadOnly, mViewIdGenerator);
                editor.setEditorListener(this);
                editor.setDeletable(true);
                mEditors.addView(editor);
                entryIndex++;
            }
        }
    }

    /**
     * Reads the scrollbarSize of the current theme
     */
    private static int getThemeScrollbarSize(Context context) {
        if (sCachedThemePaddingRight == -1) {
            final TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.scrollbarSize, outValue, true);
            final WindowManager windowManager =
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            sCachedThemePaddingRight = (int) TypedValue.complexToDimension(outValue.data, metrics);
        }

        return sCachedThemePaddingRight;
    }

    protected void updateEditorsVisible() {
        final boolean hasChildren = getEditorCount() > 0;
        mEditors.setVisibility(hasChildren ? View.VISIBLE : View.GONE);
    }

    private void updateVisible() {
        setVisibility(getEditorCount() != 0 ? VISIBLE : GONE);
    }


    protected void updateAddEnabled() {
        // Set enabled state on the "add" view
        final boolean canInsert = EntityModifier.canInsert(mState, mKind);
        final boolean isEnabled = !mReadOnly && canInsert;
        mAdd.setEnabled(isEnabled);
    }

    public void addItem() {
        // if this is a list, we can freely add. if not, only allow adding the first
        if (!mKind.isList && getEditorCount() == 1)
            return;

        final ViewGroupAnimator animator = ViewGroupAnimator.captureView(getRootView());

        // Insert a new child and rebuild
        final ValuesDelta newValues = EntityModifier.insertChild(mState, mKind);
        rebuildFromState();
        updateAddEnabled();
        updateEditorsVisible();

        // Find the newly added EditView and set focus.
        final int newFieldId = mViewIdGenerator.getId(mState, mKind, newValues, 0);
        final View newField = findViewById(newFieldId);
        if (newField != null) {
            newField.requestFocus();
        }

        updateVisible();

        animator.animate();
    }

    public int getEditorCount() {
        return mEditors.getChildCount();
    }

    public DataKind getKind() {
        return mKind;
    }
}
