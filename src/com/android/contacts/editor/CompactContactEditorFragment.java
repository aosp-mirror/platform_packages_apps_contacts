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
 * limitations under the License
 */

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorBaseActivity.ContactEditor;
import com.android.contacts.editor.ContactEditorBaseFragment.Listener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorFragment extends ContactEditorBaseFragment
        implements ContactEditor {

    private Context mContext;
    private Listener mListener;

    private String mAction;
    private Uri mLookupUri;
    private Bundle mIntentExtras;

    private LinearLayout mContent;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(
                R.layout.compact_contact_editor_fragment, container, false);
        mContent = (LinearLayout) view.findViewById(R.id.editors);
        return view;
    }

    @Override
    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        mAction = action;
        mLookupUri = lookupUri;
        mIntentExtras = intentExtras;
    }

    @Override
    public void setIntentExtras(Bundle extras) {
    }

    @Override
    public boolean save(int saveMode) {
        onSaveCompleted(/* hadChanges =*/ false, saveMode,
                /* saveSucceeded =*/ mLookupUri != null, mLookupUri);
        return true;
    }

    @Override
    public void onJoinCompleted(Uri uri) {
        onSaveCompleted(/* hadChanges =*/ false, SaveMode.RELOAD,
                /* saveSucceeded =*/ uri != null, uri);
    }

    @Override
    public void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
            Uri contactLookupUri) {
        switch (saveMode) {
            case SaveMode.CLOSE:
            case SaveMode.HOME:
                if (mListener != null) {
                    final Intent resultIntent;
                    if (saveSucceeded && contactLookupUri != null) {
                        final Uri lookupUri = maybeConvertToLegacyLookupUri(
                                mContext, contactLookupUri, mLookupUri);
                        resultIntent = composeQuickContactsIntent(mContext, lookupUri);
                    } else {
                        resultIntent = null;
                    }
                    mListener.onSaveFinished(resultIntent);
                }
                break;
        }
    }
}
