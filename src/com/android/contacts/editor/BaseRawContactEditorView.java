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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditType;
import com.android.contacts.common.model.account.AccountWithDataSet;

/**
 * Base view that provides common code for the editor interaction for a specific
 * RawContact represented through an {@link RawContactDelta}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link RawContact} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link RawContactModifier} to ensure that {@link AccountType} are enforced.
 */
public abstract class BaseRawContactEditorView extends LinearLayout {

    private PhotoEditorView mPhoto;

    private View mAccountHeaderContainer;
    private ImageView mExpandAccountButton;
    private LinearLayout mCollapsibleSection;
    private TextView mAccountName;
    private TextView mAccountType;

    protected Listener mListener;

    public interface Listener {
        void onExternalEditorRequest(AccountWithDataSet account, Uri uri);
        void onEditorExpansionChanged();
    }

    public BaseRawContactEditorView(Context context) {
        super(context);
    }

    public BaseRawContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhoto.setEnabled(isEnabled());

        mAccountHeaderContainer = findViewById(R.id.account_header_container);
        mExpandAccountButton = (ImageView) findViewById(R.id.expand_account_button);
        mCollapsibleSection = (LinearLayout) findViewById(R.id.collapsable_section);
        mAccountName = (TextView) findViewById(R.id.account_name);
        mAccountType = (TextView) findViewById(R.id.account_type);

        setCollapsed(false);
        setCollapsible(true);
    }

    public void setGroupMetaData(Cursor groupMetaData) {
    }


    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Assign the given {@link Bitmap} to the internal {@link PhotoEditorView}
     * in order to update the {@link RawContactDelta} currently being edited.
     */
    public void setPhotoEntry(Bitmap bitmap) {
        mPhoto.setPhotoEntry(bitmap);
    }

    /**
     * Assign the given photo {@link Uri} to UI of the {@link PhotoEditorView}, so that it can
     * display a full sized photo.
     */
    public void setFullSizedPhoto(Uri uri) {
        mPhoto.setFullSizedPhoto(uri);
    }

    protected void setHasPhotoEditor(boolean hasPhotoEditor) {
        mPhoto.setVisibility(hasPhotoEditor ? View.VISIBLE : View.GONE);
    }

    /**
     * Return true if internal {@link PhotoEditorView} has a {@link Photo} set.
     */
    public boolean hasSetPhoto() {
        return mPhoto.hasSetPhoto();
    }

    public PhotoEditorView getPhotoEditor() {
        return mPhoto;
    }

    /**
     * @return the RawContact ID that this editor is editing.
     */
    public abstract long getRawContactId();

    /**
     * If {@param isCollapsible} is TRUE, then this editor can be collapsed by clicking on its
     * account header.
     */
    public void setCollapsible(boolean isCollapsible) {
        if (isCollapsible) {
            mAccountHeaderContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int startingHeight = mCollapsibleSection.getMeasuredHeight();
                    final boolean isCollapsed = isCollapsed();
                    setCollapsed(!isCollapsed);
                    // The slideAndFadeIn animation only looks good when collapsing. For expanding,
                    // it looks like the editor is loading sluggishly. I tried animating the
                    // clipping bounds instead of the alpha value. But because the editors are very
                    // tall, this animation looked very similar to doing no animation at all. It
                    // wasn't worth the significant additional complexity.
                    if (!isCollapsed) {
                        EditorAnimator.getInstance().slideAndFadeIn(mCollapsibleSection,
                                startingHeight);
                        // We want to place the focus near the top of the screen now that a
                        // potentially focused editor is being collapsed.
                        EditorAnimator.placeFocusAtTopOfScreenAfterReLayout(mCollapsibleSection);
                    } else {
                        // When expanding we should scroll the expanded view onto the screen.
                        // Otherwise, user's may not notice that any expansion happened.
                        EditorAnimator.getInstance().scrollViewToTop(mAccountHeaderContainer);
                        mCollapsibleSection.requestFocus();
                    }
                    if (mListener != null) {
                        mListener.onEditorExpansionChanged();
                    }
                    updateAccountHeaderContentDescription();
                }
            });
            mExpandAccountButton.setVisibility(View.VISIBLE);
            mAccountHeaderContainer.setClickable(true);
        } else {
            mAccountHeaderContainer.setOnClickListener(null);
            mExpandAccountButton.setVisibility(View.GONE);
            mAccountHeaderContainer.setClickable(false);
        }
    }

    public boolean isCollapsed() {
        return mCollapsibleSection.getLayoutParams().height == 0;
    }

    public void setCollapsed(boolean isCollapsed) {
        final LinearLayout.LayoutParams params
                = (LayoutParams) mCollapsibleSection.getLayoutParams();
        if (isCollapsed) {
            params.height = 0;
            mCollapsibleSection.setLayoutParams(params);
            mExpandAccountButton.setImageResource(R.drawable.ic_menu_expander_minimized_holo_light);
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mCollapsibleSection.setLayoutParams(params);
            mExpandAccountButton.setImageResource(R.drawable.ic_menu_expander_maximized_holo_light);
        }
    }

    protected void updateAccountHeaderContentDescription() {
        final StringBuilder builder = new StringBuilder();
        builder.append(EditorUiUtils.getAccountInfoContentDescription(
                mAccountName.getText(), mAccountType.getText()));
        if (mExpandAccountButton.getVisibility() == View.VISIBLE) {
            builder.append(getResources().getString(isCollapsed()
                    ? R.string.content_description_expand_editor
                    : R.string.content_description_collapse_editor));
        }
        mAccountHeaderContainer.setContentDescription(builder);
    }

    /**
     * Set the internal state for this view, given a current
     * {@link RawContactDelta} state and the {@link AccountType} that
     * apply to that state.
     */
    public abstract void setState(RawContactDelta state, AccountType source, ViewIdGenerator vig,
            boolean isProfile);
}
