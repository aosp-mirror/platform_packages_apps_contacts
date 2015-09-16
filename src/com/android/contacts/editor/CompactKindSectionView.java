/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * Version of {@link KindSectionView} that supports multiple RawContactDeltas.
 */
public class CompactKindSectionView extends LinearLayout implements EditorListener {

    private ViewGroup mEditors;
    private ImageView mIcon;

    private List<KindSectionData> mKindSectionDatas;
    private boolean mReadOnly;
    private boolean mShowOneEmptyEditor = false;
    private boolean mHideIfEmpty = true;

    private ViewIdGenerator mViewIdGenerator;
    private LayoutInflater mInflater;

    public CompactKindSectionView(Context context) {
        this(context, /** attrs =*/ null);
    }

    public CompactKindSectionView(Context context, AttributeSet attrs) {
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
        // TODO: why is this necessary?
        updateEmptyEditors(/* shouldAnimate = */ true);
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
        if (mShowOneEmptyEditor && getEditorCount() == 1) {
            // If there is only 1 editor in the section, then don't allow the user to delete it.
            // Just clear the fields in the editor.
            editor.clearAllFields();
        } else {
            editor.deleteEditor();
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

    /**
     * @param showOneEmptyEditor If true, we will always show one empty, otherwise an empty editor
     *         will not be shown until the user enters a value.
     */
    public void setShowOneEmptyEditor(boolean showOneEmptyEditor) {
        mShowOneEmptyEditor = showOneEmptyEditor;
    }

    /**
     * @param hideWhenEmpty If true, the entire section will be hidden if all inputs are empty,
     *         otherwise one empty input will always be displayed.
     */
    public void setHideWhenEmpty(boolean hideWhenEmpty) {
        mHideIfEmpty = hideWhenEmpty;
    }

    public void setState(List<KindSectionData> kindSectionDatas, boolean readOnly,
            ViewIdGenerator viewIdGenerator) {
        mKindSectionDatas = kindSectionDatas;
        mReadOnly = readOnly;
        mViewIdGenerator = viewIdGenerator;

        // Set the icon using the first DataKind (all DataKinds should be the same type)
        final DataKind dataKind = mKindSectionDatas.isEmpty()
                ? null : mKindSectionDatas.get(0).getDataKind();
        if (dataKind != null) {
            mIcon.setContentDescription(dataKind.titleRes == -1 || dataKind.titleRes == 0
                    ? "" : getResources().getString(dataKind.titleRes));
            mIcon.setImageDrawable(EditorUiUtils.getMimeTypeDrawable(getContext(),
                    dataKind.mimeType));
            if (mIcon.getDrawable() == null) mIcon.setContentDescription(null);
        }

        rebuildFromState();
        updateEmptyEditors(/* shouldAnimate = */ false);
    }

    /**
     * Build editors for all current rows.
     */
    private void rebuildFromState() {
        // Remove any existing editors
        mEditors.removeAllViews();

        // Check if we are displaying anything here
        boolean hasValuesDeltas = false;
        for (KindSectionData kindSectionData : mKindSectionDatas) {
            if (kindSectionData.hasValuesDeltas()) {
                hasValuesDeltas = true;
                break;
            }
        }

        if (hasValuesDeltas) {
            for (KindSectionData kindSectionData : mKindSectionDatas) {
                for (ValuesDelta valuesDelta : kindSectionData.getValuesDeltas()) {
                    // Skip entries that aren't visible
                    if (!valuesDelta.isVisible()) continue;
                    if (isUnchanged(kindSectionData.getDataKind(), valuesDelta)) continue;
                    createEditorView(kindSectionData.getRawContactDelta(),
                            kindSectionData.getDataKind(), valuesDelta);
                }
            }
        }
    }

    /**
     * Creates an EditorView for the given values delta. This function must be used while
     * constructing the views corresponding to the the object-model. The resulting EditorView is
     * also added to the end of mEditors
     */
    private View createEditorView(RawContactDelta rawContactDelta, DataKind dataKind,
            ValuesDelta entry) {
        // Inflate the layout
        final View view;
        final int layoutResId = EditorUiUtils.getLayoutResourceId(dataKind.mimeType);
        try {
            view = mInflater.inflate(layoutResId, mEditors, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate editor with layout resource ID " +
                    layoutResId + " for mime type " + dataKind.mimeType + ": " + e.toString());
        }

        // Hide the types drop downs until the associated edit field is focused
        if (view instanceof LabeledEditorView) {
            ((LabeledEditorView) view).setHideTypeInitially(true);
        }

        // Fix the start margin for phonetic name views
        if (view instanceof PhoneticNameEditorView) {
            final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(0, 0, 0, 0);
            view.setLayoutParams(layoutParams);
        }

        // Set whether the editor is enabled
        view.setEnabled(isEnabled());

        if (view instanceof Editor) {
            Editor editor = (Editor) view;
            editor.setDeletable(true);
            editor.setValues(dataKind, entry, rawContactDelta, !dataKind.editable, mViewIdGenerator);
            editor.setEditorListener(this);
        }
        mEditors.addView(view);
        return view;
    }

    /**
     * Whether the given values delta has no changes (i.e. it exists in the database but is empty).
     */
    private static boolean isUnchanged(DataKind dataKind, ValuesDelta item) {
        if (!item.isNoop()) return false;
        final int fieldCount = dataKind.fieldList == null ? 0 : dataKind.fieldList.size();
        for (int i = 0; i < fieldCount; i++) {
            final String column = dataKind.fieldList.get(i).column;
            final String value = item.getAsString(column);
            if (!TextUtils.isEmpty(value)) return false;
        }
        return true;
    }

    /**
     * Updates the editors being displayed to the user removing extra empty
     * {@link Editor}s, so there is only max 1 empty {@link Editor} view at a time.
     * If there is only 1 empty editor and {@link #setHideWhenEmpty} was set to true,
     * then the entire section is hidden.
     */
    public void updateEmptyEditors(boolean shouldAnimate) {
        if (mKindSectionDatas.isEmpty()) return;
        final DataKind dataKind = mKindSectionDatas.get(0).getDataKind();
        final RawContactDelta rawContactDelta = mKindSectionDatas.get(0).getRawContactDelta();

        // Update whether the entire section is visible or not
        final int editorCount = getEditorCount();
        final List<View> emptyEditors = getEmptyEditors();
        if (editorCount == emptyEditors.size() && mHideIfEmpty) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        // Update the number of empty editors
        if (emptyEditors.size() > 1) {
            // If there is more than 1 empty editor, then remove it from the list of editors.
            int deleted = 0;
            for (final View emptyEditorView : emptyEditors) {
                // If no child {@link View}s are being focused on within this {@link View}, then
                // remove this empty editor. We can assume that at least one empty editor has focus.
                // One way to get two empty editors is by deleting characters from a non-empty
                // editor, in which case this editor has focus.  Another way is if there is more
                // values delta so we must also count number of editors deleted.

                // TODO: we must not delete the editor for the "primary" account. It's working
                // because the primary account is always the last one when the account is changed
                // in the editor but it is a bit brittle to rely on that (though that is what is
                // happening in LMP).
                if (emptyEditorView.findFocus() == null) {
                    final Editor editor = (Editor) emptyEditorView;
                    if (shouldAnimate) {
                        editor.deleteEditor();
                    } else {
                        mEditors.removeView(emptyEditorView);
                    }
                    deleted++;
                    if (deleted == emptyEditors.size() -1) break;
                }
            }
            return;
        }
        if (dataKind == null // There is nothing we can do.
                || mReadOnly // We don't show empty editors for read only data kinds.
                // We have already reached the maximum number of editors, don't add any more.
                || (dataKind.typeOverallMax == editorCount && dataKind.typeOverallMax != 0)
                // We have already reached the maximum number of empty editors, don't add any more.
                || emptyEditors.size() == 1) {
            return;
        }
        // Add a new empty editor
        if (mShowOneEmptyEditor) {
            final ValuesDelta values = RawContactModifier.insertChild(rawContactDelta, dataKind);
            final View editorView = createEditorView(rawContactDelta, dataKind, values);
            if (shouldAnimate) {
                editorView.setVisibility(View.GONE);
                EditorAnimator.getInstance().showFieldFooter(editorView);
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

    private int getEditorCount() {
        return mEditors.getChildCount();
    }

    /**
     * Returns the editor View at the given index.
     */
    public View getEditorView(int index) {
        return mEditors == null || mEditors.getChildCount() == 0 || mEditors.getChildCount() > index
                ? null : mEditors.getChildAt(index);
    }
}
