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
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
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
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.editor.Editor.EditorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Version of {@link KindSectionView} that supports multiple RawContactDeltas.
 */
public class CompactKindSectionView extends LinearLayout implements EditorListener {

    /**
     * Marks a name as super primary when it is changed.
     *
     * This is for the case when two or more raw contacts with names are joined where neither is
     * marked as super primary.  If the user hits back (which causes a save) after changing the
     * name that was arbitrarily displayed, we want that to be the name that is used.
     *
     * Should only be set when a super primary name does not already exist since we only show
     * one name field.
     */
    private static final class NameEditorListener implements Editor.EditorListener {

        private final ValuesDelta mValuesDelta;
        private final long mRawContactId;
        private final CompactRawContactsEditorView.Listener mListener;

        public NameEditorListener(ValuesDelta valuesDelta, long rawContactId,
                CompactRawContactsEditorView.Listener listener) {
            mValuesDelta = valuesDelta;
            mRawContactId = rawContactId;
            mListener = listener;
        }

        @Override
        public void onRequest(int request) {
            if (request == Editor.EditorListener.FIELD_CHANGED) {
                mValuesDelta.setSuperPrimary(true);
                if (mListener != null) {
                    mListener.onNameFieldChanged(mRawContactId, mValuesDelta);
                }
            } else if (request == Editor.EditorListener.FIELD_TURNED_EMPTY) {
                mValuesDelta.setSuperPrimary(false);
            }
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            editor.clearAllFields();
        }
    }

    private static final class NicknameEditorListener implements Editor.EditorListener {

        @Override
        public void onRequest(int request) {
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            editor.clearAllFields();
        }
    }

    private List<KindSectionData> mKindSectionDataList;
    private boolean mReadOnly;
    private ViewIdGenerator mViewIdGenerator;
    private CompactRawContactsEditorView.Listener mListener;
    private String mMimeType;

    private boolean mShowOneEmptyEditor = false;
    private boolean mHideIfEmpty = true;

    private LayoutInflater mInflater;
    private ViewGroup mEditors;
    private ImageView mIcon;

