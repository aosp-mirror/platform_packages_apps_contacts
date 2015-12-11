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

import android.content.Context;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.contacts.R;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.editor.Editor.EditorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout implements EditorListener {

    public interface Listener {

        /**
         * Invoked when any editor that is displayed in this section view is deleted by the user.
         */
        public void onDeleteRequested(Editor editor);
    }

    private ViewGroup mEditors;
    private ImageView mIcon;

    private DataKind mKind;
    private RawContactDelta mState;
    private boolean mReadOnly;

    private ViewIdGenerator mViewIdGenerator;

    private LayoutInflater mInflater;

    private Listener mListener;

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

        updateEmptyEditors(/* shouldAnimate = */ true);
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mEditors = (ViewGroup) findViewById(R.id.kind_editors);
        mIcon = (ImageView) findViewById(R.id.kind_icon);
    }

    @Override
    public void onDeleteRequested(Editor editor) {
        if (getEditorCount() == 1) {
            // If there is only 1 editor in the section, then don't allow the user to delete it.
            // Just clear the fields in the editor.
            editor.clearAllFields();
        } else {
            // If there is a listener, let it decide whether to delete the Editor or the entire
            // KindSectionView so that there is no jank from both animations happening in succession.
            if (mListener != null) {
                editor.markDeleted();
                mListener.onDeleteRequested(editor);
            } else {
                editor.deleteEditor();
            }
        }
    }

    @Override
    public void onRequest(int request) {
        // If a field has become empty or non-empty, then check if another row
        // can be added dynamically.
        if (request == FIELD_TURNED_EMPTY || request == FIELD_TURNED_NON_EMPTY) {
            updateEmptyEditors(/* shouldAnimate = */ true);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setState(DataKind kind, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        mKind = kind;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;

        setId(mViewIdGenerator.getId(state, kind, null, ViewIdGenerator.NO_VIEW_INDEX));

        // TODO: handle resources from remote packages
        final String titleString = (kind.titleRes == -1 || kind.titleRes == 0)
                ? ""
                : getResources().getString(kind.titleRes);
        mIcon.setContentDescription(titleString);

        mIcon.setImageDrawable(EditorUiUtils.getMimeTypeDrawable(getContext(), kind.mimeType));
        if (mIcon.getDrawable() == null) {
            mIcon.setContentDescription(null);
        }

        rebuildFromState();
        updateEmptyEditors(/* shouldAnimate = */ false);
    }

    /**
     * Build editors for all current {@link #mState} rows.
     */
    private void rebuildFromState() {
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
        final int layoutResId = EditorUiUtils.getLayoutResourceId(mKind.mimeType);
        try {
            view = mInflater.inflate(layoutResId, mEditors, false);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot allocate editor with layout resource ID " +
                    layoutResId + " for MIME type " + mKind.mimeType +
                    " with error " + e.toString());
        }
        view.setEnabled(isEnabled());
        if (view instanceof Editor) {
            Editor editor = (Editor) view;
            editor.setDeletable(true);
            editor.setValues(mKind, entry, mState, mReadOnly, mViewIdGenerator);
            editor.setEditorListener(this);
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

    /**
     * Updates the editors being displayed to the user removing extra empty
     * {@link Editor}s, so there is only max 1 empty {@link Editor} view at a time.
     */
    public void updateEmptyEditors(boolean shouldAnimate) {

        final List<View> emptyEditors = getEmptyEditors();

        // If there is more than 1 empty editor, then remove it from the list of editors.
        if (emptyEditors.size() > 1) {
            for (final View emptyEditorView : emptyEditors) {
                // If no child {@link View}s are being focused on within this {@link View}, then
                // remove this empty editor. We can assume that at least one empty editor has focus.
                // The only way to get two empty editors is by deleting characters from a non-empty
                // editor, in which case this editor has focus.
                if (emptyEditorView.findFocus() == null) {
                    final Editor editor = (Editor) emptyEditorView;
                    if (shouldAnimate) {
                        editor.deleteEditor();
                    } else {
                        mEditors.removeView(emptyEditorView);
                    }
                }
            }
        } else if (mKind == null) {
            // There is nothing we can do.
            return;
        } else if (isReadOnly()) {
            // We don't show empty editors for read only data kinds.
            return;
        } else if (!RawContactModifier.canInsert(mState, mKind)) {
            // We have already reached the maximum number of editors. Lets not add any more.
            return;
        } else if (emptyEditors.size() == 1) {
            // We have already reached the maximum number of empty editors. Lets not add any more.
            return;
        } else {
            final ValuesDelta values = RawContactModifier.insertChild(mState, mKind);
            final View newField = createEditorView(values);
            if (shouldAnimate) {
                newField.setVisibility(View.GONE);
                EditorAnimator.getInstance().showFieldFooter(newField);
            }
        }
    }

    /**
     * Returns a list of empty editor views in this section.
     */
    private List<View> getEmptyEditors() {
        List<View> emptyEditorViews = new ArrayList<View>();
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            View view = mEditors.getChildAt(i);
            if (((Editor) view).isEmpty()) {
                emptyEditorViews.add(view);
            }
        }
        return emptyEditorViews;
    }

    public int getEditorCount() {
        return mEditors.getChildCount();
    }

    public DataKind getKind() {
        return mKind;
    }
}