    public CompactKindSectionView(Context context) {
        this(context, /* attrs =*/ null);
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
     * @param showOneEmptyEditor If true, we will always show one empty editor, otherwise an empty
     *         editor will not be shown until the user enters a value.  Note, this has no effect
     *         on name editors since the policy is to always show names.
     */
    public void setShowOneEmptyEditor(boolean showOneEmptyEditor) {
        mShowOneEmptyEditor = showOneEmptyEditor;
    }

    /**
     * @param hideWhenEmpty If true, the entire section will be hidden if all inputs are empty,
     *         otherwise one empty input will always be displayed.  Note, this has no effect
     *         on name editors since the policy is to always show names.
     */
    public void setHideWhenEmpty(boolean hideWhenEmpty) {
        mHideIfEmpty = hideWhenEmpty;
    }

    public void setGroupMetaData(Cursor cursor) {
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof GroupMembershipView) {
                ((GroupMembershipView) view).setGroupMetaData(cursor);
            }
        }
    }

    public void setState(List<KindSectionData> kindSectionDataList, boolean readOnly,
            ViewIdGenerator viewIdGenerator, CompactRawContactsEditorView.Listener listener) {
        mKindSectionDataList = kindSectionDataList;
        mReadOnly = readOnly;
        mViewIdGenerator = viewIdGenerator;
        mListener = listener;

        // Set the icon using the first DataKind (all DataKinds should be the same type)
        final DataKind dataKind = mKindSectionDataList.isEmpty()
                ? null : mKindSectionDataList.get(0).getDataKind();
        if (dataKind != null) {
            mIcon.setContentDescription(dataKind.titleRes == -1 || dataKind.titleRes == 0
                    ? "" : getResources().getString(dataKind.titleRes));
            mIcon.setImageDrawable(EditorUiUtils.getMimeTypeDrawable(getContext(),
                    dataKind.mimeType));
            if (mIcon.getDrawable() == null) mIcon.setContentDescription(null);
            mMimeType = dataKind.mimeType;
        }

        rebuildFromState();
        updateEmptyEditors(/* shouldAnimate = */ false);
    }

    /**
     * Build editors for all current rows.
     */
    private void rebuildFromState() {
        mEditors.removeAllViews();

        for (KindSectionData kindSectionData : mKindSectionDataList) {
            final String mimeType = kindSectionData.getDataKind().mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                for (ValuesDelta valuesDelta : kindSectionData.getValuesDeltas()) {
                    createNameEditorViews(kindSectionData.getAccountType(),
                            valuesDelta, kindSectionData.getRawContactDelta());
                }
            } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                createGroupEditorView(kindSectionData.getRawContactDelta(),
                        kindSectionData.getDataKind());
            } else {
                for (ValuesDelta valuesDelta : kindSectionData.getValuesDeltas()) {
                    createEditorView(kindSectionData.getRawContactDelta(),
                            kindSectionData.getDataKind(), valuesDelta);
                }
            }
        }
    }

    private void createNameEditorViews(AccountType accountType,
            ValuesDelta valuesDelta, RawContactDelta rawContactDelta) {
        final boolean readOnly = !accountType.areContactsWritable();

        // Structured name
        final StructuredNameEditorView nameView = (StructuredNameEditorView) mInflater.inflate(
                R.layout.structured_name_editor_view, mEditors, /* attachToRoot =*/ false);
        nameView.setEditorListener(new NameEditorListener(valuesDelta,
                rawContactDelta.getRawContactId(), mListener));
        nameView.setDeletable(false);
        nameView.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME),
                valuesDelta, rawContactDelta, readOnly, mViewIdGenerator);
        if (readOnly) nameView.setAccountType(accountType);

        // Correct start margin since there is another icon in the structured name layout
        nameView.findViewById(R.id.kind_icon).setVisibility(View.GONE);
        mEditors.addView(nameView);

        // Phonetic name
        if (readOnly) return;

        final PhoneticNameEditorView phoneticNameView = (PhoneticNameEditorView) mInflater.inflate(
                R.layout.phonetic_name_editor_view, mEditors, /* attachToRoot =*/ false);
        phoneticNameView.setDeletable(false);
        phoneticNameView.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME),
                valuesDelta, rawContactDelta, readOnly, mViewIdGenerator);

        // Fix the start margin for phonetic name views
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, 0);
        phoneticNameView.setLayoutParams(layoutParams);
        mEditors.addView(phoneticNameView);
    }

    private void createGroupEditorView(RawContactDelta rawContactDelta, DataKind dataKind) {
        final GroupMembershipView view = (GroupMembershipView) mInflater.inflate(
                R.layout.item_group_membership, mEditors, /* attachToRoot =*/ false);
        view.setKind(dataKind);
        view.setEnabled(isEnabled());
        view.setState(rawContactDelta);

        // Correct start margin since there is another icon in the group layout
        view.findViewById(R.id.kind_icon).setVisibility(View.GONE);

        mEditors.addView(view);
    }

    /**
     * Creates an EditorView for the given values delta. This function must be used while
     * constructing the views corresponding to the the object-model. The resulting EditorView is
     * also added to the end of mEditors
     */
    private View createEditorView(RawContactDelta rawContactDelta, DataKind dataKind,
            ValuesDelta valuesDelta) {
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

        // Set whether the editor is enabled
        view.setEnabled(isEnabled());

        if (view instanceof Editor) {
            final Editor editor = (Editor) view;
            editor.setDeletable(true);
            // TODO: it's awkward to be doing something special for nicknames here
            if (Nickname.CONTENT_ITEM_TYPE.equals(dataKind.mimeType)) {
                editor.setEditorListener(new NicknameEditorListener());
            } else {
                editor.setEditorListener(this);
            }
            editor.setValues(dataKind, valuesDelta, rawContactDelta, !dataKind.editable,
                    mViewIdGenerator);
        }
        mEditors.addView(view);
        return view;
    }

    /**
     * Updates the editors being displayed to the user removing extra empty
     * {@link Editor}s, so there is only max 1 empty {@link Editor} view at a time.
     * If there is only 1 empty editor and {@link #setHideWhenEmpty} was set to true,
     * then the entire section is hidden.
     */
    public void updateEmptyEditors(boolean shouldAnimate) {
        if (mKindSectionDataList.get(0).isNameDataKind()) {
            updateEmptyNameEditors(shouldAnimate);
        } else {
            updateEmptyNonNameEditors(shouldAnimate);
        }
    }

    private void updateEmptyNameEditors(boolean shouldAnimate) {
        boolean isEmptyNameEditorVisible = false;

        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            final Editor editor = (Editor) view;
            if (view instanceof StructuredNameEditorView) {
                // We always show one empty structured name view
                if (editor.isEmpty()) {
                    if (isEmptyNameEditorVisible) {
                        // If we're already showing an empty editor then hide any other empties
                        if (mHideIfEmpty) {
                            view.setVisibility(View.GONE);
                        }
                    } else {
                        isEmptyNameEditorVisible = true;
                    }
                } else {
                    showView(view, shouldAnimate);
                    isEmptyNameEditorVisible = true;
                }
            } else {
                // For phonetic names and nicknames, which can't be added, just show or hide them
                if (mHideIfEmpty && editor.isEmpty()) {
                    hideView(view, shouldAnimate);
                } else {
                    showView(view, shouldAnimate);
                }
            }
        }
    }

    private void updateEmptyNonNameEditors(boolean shouldAnimate) {
        // Update whether the entire section is visible or not
        final int editorCount = getEditorCount();
        final List<View> emptyEditors = getEmptyEditors();
        if (editorCount == emptyEditors.size() && mHideIfEmpty) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        // Prune excess empty editors
        if (emptyEditors.size() > 1) {
            // If there is more than 1 empty editor, then remove it from the list of editors.
            int deleted = 0;
            for (final View view : emptyEditors) {
                // If no child {@link View}s are being focused on within this {@link View}, then
                // remove this empty editor. We can assume that at least one empty editor has
                // focus. One way to get two empty editors is by deleting characters from a
                // non-empty editor, in which case this editor has focus.  Another way is if
                // there is more values delta so we must also count number of editors deleted.
                if (view.findFocus() == null) {
                    deleteView(view, shouldAnimate);
                    deleted++;
                    if (deleted == emptyEditors.size() - 1) break;
                }
            }
            return;
        }
        // Determine if we should add a new empty editor
        final DataKind dataKind = mKindSectionDataList.get(0).getDataKind();
        if (mReadOnly // We don't show empty editors for read only data kinds.
                || dataKind == null // There is nothing we can do.
                // We have already reached the maximum number of editors, don't add any more.
                || (dataKind.typeOverallMax == editorCount && dataKind.typeOverallMax != 0)
                // We have already reached the maximum number of empty editors, don't add any more.
                || emptyEditors.size() == 1) {
            return;
        }
        // Add a new empty editor
        if (mShowOneEmptyEditor) {
            final RawContactDelta rawContactDelta = mKindSectionDataList.get(0).getRawContactDelta();
            final ValuesDelta values = RawContactModifier.insertChild(rawContactDelta, dataKind);
            final View view = createEditorView(rawContactDelta, dataKind, values);
            showView(view, shouldAnimate);
        }
    }

    private void hideView(View view, boolean shouldAnimate) {
        if (shouldAnimate) {
            EditorAnimator.getInstance().hideEditorView(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void deleteView(View view, boolean shouldAnimate) {
        if (shouldAnimate) {
            final Editor editor = (Editor) view;
            editor.deleteEditor();
        } else {
            mEditors.removeView(view);
        }
    }

    private void showView(View view, boolean shouldAnimate) {
        if (shouldAnimate) {
            view.setVisibility(View.GONE);
            // TODO: still need this since we have animateLayoutChanges="true" on the parent layout?
            EditorAnimator.getInstance().showFieldFooter(view);
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Returns a list of empty editor views in this section.
     */
    private List<View> getEmptyEditors() {
        List<View> emptyEditorViews = new ArrayList<View>();
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof Editor && ((Editor) view).isEmpty()) {
                emptyEditorViews.add(view);
            }
        }
        return emptyEditorViews;
    }

    private int getEditorCount() {
        return mEditors.getChildCount();
    }

    /**
     * Returns the mime type the kind being edited in this section.
     */
    public String getMimeType() {
        return mMimeType;
    }
}
